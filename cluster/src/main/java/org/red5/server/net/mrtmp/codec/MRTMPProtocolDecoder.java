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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;

import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.ProtocolDecoder;
import org.apache.mina.filter.codec.ProtocolDecoderOutput;
import org.red5.server.net.mrtmp.MRTMPPacket;
import org.red5.server.net.rtmp.message.Packet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Steven Gong (steven.gong@gmail.com)
 */
public class MRTMPProtocolDecoder implements ProtocolDecoder {
	private static final Logger log = LoggerFactory.getLogger(MRTMPProtocolDecoder.class);

	public void decode(IoSession session, IoBuffer in,
			ProtocolDecoderOutput out) throws Exception {
		IoBuffer buffer = (IoBuffer) session.getAttribute("buffer");
		if (buffer == null) {
			buffer = IoBuffer.allocate(16 * 1024);
			buffer.setAutoExpand(true);
			session.setAttribute("buffer", buffer);
		}
		buffer.put(in);
		buffer.flip();
		while (true) {
			if (buffer.remaining() < MRTMPPacket.COMMON_HEADER_LENGTH) {
				break;
			}
			int pos = buffer.position();
			MRTMPPacket.Header header = decodeHeader(buffer);
			if (header == null) {
				buffer.position(pos);
				break;
			}
			if (buffer.remaining() < header.getBodyLength()) {
				buffer.position(pos);
				break;
			}
			MRTMPPacket.Body body = decodeBody(buffer, header);
			MRTMPPacket packet = new MRTMPPacket();
			packet.setHeader(header);
			packet.setBody(body);
			if (log.isDebugEnabled()) {
				log.debug(packet.toString());
			}
			out.write(packet);
		}
		buffer.compact();
	}

	public void dispose(IoSession session) throws Exception {
		// nothing to dispose for decoding
	}

	public void finishDecode(IoSession session, ProtocolDecoderOutput out)
			throws Exception {
		IoBuffer buffer = (IoBuffer) session.getAttribute("buffer");
		if (buffer != null) {
			buffer.free();
		}
	}
	
	public MRTMPPacket.Header decodeHeader(IoBuffer buffer) {
		short type = buffer.getShort();
		short bodyEncoding = buffer.getShort();
		int preserved = buffer.getInt();
		int clientId = buffer.getInt();
		int headerLength = buffer.getInt();
		int bodyLength = buffer.getInt();
		if (buffer.remaining() < headerLength - MRTMPPacket.COMMON_HEADER_LENGTH) {
			return null;
		}
		MRTMPPacket.Header header = null;
		if (type == MRTMPPacket.RTMP && headerLength != MRTMPPacket.RTMP_HEADER_LENGTH) {
			// XXX errrh, something weird happens
			log.warn("Codec error: wrong RTMP header length " + headerLength);
			header = new MRTMPPacket.Header();
			buffer.skip(headerLength - MRTMPPacket.COMMON_HEADER_LENGTH);
		} else if (type == MRTMPPacket.RTMP) {
			header = new MRTMPPacket.RTMPHeader();
			MRTMPPacket.RTMPHeader rtmpHeader = (MRTMPPacket.RTMPHeader) header;
			rtmpHeader.setRtmpType(buffer.getInt());
		} else {
			header = new MRTMPPacket.Header();
			buffer.skip(headerLength - MRTMPPacket.COMMON_HEADER_LENGTH);
		}
		header.setType(type);
		header.setBodyEncoding(bodyEncoding);
		header.setDynamic((preserved & 0x8000000) != 0);
		header.setClientId(clientId);
		header.setHeaderLength(headerLength);
		header.setBodyLength(bodyLength);
		return header;
	}

	public MRTMPPacket.Body decodeBody(IoBuffer buffer, MRTMPPacket.Header header) {
		MRTMPPacket.Body body = null;
		switch (header.getType()) {
			case MRTMPPacket.CONNECT:
			case MRTMPPacket.CLOSE:
				if (header.getBodyLength() != 0) {
					// XXX something weird happens
					log.warn("Codec error: wrong connect/close body length " + header.getBodyLength());
				}
				return new MRTMPPacket.Body();
			case MRTMPPacket.RTMP:
				byte[] byteArray = new byte[header.getBodyLength()];
				buffer.get(byteArray);
				ObjectInputStream ois = null;
				try {
					ois = new ObjectInputStream(new ByteArrayInputStream(byteArray));
					Packet packet = (Packet) ois.readObject();
					body = new MRTMPPacket.RTMPBody();
					MRTMPPacket.RTMPBody rtmpBody = (MRTMPPacket.RTMPBody) body;
					rtmpBody.setRtmpPacket(packet);
				} catch (IOException e) {
					// XXX should not happen
					log.error("", e);
				} catch (ClassNotFoundException e) {
					// XXX should not happen
					log.error("", e);
				}
				break;
			default:
				byteArray = new byte[header.getBodyLength()];
				buffer.get(byteArray);
				body = new MRTMPPacket.Body();
				body.setRawBuf(IoBuffer.wrap(byteArray));
				break;
		}
		return body;
	}
}
