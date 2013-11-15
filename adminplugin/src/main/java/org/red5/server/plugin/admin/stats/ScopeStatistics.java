package org.red5.server.plugin.admin.stats;

/*
 * RED5 Open Source Flash Server - http://www.osflash.org/red5
 * 
 * Copyright (c) 2006-2008 by respective authors (see below). All rights reserved.
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

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;

import org.red5.server.api.IConnection;
import org.red5.server.api.persistence.IPersistable;
import org.red5.server.api.persistence.IPersistenceStore;
import org.red5.server.api.scope.IScope;
import org.red5.server.api.statistics.IScopeStatistics;
import org.red5.server.plugin.admin.utils.Utils;

/**
 * 
 * @author The Red5 Project (red5@osflash.org)
 * @author Martijn van Beek (martijn.vanbeek@gmail.com)
 */
public class ScopeStatistics {

	private HashMap<Integer, HashMap<String, String>> apps;

	private int id;

	public HashMap<Integer, HashMap<String, String>> getStats(IScope root) {
		apps = new HashMap<Integer, HashMap<String, String>>();
		id = 0;
		IScopeStatistics stats = root.getStatistics();
		extractConnectionData(root);
		addData("Persistence Data", "--");
		IPersistenceStore data = root.getStore();
		Collection<IPersistable> objects = data.getObjects();
		Iterator<IPersistable> iter = objects.iterator();
		while (iter.hasNext()) {
			IPersistable name = iter.next();
			addData("Name", name.getName());
			addData("Type", name.getType());
			addData("Path", name.getPath());
			addData("Last modified", Utils.formatDate(name.getLastModified()));
		}
		addData("Scope Data", "--");
		addData("Active sub scopes", stats.getActiveSubscopes());
		addData("Total sub scopes", stats.getTotalSubscopes());
		addData("Active clients", stats.getActiveClients());
		addData("Total clients", stats.getTotalClients());
		addData("Active connections", stats.getActiveConnections());
		addData("Total connections", stats.getTotalConnections());
		addData("Created", Utils.formatDate(stats.getCreationTime()));
		return apps;
	}

	protected void addData(String name, Object value) {
		HashMap<String, String> app = new HashMap<String, String>();
		app.put("name", name);
		app.put("value", value.toString());
		apps.put(id, app);
		id++;
	}

	protected void extractConnectionData(IScope root) {
		Collection<Set<IConnection>> conns = root.getConnections();		
		for (Set<IConnection> set : conns) {
			for (IConnection connection : set) {
				addData("Scope statistics", "--");
				addData("Send bytes", Utils.formatBytes(connection
						.getWrittenBytes()));
				addData("Received bytes", Utils.formatBytes(connection
						.getReadBytes()));
				addData("Send messages", connection.getWrittenMessages());
				addData("Dropped messages", connection.getDroppedMessages());
				addData("Pending messages", connection.getPendingMessages());
				addData("Received messages", connection.getReadMessages());
				addData("Remote address", connection.getRemoteAddress() + ":"
						+ connection.getRemotePort() + " (" + connection.getHost()
						+ ")");
				addData("Path", connection.getPath());
			}
		}
	}
	
}