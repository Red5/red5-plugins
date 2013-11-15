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

import org.apache.mina.core.session.IoSession;
import org.red5.server.net.rtmp.message.Packet;

/**
 * @author Steven Gong (steven.gong@gmail.com)
 */
public class BaseMRTMPConnection implements IMRTMPConnection {
	private IoSession ioSession;
	
	public void write(int clientId, Packet packet) {
		MRTMPPacket mrtmpPacket = new MRTMPPacket();
		MRTMPPacket.RTMPHeader header = new MRTMPPacket.RTMPHeader();
		MRTMPPacket.RTMPBody body = new MRTMPPacket.RTMPBody();
		mrtmpPacket.setHeader(header);
		mrtmpPacket.setBody(body);
		header.setType(MRTMPPacket.RTMP);
		header.setClientId(clientId);
		// header and body length will be filled in the protocol codec
		header.setRtmpType(packet.getHeader().getDataType());
		body.setRtmpPacket(packet);
		ioSession.write(mrtmpPacket);
	}
	
	public void connect(int clientId) {
		MRTMPPacket mrtmpPacket = new MRTMPPacket();
		MRTMPPacket.Header header = new MRTMPPacket.Header();
		MRTMPPacket.Body body = new MRTMPPacket.Body();
		mrtmpPacket.setHeader(header);
		mrtmpPacket.setBody(body);
		header.setType(MRTMPPacket.CONNECT);
		header.setClientId(clientId);
		// header and body length will be filled in the protocol codec
		ioSession.write(mrtmpPacket);
	}

	public void disconnect(int clientId) {
		MRTMPPacket mrtmpPacket = new MRTMPPacket();
		MRTMPPacket.Header header = new MRTMPPacket.Header();
		MRTMPPacket.Body body = new MRTMPPacket.Body();
		mrtmpPacket.setHeader(header);
		mrtmpPacket.setBody(body);
		header.setType(MRTMPPacket.CLOSE);
		header.setClientId(clientId);
		// header and body length will be filled in the protocol codec
		ioSession.write(mrtmpPacket);		
	}

	public void close() {
		ioSession.close(true);
	}

	public IoSession getIoSession() {
		return ioSession;
	}

	public void setIoSession(IoSession ioSession) {
		this.ioSession = ioSession;
	}
}
