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

import java.io.UnsupportedEncodingException;

import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.ProtocolDecoderOutput;
import org.eclipse.moquette.proto.messages.AbstractMessage.QOSType;
import org.eclipse.moquette.proto.messages.SubscribeMessage;
import org.red5.server.mqtt.codec.MQTTProtocol;
import org.red5.server.mqtt.codec.exception.CorruptedFrameException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Subscribe decoder.
 *
 * @author andrea
 * @author Paul Gregoire
 */
public class SubscribeDecoder extends DemuxDecoder {

	private static final Logger log = LoggerFactory.getLogger(SubscribeDecoder.class);

	@Override
	public void decode(IoSession session, IoBuffer in, ProtocolDecoderOutput out) throws Exception {
		//Common decoding part
		SubscribeMessage message = new SubscribeMessage();
		in.reset();
		if (!decodeCommonHeader(message, 0x02, in)) {
			in.reset();
			return;
		}
		//check qos level
		if (message.getQos() != QOSType.LEAST_ONE) {
			throw new CorruptedFrameException("Received Subscribe message with QoS other than LEAST_ONE, was: " + message.getQos());
		}
		int start = in.markValue();
		//read  messageIDs
		message.setMessageID(in.getUnsignedShort());
		int readed = in.position() - start;
		log.trace("Subscribe start: {} readed: {}", start, readed);
		while (readed < message.getRemainingLength()) {
			decodeSubscription(in, message);
			readed = in.position() - start;
			log.trace("Subscribe readed: {}", readed);
		}
		if (message.subscriptions().isEmpty()) {
			throw new CorruptedFrameException("subscribe MUST have got at least 1 couple topic/QoS");
		}
		out.write(message);
	}

	/**
	 * Populate the message with couple of Qos, topic
	 * 
	 * @throws CorruptedFrameException
	 */
	private void decodeSubscription(IoBuffer in, SubscribeMessage message) throws UnsupportedEncodingException, CorruptedFrameException {
		String topic = MQTTProtocol.decodeString(in);
		log.trace("Subscribe topic: {}", topic);
		byte qosByte = in.get();
		log.trace("QoS byte: {}", qosByte);
		if ((qosByte & 0xFC) > 0) { //the first 6 bits is reserved => has to be 0
			throw new CorruptedFrameException("subscribe MUST have QoS byte with reserved buts to 0, found " + Integer.toHexString(qosByte));
		}
		byte qos = (byte) (qosByte & 0x03);
		//TODO check qos id 000000xx
		message.addSubscription(new SubscribeMessage.Couple(qos, topic));
	}

}
