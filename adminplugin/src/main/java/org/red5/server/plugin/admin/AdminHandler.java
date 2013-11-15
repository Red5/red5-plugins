package org.red5.server.plugin.admin;

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

import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;

import org.red5.logging.Red5LoggerFactory;
import org.red5.server.api.IClient;
import org.red5.server.api.IConnection;
import org.red5.server.api.Red5;
import org.red5.server.api.event.IEvent;
import org.red5.server.api.scope.IBasicScope;
import org.red5.server.api.scope.IScope;
import org.red5.server.api.scope.IScopeHandler;
import org.red5.server.api.service.IServiceCall;
import org.red5.server.api.service.ServiceUtils;
import org.red5.server.plugin.admin.stats.ScopeStatistics;
import org.red5.server.plugin.admin.stats.UserStatistics;
import org.red5.server.scope.Scope;
import org.red5.server.util.ScopeUtils;
import org.slf4j.Logger;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ResourceBundleMessageSource;

/**
 * Primary "admin" class, it handles all the features and functions of a
 * standard red5 application. This code is based on original code in the
 * admin application.
 * 
 * @author Paul Gregoire
 */
public class AdminHandler implements IScopeHandler {

	private static Logger log = Red5LoggerFactory.getLogger(AdminHandler.class, "admin");

	private static ResourceBundleMessageSource messageSource;
	
	private IScope scope;

	private HashMap<Integer, String> scopes;

	private int scopeId = 0;
	
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

	/**
	 * Get all running applications
	 * 
	 * @return HashMap containing all applications
	 */
	public HashMap<Integer, Object> getApplications() {
		IScope root = ScopeUtils.findRoot(scope);
		Iterator<String> iter = (Iterator<String>) root.getScopeNames();
		HashMap<Integer, Object> apps = new HashMap<Integer, Object>();
		int id = 0;
		while (iter.hasNext()) {
			String name = iter.next();
			String name2 = name.substring(1, name.length());
			int size = getConnections(name2).size();
			HashMap<String, String> app = new HashMap<String, String>();
			app.put("name", name2);
			app.put("clients", size + "");
			apps.put(id, app);
			id++;
		}
		return apps;
	}

	/**
	 * Get Application statistics.
	 * 
	 * @param scopeName
	 * @return HashMap with the statistics
	 */
	public HashMap<Integer, HashMap<String, String>> getStatistics(String scopeName) {
		ScopeStatistics scopestats = new ScopeStatistics();
		return scopestats.getStats(getScope(scopeName));
	}

	/**
	 * Get Client statistics
	 * 
	 * @param userid
	 * @return HashMap with the statistics
	 */
	public HashMap<Integer, HashMap<String, String>> getUserStatistics(String userid) {
		UserStatistics userstats = new UserStatistics();
		return userstats.getStats(userid, scope);
	}

	/**
	 * Get all the scopes
	 * 
	 * @param scopeName
	 * @return HashMap containing all the scopes
	 */
	public HashMap<Integer, String> getScopes(String scopeName) {
		IScope root = ScopeUtils.findRoot(scope);
		IScope scopeObj = root.getScope(scopeName);
		scopes = new HashMap<Integer, String>();
		try {
			getRooms(scopeObj, 0);
		} catch (NullPointerException npe) {
			log.debug(npe.toString());
		}
		return scopes;
	}

	/**
	 * Get all the scopes
	 * 
	 * @param root
	 *            the scope to from
	 * @param depth
	 *            scope depth
	 */
	public void getRooms(IScope root, int depth) {
		Iterator<String> iter = (Iterator<String>) root.getScopeNames();
		String indent = "";
		for (int i = 0; i < depth; i++) {
			indent += " ";
		}
		while (iter.hasNext()) {
			String name = iter.next();
			String name2 = name.substring(1, name.length());
			try {
				IScope parent = root.getScope(name2);
				// parent
				getRooms(parent, depth + 1);
				scopes.put(scopeId, indent + name2);
				scopeId++;
				// log.info("Found scope: "+name2);
			} catch (NullPointerException npe) {
				log.debug(npe.toString());
			}
		}
	}

