/*
 * Copyright (c) 2012-2015 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 * The Eclipse Public License is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * The Apache License v2.0 is available at
 * http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */
package org.red5.server.mqtt;

/**
 * Maintains the information of single connection, like ClientID, IoSession, and other connection related flags.
 * 
 * @author andrea
 */
public class ConnectionDescriptor {

	private String clientId;

	private ServerChannel session;

	private boolean cleanSession;

	public ConnectionDescriptor(String clientId, ServerChannel session, boolean cleanSession) {
		this.clientId = clientId;
		this.session = session;
		this.cleanSession = cleanSession;
	}

	public boolean isCleanSession() {
		return cleanSession;
	}

	public String getClientID() {
		return clientId;
	}

	public ServerChannel getSession() {
		return session;
	}

	@Override
	public String toString() {
		return "ConnectionDescriptor{clientId=" + clientId + ", cleanSession=" + cleanSession + '}';
	}

}
