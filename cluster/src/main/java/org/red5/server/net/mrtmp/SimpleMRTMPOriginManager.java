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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.red5.server.api.IConnection;
import org.red5.server.net.rtmp.RTMPConnection;
import org.red5.server.net.rtmp.RTMPOriginConnection;

/**
 * @author Steven Gong (steven.gong@gmail.com)
 */
public class SimpleMRTMPOriginManager implements IMRTMPOriginManager {
	private static final Logger log = LoggerFactory.getLogger(SimpleMRTMPOriginManager.class);
	
	private ReadWriteLock lock = new ReentrantReadWriteLock();
	private Set<IMRTMPConnection> connSet = new HashSet<IMRTMPConnection>();
	private Map<RTMPConnection, IMRTMPConnection> clientToConnMap;
	private OriginMRTMPHandler originMRTMPHandler;
	
	public SimpleMRTMPOriginManager() {
		// XXX Use HashMap instead of WeakHashMap temporarily
		// to avoid package routing issue before Terracotta
		// integration.
		clientToConnMap = Collections.synchronizedMap(
				new HashMap<RTMPConnection, IMRTMPConnection>());
	}

	public void setOriginMRTMPHandler(OriginMRTMPHandler originMRTMPHandler) {
		this.originMRTMPHandler = originMRTMPHandler;
	}

	public boolean registerConnection(IMRTMPConnection conn) {
		lock.writeLock().lock();
		try {
			return connSet.add(conn);
		} finally {
			lock.writeLock().unlock();
		}
	}

	public boolean unregisterConnection(IMRTMPConnection conn) {
		boolean ret;
		ArrayList<RTMPConnection> list = new ArrayList<RTMPConnection>();
		lock.writeLock().lock();
		try {
			ret = connSet.remove(conn);
			if (ret) {
				for (Iterator<Entry<RTMPConnection, IMRTMPConnection>> iter = clientToConnMap.entrySet().iterator(); iter.hasNext(); ) {
					Entry<RTMPConnection, IMRTMPConnection> entry = iter.next();
					if (entry.getValue() == conn) {
						list.add(entry.getKey());
					}
				}
			}
		} finally {
			lock.writeLock().unlock();
		}
		// close all RTMPOriginConnections
		for (RTMPConnection rtmpConn : list) {
			log.debug("Close RTMPOriginConnection " + rtmpConn.getId() + " due to MRTMP Connection closed!");
			originMRTMPHandler.closeConnection((RTMPOriginConnection) rtmpConn);
		}
		return ret;
	}

	public void associate(RTMPConnection rtmpConn, IMRTMPConnection mrtmpConn) {
		clientToConnMap.put(rtmpConn, mrtmpConn);
	}

	public void dissociate(RTMPConnection rtmpConn) {
		clientToConnMap.remove(rtmpConn);
	}

	public IMRTMPConnection lookupMRTMPConnection(RTMPConnection rtmpConn) {
		lock.readLock().lock();
		try {
			IMRTMPConnection conn = clientToConnMap.get(rtmpConn);
			if (conn != null && !connSet.contains(conn)) {
				clientToConnMap.remove(rtmpConn);
				conn = null;
			}
			// mrtmp connection not found, we locate the next mrtmp connection
			// when the connection is not persistent.
			if (conn == null && !rtmpConn.getType().equals(IConnection.PERSISTENT)) {
				if (connSet.size() > 0) {
					conn = connSet.iterator().next();
				}
			}
			// TODO handle conn == null case
			return conn;
		} finally {
			lock.readLock().unlock();
		}
	}

}
