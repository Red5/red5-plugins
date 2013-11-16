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
import java.util.HashMap;

import org.apache.commons.lang3.ArrayUtils;
import org.red5.logging.Red5LoggerFactory;
import org.red5.server.api.scope.IScope;
import org.red5.server.api.stream.IStreamPublishSecurity;
import org.slf4j.Logger;

public class PublishSecurityHandler extends SecurityBase implements IStreamPublishSecurity {

	private Boolean enablePublish = true;

	private String publishNames;

	private Boolean NamesAuth = false;

	private HashMap<String, String[]> allowedPublishNames;

	private static Logger log = Red5LoggerFactory.getLogger(PublishSecurityHandler.class, "securityTest");

	@Override
	public void init() {
		if (properties.containsKey("publishNames")) {
			publishNames = properties.get("publishNames").toString();
		}
		if (properties.containsKey("enablePublish")) {
			enablePublish = Boolean.valueOf(properties.get("enablePublish").toString());
		}
		allowedPublishNames = readValidNames(publishNames);

		if (NamesAuth) {
			log.debug("Authentication of Publish Names is enabled");
		}
		
		//now register with the application
		application.registerStreamPublishSecurity(this);
	}

	public boolean isPublishAllowed(IScope scope, String name, String mode) {

		if (enablePublish) {
			if (NamesAuth && !validate(name, mode, allowedPublishNames)) {
				log.debug("Authentication failed for publish name: " + name);
				return false;
			}
			return true;
		}

		return false;
	}

	private Boolean validate(String name, String mode, HashMap<String, String[]> patterns) {
		if (patterns.get(name) != null) {
			String[] modes = patterns.get(name);
			if (ArrayUtils.indexOf(modes, mode) != ArrayUtils.INDEX_NOT_FOUND)
				return true;
		}
		return false;
	}

	private HashMap<String, String[]> readValidNames(String fileName) {

		HashMap<String, String[]> map = new HashMap<String, String[]>();

		try {
			NamesAuth = true;
			//FileInputStream fstream = new FileInputStream(fileName);
			DataInputStream in = new DataInputStream(application.getResource("WEB-INF/" + fileName).getInputStream());
			BufferedReader br = new BufferedReader(new InputStreamReader(in));

			String strLine = "";

			while ((strLine = br.readLine()) != null) {
				if (strLine.equals("") || strLine.indexOf("#") == 0) {
					continue;
				}

				if (strLine.indexOf(" ") < 0) {
					String line = strLine.toLowerCase();
					String[] nameMode = line.split(";");
					String name = nameMode[0];
					String[] modes = nameMode[1].split(",");

					map.put(name, modes);

					if (strLine == "*") {
						log.debug("Found wildcard (*) entry: disabling authentication of publish names ");
						NamesAuth = false;

					}
				}
			}

			in.close();
		} catch (Exception e) {
			log.error("Problem: {}", e.getStackTrace());
			NamesAuth = false;
		}

		return map;
	}

}