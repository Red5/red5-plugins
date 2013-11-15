package org.red5.server.net.rtsp.codec;

/*
 * RED5 Open Source Flash Server - http://www.osflash.org/red5
 * 
 * Copyright (c) 2006-2008 by respective authors (see below). All rights reserved.
 * 
 * This library is free software; you can redistribute it and/or modify it under the 
 * terms of the GNU Lesser General Public License as published by the Free Software 
 * Foundation; either version 2.1 of the License, or (at your option) any later 
 * version. 
 * 
 * This library is distributed in the hope that it will be useful, but WITHOUT ANY 
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A 
 * PARTICULAR PURPOSE. See the GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License along 
 * with this library; if not, write to the Free Software Foundation, Inc., 
 * 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA 
 */

/***************************************************************************
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *                                                                         *
 *   Copyright (C) 2005 - Matteo Merli - matteo.merli@gmail.com            *
 *                                                                         *
 ***************************************************************************/

import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.ProtocolEncoder;
import org.apache.mina.filter.codec.ProtocolEncoderException;
import org.apache.mina.filter.codec.ProtocolEncoderOutput;
import org.red5.server.net.rtsp.messages.RTSPMessage;

/**
 * Encode a RTSP message into a buffer for sending.
 * 
 * @author Matteo Merli (matteo.merli@gmail.com)
 */
public class RTSPEncoder implements ProtocolEncoder {

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.apache.mina.protocol.ProtocolEncoder#encode(org.apache.mina.protocol.ProtocolSession,
	 *      java.lang.Object, org.apache.mina.protocol.ProtocolEncoderOutput)
	 */
	public void encode(IoSession session, Object message,
			ProtocolEncoderOutput out) throws ProtocolEncoderException {
		// Serialization to string is already provided in RTSP messages.
		String val = ((RTSPMessage) message).toString();
		/*
		IoBuffer buf = IoBuffer.allocate( val.length() );
		for ( int i = 0; i < val.length(); i++ ) {
			buf.put( (byte) val.charAt( i ) );
		}

		buf.flip();
		*/

		// TODO: Alternative implementation, should be better.
		IoBuffer buf = IoBuffer.wrap(val.getBytes());

		out.write(buf);
	}

	/* (non-Javadoc)
	 * @see org.apache.mina.filter.codec.ProtocolEncoder#dispose(org.apache.mina.common.IoSession)
	 */
	public void dispose(IoSession arg0) throws Exception {
		// Don't need to do nothing
	}
}
