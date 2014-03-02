package org.red5.server.tomcat;

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
import java.lang.management.ManagementFactory;
import java.util.Map;

import javax.management.JMX;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.servlet.ServletContext;

import org.apache.catalina.Container;
import org.apache.catalina.Context;
import org.apache.catalina.Host;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.Loader;
import org.apache.catalina.Valve;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.core.StandardHost;
import org.red5.server.ContextLoader;
import org.red5.server.LoaderBase;
import org.red5.server.jmx.mxbeans.ContextLoaderMXBean;
import org.red5.server.jmx.mxbeans.TomcatVHostLoaderMXBean;
import org.red5.server.util.FileUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.jmx.export.annotation.ManagedResource;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.XmlWebApplicationContext;

/**
 * Red5 loader for Tomcat virtual hosts.
 * 
 * @author Paul Gregoire (mondain@gmail.com)
 */
@ManagedResource(objectName = "org.red5.server:type=TomcatVHostLoader", description = "TomcatVHostLoader")
public class TomcatVHostLoader extends TomcatLoader implements TomcatVHostLoaderMXBean {

	// Initialize Logging
	private static Logger log = LoggerFactory.getLogger(TomcatVHostLoader.class);

	/**
	 * Base web applications directory
	 */
	protected String webappRoot;
	
	//the virtual hosts name
	protected String name;
	//the domain
	protected String domain;	
	
	protected boolean autoDeploy;
	protected boolean liveDeploy;
	protected boolean startChildren = true;
	protected boolean unpackWARs;	
	
	/**
	 * MBean object name used for de/registration purposes.
	 */
	private ObjectName oName;	
	
	private String defaultApplicationContextId = "default.context";
	
