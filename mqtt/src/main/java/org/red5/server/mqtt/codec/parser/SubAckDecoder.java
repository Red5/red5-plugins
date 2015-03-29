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
import org.eclipse.moquette.proto.messages.SubAckMessage;

/**
 * SubAck decoder.
 *
 * @author andrea
 * @author Paul Gregoire
 */
public class SubAckDecoder extends DemuxDecoder {

	@Override
	public void decode(IoSession session, IoBuffer in, ProtocolDecoderOutput out) throws Exception {
		//Common decoding part
		in.reset();
		SubAckMessage message = new SubAckMessage();
		if (!decodeCommonHeader(message, 0x00, in)) {
			in.reset();
			return;
		}
		int remainingLength = message.getRemainingLength();
		//MessageID
		message.setMessageID(in.getUnsignedShort());
		remainingLength -= 2;
		//Qos array
		if (in.remaining() < remainingLength) {
			in.reset();
			return;
		}
		for (int i = 0; i < remainingLength; i++) {
			byte qos = in.get();
			message.addType(AbstractMessage.QOSType.values()[qos]);
		}
		out.write(message);
	}

}
