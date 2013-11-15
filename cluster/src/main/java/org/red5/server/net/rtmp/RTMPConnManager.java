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

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.red5.server.BaseConnection;
import org.red5.server.api.scheduling.ISchedulingService;
import org.red5.server.net.mrtmp.EdgeRTMPMinaConnection;
import org.red5.server.net.rtmpt.RTMPTConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

public class RTMPConnManager implements IRTMPConnManager, ApplicationContextAware {

	private static final Logger log = LoggerFactory.getLogger(RTMPConnManager.class);

	private ConcurrentMap<Integer, RTMPConnection> connMap = new ConcurrentHashMap<Integer, RTMPConnection>();

	private ReadWriteLock lock = new ReentrantReadWriteLock();

	private ApplicationContext appCtx;

	public RTMPConnection createConnection(Class<?> connCls) {
		if (!RTMPConnection.class.isAssignableFrom(connCls)) {
			return null;
		}
		try {
			RTMPConnection conn = createConnectionInstance(connCls);
			lock.writeLock().lock();
			try {
				int clientId = BaseConnection.getNextClientId();
				conn.setId(clientId);
				connMap.put(clientId, conn);
				log.debug("Connection created, id: {}", conn.getId());
			} finally {
				lock.writeLock().unlock();
			}
			return conn;
		} catch (Exception e) {
			return null;
		}
	}

	public RTMPConnection getConnection(int clientId) {
		lock.readLock().lock();
		try {
			return connMap.get(clientId);
		} finally {
			lock.readLock().unlock();
		}
	}

	public RTMPConnection removeConnection(int clientId) {
		lock.writeLock().lock();
		try {
			log.debug("Removing connection with id: {}", clientId);
			return connMap.remove(clientId);
		} finally {
			lock.writeLock().unlock();
		}
	}

	public Collection<RTMPConnection> removeConnections() {
		ArrayList<RTMPConnection> list = new ArrayList<RTMPConnection>(connMap.size());
		lock.writeLock().lock();
		try {
			list.addAll(connMap.values());
			return list;
		} finally {
			lock.writeLock().unlock();
		}
	}

	public void setApplicationContext(ApplicationContext appCtx) throws BeansException {
		this.appCtx = appCtx;
	}

	public RTMPConnection createConnectionInstance(Class<?> cls) throws Exception {
		RTMPConnection conn = null;
		if (cls == RTMPMinaConnection.class) {
			conn = (RTMPMinaConnection) appCtx.getBean("rtmpMinaConnection");
		} else if (cls == EdgeRTMPMinaConnection.class) {
			conn = (EdgeRTMPMinaConnection) appCtx.getBean("rtmpEdgeMinaConnection");
		} else if (cls == RTMPTConnection.class) {
			conn = (RTMPTConnection) appCtx.getBean("rtmptConnection");
		} else {
			conn = (RTMPConnection) cls.newInstance();
		}
		//set the scheduling service for easy access in the connection
		conn.setSchedulingService((ISchedulingService) appCtx.getBean(ISchedulingService.BEAN_NAME));
		return conn;
	}
}
