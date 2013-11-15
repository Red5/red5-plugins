package org.red5.server.winstone;

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
import org.red5.server.api.IApplicationLoader;
import org.slf4j.Logger;
import org.springframework.context.ApplicationContext;

import winstone.HostConfiguration;

/**
 * Class that can load new applications in Winstone.
 * 
 * @author The Red5 Project (red5@osflash.org)
 * @author Paul Gregoire (mondain@gmail.com)
 */
public class WinstoneApplicationLoader implements IApplicationLoader {

	// Initialize Logging
	protected static Logger log = Red5LoggerFactory.getLogger(WinstoneApplicationLoader.class);

	private HostConfiguration host;
	
	/** Stores reference to the root ApplicationContext. */
	private ApplicationContext rootCtx;

	/**
	 * Wraps Winstone host configuration and spring application context.
	 * 
	 * @param host
	 * @param rootCtx
	 */
	protected WinstoneApplicationLoader(HostConfiguration host, ApplicationContext rootCtx) {
		this.host = host;
		this.rootCtx = rootCtx;
	}

	/** {@inheritDoc} */
	public ApplicationContext getRootContext() {
		log.debug("getRootContext");
		return rootCtx;
	}

	public HostConfiguration getHostConfiguration() {
		return host;
	}

	/** {@inheritDoc} */
	public void loadApplication(String contextPath, String virtualHosts, String directory) throws Exception {
		log.debug("Load application - context path: {} directory: {} virtual hosts: {}", new Object[] { contextPath, directory, virtualHosts });
		if (directory.startsWith("file:")) {
			directory = directory.substring(5);
		}
		if (host.getWebAppByURI(contextPath) == null) {
			log.warn("Not supported at this time");
//			WebAppConfiguration c = createContext(contextPath, directory);
//			LoaderBase.setRed5ApplicationContext(contextPath, new WinstoneApplicationContext(c));
//			host.addChild(c);
//			//add virtual hosts / aliases
//			String[] vhosts = virtualHosts.split(",");
//			for (String s : vhosts) {
//				if (!"*".equals(s)) {
//					//if theres a port, strip it
//					if (s.indexOf(':') == -1) {
//						host.addAlias(s);
//					} else {
//						host.addAlias(s.split(":")[0]);
//					}
//				} else {
//					log.warn("\"*\" based virtual hosts not supported");
//				}
//			}
		} else {
			log.warn("Context path already exists with host");
		}
	}
	
}
