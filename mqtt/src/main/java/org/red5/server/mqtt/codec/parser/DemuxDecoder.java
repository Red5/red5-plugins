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
import org.red5.server.mqtt.codec.MQTTProtocol;
import org.red5.server.mqtt.codec.exception.CorruptedFrameException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base demux decoder.
 *
 * @author andrea
 * @author Paul Gregoire
 */
public abstract class DemuxDecoder {

	private static final Logger log = LoggerFactory.getLogger(DemuxDecoder.class);

	public abstract void decode(IoSession session, IoBuffer in, ProtocolDecoderOutput out) throws Exception;

	/**
	 * Decodes the first 2 bytes of the MQTT packet. The first byte contain the packet operation code and the flags, the second byte and more contains the overall packet length.
	 * 
	 * @throws CorruptedFrameException
	 */
	protected boolean decodeCommonHeader(AbstractMessage message, IoBuffer in) throws CorruptedFrameException {
		return genericDecodeCommonHeader(message, null, in);
	}

	/**
	 * Do the same as the @see#decodeCommonHeader but having a strong validation on the flags values
	 * 
	 * @throws CorruptedFrameException
	 */
	protected boolean decodeCommonHeader(AbstractMessage message, int expectedFlags, IoBuffer in) throws CorruptedFrameException {
		return genericDecodeCommonHeader(message, expectedFlags, in);
	}

	private boolean genericDecodeCommonHeader(AbstractMessage message, Integer expectedFlagsOpt, IoBuffer in) throws CorruptedFrameException {
		// common decoding part 
		if (in.remaining() < 2) {
			return false;
		}
		byte h1 = in.get();
		byte messageType = (byte) ((h1 & 0x00F0) >> 4);
		byte flags = (byte) (h1 & 0x0F);
		if (expectedFlagsOpt != null) {
			int expectedFlags = expectedFlagsOpt;
			if ((byte) expectedFlags != flags) {
				String hexExpected = Integer.toHexString(expectedFlags);
				String hexReceived = Integer.toHexString(flags);
				throw new CorruptedFrameException(String.format("Received a message with fixed header flags (%s) != expected (%s)", hexReceived, hexExpected));
			}
		}
		boolean dupFlag = ((byte) ((h1 & 0x0008) >> 3) == 1);
		byte qosLevel = (byte) ((h1 & 0x0006) >> 1);
		log.trace("Message - qos level: {}", qosLevel);
		boolean retainFlag = ((byte) (h1 & 0x0001) == 1);
		int remainingLength = MQTTProtocol.decodeRemainingLength(in);
		if (remainingLength == -1) {
			return false;
		}
		message.setMessageType(messageType);
		message.setDupFlag(dupFlag);
		message.setQos(AbstractMessage.QOSType.values()[qosLevel]);
		message.setRetainFlag(retainFlag);
		message.setRemainingLength(remainingLength);
		return true;
	}

}
