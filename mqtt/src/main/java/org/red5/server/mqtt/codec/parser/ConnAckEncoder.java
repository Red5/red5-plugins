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
import org.eclipse.moquette.proto.messages.ConnAckMessage;
import org.red5.server.mqtt.codec.MQTTProtocol;
import org.red5.server.mqtt.codec.exception.CorruptedFrameException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author andrea
 */
public class ConnAckEncoder extends DemuxEncoder<ConnAckMessage> {

	private static final Logger log = LoggerFactory.getLogger(ConnAckEncoder.class);

	@Override
	public IoBuffer encode(IoSession session, ConnAckMessage message) throws CorruptedFrameException {
		log.trace("CONACK - return code: {} qos: {}", message.getReturnCode(), message.getQos());		
		IoBuffer out = IoBuffer.allocate(4).setAutoExpand(true);
		out.put((byte) (AbstractMessage.CONNACK << 4));
        out.put(MQTTProtocol.encodeRemainingLength(2));
		out.put((byte) (message.isSessionPresent() ? 0x01 : 0x00));
		out.put(message.getReturnCode());
		out.flip();
		log.trace("Encoded CONACK: {}", out);
		return out;
	}

}
