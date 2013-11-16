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
import java.util.Map;

import org.apache.commons.lang3.ArrayUtils;
import org.red5.logging.Red5LoggerFactory;
import org.red5.server.api.IConnection;
import org.red5.server.api.Red5;
import org.red5.server.api.scope.IScope;
import org.red5.server.api.stream.IStreamPlaybackSecurity;
import org.slf4j.Logger;

public class PlaybackSecurityHandler extends SecurityBase implements IStreamPlaybackSecurity {

	private Boolean HTMLDomainsAuth = true;

	private Boolean SWFDomainsAuth = true;

	private String[] allowedHTMLDomains;

	private String[] allowedSWFDomains;

	private String htmlDomains = "allowedHTMLdomains.txt";

	private String swfDomains = "allowedSWFdomains.txt";

	private static Logger log = Red5LoggerFactory.getLogger(PlaybackSecurityHandler.class, "securityTest");

	@Override
	public void init() {
		if (properties.containsKey("htmlDomains")) {
			htmlDomains = properties.get("htmlDomains").toString();
		}
		if (properties.containsKey("swfDomains")) {
			swfDomains = properties.get("swfDomains").toString();
		}
		
		allowedHTMLDomains = readValidDomains(htmlDomains, "HTMLDomains");

		// Populating the list of domains which are allowed to host a SWF file
		// which may connect to this application
		allowedSWFDomains = readValidDomains(swfDomains, "SWFDomains");

		// Logging
		if (HTMLDomainsAuth) {
			log.debug("Authentication of HTML page URL domains is enabled");
		}
		if (SWFDomainsAuth) {
			log.debug("Authentication of SWF URL domains is enabled");
		}

		log.debug("...loading completed.");
		
		//now register with the application
		application.registerStreamPlaybackSecurity(this);
	}

	public boolean isPlaybackAllowed(IScope scope, String name, int start, int length, boolean flushPlaylist) {
		IConnection conn = Red5.getConnectionLocal();
		
		try {
			
			Map<String, Object> connectParams = conn.getConnectParams();
			
			String pageUrl = conn.getConnectParams().get("pageUrl").toString();
			String swfUrl = conn.getConnectParams().get("swfUrl").toString();
			String ip = conn.getRemoteAddress();

			if ((ip != "127.0.0.1") && HTMLDomainsAuth && !this.validate(pageUrl, this.allowedHTMLDomains)) {
				log.debug("Authentication failed for pageurl: " + pageUrl + ", rejecting connection from " + ip);
				return false;
			}

			// Authenticating the SWF file's domain for the request :
			// Don't call validate() when the request is from localhost 
			// or SWF Domains Authentication is off.
			if ((ip != "127.0.0.1") && SWFDomainsAuth && !this.validate(swfUrl, this.allowedSWFDomains)) {
				log.debug("Authentication failed for referrer: " + swfUrl + ", rejecting connection from " + ip);
				return false;
			}
		} catch (Exception e) {
			if (HTMLDomainsAuth || SWFDomainsAuth)
				return false;
			return true;
		}
		return true;
	}

	private Boolean validate(String url, String[] patterns) {
		// Convert to lower case
		url = url.toLowerCase();
		int domainStartPos = 0; // domain start position in the URL
		int domainEndPos = 0; // domain end position in the URL

		switch (url.indexOf("://")) {
			case 4:
				if (url.indexOf("http://") == 0)
					domainStartPos = 7;
				break;
			case 5:
				if (url.indexOf("https://") == 0)
					domainStartPos = 8;
				break;
		}
		if (domainStartPos == 0) {
			// URL must be HTTP or HTTPS protocol based
			return false;
		}
		domainEndPos = url.indexOf("/", domainStartPos);
		if (domainEndPos > 0) {
			int colonPos = url.indexOf(":", domainStartPos);
			if ((colonPos > 0) && (domainEndPos > colonPos)) {
				// probably URL contains a port number
				domainEndPos = colonPos; // truncate the port number in the URL
			}
		}

		url = url.substring(domainStartPos, domainEndPos);

		int indexOf = ArrayUtils.indexOf(patterns, url);
		if (ArrayUtils.indexOf(patterns, url) != ArrayUtils.INDEX_NOT_FOUND) {
			return true;
		}

		return false;
	}

	private String[] readValidDomains(String fileName, String domainsType) {
		String[] domainsArray = new String[100];

		try {
			DataInputStream in = new DataInputStream(application.getResource("WEB-INF/" + fileName).getInputStream());
			BufferedReader br = new BufferedReader(new InputStreamReader(in));

			int index = 0;
			String strLine = "";

			while ((strLine = br.readLine()) != null) {
				if (strLine.equals("") || strLine.indexOf("#") == 0) {
					continue;
				}

				if (strLine.indexOf(" ") < 0) {

					index++;
					domainsArray[index] = strLine.toLowerCase();
					log.debug(domainsArray[index]);

					if (strLine.trim().equals("*")) {
						if (domainsType.equals("HTMLDomains")) {
							log.debug("Found wildcard (*) entry: disabling authentication of HTML file domains ");
							HTMLDomainsAuth = false;
						} else if (domainsType.equals("SWFDomains")) {
							log.debug("Found wildcard (*) entry: disabling authentication of SWF file domains ");
							SWFDomainsAuth = false;
						}

					}
				}
			}

			in.close();
		} catch (Exception e) {
			log.error("{}", e.getMessage());
			e.printStackTrace();
			if (domainsType.equals("HTMLDomains")) {
				HTMLDomainsAuth = false;
			} else if (domainsType.equals("HTMLDomains")) {
				SWFDomainsAuth = false;
			}
		}

		return domainsArray;
	}

}
