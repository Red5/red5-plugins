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

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Constructor;
import java.net.BindException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.servlet.ServletContext;

import org.red5.logging.Red5LoggerFactory;
import org.red5.server.LoaderBase;
import org.red5.server.api.IApplicationContext;
import org.red5.server.jmx.mxbeans.LoaderMXBean;
import org.red5.server.util.FileUtil;
import org.slf4j.Logger;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.jmx.export.annotation.ManagedResource;
import org.springframework.web.context.ConfigurableWebApplicationContext;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.XmlWebApplicationContext;

import winstone.HostConfiguration;
import winstone.Launcher;
import winstone.WebAppConfiguration;

/**
 * Red5 loader for the Winstone servlet container.
 * 
 * @author Paul Gregoire (mondain@gmail.com)
 */
@ManagedResource(objectName = "org.red5.server:type=WinstoneLoader", description = "WinstoneLoader")
public class WinstoneLoader extends LoaderBase implements ApplicationContextAware, LoaderMXBean {

	// Initialize Logging
	private static Logger log = Red5LoggerFactory.getLogger(WinstoneLoader.class);

	public static final String defaultSpringConfigLocation = "/WEB-INF/red5-*.xml";

	public static final String defaultParentContextKey = "default.context";

	private static List<String> contextNames = new ArrayList<String>();
	
	static {
		log.debug("Initializing Winstone");
	}

	/**
	 * Common name for the Service and Engine components.
	 */
	public String serviceEngineName = "red5Engine";

	/**
	 * Embedded Winstone service (like Catalina).
	 */
	protected static StoneLauncher embedded;

	/**
	 * Additional connection properties to be set at init.
	 */
	protected Map<String, String> connectionProperties = new HashMap<String, String>();

	/**
	 * IP Address to bind to.
	 */
	protected InetAddress address;

	/**
	 * Add context for path and docbase to current host.
	 * 
	 * @param path Path
	 * @param docBase Document base
	 * @return context (that is, web application)
	 */
	public WebAppConfiguration addContext(String path, String docBase) {
		log.debug("Add context - path: {} docbase: {}", path, docBase);
		return null;
	}

	/**
	 * Remove context from the current host.
	 * 
	 * @param path Path
	 */
	@Override
	public void removeContext(String path) {
		WinstoneApplicationLoader appLoader = (WinstoneApplicationLoader) LoaderBase.getApplicationLoader();
		WebAppConfiguration c = appLoader.getHostConfiguration().getWebAppByURI(path);
		c.destroy();
		IApplicationContext ctx = LoaderBase.removeRed5ApplicationContext(path);
		if (ctx != null) {
			ctx.stop();
		} else {
			log.warn("Context could not be stopped, it was null for path: {}", path);
		}
	}

