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

package org.red5.server.net.rtmp;

import org.apache.mina.core.buffer.IoBuffer;
import org.red5.server.api.scheduling.ISchedulingService;
import org.red5.server.net.mrtmp.IMRTMPConnection;
import org.red5.server.net.mrtmp.IMRTMPOriginManager;
import org.red5.server.net.mrtmp.OriginMRTMPHandler;
import org.red5.server.net.rtmp.codec.RTMP;
import org.red5.server.net.rtmp.message.Packet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A pseudo-connection on Origin that represents a client
 * on Edge.
 *
 * The connection is created behind a MRTMP connection so
 * no handshake job or keep-alive job is necessary. No raw byte
 * data write is needed either.
 *
 * @author Steven Gong (steven.gong@gmail.com)
 * @version $Id$
 */
public class RTMPOriginConnection extends RTMPConnection {
	
	private static final Logger log = LoggerFactory.getLogger(RTMPOriginConnection.class);
	
	private int ioSessionId;
	private IMRTMPOriginManager mrtmpManager;
	private OriginMRTMPHandler handler;
	private RTMP state;

	public RTMPOriginConnection(String type, int clientId) {
		this(type, clientId, 0);
	}

	public RTMPOriginConnection(String type, int clientId, int ioSessionId) {
		super(type);
		setId(clientId);
		this.ioSessionId = ioSessionId;
		state = new RTMP();
		state.setState(RTMP.STATE_CONNECTED);
	}
	
	public int getIoSessionId() {
		return ioSessionId;
	}

	public void setMrtmpManager(IMRTMPOriginManager mrtmpManager) {
		this.mrtmpManager = mrtmpManager;
	}

	public void setHandler(OriginMRTMPHandler handler) {
		this.handler = handler;
	}

	public RTMP getState() {
		return state;
	}

	@Override
	protected void onInactive() {
		// Edge already tracks the activity
		// no need to do again here.
	}

//	@Override
//	public void rawWrite(IoBuffer out) {
//		// won't write any raw data on the wire
//		// XXX should we throw exception here
//		// to indicate an abnormal state ?
//		log.warn("Erhhh... Raw write. Shouldn't be in here!");
//	}

	@Override
	public void write(Packet packet) {
		IMRTMPConnection conn = mrtmpManager.lookupMRTMPConnection(this);
		if (conn == null) {
			// the connect is gone
			log.debug("Client {} is gone!", getId());
			return;
		}
		if (!type.equals(PERSISTENT)) {
			mrtmpManager.associate(this, conn);
		}
		log.debug("Origin writing packet to client {}:{}", getId(), packet.getMessage());
		conn.write(getId(), packet);
	}

	@Override
	public void startRoundTripMeasurement() {
		// Edge already tracks the RTT
		// no need to track RTT here.
	}

//	@Override
//	protected void startWaitForHandshake(ISchedulingService service) {
//		// no handshake in MRTMP, simply ignore
//	}

	@Override
	synchronized public void close() {
		if (state.getState() == RTMP.STATE_DISCONNECTED) {
			return;
		}
		IMRTMPConnection conn = mrtmpManager.lookupMRTMPConnection(this);
		if (conn != null) {
			conn.disconnect(getId());
		}
		handler.closeConnection(this);
	}
	
	synchronized public void realClose() {
		if (state.getState() != RTMP.STATE_DISCONNECTED) {
			state.setState(RTMP.STATE_DISCONNECTED);
			super.close();
		}
	}

	@Override
	public void writeRaw(IoBuffer out) {
		// TODO Auto-generated method stub
		
	}
	
}
