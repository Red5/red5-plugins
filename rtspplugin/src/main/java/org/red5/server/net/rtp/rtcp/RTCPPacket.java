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

import org.apache.mina.core.buffer.IoBuffer;
import org.red5.io.object.UnsignedByte;
import org.red5.io.object.UnsignedInt;
import org.red5.server.net.rtp.Packet;

/**
 * @author Matteo Merli (matteo.merli@gmail.com)
 */
public class RTCPPacket implements Packet {

	// private static Logger log = LoggerFactory.getLogger( RTCPPacket.class );

	public enum Type {
		/** Sender Report */
		SR(200),
		/** Receiver Report */
		RR(201),
		/** Source description */
		SDES(202),
		/** End of participation */
		BYE(203),
		/** Application specific */
		APP(204),

		NONE(0);

		private final UnsignedByte value;

		public static Type valueOf(UnsignedByte value) {
			for (Type t : Type.values())
				if (t.value.equals(value))
					return t;
			return NONE;
		}

		private Type(int value) {
			this.value = new UnsignedByte(value);
		}

		public UnsignedByte getValue() {
			return value;
		}
	}

	/** protocol version */
	protected byte version;

	/** padding flag */
	protected boolean padding;

	/** varies by packet type */
	protected byte count;

	/** RTCP packet type */
	protected UnsignedByte packetType;

	/** pkt len in words, w/o this word */
	protected short length;

	protected UnsignedInt ssrc;

	// private RTCPInfo rtcpInfo;
	protected byte[] packetBuffer;

	/**
	 * TODO: At this moment, the RTCP packet is not completely parsed, only some
	 * informations are extracted such as the SSRC identificator. The rest of
	 * the packet is saved but not processed nor validated (for now).
	 */
	public RTCPPacket(IoBuffer buffer) {
		byte c = buffer.get();
		// |V=2|P=1| SC=5 |
		version = (byte) ((c & 0xC0) >> 6);
		padding = ((c & 0x20) >> 5) == 1;
		count = (byte) (c & 0x1F);
		packetType = new UnsignedByte(buffer.get());
		length = buffer.getShort();

		ssrc = new UnsignedInt(buffer.getInt());

		// we have already read 2 * 4 = 8 bytes
		// out of ( length + 1 ) * 4 totals
		int size = Math.min(((length + 1) * 4 - 8), buffer.remaining());
		packetBuffer = new byte[size];
		buffer.get(packetBuffer);

		/*
		 * System.err.println( "version: " + version ); System.err.println(
		 * "Padding: " + padding ); System.err.println( "count: " + count );
		 * System.err.println( "packetType: " + Type.fromByte( packetType ) );
		 * System.err.println( "length: " + length ); System.err.println(
		 * "ssrc: " + Long.toHexString( (long) ssrc & 0xFFFFFFFFL ) );
		 * System.err.println( "buffer: " + Arrays.toString( packetBuffer ) );
		 */

		/**
		 * <pre>
		 * switch (Type.fromByte(packetType)) {
		 * 	case SR:
		 * 	case RR:
		 * 	case SDES:
		 * 		rtcpInfo = new SDESInfo(this, buffer);
		 * 		break;
		 * 	case BYE:
		 * 	case APP:
		 * 	case NONE:
		 * 		log.debug(&quot;Invalid RTCP	 packet.&quot;);
		 * }
		 * </pre>
		 */
	}

	protected RTCPPacket() {
	}

	/**
	 * @return Returns the ssrc.
	 */
	public UnsignedInt getSsrc() {
		return ssrc;
	}

	/**
	 * @param ssrc
	 *            The ssrc to set.
	 */
	public void setSsrc(UnsignedInt ssrc) {
		this.ssrc = ssrc;
	}

	public Type getType() {
		return Type.valueOf(packetType);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see rtspproxy.rtp.Packet#toIoBuffer()
	 */
	public IoBuffer toByteBuffer() {
		int packetSize = (packetBuffer.length + 2) * 4; // content
		IoBuffer buffer = IoBuffer.allocate(packetSize);
		buffer.limit(packetSize);

		// |V=2|P=1| SC=5 |
		byte c;
		c = (byte) ((version << 6) & 0xC0);
		c |= (byte) (((padding ? 1 : 0) << 5) & 0x20);
		c |= (byte) (count & 0x1F);
		buffer.put(c);
		buffer.put(packetType.getBytes());
		buffer.putShort(length);
		buffer.put(ssrc.getBytes());

		buffer.put(packetBuffer);
		buffer.rewind();
		return buffer;
	}
}
