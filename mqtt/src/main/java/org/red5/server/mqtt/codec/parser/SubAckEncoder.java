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
import org.eclipse.moquette.proto.messages.SubAckMessage;
import org.red5.server.mqtt.codec.MQTTProtocol;
import org.red5.server.mqtt.codec.exception.CorruptedFrameException;

/**
 * SubAck encoder.
 * 
 * @author andrea
 * @author Paul Gregoire
 */
public class SubAckEncoder extends DemuxEncoder<SubAckMessage> {

	@Override
	public IoBuffer encode(IoSession session, SubAckMessage message) throws CorruptedFrameException {
		if (message.types().isEmpty()) {
			throw new IllegalArgumentException("Found a suback message with empty topics");
		}
		int variableHeaderSize = 2 + message.types().size();
		IoBuffer out = IoBuffer.allocate(6 + variableHeaderSize);
		out.put((byte) (AbstractMessage.SUBACK << 4));
		out.put(MQTTProtocol.encodeRemainingLength(variableHeaderSize));
		out.putShort((short) message.getMessageID());
		for (AbstractMessage.QOSType c : message.types()) {
			out.put((byte) c.ordinal());
		}
		out.flip();
		return out;
	}

}
