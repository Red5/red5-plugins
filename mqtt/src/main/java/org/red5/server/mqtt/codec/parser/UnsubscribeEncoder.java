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
import org.eclipse.moquette.proto.messages.UnsubscribeMessage;
import org.red5.server.mqtt.codec.MQTTProtocol;
import org.red5.server.mqtt.codec.exception.CorruptedFrameException;

/**
 * Unsubscribe encoder.
 * 
 * @author andrea
 * @author Paul Gregoire
 */
public class UnsubscribeEncoder extends DemuxEncoder<UnsubscribeMessage> {

	@Override
	public IoBuffer encode(IoSession session, UnsubscribeMessage message) throws CorruptedFrameException {
		if (message.topicFilters().isEmpty()) {
			throw new IllegalArgumentException("Found an unsubscribe message with empty topics");
		}
		if (message.getQos() != AbstractMessage.QOSType.LEAST_ONE) {
			throw new IllegalArgumentException("Expected a message with QOS 1, found " + message.getQos());
		}
		IoBuffer out = null;
		IoBuffer variableHeaderBuff = IoBuffer.allocate(4);
		try {
			variableHeaderBuff.putShort((short) message.getMessageID());
			for (String topic : message.topicFilters()) {
				variableHeaderBuff.put(MQTTProtocol.encodeString(topic));
			}
			int variableHeaderSize = variableHeaderBuff.limit();
			byte flags = MQTTProtocol.encodeFlags(message);
			out = IoBuffer.allocate(2 + variableHeaderSize);
			out.put((byte) (AbstractMessage.UNSUBSCRIBE << 4 | flags));
			out.put(MQTTProtocol.encodeRemainingLength(variableHeaderSize));
			variableHeaderBuff.flip();
			out.put(variableHeaderBuff);
			out.flip();
			return out;
		} finally {
			variableHeaderBuff.free();
		}
	}

}
