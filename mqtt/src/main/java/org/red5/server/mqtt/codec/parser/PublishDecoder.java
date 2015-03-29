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

import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.ProtocolDecoderOutput;
import org.eclipse.moquette.proto.messages.AbstractMessage;
import org.eclipse.moquette.proto.messages.PublishMessage;
import org.red5.server.mqtt.codec.MQTTDecoder;
import org.red5.server.mqtt.codec.MQTTProtocol;
import org.red5.server.mqtt.codec.exception.CorruptedFrameException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Publish decoder.
 *
 * @author andrea
 * @author Paul Gregoire
 */
public class PublishDecoder extends DemuxDecoder {

	private static Logger LOG = LoggerFactory.getLogger(PublishDecoder.class);

	@Override
	public void decode(IoSession session, IoBuffer in, ProtocolDecoderOutput out) throws Exception {
		LOG.info("decode invoked with buffer {}", in);
		in.reset();
		int startPos = in.markValue();
		//Common decoding part
		PublishMessage message = new PublishMessage();
		if (!decodeCommonHeader(message, in)) {
			LOG.info("decode ask for more data after {}", in);
			in.reset();
			return;
		}
		if (((int) session.getAttribute(MQTTDecoder.PROTOCOL_VERSION)) == MQTTProtocol.VERSION_3_1_1) {
			if (message.getQos() == AbstractMessage.QOSType.MOST_ONE && message.isDupFlag()) {
				//bad protocol, if QoS=0 => DUP = 0
				throw new CorruptedFrameException("Received a PUBLISH with QoS=0 & DUP = 1, MQTT 3.1.1 violation");
			}
			if (message.getQos() == AbstractMessage.QOSType.RESERVED) {
				throw new CorruptedFrameException("Received a PUBLISH with QoS flags setted 10 b11, MQTT 3.1.1 violation");
			}
		}
		int remainingLength = message.getRemainingLength();
		//Topic name
		String topic = MQTTProtocol.decodeString(in);
		if (topic == null) {
			in.reset();
			return;
		}
		if (topic.contains("+") || topic.contains("#")) {
			throw new CorruptedFrameException("Received a PUBLISH with topic containting wild card chars, topic: " + topic);
		}
		message.setTopicName(topic);
		if (message.getQos() == AbstractMessage.QOSType.LEAST_ONE || message.getQos() == AbstractMessage.QOSType.EXACTLY_ONCE) {
			message.setMessageID(in.getUnsignedShort());
		}
		int stopPos = in.position();
		//read the payload
		int payloadSize = remainingLength - (stopPos - startPos - 2) + (MQTTProtocol.numBytesToEncode(remainingLength) - 1);
		LOG.info("payload size: {}", payloadSize);
		if (in.remaining() < payloadSize) {
			in.reset();
			return;
		}
		byte[] payload = new byte[payloadSize];
		in.get(payload);
		message.setPayload(payload);
		out.write(message);
	}

}
