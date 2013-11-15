package org.red5.server.jetty;

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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.management.ManagementFactory;
import java.util.Map;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.eclipse.jetty.deploy.WebAppDeployer;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.util.thread.ThreadPool;
import org.red5.server.LoaderBase;
import org.red5.server.api.IApplicationContext;
import org.red5.server.jmx.mxbeans.LoaderMXBean;
import org.red5.server.util.FileUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.AbstractApplicationContext;

/**
 * Class that loads Red5 applications using Jetty.
 */
@SuppressWarnings("deprecation")
public class JettyLoader extends LoaderBase implements LoaderMXBean {

	/**
	 *  Logger
	 */
	protected static Logger log = LoggerFactory.getLogger(JettyLoader.class);

	/**
	 *  IServer implementation
	 */
	protected Server jetty;

	protected Connector[] connectors;

	protected ThreadPool threadPool;

	protected HandlerCollection handlers;

	/**
	 * Remove context from the current host.
	 * 
	 * @param path		Path
	 */
	@Override
	public void removeContext(String path) {
		Handler[] handlers = jetty.getHandlers();
		for (Handler handler : handlers) {
			if (handler instanceof ContextHandler && ((ContextHandler) handler).getContextPath().equals(path)) {
				try {
					((ContextHandler) handler).stop();
					jetty.removeBean(handler);
					break;
				} catch (Exception e) {
					log.error("Could not remove context: {}", path, e);
				}
			}
		}
		IApplicationContext ctx = LoaderBase.removeRed5ApplicationContext(path);
		if (ctx != null) {
			ctx.stop();
		} else {
			log.warn("Red5 application context could not be stopped, it was null for path: {}", path);
		}
	}

	/**
	 *
	 */
	@SuppressWarnings("all")
	public void init() {
		log.info("Loading Jetty context");
		// So this class is left just starting jetty
		try {
			// locate default web config
			File webConfigFile = new File(System.getProperty("red5.plugins_root"), "jetty-web.xml");
			if (!webConfigFile.exists()) {
				// extract from the jar
				extractFromJAR("jetty-web.xml", System.getProperty("red5.plugins_root"));
				if (!webConfigFile.exists()) {
					log.warn("Default web config was not found");
				}
			}
			String defaultWebConfig = webConfigFile.getAbsolutePath();
			log.info("Default web config file: {}", defaultWebConfig);

			if (webappFolder == null) {
				// Use default webapps directory
				webappFolder = FileUtil.formatPath(System.getProperty("red5.root"), "/webapps");
			}
			System.setProperty("red5.webapp.root", webappFolder);
			log.info("Application root: {}", webappFolder);

			// root location for servlet container
			String serverRoot = System.getProperty("red5.root");
			log.debug("Server root: {}", serverRoot);

			// set in the system for tomcat classes
			System.setProperty("jetty.home", serverRoot);
			System.setProperty("jetty.class.path", String.format("%s/lib%s%s/plugins", serverRoot, File.separator, serverRoot));

			String[] handlersArr = new String[] { "org.eclipse.jetty.webapp.WebInfConfiguration", "org.eclipse.jetty.webapp.WebXmlConfiguration",
					"org.eclipse.jetty.webapp.JettyWebXmlConfiguration", "org.eclipse.jetty.webapp.TagLibConfiguration", "org.red5.server.jetty.Red5WebPropertiesConfiguration" };

			// instance a new org.mortbay.jetty.Server
			log.info("Starting jetty servlet engine");
			jetty = new Server();
			jetty.setConnectors(connectors);
			jetty.setHandler(handlers);
			jetty.setThreadPool(threadPool);
			jetty.setStopAtShutdown(true);

			LoaderBase.setApplicationLoader(new JettyApplicationLoader(jetty, applicationContext));

			try {
				// Add web applications from web app root with web config
				HandlerCollection contexts = (HandlerCollection) jetty.getChildHandlerByClass(ContextHandlerCollection.class);
				if (contexts == null) {
					contexts = (HandlerCollection) jetty.getChildHandlerByClass(HandlerCollection.class);
				}
				WebAppDeployer deployer = new WebAppDeployer();
				deployer.setContexts(contexts);
				deployer.setWebAppDir(webappFolder);
				deployer.setDefaultsDescriptor(defaultWebConfig);
				deployer.setConfigurationClasses(handlersArr);
				deployer.setExtract(true);
				deployer.setParentLoaderPriority(true);
				deployer.start();
			} catch (Exception e) {
				log.error("Error deploying web applications", e);
			}

			// Start Jetty
			jetty.start();

		} catch (Exception e) {
			log.error("Error loading jetty", e);
		} finally {
			registerJMX();
		}

	}