	/**
	 * Initialization.
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public void init() {
		log.info("Loading Winstone context");
		//get a reference to the current threads classloader
		final ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
		// root location for servlet container
		String serverRoot = System.getProperty("red5.root");
		log.info("Server root: {}", serverRoot);
		String confRoot = System.getProperty("red5.config_root");
		log.info("Config root: {}", confRoot);
		// configure the webapps folder, make sure we have one
		if (webappFolder == null) {
			// Use default webapps directory
			webappFolder = FileUtil.formatPath(System.getProperty("red5.root"), "/webapps");
		}
		System.setProperty("red5.webapp.root", webappFolder);
		log.info("Application root: {}", webappFolder);
		// create one embedded (server) and use it everywhere
		Map args = new HashMap();
		//args.put("webroot", webappFolder + "/root");
		args.put("webappsDir", webappFolder);
		// Start server
		try {
			log.info("Starting Winstone servlet engine");
			Launcher.initLogger(args);	
			// spawns threads, so your application doesn't block
			embedded = new StoneLauncher(args);
			log.trace("Classloader for embedded: {} TCL: {}", Launcher.class.getClassLoader(), originalClassLoader);
			// get the default host
			HostConfiguration host = embedded.getHostGroup().getHostByName(null);
			// set the primary application loader
			LoaderBase.setApplicationLoader(new WinstoneApplicationLoader(host, applicationContext));
			// get root first, we may want to start a spring config internally but for now skip it
			WebAppConfiguration root = host.getWebAppByURI("/");
			log.trace("Root: {}", root);
			// scan the sub directories to determine our context names
			buildContextNameList(webappFolder);
			// loop the other contexts
			for (String contextName : contextNames) {
				WebAppConfiguration ctx = host.getWebAppByURI(contextName);
				// get access to the servlet context
				final ServletContext servletContext = ctx.getContext(contextName);
				log.debug("Context initialized: {}", servletContext.getContextPath());
				//set the hosts id
				servletContext.setAttribute("red5.host.id", host.getHostname());
				// get the path
				String prefix = servletContext.getRealPath("/");
				log.debug("Path: {}", prefix);
				try {
					final ClassLoader cldr = ctx.getLoader();
					log.debug("Loader type: {}", cldr.getClass().getName());
					// get the (spring) config file path
					final String contextConfigLocation = servletContext.getInitParameter(org.springframework.web.context.ContextLoader.CONFIG_LOCATION_PARAM) == null ? defaultSpringConfigLocation
							: servletContext.getInitParameter(org.springframework.web.context.ContextLoader.CONFIG_LOCATION_PARAM);
					log.debug("Spring context config location: {}", contextConfigLocation);
					// get the (spring) parent context key
					final String parentContextKey = servletContext.getInitParameter(org.springframework.web.context.ContextLoader.LOCATOR_FACTORY_KEY_PARAM) == null ? defaultParentContextKey
							: servletContext.getInitParameter(org.springframework.web.context.ContextLoader.LOCATOR_FACTORY_KEY_PARAM);
					log.debug("Spring parent context key: {}", parentContextKey);
					//set current threads classloader to the webapp classloader
					Thread.currentThread().setContextClassLoader(cldr);
					//create a thread to speed-up application loading
					Thread thread = new Thread("Launcher:" + servletContext.getContextPath()) {
						public void run() {
							//set thread context classloader to web classloader
							Thread.currentThread().setContextClassLoader(cldr);
							//get the web app's parent context
							ApplicationContext parentContext = null;
							if (applicationContext.containsBean(parentContextKey)) {
								parentContext = (ApplicationContext) applicationContext.getBean(parentContextKey);
							} else {
								log.warn("Parent context was not found: {}", parentContextKey);
							}
							// create a spring web application context
							final String contextClass = servletContext.getInitParameter(org.springframework.web.context.ContextLoader.CONTEXT_CLASS_PARAM) == null ? XmlWebApplicationContext.class
									.getName() : servletContext.getInitParameter(org.springframework.web.context.ContextLoader.CONTEXT_CLASS_PARAM);
							//web app context (spring)
							ConfigurableWebApplicationContext appctx = null;
							try {
								Class<?> clazz = Class.forName(contextClass, true, cldr);
								appctx = (ConfigurableWebApplicationContext) clazz.newInstance();
							} catch (Throwable e) {
								throw new RuntimeException("Failed to load webapplication context class.", e);
							}
							appctx.setConfigLocations(new String[] { contextConfigLocation });
							appctx.setServletContext(servletContext);
							//set parent context or use current app context
							if (parentContext != null) {
								appctx.setParent(parentContext);
							} else {
								appctx.setParent(applicationContext);
							}
							// set the root webapp ctx attr on the each servlet context so spring can find it later
							servletContext.setAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, appctx);
							//refresh the factory
							log.trace("Classloader prior to refresh: {}", appctx.getClassLoader());
							appctx.refresh();
							if (log.isDebugEnabled()) {
								log.debug("Red5 app is active: {} running: {}", appctx.isActive(), appctx.isRunning());
							}
						}
					};
					thread.setDaemon(true);
					thread.start();
				} catch (Throwable t) {
					log.error("Error setting up context: {} due to: {}", servletContext.getContextPath(), t.getMessage());
					t.printStackTrace();
				} finally {
					//reset the classloader
					Thread.currentThread().setContextClassLoader(originalClassLoader);
				}
			}			
		} catch (Exception e) {
			if (e instanceof BindException || e.getMessage().indexOf("BindException") != -1) {
				log.error("Error loading Winstone, unable to bind connector. You may not have permission to use the selected port", e);
			} else {
				log.error("Error loading Winstone", e);
			}
		} finally {
			registerJMX();
		}

	}
	
	/**
	 * Starts a web application and its red5 (spring) component. This is
	 * basically a stripped down version of init().
	 * 
	 * @return true on success
	 */
	public boolean startWebApplication(String applicationName) {
		return false;
	}	
	
