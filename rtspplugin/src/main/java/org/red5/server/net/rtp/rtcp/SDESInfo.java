package org.red5.server.net.rtp.rtcp;

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

import java.nio.ByteBuffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Matteo Merli (matteo.merli@gmail.com)
 */
public class SDESInfo implements RTCPInfo {
	static Logger log = LoggerFactory.getLogger(SDESInfo.class);

	public enum Type {
		END(0), CNAME(1), NAME(2), EMAIL(3), PHONE(4), LOC(5), TOOL(6), NOTE(7), PRIV(
				8);

		public final byte value;

		public static Type fromByte(byte value) {
			for (Type t : Type.values())
				if (t.value == value)
					return t;
			return END;
		}

		private Type(int value) {
			this.value = (byte) value;
		}
	}

	private class Chunk {

		@SuppressWarnings("unused")
		public int ssrc;

		public Type type;

		@SuppressWarnings("unused")
		public byte[] value;
	}

	private Chunk[] chunkList;

	public SDESInfo(RTCPPacket packet, ByteBuffer buffer) {
		// int totalBytesToRead = packet.length * 4;
		byte sourceCount = packet.count;

		chunkList = new Chunk[sourceCount];

		for (byte i = 0; i < sourceCount; i++) {
			chunkList[i] = new Chunk();
			Chunk c = chunkList[i];

			c.ssrc = buffer.getInt();
			c.type = Type.fromByte(buffer.get());

			switch (c.type) {
				case PRIV:
					log.debug("Chunk private...");
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see rtspproxy.rtp.rtcp.RTCPInfo#toBuffer()
	 */
	public ByteBuffer toBuffer() {
		return null;
	}

}
