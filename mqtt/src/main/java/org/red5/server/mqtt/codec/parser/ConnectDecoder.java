/*
 * Copyright (c) 2012-2015 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 * The Eclipse Public License is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * The Apache License v2.0 is available at
 * http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */
package org.red5.server.mqtt.codec.parser;

import static org.red5.server.mqtt.codec.MQTTProtocol.VERSION_3_1;
import static org.red5.server.mqtt.codec.MQTTProtocol.VERSION_3_1_1;

import java.io.UnsupportedEncodingException;

import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.ProtocolDecoderOutput;
import org.eclipse.moquette.proto.messages.AbstractMessage;
import org.eclipse.moquette.proto.messages.ConnectMessage;
import org.red5.server.mqtt.codec.MQTTDecoder;
import org.red5.server.mqtt.codec.MQTTProtocol;
import org.red5.server.mqtt.codec.exception.CorruptedFrameException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Connect decoder.
 *
 * @author andrea
 * @author Paul Gregoire
 */
public class ConnectDecoder extends DemuxDecoder {

	private static final Logger log = LoggerFactory.getLogger(ConnectDecoder.class);

	public static final String CONNECT_STATUS = "connected";

	@Override
	public void decode(IoSession session, IoBuffer in, ProtocolDecoderOutput out) throws UnsupportedEncodingException, CorruptedFrameException {
		in.reset();
		//Common decoding part
		ConnectMessage message = new ConnectMessage();
		if (!decodeCommonHeader(message, 0x00, in)) {
			in.reset();
			return;
		}
		int remainingLength = message.getRemainingLength();
		log.trace("remainingLength: {}", remainingLength);
		int start = in.markValue();
		int protocolNameLen = in.getUnsignedShort();
		log.trace("protocolNameLen: {}", protocolNameLen);
		byte[] encProtoName;
		String protoName;
		switch (protocolNameLen) {
			case 6:
				//MQTT version 3.1 "MQIsdp"
				//ProtocolName 8 bytes or 6 bytes
				if (in.remaining() < 10) {
					in.reset();
					return;
				}
				encProtoName = new byte[6];
				in.get(encProtoName);
				protoName = new String(encProtoName, "UTF-8");
				if (!"MQIsdp".equals(protoName)) {
					in.remaining();
					throw new CorruptedFrameException("Invalid protoName: " + protoName);
				}
				message.setProtocolName(protoName);
				session.setAttribute(MQTTDecoder.PROTOCOL_VERSION, (int) VERSION_3_1);
				break;
			case 4:
				//MQTT version 3.1.1 "MQTT"
				//ProtocolName 6 bytes
				if (in.remaining() < 8) {
					in.reset();
					return;
				}
				encProtoName = new byte[4];
				in.get(encProtoName);
				protoName = new String(encProtoName, "UTF-8");
				if (!"MQTT".equals(protoName)) {
					in.reset();
					throw new CorruptedFrameException("Invalid protoName: " + protoName);
				}
				message.setProtocolName(protoName);
				session.setAttribute(MQTTDecoder.PROTOCOL_VERSION, (int) VERSION_3_1_1);
				break;
			default:
				//protocol broken
				throw new CorruptedFrameException("Invalid protoName size: " + protocolNameLen);
		}
		log.trace("protoName: {}", protoName);
		//ProtocolVersion 1 byte (value 0x03 for 3.1, 0x04 for 3.1.1)
		message.setProcotolVersion(in.get());
		if (message.getProcotolVersion() == VERSION_3_1_1) {
			//if 3.1.1, check the flags (dup, retain and qos == 0)
			if (message.isDupFlag() || message.isRetainFlag() || message.getQos() != AbstractMessage.QOSType.MOST_ONE) {
				throw new CorruptedFrameException("Received a CONNECT with fixed header flags != 0");
			}

			//check if this is another connect from the same client on the same session
			Boolean alreadyConnected = (Boolean) session.getAttribute(ConnectDecoder.CONNECT_STATUS);
			if (alreadyConnected == null) {
				//never set
				session.setAttribute(ConnectDecoder.CONNECT_STATUS, Boolean.TRUE);
			} else if (alreadyConnected) {
				throw new CorruptedFrameException("Received a second CONNECT on the same network connection");
			}
		}

		//Connection flag
		byte connFlags = in.get();
		if (message.getProcotolVersion() == VERSION_3_1_1) {
			if ((connFlags & 0x01) != 0) { //bit(0) of connection flags is != 0
				throw new CorruptedFrameException("Received a CONNECT with connectionFlags[0(bit)] != 0");
			}
		}

		boolean cleanSession = ((connFlags & 0x02) >> 1) == 1;
		boolean willFlag = ((connFlags & 0x04) >> 2) == 1;
		byte willQos = (byte) ((connFlags & 0x18) >> 3);
		if (willQos > 2) {
			in.reset();
			throw new CorruptedFrameException("Expected will QoS in range 0..2 but found: " + willQos);
		}
		boolean willRetain = ((connFlags & 0x20) >> 5) == 1;
		boolean passwordFlag = ((connFlags & 0x40) >> 6) == 1;
		boolean userFlag = ((connFlags & 0x80) >> 7) == 1;
		//a password is true iff user is true
		if (!userFlag && passwordFlag) {
			in.reset();
			throw new CorruptedFrameException("Expected password flag to true if the user flag is true but was: " + passwordFlag);
		}
		message.setCleanSession(cleanSession);
		message.setWillFlag(willFlag);
		message.setWillQos(willQos);
		message.setWillRetain(willRetain);
		message.setPasswordFlag(passwordFlag);
		message.setUserFlag(userFlag);

		//Keep Alive timer 2 bytes
		//int keepAlive = Utils.readWord(in);
		int keepAlive = in.getUnsignedShort();
		message.setKeepAlive(keepAlive);

		if ((remainingLength == 12 && message.getProcotolVersion() == VERSION_3_1) || (remainingLength == 10 && message.getProcotolVersion() == VERSION_3_1_1)) {
			out.write(message);
			return;
		}

		//Decode the ClientID
		String clientID = MQTTProtocol.decodeString(in);
		if (clientID == null) {
			in.reset();
			return;
		}
		message.setClientID(clientID);

		//Decode willTopic
		if (willFlag) {
			String willTopic = MQTTProtocol.decodeString(in);
			if (willTopic == null) {
				in.reset();
				return;
			}
			message.setWillTopic(willTopic);
		}

		//Decode willMessage
		if (willFlag) {
			String willMessage = MQTTProtocol.decodeString(in);
			if (willMessage == null) {
				in.reset();
				return;
			}
			message.setWillMessage(willMessage);
		}

		//Compatibility check with v3.0, remaining length has precedence over
		//the user and password flags
		int readed = in.markValue() - start;
		if (readed == remainingLength) {
			out.write(message);
			return;
		}

		//Decode username
		if (userFlag) {
			String userName = MQTTProtocol.decodeString(in);
			if (userName == null) {
				in.reset();
				return;
			}
			message.setUsername(userName);
		}

		readed = in.position() - start;
		if (readed == remainingLength) {
			out.write(message);
			return;
		}

		//Decode password
		if (passwordFlag) {
			String password = MQTTProtocol.decodeString(in);
			if (password == null) {
				in.reset();
				return;
			}
			message.setPassword(password);
		}

		out.write(message);
	}

}
