/*
0 * RED5 Open Source Flash Server - http://code.google.com/p/red5/
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

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.apache.mina.core.service.IoHandlerAdapter;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.ProtocolCodecFactory;
import org.apache.mina.filter.codec.ProtocolCodecFilter;
import org.apache.mina.filter.logging.LoggingFilter;
import org.red5.server.api.IConnection;
import org.red5.server.net.rtmp.IRTMPHandler;
import org.red5.server.net.rtmp.RTMPConnection;
import org.red5.server.net.rtmp.RTMPOriginConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Steven Gong (steven.gong@gmail.com)
 */
public class OriginMRTMPHandler extends IoHandlerAdapter {
	
	private Logger log = LoggerFactory.getLogger(OriginMRTMPHandler.class);
	
	private IMRTMPOriginManager mrtmpManager;
	private ProtocolCodecFactory codecFactory;
	private IRTMPHandler handler;
	private Map<Integer, RTMPOriginConnection> dynConnMap =
		new HashMap<Integer, RTMPOriginConnection>();
	private Map<StaticConnId, RTMPOriginConnection> statConnMap =
		new HashMap<StaticConnId, RTMPOriginConnection>();
	private ReadWriteLock lock = new ReentrantReadWriteLock();
	
	
	public void setMrtmpManager(IMRTMPOriginManager mrtmpManager) {
		this.mrtmpManager = mrtmpManager;
	}

	public void setHandler(IRTMPHandler handler) {
		this.handler = handler;
	}

	public void setCodecFactory(ProtocolCodecFactory codecFactory) {
		this.codecFactory = codecFactory;
	}

	@Override
	public void messageReceived(IoSession session, Object message) throws Exception {
		MRTMPPacket packet = (MRTMPPacket) message;
		MRTMPPacket.Header header = packet.getHeader();
		MRTMPPacket.Body body = packet.getBody();
		if (log.isDebugEnabled()) {
			log.debug(packet.toString());
		}
		int clientId = header.getClientId();
		int sessionId = getSessionId(session);
		MRTMPOriginConnection mrtmpConn = (MRTMPOriginConnection) session.getAttribute(MRTMPOriginConnection.ORIGIN_CONNECTION_KEY);
		RTMPOriginConnection conn = null;
		switch (packet.getHeader().getType()) {
			case MRTMPPacket.CONNECT:
				lock.writeLock().lock();
				try {
					if (header.isDynamic()) {
						if (!dynConnMap.containsKey(clientId)) {
							conn = new RTMPOriginConnection(
									IConnection.POLLING,
									header.getClientId()
									);
							conn.setMrtmpManager(mrtmpManager);
							conn.setHandler(this);
							dynConnMap.put(clientId, conn);
						} else {
							log.warn("Open an already existing RTMPT origin connection!");
						}
					} else {
						StaticConnId connId = new StaticConnId();
						connId.clientId = header.getClientId();
						connId.sessionId = sessionId;
						if (!statConnMap.containsKey(connId)) {
							conn = new RTMPOriginConnection(
									IConnection.PERSISTENT,
									header.getClientId(),
									sessionId
									);
							conn.setMrtmpManager(mrtmpManager);
							conn.setHandler(this);
							statConnMap.put(connId, conn);
						} else {
							log.warn("Open an already existing RTMP origin connection!");
						}
					}
				} finally {
					lock.writeLock().unlock();
				}
				break;
			case MRTMPPacket.CLOSE:
			case MRTMPPacket.RTMP:
				lock.readLock().lock();
				try {
					if (header.isDynamic()) {
						conn = dynConnMap.get(clientId);
					} else {
						StaticConnId connId = new StaticConnId();
						connId.clientId = header.getClientId();
						connId.sessionId = sessionId;
						conn = statConnMap.get(connId);
					}
				} finally {
					lock.readLock().unlock();
				}
				if (conn != null) {
					if (packet.getHeader().getType() == MRTMPPacket.CLOSE) {
						closeConnection(conn);
						conn = null;
					} else {
						MRTMPPacket.RTMPBody rtmpBody = (MRTMPPacket.RTMPBody) body;
						handler.messageReceived((RTMPConnection)conn, packet);
					}
				} else {
					log.warn("Handle on a non-existent origin connection!");
				}
				break;
			default:
				log.warn("Unknown mrtmp packet received!");
				break;
		}
		if (conn != null) {
			mrtmpManager.associate(conn, mrtmpConn);
		}
	}

