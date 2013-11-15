package org.red5.server.plugin.oflaDemo;

/*
 * RED5 Open Source Flash Server - http://www.osflash.org/red5
 * 
 * Copyright (c) 2006-2009 by respective authors (see below). All rights reserved.
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

import org.red5.logging.Red5LoggerFactory;
import org.red5.server.api.IClient;
import org.red5.server.api.IConnection;
import org.red5.server.api.event.IEvent;
import org.red5.server.api.scope.IBasicScope;
import org.red5.server.api.scope.IScope;
import org.red5.server.api.scope.IScopeHandler;
import org.red5.server.api.service.IServiceCall;
import org.red5.server.scope.Scope;
import org.slf4j.Logger;
import org.springframework.context.ApplicationContext;

/**
 * Primary "oflaDemo" class, it handles all the features and functions of a standard red5 application. This code is based on original code in the
 * oflaDemo application.
 * 
 * @author Paul Gregoire
 */
public class OflaDemoHandler implements IScopeHandler {

	private static Logger log = Red5LoggerFactory.getLogger(OflaDemoHandler.class);

	private IScope scope;
	
	@SuppressWarnings("unused")
	private ApplicationContext context;

	public boolean start(IScope scope) {
		log.info("start: {}", scope);
		return true;
	}

	public void stop(IScope scope) {
		log.info("stop: {}", scope);
		if (scope != null) {
			//un-initializing admin scope and children
			((Scope) this.scope).uninit();
		}
	}

	public boolean handleEvent(IEvent event) {
		log.debug("Scope event: {}", event);
		return false;
	}		
			
	public boolean connect(IConnection conn, IScope scope, Object[] params) {
		log.info("connect - conn: {} params: {} scope: {}", new Object[]{conn, params, scope});
		return true;
	}

	public void disconnect(IConnection conn, IScope scope) {
		log.info("disconnect");
		// Get the previously stored username
		String rid = conn.getClient().getId();
		// Unregister user
		log.info("Client with id {} disconnected.", rid);
		
	}

	public boolean join(IClient client, IScope scope) {
		log.info("join - client: {} scope: {}", new Object[]{client, scope});
		return true;
	}

	public void leave(IClient client, IScope scope) {		
		log.info("leave");
	}

	public boolean addChildScope(IBasicScope scope) {
		log.info("addChildScope: {}", scope);
		return false;
	}

	public void removeChildScope(IBasicScope scope) {
		log.info("removeChildScope: {}", scope);
	}

	public boolean serviceCall(IConnection conn, IServiceCall call) {
		log.info("serviceCall {}", call);
		return true;
	}

	public void setContext(ApplicationContext ctx) {
		this.context = ctx;
	}

	/**
	 * @param scope the scope to set
	 */
	public void setScope(IScope scope) {
		this.scope = scope;
	}
	
}
