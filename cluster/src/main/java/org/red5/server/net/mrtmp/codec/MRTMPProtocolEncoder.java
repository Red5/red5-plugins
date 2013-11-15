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

package org.red5.server.net.mrtmp.codec;

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;

import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.ProtocolEncoder;
import org.apache.mina.filter.codec.ProtocolEncoderOutput;
import org.red5.server.net.mrtmp.MRTMPPacket;

/**
 * @author Steven Gong (steven.gong@gmail.com)
 */
public class MRTMPProtocolEncoder implements ProtocolEncoder {

	public void dispose(IoSession session) throws Exception {
	}

	public void encode(IoSession session, Object message,
			ProtocolEncoderOutput out) throws Exception {
		MRTMPPacket packet = (MRTMPPacket) message;
		MRTMPPacket.Header header = packet.getHeader();
		IoBuffer buf = null;
		switch (header.getType()) {
			case MRTMPPacket.CONNECT:
			case MRTMPPacket.CLOSE:
				buf = IoBuffer.allocate(MRTMPPacket.COMMON_HEADER_LENGTH);
				buf.setAutoExpand(true);
				break;
			case MRTMPPacket.RTMP:
				buf = IoBuffer.allocate(MRTMPPacket.RTMP_HEADER_LENGTH);
				buf.setAutoExpand(true);
				break;
			default:
				break;
		}
		if (buf == null) {
			return;
		}
		buf.putShort(header.getType());
		buf.putShort(MRTMPPacket.JAVA_ENCODING);
		int preserved = header.isDynamic() ? 0x80000000 : 0;
		buf.putInt(preserved);
		buf.putInt(header.getClientId());
		if (header.getType() == MRTMPPacket.CONNECT ||
				header.getType() == MRTMPPacket.CLOSE) {
			buf.putInt(MRTMPPacket.COMMON_HEADER_LENGTH);
			buf.putInt(0);
		} else if (header.getType() == MRTMPPacket.RTMP) {
			buf.putInt(MRTMPPacket.RTMP_HEADER_LENGTH);
			int bodyLengthPos = buf.position();
			buf.putInt(0);
			MRTMPPacket.RTMPHeader rtmpHeader = (MRTMPPacket.RTMPHeader) packet.getHeader();
			buf.putInt(rtmpHeader.getRtmpType());
			MRTMPPacket.RTMPBody body = (MRTMPPacket.RTMPBody) packet.getBody();
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			ObjectOutputStream oos = new ObjectOutputStream(baos);
			oos.writeObject(body.getRtmpPacket());
			oos.close();
			buf.put(baos.toByteArray());
			// substract the 8-byte body length field and rtmp type field
			buf.putInt(bodyLengthPos, buf.position() - bodyLengthPos - 8);
		}
		buf.flip();
		out.write(buf);
	}
}