	@Override
	public void messageSent(IoSession session, Object message) throws Exception {
		MRTMPPacket packet = (MRTMPPacket) message;
		if (packet.getHeader().getType() != MRTMPPacket.RTMP) {
			return;
		}
		MRTMPPacket.Header header = packet.getHeader();
		//MRTMPPacket.Body body = packet.getBody();
		int clientId = header.getClientId();
		int sessionId = getSessionId(session);
		
		@SuppressWarnings("unused")
		RTMPOriginConnection conn = null;
		
		lock.readLock().lock();
		try {
			if (header.isDynamic()) {
				conn = dynConnMap.get(clientId);
			} else {
				StaticConnId connId = new StaticConnId();
				connId.clientId = header.getClientId();
				connId.sessionId = sessionId;
				conn = statConnMap.get(connId);
			}
		} finally {
			lock.readLock().unlock();
		}
		//if (conn != null) {
			//MRTMPPacket.RTMPBody rtmpBody = (MRTMPPacket.RTMPBody) body;			
			//final int channelId = rtmpBody.getRtmpPacket().getHeader().getChannelId();
			//final IClientStream stream = conn.getStreamByChannelId(channelId);
			// XXX we'd better use new event model for notification
			//if (stream != null && (stream instanceof PlaylistSubscriberStream)) {
			//	((PlaylistSubscriberStream) stream).written(rtmpBody.getRtmpPacket().getMessage());
			//}
		//} else {
			//log.warn("Handle on a non-existent origin connection!");
		//}
	}

	@Override
	public void sessionClosed(IoSession session) throws Exception {
		MRTMPOriginConnection conn = (MRTMPOriginConnection) session.getAttribute(MRTMPOriginConnection.ORIGIN_CONNECTION_KEY);
		// TODO we need to handle the case when all MRTMP connection is broken.
		mrtmpManager.unregisterConnection(conn);
		conn.close();
		log.debug("Closed MRTMP Origin Connection " + conn);
	}

	@Override
	public void sessionCreated(IoSession session) throws Exception {
		MRTMPOriginConnection conn = new MRTMPOriginConnection();
		conn.setIoSession(session);
		mrtmpManager.registerConnection(conn);
		session.setAttribute(MRTMPOriginConnection.ORIGIN_CONNECTION_KEY, conn);
		session.getFilterChain().addFirst("protocolFilter",
				new ProtocolCodecFilter(this.codecFactory));
		if (log.isDebugEnabled()) {
			session.getFilterChain().addLast("logger", new LoggingFilter());
		}
		log.debug("Created MRTMP Origin Connection {}", conn);
	}

	public void closeConnection(RTMPOriginConnection conn) {
		boolean dynamic = !conn.getType().equals(IConnection.PERSISTENT);
		lock.writeLock().lock();
		try {
			if (dynamic) {
				if (dynConnMap.containsKey(conn.getId())) {
					dynConnMap.remove(conn.getId());
					conn.realClose();
				} else {
					log.warn("Close a non-existent origin connection!");
				}
			} else {
				StaticConnId connId = new StaticConnId();
				connId.clientId = conn.getId();
				connId.sessionId = conn.getIoSessionId();
				if (statConnMap.containsKey(connId)) {
					statConnMap.remove(connId);
					conn.realClose();
				} else {
					log.warn("Close a non-existent origin connection!");
				}
			}
		} finally {
			lock.writeLock().unlock();
		}
		mrtmpManager.dissociate(conn);
	}
	
	protected int getSessionId(IoSession session) {
		MRTMPOriginConnection mrtmpConn = (MRTMPOriginConnection) session.getAttribute(MRTMPOriginConnection.ORIGIN_CONNECTION_KEY);
		if (mrtmpConn != null) {
			return mrtmpConn.hashCode();
		}
		return 0;
	}
	
	private static class StaticConnId {
		public int sessionId;
		public int clientId;
		
		@Override
		public int hashCode() {
			final int PRIME = 31;
			int result = 1;
			result = PRIME * result + clientId;
			result = PRIME * result + sessionId;
			return result;
		}
		
		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			final StaticConnId other = (StaticConnId) obj;
			if (clientId != other.clientId)
				return false;
			if (sessionId != other.sessionId)
				return false;
			return true;
		}
		
	}
}
