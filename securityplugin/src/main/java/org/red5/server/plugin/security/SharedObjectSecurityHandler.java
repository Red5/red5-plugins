package org.red5.server.plugin.security;

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

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.InputStreamReader;
import java.util.List;

import org.apache.commons.lang3.ArrayUtils;
import org.red5.logging.Red5LoggerFactory;
import org.red5.server.api.scope.IScope;
import org.red5.server.api.so.ISharedObject;
import org.red5.server.api.so.ISharedObjectSecurity;
import org.slf4j.Logger;

public class SharedObjectSecurityHandler extends SecurityBase implements ISharedObjectSecurity {

	private Boolean creationAllowed;

	private Boolean connectionAllowed;

	private Boolean deleteAllowed;

	private Boolean sendAllowed;

	private Boolean writeAllowed;

	private Boolean enableSharedObjects;

	private String sharedObjectNames;

	private Boolean NamesAuth = false;

	private String[] allowedSharedObjectNames;

	private static Logger log = Red5LoggerFactory.getLogger(SharedObjectSecurityHandler.class, "plugins");

	@Override
	public void init() {
		if (properties.containsKey("connectionAllowed")) {
			connectionAllowed = Boolean.valueOf(properties.get("connectionAllowed").toString());
		}
		if (properties.containsKey("creationAllowed")) {
			creationAllowed = Boolean.valueOf(properties.get("creationAllowed").toString());
		}
		if (properties.containsKey("deleteAllowed")) {
			deleteAllowed = Boolean.valueOf(properties.get("deleteAllowed").toString());
		}
		if (properties.containsKey("sendAllowed")) {
			sendAllowed = Boolean.valueOf(properties.get("sendAllowed").toString());
		}
		if (properties.containsKey("writeAllowed")) {
			writeAllowed = Boolean.valueOf(properties.get("writeAllowed").toString());
		}
		if (properties.containsKey("enableSharedObjects")) {
			enableSharedObjects = Boolean.valueOf(properties.get("enableSharedObjects").toString());
		}
		if (properties.containsKey("sharedObjectNames")) {
			sharedObjectNames = properties.get("sharedObjectNames").toString();
		}
		allowedSharedObjectNames = readValidNames(sharedObjectNames);

		if (NamesAuth) {
			log.debug("Authentication of Shared Object Names is enabled");
		}
		
		//now register with the application
		application.registerSharedObjectSecurity(this);
	}

	private Boolean validate(String name, String[] patterns) {
		if (ArrayUtils.indexOf(patterns, name) > 0)
			return true;
		return false;
	}

	private String[] readValidNames(String fileName) {
		String[] namesArray = {};

		try {
			NamesAuth = true;
			//FileInputStream fstream = new FileInputStream(fileName);
			DataInputStream in = new DataInputStream(application.getResource("WEB-INF/" + fileName).getInputStream());
			BufferedReader br = new BufferedReader(new InputStreamReader(in));

			int index = 0;
			String strLine = "";

			while ((strLine = br.readLine()) != null) {
				if (strLine.equals("") || strLine.indexOf("#") == 0) {
					continue;
				}

				if (strLine.indexOf(" ") < 0) {

					namesArray[index] = strLine.toLowerCase();
					index++;

					if (strLine == "*") {
						log.debug("Found wildcard (*) entry: disabling authentication of HTML file domains ");
						NamesAuth = false;

					}
				}
			}

			in.close();
		} catch (Exception e) {
			log.error("Problem: {}", e.getStackTrace());
			NamesAuth = false;
		}

		return namesArray;
	}

	public boolean isConnectionAllowed(ISharedObject so) {
		// Note: we don't check for the name here as only one SO can be
		//       created with this handler.
		return (enableSharedObjects && connectionAllowed);
	}

	public boolean isCreationAllowed(IScope scope, String name, boolean persistent) {

		if (enableSharedObjects && creationAllowed) {
			if (NamesAuth && !validate(name, allowedSharedObjectNames)) {
				log.debug("Authentication failed for shared object name: " + name);
				return false;
			}
			return true;
		}
		return false;
	}

	public boolean isDeleteAllowed(ISharedObject so, String key) {
		return deleteAllowed;
	}

	public boolean isSendAllowed(ISharedObject so, String message, List<?> arguments) {
		return sendAllowed;
	}

	public boolean isWriteAllowed(ISharedObject so, String key, Object value) {
		return writeAllowed;
	}

}