	/**
	 * Initialization.
	 */
	@SuppressWarnings("cast")
	@Override
	public void start() {
		log.info("Loading tomcat virtual host");
		if (webappFolder != null) {
			//check for match with base webapp root
			if (webappFolder.equals(webappRoot)) {
				log.error("Web application root cannot be the same as base");
				return;
			}
		}
		ClassLoader classloader = Thread.currentThread().getContextClassLoader();
		//ensure we have a host
		if (host == null) {
			host = createHost();
		}
		host.setParentClassLoader(classloader);
		String propertyPrefix = name;
		if (domain != null) {
			propertyPrefix += '_' + domain.replace('.', '_');
		}
		log.debug("Generating name (for props) {}", propertyPrefix);
		System.setProperty(propertyPrefix + ".webapp.root", webappRoot);
		log.info("Virtual host root: {}", webappRoot);
		log.info("Virtual host context id: {}", defaultApplicationContextId);
		// Root applications directory
		File appDirBase = new File(webappRoot);
		// Subdirs of root apps dir
		File[] dirs = appDirBase.listFiles(new TomcatLoader.DirectoryFilter());
		// Search for additional context files
		for (File dir : dirs) {
			String dirName = '/' + dir.getName();
			// check to see if the directory is already mapped
			if (null == host.findChild(dirName)) {
			    String webappContextDir = FileUtil.formatPath(appDirBase.getAbsolutePath(), dirName);
				Context ctx = null;
				if ("/root".equals(dirName) || "/root".equalsIgnoreCase(dirName)) {
					log.debug("Adding ROOT context");
					ctx = addContext("/", webappContextDir);
				} else {
					log.debug("Adding context from directory scan: {}", dirName);
					ctx = addContext(dirName, webappContextDir);
				}
				log.debug("Context: {}", ctx);
				webappContextDir = null;			
			}
		}
        appDirBase = null;
        dirs = null;
		// Dump context list
		if (log.isDebugEnabled()) {
			for (Container cont : host.findChildren()) {
				log.debug("Context child name: {}", cont.getName());
			}
		}
		engine.addChild(host);
		// Start server
		try {
			log.info("Starting Tomcat virtual host");
			//may not have to do this step for every host
			LoaderBase.setApplicationLoader(new TomcatApplicationLoader(embedded, host, applicationContext));
			for (Container cont : host.findChildren()) {
				if (cont instanceof StandardContext) {
					StandardContext ctx = (StandardContext) cont;			
            		ServletContext servletContext = ctx.getServletContext();
            		log.debug("Context initialized: {}", servletContext.getContextPath());
					//set the hosts id
					servletContext.setAttribute("red5.host.id", getHostId());
            		String prefix = servletContext.getRealPath("/");
            		log.debug("Path: {}", prefix);
            		try {
            			Loader cldr = ctx.getLoader();
            			log.debug("Loader type: {}", cldr.getClass().getName());
            			ClassLoader webClassLoader = cldr.getClassLoader();
            			log.debug("Webapp classloader: {}", webClassLoader);
            			//create a spring web application context
            			XmlWebApplicationContext appctx = new XmlWebApplicationContext();
            			appctx.setClassLoader(webClassLoader);
            			appctx.setConfigLocations(new String[]{"/WEB-INF/red5-*.xml"});
            			//check for red5 context bean
            			if (applicationContext.containsBean(defaultApplicationContextId)) {
                			appctx.setParent((ApplicationContext) applicationContext.getBean(defaultApplicationContextId));
						} else {
            				log.warn("{} bean was not found in context: {}", defaultApplicationContextId, applicationContext.getDisplayName());
            				//lookup context loader and attempt to get what we need from it
            				if (applicationContext.containsBean("context.loader")) {
            					ContextLoader contextLoader = (ContextLoader) applicationContext.getBean("context.loader");
            					appctx.setParent(contextLoader.getContext(defaultApplicationContextId));					
            				} else {
    							log.debug("Context loader was not found, trying JMX");
    							MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
    							// get the ContextLoader from jmx
    							ContextLoaderMXBean proxy = null;
    							ObjectName oName = null;
    							try {
    								oName = new ObjectName("org.red5.server:name=contextLoader,type=ContextLoader");
    								if (mbs.isRegistered(oName)) {
    									proxy = JMX.newMXBeanProxy(mbs, oName, ContextLoaderMXBean.class, true);
    									log.debug("Context loader was found");
    									proxy.setParentContext(defaultApplicationContextId, appctx.getId());
    								} else {
    									log.warn("Context loader was not found");
    								}
    							} catch (Exception e) {
    								log.warn("Exception looking up ContextLoader", e);
    							}            					
            				}
            			}
            			if (log.isDebugEnabled()) {
            				if (appctx.getParent() != null) {
            					log.debug("Parent application context: {}", appctx.getParent().getDisplayName());
            				}
            			}
            			//
            			appctx.setServletContext(servletContext);
            			//set the root webapp ctx attr on the each servlet context so spring can find it later					
            			servletContext.setAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, appctx);
            			appctx.refresh();
            		} catch (Throwable t) {
            			log.error("Error setting up context: {}", servletContext.getContextPath(), t);
            			if (log.isDebugEnabled()) {
            				t.printStackTrace();
            			}
            		}					
				}
			}	
		} catch (Exception e) {
			log.error("Error loading Tomcat virtual host", e);
		}			
		
	}

	/**
	 * Un-initialization.
	 */	
	@Override
	public void destroy() throws Exception {
		log.debug("TomcatVHostLoader un-init");		
		Container[] children = host.findChildren();
		for (Container c : children) {
			if (c instanceof StandardContext) {
				try {
					((StandardContext) c).stop();
					host.removeChild(c);			
				} catch (Exception e) {
					log.error("Could not stop context: {}", c.getName(), e);
				}				
			}
		}
		//remove system prop
		String propertyPrefix = name;
		if (domain != null) {
			propertyPrefix += '_' + domain.replace('.', '_');
		}		
		System.clearProperty(propertyPrefix + ".webapp.root");
		//stop the host
		try {
			((StandardHost) host).stop();
		} catch (LifecycleException e) {
			log.error("Could not stop host: {}", host.getName(), e);
		}
		//remove host
		engine.removeChild(host);
		//unregister jmx
		unregisterJMX();
	}	
	
	/**
	 * Starts a web application and its red5 (spring) component. This is basically a stripped down
	 * version of init().
	 * 
	 * @return true on success
	 */
	@SuppressWarnings("cast")
	public boolean startWebApplication(String applicationName) {
		boolean result = false;
		log.info("Starting Tomcat virtual host - Web application");	
		log.info("Virtual host root: {}", webappRoot);
		log.info("Virtual host context id: {}", defaultApplicationContextId);
		// application directory
		String contextName = '/' + applicationName;
		Container cont = null;
		//check if the context already exists for the host
		if ((cont = host.findChild(contextName)) == null) {
			log.debug("Context did not exist in host");
			String webappContextDir = FileUtil.formatPath(webappRoot, applicationName);
			//prepend slash
			Context ctx = addContext(contextName, webappContextDir);
    		//set the newly created context as the current container
    		cont = ctx;
		} else {
			log.debug("Context already exists in host");
		}
		try {
    		ServletContext servletContext = ((Context) cont).getServletContext();
    		log.debug("Context initialized: {}", servletContext.getContextPath());
    		String prefix = servletContext.getRealPath("/");
    		log.debug("Path: {}", prefix);
			Loader cldr = cont.getLoader();
			log.debug("Loader type: {}", cldr.getClass().getName());
			ClassLoader webClassLoader = cldr.getClassLoader();
			log.debug("Webapp classloader: {}", webClassLoader);
			//create a spring web application context
			XmlWebApplicationContext appctx = new XmlWebApplicationContext();
			appctx.setClassLoader(webClassLoader);
			appctx.setConfigLocations(new String[]{"/WEB-INF/red5-*.xml"});
			//check for red5 context bean
			if (applicationContext.containsBean(defaultApplicationContextId)) {
    			appctx.setParent((ApplicationContext) applicationContext.getBean(defaultApplicationContextId));					            				
			} else {
				log.warn("{} bean was not found in context: {}", defaultApplicationContextId, applicationContext.getDisplayName());
				//lookup context loader and attempt to get what we need from it
				if (applicationContext.containsBean("context.loader")) {
					ContextLoader contextLoader = (ContextLoader) applicationContext.getBean("context.loader");
					appctx.setParent(contextLoader.getContext(defaultApplicationContextId));					
				} else {
					log.debug("Context loader was not found, trying JMX");
					MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
					// get the ContextLoader from jmx
					ContextLoaderMXBean proxy = null;
					ObjectName oName = null;
					try {
						oName = new ObjectName("org.red5.server:name=contextLoader,type=ContextLoader");
						if (mbs.isRegistered(oName)) {
							proxy = JMX.newMXBeanProxy(mbs, oName, ContextLoaderMXBean.class, true);
							log.debug("Context loader was found");
							proxy.setParentContext(defaultApplicationContextId, appctx.getId());
						} else {
							log.warn("Context loader was not found");
						}
					} catch (Exception e) {
						log.warn("Exception looking up ContextLoader", e);
					}            					
				}
			}
			if (log.isDebugEnabled()) {
				if (appctx.getParent() != null) {
					log.debug("Parent application context: {}", appctx.getParent().getDisplayName());
				}
			}
			//
			appctx.setServletContext(servletContext);
			//set the root webapp ctx attr on the each servlet context so spring can find it later					
			servletContext.setAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, appctx);
			appctx.refresh();			
			result = true;
		} catch (Throwable t) {
			log.error("Error setting up context: {}", applicationName, t);
			if (log.isDebugEnabled()) {
				t.printStackTrace();
			}
		}			
		
		return result;
	}		
	
	/**
	 * Create a standard host.
	 * 
	 * @return host
	 */
	public Host createHost() {
		log.debug("Creating host");
		StandardHost stdHost = new StandardHost();
		stdHost.setAppBase(webappRoot);
		stdHost.setAutoDeploy(autoDeploy);
		if (domain == null) {
			stdHost.setName(name);
		} else {
			stdHost.setDomain(domain);
			//seems to require that the domain be appended to the name
			stdHost.setName(name + '.' + domain);
		}
		stdHost.setStartChildren(startChildren);
		stdHost.setUnpackWARs(unpackWARs);
		// See http://tomcat.apache.org/migration-7.html#Deployment
		stdHost.setCopyXML(true);	
		return stdHost;
	}
	
	/**
	 * Returns the current host.
	 *
	 * @return host
	 */
	public Host getHost() {
		return host;
	}
	
	/**
	 * Adds an alias to the current host.
	 * 
	 * @param alias alias
	 */
	public void addAlias(String alias) {
		log.debug("Adding alias: {}", alias);
		host.addAlias(alias);
	}	
	
	/**
	 * Removes an alias from the current host.
	 * 
	 * @param alias Alias
	 */
	public void removeAlias(String alias) {
		log.debug("Removing alias: {}", alias);
		String[] aliases = host.findAliases();
		for (String s : aliases) {
			if (alias.equals(s)) {
				host.removeAlias(alias);
				break;
			}
		}
	}
	
	/**
	 * Adds a valve to the current host.
	 * 
	 * @param valve Valve
	 */
	public void addValve(Valve valve) {
		log.debug("Adding valve: {}", valve);
		log.debug("Valve info: {}", valve.getInfo());
		((StandardHost) host).addValve(valve);
	}
	
	/**
	 * Removes a valve from the current host.
	 * 
	 * @param valveInfo Valve Information.
	 */
	public void removeValve(String valveInfo) {
		log.debug("Removing valve: {}", valveInfo);
		try {
			String[] valveNames = ((StandardHost) host).getValveNames();
			for (String s : valveNames) {
				log.debug("Valve name: {}", s);
			}
		} catch (Exception e) {
			log.error("", e);
		}		
		//TODO: fix removing valves
		//((StandardHost) host).removeValve(valve);	
	}
	
	/**
	 * Set additional contexts.
	 * 
	 * @param contexts Map of contexts
	 */
	@SuppressWarnings("deprecation")
	@Override
	public void setContexts(Map<String, String> contexts) {
		log.debug("setContexts: {}", contexts.size());
		for (Map.Entry<String, String> entry : contexts.entrySet()) {
			host.addChild(embedded.createContext(entry.getKey(), webappRoot	+ entry.getValue()));
		}
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getDomain() {
		return domain;
	}

	public void setDomain(String domain) {
		this.domain = domain;
	}

	public String getWebappRoot() {
		return webappRoot;
	}

	public void setWebappRoot(String webappRoot) {
		this.webappRoot = webappRoot;
	}

	public boolean getAutoDeploy() {
		return autoDeploy;
	}

	public void setAutoDeploy(boolean autoDeploy) {
		this.autoDeploy = autoDeploy;
	}

	public boolean getLiveDeploy() {
		return liveDeploy;
	}

	public void setLiveDeploy(boolean liveDeploy) {
		this.liveDeploy = liveDeploy;
	}

	public boolean getStartChildren() {
		return startChildren;
	}

	public void setStartChildren(boolean startChildren) {
		this.startChildren = startChildren;
	}

	public boolean getUnpackWARs() {
		return unpackWARs;
	}

	public void setUnpackWARs(boolean unpackWARs) {
		this.unpackWARs = unpackWARs;
	}
	
	public String getDefaultApplicationContextId() {
		return defaultApplicationContextId;
	}

	public void setDefaultApplicationContextId(String defaultApplicationContextId) {
		this.defaultApplicationContextId = defaultApplicationContextId;
	}
	
	protected void registerJMX() {
		// register with jmx
		MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
		try {
			oName = new ObjectName(String.format("org.red5.server:type=TomcatVHostLoader,name=%s,domain=%s", name, domain));
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
			mbs.unregisterMBean(oName);
		} catch (Exception e) {
			log.warn("Exception unregistering", e);
		}
	}

	
}