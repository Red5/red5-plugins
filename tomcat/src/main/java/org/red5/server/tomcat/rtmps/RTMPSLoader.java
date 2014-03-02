package org.red5.server.tomcat.rtmps;

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

import java.io.File;

import org.apache.catalina.Context;
import org.apache.catalina.Engine;
import org.apache.catalina.Valve;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.core.StandardHost;
import org.apache.catalina.core.StandardWrapper;
import org.apache.catalina.loader.WebappLoader;
import org.red5.logging.Red5LoggerFactory;
import org.red5.server.api.IServer;
import org.red5.server.tomcat.TomcatConnector;
import org.red5.server.tomcat.rtmpt.RTMPTLoader;
import org.red5.server.util.FileUtil;
import org.slf4j.Logger;

/**
 * Loader for the RTMPS server which uses Tomcat.
 * 
 * @author The Red5 Project (red5@osflash.org)
 * @author Paul Gregoire (mondain@gmail.com)
 */
public class RTMPSLoader extends RTMPTLoader {

	// Initialize Logging
	private Logger log = Red5LoggerFactory.getLogger(RTMPSLoader.class);

	/**
	 * RTMPS Tomcat engine.
	 */
	protected Engine rtmpsEngine;

	/**
	 * Setter for server
	 * 
	 * @param server Value to set for property 'server'.
	 */
	public void setServer(IServer server) {
		log.debug("RTMPS setServer");
		this.server = server;
	}

	/** {@inheritDoc} */
	@SuppressWarnings("deprecation")
	@Override
	public void start() {
		log.info("Loading RTMPS context");
		rtmpsEngine = embedded.createEngine();
		rtmpsEngine.setDefaultHost(host.getName());
		rtmpsEngine.setName("red5RTMPSEngine");
		rtmpsEngine.setService(embedded);
		// add the valves to the host
		for (Valve valve : valves) {
			log.debug("Adding host valve: {}", valve);
			((StandardHost) host).addValve(valve);
		}

		// create and add root context
		File appDirBase = new File(webappFolder);
		String webappContextDir = FileUtil.formatPath(appDirBase.getAbsolutePath(), "/root");
		Context ctx = embedded.createContext("/", webappContextDir);
		//no reload for now
		ctx.setReloadable(false);
		log.debug("Context name: {}", ctx.getName());
		Object ldr = ctx.getLoader();
		log.trace("Context loader (null if the context has not been started): {}", ldr);
		if (ldr == null) {
			ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
			//log.debug("Classloaders - Parent {}\nTCL {}\n\n", new Object[] {classLoader.getParent(), classLoader});
			ctx.setParentClassLoader(classLoader);

			WebappLoader wldr = new WebappLoader(classLoader);
			//add the Loader to the context
			ctx.setLoader(wldr);
		}
		appDirBase = null;
		webappContextDir = null;

		host.addChild(ctx);
		// add servlet wrapper
		StandardWrapper wrapper = new StandardWrapper();
		wrapper.setServletName("RTMPTServlet");
		wrapper.setServletClass("org.red5.server.net.rtmpt.RTMPTServlet");
		ctx.addChild(wrapper);

		// add servlet mappings
		ctx.addServletMapping("/open/*", "RTMPTServlet");
		ctx.addServletMapping("/close/*", "RTMPTServlet");
		ctx.addServletMapping("/send/*", "RTMPTServlet");
		ctx.addServletMapping("/idle/*", "RTMPTServlet");
		// add the host
		rtmpsEngine.addChild(host);
		// add new Engine to set of Engine for embedded server
		embedded.addEngine(rtmpsEngine);
		try {
			// loop through connectors and apply methods / props
			for (TomcatConnector tomcatConnector : connectors) {
				// get the connector
				Connector connector = tomcatConnector.getConnector();
        		// add new Connector to set of Connectors for embedded server, associated with Engine
       			embedded.addConnector(connector);
       			log.trace("Connector oName: {}", connector.getObjectName());
				log.info("Starting RTMPS engine");
				// start connector
				connector.start();
			}
		} catch (Exception e) {
			log.error("Error initializing RTMPS server instance", e);
		} finally {
			registerJMX();
		}

	}

}