	/**
	 * Get all the connections (clients)
	 * 
	 * @param scopeName
	 * @return HashMap with all clients in the given scope
	 */
	public HashMap<Integer, String> getConnections(String scopeName) {
		HashMap<Integer, String> connections = new HashMap<Integer, String>();
		IScope root = getScope(scopeName);
		if (root != null) {
			Set<IClient> clients = root.getClients();
			Iterator<IClient> client = clients.iterator();
			int id = 0;
			while (client.hasNext()) {
				IClient c = client.next();
				String user = c.getId();
				connections.put(id, user);
				id++;
			}
		}
		return connections;
	}

	/**
	 * Kill a client
	 * 
	 * @param userid
	 */
	public void killUser(String userid) {
		IScope root = ScopeUtils.findRoot(scope);
		Set<IClient> clients = root.getClients();
		Iterator<IClient> client = clients.iterator();
		while (client.hasNext()) {
			IClient c = client.next();
			if (c.getId().equals(userid)) {
				c.disconnect();
			}
		}
	}

	/**
	 * Get an scope by name
	 * 
	 * @param scopeName
	 * @return IScope the requested scope
	 */
	private IScope getScope(String scopeName) {
		IScope root = ScopeUtils.findRoot(scope);
		return getScopes(root, scopeName);
	}

	/**
	 * Search through all the scopes in the given scope to a scope with the
	 * given name
	 * 
	 * @param root
	 * @param scopeName
	 * @return IScope the requested scope
	 */
	private IScope getScopes(IScope root, String scopeName) {
		// log.info("Found scope "+root.getName());
		if (root.getName().equals(scopeName)) {
			return root;
		} else {
			Iterator<String> iter = (Iterator<String>) root.getScopeNames();
			while (iter.hasNext()) {
				String name = iter.next();
				String name2 = name.substring(1, name.length());
				try {
					IScope parent = root.getScope(name2);
					IScope scope = getScopes(parent, scopeName);
					if (scope != null) {
						return scope;
					}
				} catch (NullPointerException npe) {
					log.debug(npe.toString());
				}
			}
		}
		return null;
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

	public void setContext(ApplicationContext ctx) {
		this.context = ctx;
		messageSource = (ResourceBundleMessageSource) context.getBean("adminMessageSource");
	}

	/**
	 * Method for setting locale property on the connection.
	 * 
	 * @param localeId
	 */
	public void setLocale(String localeId) {
		IConnection conn = Red5.getConnectionLocal();
		if (conn != null) {
			conn.setAttribute("locale", localeId);
		}
	}	
	
	/**
	 * onError callback invoker.
	 * 
	 * @param code
	 * @param args
	 */
	public static boolean sendError(String code, Object[] args) {
		boolean sent = false;
		IConnection conn = Red5.getConnectionLocal();
		if (conn != null) {
			Locale locale = Locale.ENGLISH;
			if (conn.hasAttribute("locale")) {
				// http://java.sun.com/j2se/1.5.0/docs/api/java/util/Locale.html
				String[] parts = conn.getStringAttribute("locale").split("[-_]");
				if (parts.length == 1) {
					locale = new Locale(parts[0]);
				} else {
					locale = new Locale(parts[0], parts[1]);
				}
			}
			String errorMessage = messageSource.getMessage(code, args, locale);
			log.debug("Sending error to client: {}", errorMessage);
			if (ServiceUtils.invokeOnConnection(conn, "onError", new Object[]{errorMessage})) {
				//call succeeded
				sent = true;
			} else {
				log.warn("Send error failed - client id: {} method: {}", conn.getClient().getId());
			}
		} else {
			log.warn("Connection was not found for sending error");
		}
		return sent;
	}

	/**
	 * Locale echo test.
	 */
	public void echo() {
		sendError("echo", null);
	}	
	
}
