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
import org.eclipse.moquette.proto.messages.DisconnectMessage;
import org.red5.server.mqtt.codec.exception.CorruptedFrameException;

/**
 * Disconnect encoder.
 * 
 * @author andrea
 * @author Paul Gregoire
 */
public class DisconnectEncoder extends DemuxEncoder<DisconnectMessage> {

    @Override
	public IoBuffer encode(IoSession session, DisconnectMessage message) throws CorruptedFrameException {
    	IoBuffer out = IoBuffer.allocate(2);
        out.put((byte) (AbstractMessage.DISCONNECT << 4));
        out.put((byte) 0);
        out.flip();
        return out;
    }
    
}
