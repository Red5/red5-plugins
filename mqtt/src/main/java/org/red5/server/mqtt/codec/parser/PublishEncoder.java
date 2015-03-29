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
import org.eclipse.moquette.proto.messages.AbstractMessage;
import org.eclipse.moquette.proto.messages.PublishMessage;
import org.red5.server.mqtt.codec.MQTTProtocol;
import org.red5.server.mqtt.codec.exception.CorruptedFrameException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Publish encoder.
 * 
 * @author andrea
 * @author Paul Gregoire
 */
public class PublishEncoder extends DemuxEncoder<PublishMessage> {

	private static final Logger log = LoggerFactory.getLogger(PublishEncoder.class);

	@Override
	public IoBuffer encode(IoSession session, PublishMessage message) throws CorruptedFrameException {
		if (message.getQos() == AbstractMessage.QOSType.RESERVED) {
			throw new IllegalArgumentException("Found a message with RESERVED Qos");
		}
		if (message.getTopicName() == null || message.getTopicName().isEmpty()) {
			throw new IllegalArgumentException("Found a message with empty or null topic name");
		}
		IoBuffer variableHeaderBuff = IoBuffer.allocate(2).setAutoExpand(true);
		try {
			variableHeaderBuff.put(MQTTProtocol.encodeString(message.getTopicName()));
			if (message.getQos() == AbstractMessage.QOSType.LEAST_ONE || message.getQos() == AbstractMessage.QOSType.EXACTLY_ONCE) {
				if (message.getMessageID() == -1) {
					throw new IllegalArgumentException("Found a message with QOS 1 or 2 and not MessageID setted");
				}
				variableHeaderBuff.putShort((short) message.getMessageID());
			}
			//int lengthInBytes = MQTTProtocol.numBytesToEncode(variableHeaderBuff.limit());
			//log.trace("lengthInBytes: {}", lengthInBytes);			
			variableHeaderBuff.flip();
			log.trace("Publish variableBuf: {}", variableHeaderBuff);
			int variableHeaderSize = variableHeaderBuff.limit();
			log.trace("Publish variableHeaderSize: {}", variableHeaderSize);

			byte flags = MQTTProtocol.encodeFlags(message);

			IoBuffer out = IoBuffer.allocate(2 + variableHeaderSize).setAutoExpand(true);
			out.put((byte) (AbstractMessage.PUBLISH << 4 | flags));
			out.put(MQTTProtocol.encodeRemainingLength(variableHeaderSize + message.getPayload().length));
			out.put(variableHeaderBuff);
			out.put(message.getPayload());
			out.flip();
			log.trace("Publish out: {}", out);
			return out;
		} finally {
			variableHeaderBuff.free();
		}
	}

}