	//TODO: Implement this for those who want to use Jetty
	public boolean startWebApplication(String applicationName) {
		return false;
	}

	protected void registerJMX() {
		// register with jmx
		MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
		try {
			ObjectName oName = new ObjectName("org.red5.server:type=JettyLoader");
			// check for existing registration before registering
			if (!mbs.isRegistered(oName)) {
				mbs.registerMBean(this, oName);
			} else {
				log.debug("ContextLoader is already registered in JMX");
			}
		} catch (Exception e) {
			log.warn("Error on jmx registration", e);
		}
	}

	protected void unregisterJMX() {
		MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
		try {
			ObjectName oName = new ObjectName("org.red5.server:type=JettyLoader");
			mbs.unregisterMBean(oName);
		} catch (Exception e) {
			log.warn("Exception unregistering", e);
		}
	}

	public Connector[] getConnectors() {
		return connectors;
	}

	public void setConnectors(Connector[] connectors) {
		this.connectors = connectors;
	}

	public void setThreadPool(ThreadPool threadPool) {
		this.threadPool = threadPool;
	}

	public void setHandlers(HandlerCollection handlers) {
		this.handlers = handlers;
	}

	/**
	 * Shut server down
	 */
	public void shutdown() {
		log.info("Shutting down Jetty context");
		//run through the applications and ensure that spring is told
		//to commence shutdown / disposal
		AbstractApplicationContext absCtx = (AbstractApplicationContext) LoaderBase.getApplicationContext();
		if (absCtx != null) {
			log.debug("Using loader base application context for shutdown");
			//get all the app (web) contexts and shut them down first
			Map<String, IApplicationContext> contexts = LoaderBase.getRed5ApplicationContexts();
			if (contexts.isEmpty()) {
				log.info("No contexts were found to shutdown");
			}
			for (Map.Entry<String, IApplicationContext> entry : contexts.entrySet()) {
				//stop the context
				log.debug("Calling stop on context: {}", entry.getKey());
				entry.getValue().stop();
			}
			if (absCtx.isActive()) {
				log.debug("Closing application context");
				absCtx.close();
			}
		} else {
			log.error("Error getting Spring bean factory for shutdown");
		}
		try {
			jetty.stop();
			System.exit(0);
		} catch (Exception e) {
			log.warn("Jetty could not be stopped", e);
			System.exit(1);
		}
	}

	private void extractFromJAR(String filePath, String dest) {
		try {
			String home = getClass().getProtectionDomain().getCodeSource().getLocation().getPath().replaceAll("%20", " ");
			JarFile jar = new JarFile(home);
			ZipEntry entry = jar.getEntry(filePath);
			File efile = new File(dest, entry.getName());
			InputStream in = new BufferedInputStream(jar.getInputStream(entry));
			OutputStream out = new BufferedOutputStream(new FileOutputStream(efile));
			byte[] buffer = new byte[2048];
			for (;;) {
				int nBytes = in.read(buffer);
				if (nBytes <= 0) {
					break;
				}
				out.write(buffer, 0, nBytes);
			}
			out.flush();
			out.close();
			in.close();
		} catch (Exception e) {
			log.warn("Exception extracting file from jar", e);
		}
	}

}
