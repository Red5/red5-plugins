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

package org.red5.server.plugin.definst;

import org.red5.logging.Red5LoggerFactory;
import org.red5.server.api.IClient;
import org.red5.server.api.IConnection;
import org.red5.server.api.event.IEvent;
import org.red5.server.api.scope.IBasicScope;
import org.red5.server.api.scope.IScope;
import org.red5.server.api.scope.IScopeAware;
import org.red5.server.api.scope.IScopeHandler;
import org.red5.server.api.service.IServiceCall;
import org.red5.server.scope.Scope;
import org.slf4j.Logger;

/**
 * Primary definst class, it handles all the features and functions of a
 * standard red5 application.
 * 
 * @author Paul Gregoire
 */
public class DefinstHandler implements IScopeHandler, IScopeAware {

	private static Logger log = Red5LoggerFactory.getLogger(DefinstHandler.class);
	
	private IScope scope;

	public boolean start(IScope scope) {
		log.info("start: {}", scope);
		//return ((Scope) this.scope).start();
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
		//return ((Scope) scope).connect(conn, params);
		return true;
	}

	public void disconnect(IConnection conn, IScope scope) {
		log.info("disconnect");
		// Get the previously stored username
		String rid = conn.getClient().getId();
		// Unregister user
		log.info("Client with id {} disconnected.", rid);
		//((Scope) scope).disconnect(conn);		
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
		//return ((Scope) this.scope).addChildScope(scope);
		return true;
	}

	public void removeChildScope(IBasicScope scope) {
		log.info("removeChildScope: {}", scope);
		//((Scope) this.scope).removeChildScope(scope);
	}

	public boolean serviceCall(IConnection conn, IServiceCall call) {
		log.info("serviceCall {}", call);
		return true;
	}

	/**
	 * Get the root scope
	 * 
	 * @return IScope
	 */
	public IScope getScope() {
		return scope;
	}
	
	public void setScope(IScope scope) {
		this.scope = scope;
	}
	
}
