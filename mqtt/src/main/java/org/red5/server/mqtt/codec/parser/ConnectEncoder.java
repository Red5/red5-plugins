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
import org.eclipse.moquette.proto.messages.ConnectMessage;
import org.red5.server.mqtt.codec.MQTTProtocol;
import org.red5.server.mqtt.codec.exception.CorruptedFrameException;

/**
 * Connect encoder.
 * 
 * @author andrea
 * @author Paul Gregoire
 */
public class ConnectEncoder extends DemuxEncoder<ConnectMessage> {

    @Override
	public IoBuffer encode(IoSession session, ConnectMessage message) throws CorruptedFrameException {
		IoBuffer out = IoBuffer.allocate(0).setAutoExpand(true);
		IoBuffer staticHeaderBuff = IoBuffer.allocate(12);
		IoBuffer variableHeaderBuff = IoBuffer.allocate(12);
        try {
            staticHeaderBuff.put(MQTTProtocol.encodeString("MQIsdp"));
            //version 
            staticHeaderBuff.put((byte) 0x03);
            //connection flags and Strings
            byte connectionFlags = 0;
            if (message.isCleanSession()) {
                connectionFlags |= 0x02;
            }
            if (message.isWillFlag()) {
                connectionFlags |= 0x04;
            }
            connectionFlags |= ((message.getWillQos() & 0x03) << 3);
            if (message.isWillRetain()) {
                connectionFlags |= 0x020;
            }
            if (message.isPasswordFlag()) {
                connectionFlags |= 0x040;
            }
            if (message.isUserFlag()) {
                connectionFlags |= 0x080;
            }
            staticHeaderBuff.put(connectionFlags);
            //Keep alive timer
            staticHeaderBuff.putShort((short) message.getKeepAlive());
            //Variable part
            if (message.getClientID() != null) {
                variableHeaderBuff.put(MQTTProtocol.encodeString(message.getClientID()));
                if (message.isWillFlag()) {
                    variableHeaderBuff.put(MQTTProtocol.encodeString(message.getWillTopic()));
                    variableHeaderBuff.put(MQTTProtocol.encodeString(message.getWillMessage()));
                }
                if (message.isUserFlag() && message.getUsername() != null) {
                    variableHeaderBuff.put(MQTTProtocol.encodeString(message.getUsername()));
                    if (message.isPasswordFlag() && message.getPassword() != null) {
                        variableHeaderBuff.put(MQTTProtocol.encodeString(message.getPassword()));
                    }
                }
            }
            int variableHeaderSize = variableHeaderBuff.remaining();
            out.put((byte) (AbstractMessage.CONNECT << 4));
            out.put(MQTTProtocol.encodeRemainingLength(12 + variableHeaderSize));
            staticHeaderBuff.flip();
            out.put(staticHeaderBuff.array());
            variableHeaderBuff.flip();
            out.put(variableHeaderBuff.array());
            out.flip();
            return out;
        } finally {
            staticHeaderBuff.free();
            variableHeaderBuff.free();
        }
    }
    
}
