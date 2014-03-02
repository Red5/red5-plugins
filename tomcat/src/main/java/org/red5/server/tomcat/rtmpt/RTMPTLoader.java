package org.red5.server.tomcat.rtmpt;

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
import java.util.HashMap;
import java.util.Map;

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
import org.red5.server.tomcat.TomcatLoader;
import org.red5.server.util.FileUtil;
import org.slf4j.Logger;

/**
 * Loader for the RTMPT server which uses Tomcat.
 * 
 * @author The Red5 Project (red5@osflash.org)
 * @author Paul Gregoire (mondain@gmail.com)
 */
public class RTMPTLoader extends TomcatLoader {

	// Initialize Logging
	private Logger log = Red5LoggerFactory.getLogger(RTMPTLoader.class);

	/**
	 * RTMPT Tomcat engine.
	 */
	protected Engine rtmptEngine;	
	
	/**
	 * Server instance
	 */
	protected IServer server;

	/**
	 * Context, in terms of JEE context is web application in a servlet
	 * container
	 */
	protected Context context;

	/**
	 * Extra servlet mappings to add
	 */
	protected Map<String, String> servletMappings = new HashMap<String, String>();
	
	
	/**
	 * Setter for server
	 * 
	 * @param server Value to set for property 'server'.
	 */
	public void setServer(IServer server) {
		log.debug("RTMPT setServer");
		this.server = server;
	}

	/** {@inheritDoc} */
	@SuppressWarnings("deprecation")
	@Override
	public void start() {
		log.info("Loading RTMPT context");

		rtmptEngine = embedded.createEngine();
		rtmptEngine.setDefaultHost(host.getName());
		rtmptEngine.setName("red5RTMPTEngine");
		rtmptEngine.setService(embedded);
		
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
			ctx.setParentClassLoader(classLoader);
			WebappLoader wldr = new WebappLoader(classLoader);
			//add the Loader to the context
			ctx.setLoader(wldr);
		}  		
		appDirBase = null;
		webappContextDir = null;
		
		host.addChild(ctx);
		
		// add servlet wrapper
		StandardWrapper wrapper = (StandardWrapper) ctx.createWrapper();
		wrapper.setServletName("RTMPTServlet");
		wrapper.setServletClass("org.red5.server.net.rtmpt.RTMPTServlet");
		ctx.addChild(wrapper);
		
		// add servlet mappings
		ctx.addServletMapping("/open/*", "RTMPTServlet");
		ctx.addServletMapping("/close/*", "RTMPTServlet");
		ctx.addServletMapping("/send/*", "RTMPTServlet");
		ctx.addServletMapping("/idle/*", "RTMPTServlet");	
		
		// add any additional mappings
		for (String key : servletMappings.keySet()) {
			context.addServletMapping(servletMappings.get(key), key);
		}				
		rtmptEngine.addChild(host);
		// add new Engine to set of Engine for embedded server
		embedded.addEngine(rtmptEngine);
		try {
			// loop through connectors and apply methods / props
			for (TomcatConnector tomcatConnector : connectors) {
				// get the connector
				Connector connector = tomcatConnector.getConnector();
        		// add new Connector to set of Connectors for embedded server, associated with Engine
       			embedded.addConnector(connector);
       			log.trace("Connector oName: {}", connector.getObjectName());
				log.info("Starting RTMPT engine");
				// start connector
				connector.start();
			}
		} catch (Exception e) {
			log.error("Error initializing RTMPT server instance", e);
		} finally {
			registerJMX();		
		}

	}
	
	/**
	 * Set servlet mappings
	 * 
	 * @param mappings mappings
	 */
	public void setMappings(Map<String, String> mappings) {
		log.debug("Servlet mappings: {}", mappings.size());
		servletMappings.putAll(mappings);
	}
	
}
