/*
 * RED5 Open Source Flash Server - http://code.google.com/p/red5/
 * 
 * Copyright 2006-2012 by respective authors (see below). All rights reserved.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.red5.server.net.mrtmp;

import org.apache.mina.core.buffer.IoBuffer;
import org.red5.server.net.rtmp.message.Packet;

/**
 * @author Steven Gong (steven.gong@gmail.com)
 */
public class MRTMPPacket extends Packet {
	public static final short CONNECT = 0;
	public static final short CLOSE = 1;
	public static final short RTMP = 2;
	
	public static final short JAVA_ENCODING = 0;
	
	public static final int COMMON_HEADER_LENGTH = 20;
	public static final int RTMP_HEADER_LENGTH = COMMON_HEADER_LENGTH + 4;
	
	private Header header;
	private Body body;
	
	static public class Header extends org.red5.server.net.rtmp.message.Header {
		private short type;
		private short bodyEncoding;
		private boolean dynamic;
		private int clientId;
		private int headerLength;
		private int bodyLength;
		
		public int getBodyLength() {
			return bodyLength;
		}
		
		public void setBodyLength(int bodyLength) {
			this.bodyLength = bodyLength;
		}
		
		public int getClientId() {
			return clientId;
		}
		
		public void setClientId(int clientId) {
			this.clientId = clientId;
		}
		
		public int getHeaderLength() {
			return headerLength;
		}
		
		public void setHeaderLength(int headerLength) {
			this.headerLength = headerLength;
		}
		
		public short getType() {
			return type;
		}
		
		public void setType(short type) {
			this.type = type;
		}

		public short getBodyEncoding() {
			return bodyEncoding;
		}

		public void setBodyEncoding(short bodyEncoding) {
			this.bodyEncoding = bodyEncoding;
		}

		public boolean isDynamic() {
			return dynamic;
		}

		public void setDynamic(boolean dynamic) {
			this.dynamic = dynamic;
		}
		
	}
	
	static public class Body {
		private IoBuffer rawBuf;

		public IoBuffer getRawBuf() {
			return rawBuf;
		}

		public void setRawBuf(IoBuffer rawBuf) {
			this.rawBuf = rawBuf;
		}
		
	}
	
	static public class RTMPHeader extends Header {
		private int rtmpType;

		public int getRtmpType() {
			return rtmpType;
		}

		public void setRtmpType(int rtmpType) {
			this.rtmpType = rtmpType;
		}
		
	}
	
	static public class RTMPBody extends Body {
		private Packet rtmpPacket;

		public Packet getRtmpPacket() {
			return rtmpPacket;
		}

		public void setRtmpPacket(Packet rtmpPacket) {
			this.rtmpPacket = rtmpPacket;
		}
	}

	public Body getBody() {
		return body;
	}

	public void setBody(Body body) {
		this.body = body;
	}

	public Header getHeader() {
		return header;
	}

	public void setHeader(Header header) {
		this.header = header;
	}
	
	public String toString() {
		StringBuffer buf = new StringBuffer();
		buf.append("MRTMPPacket: type=");
		switch (header.getType()) {
			case CONNECT:
				buf.append("CONNECT");
				break;
			case CLOSE:
				buf.append("CLOSE");
				break;
			case RTMP:
				buf.append("RTMP");
				break;
			default:
				break;
		}
		buf.append(",isDynamic=" + header.isDynamic());
		buf.append(",clientId=" + header.getClientId());
		if (header.getType() == RTMP) {
			RTMPHeader rtmpHeader = (RTMPHeader) header;
			buf.append(",rtmpType=" + rtmpHeader.rtmpType);
			RTMPBody rtmpBody = (RTMPBody) body;
			buf.append(",rtmpBody=" + rtmpBody.rtmpPacket.getMessage());
		}

		return buf.toString();
	}
}
