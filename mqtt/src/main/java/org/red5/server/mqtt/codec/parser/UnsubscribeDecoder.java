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
import org.eclipse.moquette.proto.messages.UnsubscribeMessage;
import org.red5.server.mqtt.codec.MQTTProtocol;
import org.red5.server.mqtt.codec.exception.CorruptedFrameException;

/**
 * Unsubscribe decoder.
 *
 * @author andrea
 * @author Paul Gregoire
 */
public class UnsubscribeDecoder extends DemuxDecoder {

	@Override
	public void decode(IoSession session, IoBuffer in, ProtocolDecoderOutput out) throws Exception {
		//Common decoding part
		in.reset();
		UnsubscribeMessage message = new UnsubscribeMessage();
		if (!decodeCommonHeader(message, 0x02, in)) {
			in.reset();
			return;
		}
		//check qos level
		if (message.getQos() != AbstractMessage.QOSType.LEAST_ONE) {
			throw new CorruptedFrameException("Found an Usubscribe message with qos other than LEAST_ONE, was: " + message.getQos());
		}
		int start = in.markValue();
		//read  messageIDs
		message.setMessageID(in.getUnsignedShort());
		int readed = in.markValue() - start;
		while (readed < message.getRemainingLength()) {
			message.addTopicFilter(MQTTProtocol.decodeString(in));
			readed = in.position() - start;
		}
		if (message.topicFilters().isEmpty()) {
			throw new CorruptedFrameException("unsubscribe MUST have got at least 1 topic");
		}
		out.write(message);
	}

}