	/**
	 * Figure out the context names.
	 * 
	 * @param webappFolder
	 */
	private void buildContextNameList(String webappFolder) {
		// Root applications directory
		File appDirBase = new File(webappFolder);
		// Subdirs of root apps dir
		File[] dirs = appDirBase.listFiles(new DirectoryFilter());
		// Search for additional context files
		for (File dir : dirs) {
			String dirName = '/' + dir.getName();
			if (dirName.equalsIgnoreCase("/ROOT")) {
				//skip root
				continue;
			}
			String webappContextDir = FileUtil.formatPath(appDirBase.getAbsolutePath(), dirName);
			File webXml = new File(webappContextDir, "WEB-INF/web.xml");
			if (webXml.exists()) {
				log.debug("Webapp context directory (full path): {}", webappContextDir);
				contextNames.add(dirName);
			}
			webappContextDir = null;
			webXml = null;
		}
		appDirBase = null;
		dirs = null;
	}

	/**
	 * The address to which we will bind.
	 * 
	 * @param address
	 */
	public void setAddress(InetSocketAddress address) {
		log.info("Address to bind: {}", address);
		this.address = address.getAddress();
	}

	/**
	 * Set connection properties for the connector
	 * 
	 * @param props additional properties to set
	 */
	public void setConnectionProperties(Map<String, String> props) {
		log.debug("Connection props: {}", props.size());
		this.connectionProperties.putAll(props);
	}

	protected void registerJMX() {
		// register with jmx
		MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
		try {
			ObjectName oName = new ObjectName("org.red5.server:type=WinstoneLoader");
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
			ObjectName oName = new ObjectName("org.red5.server:type=WinstoneLoader");
			mbs.unregisterMBean(oName);
		} catch (Exception e) {
			log.warn("Exception unregistering", e);
		}
	}	
	
