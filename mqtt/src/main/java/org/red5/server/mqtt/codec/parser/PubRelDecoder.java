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
import org.eclipse.moquette.proto.messages.MessageIDMessage;
import org.eclipse.moquette.proto.messages.PubRelMessage;
import org.red5.server.mqtt.codec.exception.CorruptedFrameException;

/**
 *
 * @author andrea
 */
public class PubRelDecoder extends DemuxDecoder {
    
    @Override
    public void decode(IoSession session, IoBuffer in, ProtocolDecoderOutput out) throws UnsupportedEncodingException, CorruptedFrameException {
        in.reset();
        //Common decoding part
        MessageIDMessage message = new PubRelMessage();
        if (!decodeCommonHeader(message, 0x02, in)) {
            in.reset();
            return;
        }        
        //read  messageIDs
        message.setMessageID(in.getUnsignedShort());
        out.write(message);
    }

}