	/**
	 * Shut server down.
	 */
	public void shutdown() {
		log.info("Shutting down Winstone context");
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
			//stop Winstone
			embedded.shutdown();
			//kill the jvm
			System.exit(0);
		} catch (Exception e) {
			log.warn("Winstone could not be stopped", e);
			throw new RuntimeException("Winstone could not be stopped");
		}
	}

	/**
	 * Our implementation of the Winstone Launcher class.
	 */
	final class StoneLauncher {

		static final String HTTP_LISTENER_CLASS = "winstone.HttpListener";

		static final String HTTPS_LISTENER_CLASS = "winstone.ssl.HttpsListener";

		static final String DEFAULT_JNDI_MGR_CLASS = "winstone.jndi.ContainerJNDIManager";

		private winstone.HostGroup hostGroup;

		private winstone.ObjectPool objectPool;

		private List<winstone.Listener> listeners = new ArrayList<winstone.Listener>();

		@SuppressWarnings("rawtypes")
		private Map args;

		private winstone.JNDIManager globalJndiManager;

		@SuppressWarnings({ "unchecked", "rawtypes", "deprecation" })
		public StoneLauncher(Map args) throws IOException {
			// load properties file
	        InputStream embeddedPropsStream = WinstoneLoader.class.getResourceAsStream("embedded.properties");
	        if (embeddedPropsStream != null) {
	            Properties props = new Properties();
	            props.load(embeddedPropsStream);
	            for (Iterator i = props.keySet().iterator(); i.hasNext(); ) {
	                String key = (String) i.next();
	                if (!args.containsKey(key.trim())) {
	                    args.put(key.trim(), props.getProperty(key).trim());
	                }
	            }
	            props.clear();	            
	            embeddedPropsStream.close();
	        }
	        // use jndi?
			boolean useJNDI = WebAppConfiguration.booleanArg(args, "useJNDI", false);
			// Set jndi resource handler if not set (workaround for JamVM bug)
			if (useJNDI)
				try {
					Class ctxFactoryClass = Class.forName("winstone.jndi.java.javaURLContextFactory");
					if (System.getProperty("java.naming.factory.initial") == null) {
						System.setProperty("java.naming.factory.initial", ctxFactoryClass.getName());
					}
					if (System.getProperty("java.naming.factory.url.pkgs") == null) {
						System.setProperty("java.naming.factory.url.pkgs", "winstone.jndi");
					}
				} catch (ClassNotFoundException err) {
				}
			log.debug("Launcher.StartupArgs {}", args);
			this.args = args;
			// Check for java home
			List jars = new ArrayList();
			List commonLibCLPaths = new ArrayList();
			String defaultJavaHome = System.getProperty("java.home");
			String javaHome = WebAppConfiguration.stringArg(args, "javaHome", defaultJavaHome);
			log.debug("Launcher.UsingJavaHome {}", javaHome);
			String toolsJarLocation = WebAppConfiguration.stringArg(args, "toolsJar", null);
			File toolsJar = null;
			if (toolsJarLocation == null) {
				toolsJar = new File(javaHome, "lib/tools.jar");
				// first try - if it doesn't exist, try up one dir since we might have 
				// the JRE home by mistake
				if (!toolsJar.exists()) {
					File javaHome2 = new File(javaHome).getParentFile();
					File toolsJar2 = new File(javaHome2, "lib/tools.jar");
					if (toolsJar2.exists()) {
						javaHome = javaHome2.getCanonicalPath();
						toolsJar = toolsJar2;
					}
				}
			} else {
				toolsJar = new File(toolsJarLocation);
			}
			// Add tools jar to classloader path
			if (toolsJar.exists()) {
				jars.add(toolsJar.toURL());
				commonLibCLPaths.add(toolsJar);
				log.debug("Launcher.AddedCommonLibJar {}", toolsJar.getName());
			} else if (WebAppConfiguration.booleanArg(args, "useJasper", false)) {
				log.warn("Launcher.ToolsJarNotFound");
			}
			// Set up common lib class loader
			String commonLibCLFolder = WebAppConfiguration.stringArg(args, "commonLibFolder", "lib");
			File libFolder = new File(commonLibCLFolder);
			if (libFolder.exists() && libFolder.isDirectory()) {
				log.debug("Launcher.UsingCommonLib {}", libFolder.getCanonicalPath());
				File children[] = libFolder.listFiles();
				for (int n = 0; n < children.length; n++)
					if (children[n].getName().endsWith(".jar") || children[n].getName().endsWith(".zip")) {
						jars.add(children[n].toURL());
						commonLibCLPaths.add(children[n]);
						log.debug("Launcher.AddedCommonLibJar {}", children[n].getName());
					}
			} else {
				log.debug("Launcher.NoCommonLib");
			}
			ClassLoader commonLibCL = new URLClassLoader((URL[]) jars.toArray(new URL[jars.size()]), getClass().getClassLoader());
			log.trace("Launcher.CLClassLoader {}", commonLibCL.toString());
			log.trace("Launcher.CLClassLoader {}", commonLibCLPaths.toString());
			// If jndi is enabled, run the container wide jndi populator
			if (useJNDI) {
				String jndiMgrClassName = WebAppConfiguration.stringArg(args, "containerJndiClassName", DEFAULT_JNDI_MGR_CLASS).trim();
				try {
					// Build the realm
					Class jndiMgrClass = Class.forName(jndiMgrClassName, true, commonLibCL);
					Constructor jndiMgrConstr = jndiMgrClass.getConstructor(new Class[] { Map.class, List.class, ClassLoader.class });
					this.globalJndiManager = (winstone.JNDIManager) jndiMgrConstr.newInstance(new Object[] { args, null, commonLibCL });
					this.globalJndiManager.setup();
				} catch (ClassNotFoundException err) {
					log.debug("Launcher.JNDIDisabled");
				} catch (Throwable err) {
					log.error("Launcher.JNDIError {}", jndiMgrClassName, err);
				}
			}
			// create an object pool
			objectPool = new winstone.ObjectPool(args);
			// Open the web apps
			hostGroup = new winstone.HostGroup(null, objectPool, commonLibCL, (File[]) commonLibCLPaths.toArray(new File[0]), args);
			// Create connectors (http, https)
			spawnListener(HTTP_LISTENER_CLASS);
			try {
				Class.forName("javax.net.ServerSocketFactory");
				spawnListener(HTTPS_LISTENER_CLASS);
			} catch (ClassNotFoundException err) {
				log.debug("Launcher.NeedsJDK14 {}", HTTPS_LISTENER_CLASS);
			}
		}

		/**
		 * Instantiates listeners. Note that an exception thrown in the 
		 * constructor is interpreted as the listener being disabled, so 
		 * don't do anything too adventurous in the constructor, or if you do, 
		 * catch and log any errors locally before rethrowing.
		 */
		@SuppressWarnings({ "rawtypes", "unchecked" })
		protected void spawnListener(String listenerClassName) {
			try {
				Class listenerClass = Class.forName(listenerClassName);
				Constructor listenerConstructor = listenerClass.getConstructor(new Class[] { Map.class, winstone.ObjectPool.class, winstone.HostGroup.class });
				winstone.Listener listener = (winstone.Listener) listenerConstructor.newInstance(new Object[] { args, objectPool, hostGroup });
				if (listener.start()) {
					listeners.add(listener);
				}
			} catch (ClassNotFoundException err) {
				log.info("Launcher.ListenerNotFound {}", listenerClassName);
			} catch (Throwable err) {
				log.error("Launcher.ListenerStartupError {}", listenerClassName, err);
			}
		}

		public void shutdown() {
	        // Release all listeners/pools/webapps
	        for (winstone.Listener listener : listeners) {
	            listener.destroy();
	        }
	        objectPool.destroy();
	        hostGroup.destroy();
	        if (globalJndiManager != null) {
	            globalJndiManager.tearDown();
	        }
	    }		
		
	    winstone.HostGroup getHostGroup() {
			return hostGroup;
		}

		List<winstone.Listener> getListeners() {
			return listeners;
		}

	}

	/**
	 * Filters directory content
	 */
	protected final static class DirectoryFilter implements FilenameFilter {
		/**
		 * Check whether file matches filter rules
		 * 
		 * @param dir Directory
		 * @param name File name
		 * @return true If file does match filter rules, false otherwise
		 */
		public boolean accept(File dir, String name) {
			File f = new File(dir, name);
			log.trace("Filtering: {} name: {}", dir.getName(), name);
			log.trace("Constructed dir: {}", f.getAbsolutePath());
			// filter out all non-directories that are hidden and/or not
			// readable
			boolean result = f.isDirectory() && f.canRead() && !f.isHidden();
			// nullify
			f = null;
			return result;
		}
	}

}
