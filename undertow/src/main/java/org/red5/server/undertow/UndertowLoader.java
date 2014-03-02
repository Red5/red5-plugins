package org.red5.server.undertow;

/*
 * RED5 Open Source Flash Server - http://www.osflash.org/red5
 *
 * Copyright (c) 2006-2011 by respective authors (see below). All rights reserved.
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

import static io.undertow.servlet.Servlets.deployment;
import io.undertow.Undertow;
import io.undertow.Undertow.ListenerType;
import io.undertow.server.handlers.PathHandler;
import io.undertow.servlet.api.Deployment;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import io.undertow.servlet.api.FilterInfo;
import io.undertow.servlet.api.ListenerInfo;
import io.undertow.servlet.api.ServletContainer;
import io.undertow.servlet.api.ServletInfo;

import java.io.File;
import java.io.FilenameFilter;
import java.lang.management.ManagementFactory;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.EventListener;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.servlet.Filter;
import javax.servlet.Servlet;
import javax.servlet.ServletContext;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.red5.classloading.ChildFirstClassLoader;
import org.red5.classloading.ClassLoaderBuilder;
import org.red5.logging.Red5LoggerFactory;
import org.red5.server.LoaderBase;
import org.red5.server.api.IApplicationContext;
import org.red5.server.jmx.mxbeans.LoaderMXBean;
import org.red5.server.util.FileUtil;
import org.slf4j.Logger;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.jmx.export.annotation.ManagedResource;
import org.springframework.web.context.ConfigurableWebApplicationContext;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.XmlWebApplicationContext;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Red5 loader for Undertow.
 * 
 * @author Paul Gregoire (mondain@gmail.com)
 */
@ManagedResource(objectName = "org.red5.server:type=UndertowLoader", description = "UndertowLoader")
public class UndertowLoader extends LoaderBase implements DisposableBean, LoaderMXBean {

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
			// filter out all non-directories that are hidden and/or not readable
			boolean result = f.isDirectory() && f.canRead() && !f.isHidden();
			// nullify
			f = null;
			return result;
		}
	}

	// Initialize Logging
	private static Logger log = Red5LoggerFactory.getLogger(UndertowLoader.class);

	public static final String defaultSpringConfigLocation = "/WEB-INF/red5-*.xml";

	public static final String defaultParentContextKey = "default.context";

	static {
		log.debug("Initializing Undertow");
	}

	/**
	 * Common name for the Service and Engine components.
	 */
	public String serviceEngineName = "red5Engine";

	/**
	 * Embedded Undertow server.
	 */
	protected static Undertow server;

	/**
	 * Undertow servlet container.
	 */
	protected static ServletContainer container;

	/**
	 * Connectors
	 */
	protected List<UndertowConnector> connectors;
	
	private static List<String> contextPaths = new ArrayList<String>();

	/**
	 * Add context for path and docbase to current host.
	 * 
	 * @param path Path
	 * @param docBase Document base
	 * @return object
	 */
	public Object addContext(String path, String docBase) {
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
	public void start() {
		log.info("Loading undertow context");
		//get a reference to the current threads classloader
		final ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
		// root location for servlet container
		String serverRoot = System.getProperty("red5.root");
		log.info("Server root: {}", serverRoot);
		String confRoot = System.getProperty("red5.config_root");
		log.info("Config root: {}", confRoot);
		if (webappFolder == null) {
			// Use default webapps directory
			webappFolder = FileUtil.formatPath(System.getProperty("red5.root"), "/webapps");
		}
		System.setProperty("red5.webapp.root", webappFolder);
		log.info("Application root: {}", webappFolder);
		// scan the sub directories to determine our context paths
		buildContextPathList(webappFolder);
		try {
			// create our servlet container
	        container = ServletContainer.Factory.newInstance();
			// create a root path handler
			final PathHandler rootHandler = new PathHandler();
			// create one server and use it everywhere
			Undertow.Builder builder = Undertow.builder();
			// loop through connectors adding listeners to the builder
			for (UndertowConnector undertowConnector : connectors) {
				InetSocketAddress addr = undertowConnector.getSocketAddress();
				builder.addListener(addr.getPort(), addr.getHostName(), (undertowConnector.isSecure() ? ListenerType.HTTPS : ListenerType.HTTP));
			}
			log.trace("Classloader for undertow: {} TCL: {}", Undertow.class.getClassLoader(), originalClassLoader);
			// create references for later lookup
			LoaderBase.setApplicationLoader(new UndertowApplicationLoader(container, applicationContext));
			// loop the other contexts
			for (String contextPath : contextPaths) {
				// create a class loader for the context
				ClassLoader classLoader = buildWebClassLoader(originalClassLoader, webappFolder, contextPath);
				// create deployment info
				DeploymentInfo info = deployment()
		                .setClassLoader(classLoader)
		                .setContextPath(contextPath.equalsIgnoreCase("/ROOT") ? "/" : contextPath)
		                .setDefaultEncoding("UTF-8")
		                .setDeploymentName(contextPath.substring(1) + ".war")
		                .setUrlEncoding("UTF-8");
				// parse the web.xml and configure the context
				parseWebXml(webappFolder, contextPath, info);
				if (log.isDebugEnabled()) {
					log.debug("Deployment info - name: {} servlets: {} filters: {}", new Object[]{info.getDisplayName(), info.getServlets().size(), info.getFilters().size()});
				}
				// add the new deployment to the servlet container
				DeploymentManager manager = container.addDeployment(info);
				// set a reference to the manager and deploy the context
				LoaderBase.setRed5ApplicationContext(contextPath, new UndertowApplicationContext(manager));
				// deploy
				manager.deploy();
				// add path
	            rootHandler.addPrefixPath(info.getContextPath(), manager.start());
				// get deployment
				Deployment deployment = manager.getDeployment();
				// get servlet context
				final ServletContext servletContext = deployment.getServletContext();
				log.debug("Context initialized: {}", servletContext.getContextPath());
				try {
					log.debug("Context: {}", servletContext);
					final ClassLoader webClassLoader = info.getClassLoader();
					log.debug("Webapp classloader: {}", webClassLoader);
					// get the (spring) config file path
					final String contextConfigLocation = servletContext.getInitParameter(org.springframework.web.context.ContextLoader.CONFIG_LOCATION_PARAM) == null ? defaultSpringConfigLocation
							: servletContext.getInitParameter(org.springframework.web.context.ContextLoader.CONFIG_LOCATION_PARAM);
					log.debug("Spring context config location: {}", contextConfigLocation);
					// get the (spring) parent context key
					final String parentContextKey = servletContext.getInitParameter(org.springframework.web.context.ContextLoader.LOCATOR_FACTORY_KEY_PARAM) == null ? defaultParentContextKey
							: servletContext.getInitParameter(org.springframework.web.context.ContextLoader.LOCATOR_FACTORY_KEY_PARAM);
					log.debug("Spring parent context key: {}", parentContextKey);
					// set current threads classloader to the webapp classloader
					Thread.currentThread().setContextClassLoader(webClassLoader);
					// create a thread to speed-up application loading
					Thread thread = new Thread("Launcher:" + servletContext.getContextPath()) {
						public void run() {
							//set thread context classloader to web classloader
							Thread.currentThread().setContextClassLoader(webClassLoader);
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
							// web app context (spring)
							ConfigurableWebApplicationContext appctx = null;
							try {
								Class<?> clazz = Class.forName(contextClass, true, webClassLoader);
								appctx = (ConfigurableWebApplicationContext) clazz.newInstance();
								// set the root webapp ctx attr on the each servlet context so spring can find it later
								servletContext.setAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, appctx);
								appctx.setConfigLocations(new String[] { contextConfigLocation });
								appctx.setServletContext(servletContext);
								// set parent context or use current app context
								if (parentContext != null) {
									appctx.setParent(parentContext);
								} else {
									appctx.setParent(applicationContext);
								}
								// refresh the factory
								log.trace("Classloader prior to refresh: {}", appctx.getClassLoader());
								appctx.refresh();
								if (log.isDebugEnabled()) {
									log.debug("Red5 app is active: {} running: {}", appctx.isActive(), appctx.isRunning());
								}
								appctx.start();
							} catch (Throwable e) {
								throw new RuntimeException("Failed to load webapplication context class", e);
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
			// Dump deployments list
			if (log.isDebugEnabled()) {
				for (String deployment : container.listDeployments()) {
					log.debug("Container deployment: {}", deployment);
				}
			}
			// if everything is ok at this point then call the rtmpt and rtmps beans so they will init
//			if (applicationContext.containsBean("rtmpt.server")) {
//				log.debug("Initializing RTMPT");
//				applicationContext.getBean("rtmpt.server");
//				log.debug("Finished initializing RTMPT");
//			} else {
//				log.info("Dedicated RTMPT server configuration was not specified");
//			}
//			if (applicationContext.containsBean("rtmps.server")) {
//				log.debug("Initializing RTMPS");
//				applicationContext.getBean("rtmps.server");
//				log.debug("Finished initializing RTMPS");
//			} else {
//				log.debug("Dedicated RTMPS server configuration was not specified");
//			}
			// add a root handler
			builder.setHandler(rootHandler);
			// build the server instance
	        server = builder.build();
			log.info("Starting Undertow");
			server.start();
		} catch (Exception e) {
			if (e instanceof BindException || e.getMessage().indexOf("BindException") != -1) {
				log.error("Error loading undertow, unable to bind connector. You may not have permission to use the selected port", e);
			} else {
				log.error("Error loading undertow", e);
			}
		} finally {
			registerJMX();
		}
		log.debug("Undertow init exit");
	}
		
	/**
	 * Starts a web application and its red5 (spring) component. This is
	 * basically a stripped down version of start().
	 * 
	 * @return true on success
	 */
	public boolean startWebApplication(String applicationName) {
		return false;
	}	
	
	/**
	 * Figure out the context paths.
	 * 
	 * @param webappFolder
	 */
	protected void buildContextPathList(String webappFolder) {
		// Root applications directory
		File appDirBase = new File(webappFolder);
		// Subdirs of root apps dir
		File[] dirs = appDirBase.listFiles(new DirectoryFilter());
		// Search for additional context files
		for (File dir : dirs) {
			String dirName = '/' + dir.getName();
			String webappContextDir = FileUtil.formatPath(appDirBase.getAbsolutePath(), dirName);
			File webXml = new File(webappContextDir, "WEB-INF/web.xml");
			if (webXml.exists()) {
				log.debug("Webapp context directory (full path): {}", webappContextDir);
				contextPaths.add(dirName);
			}
			webappContextDir = null;
			webXml = null;
		}
		appDirBase = null;
		dirs = null;
	}		
	
	/**
	 * Build a child-first classloader for a webapp.
	 * 
	 * @param parent
	 * @param webappsPath
	 * @param contextPath
	 * @return
	 */
	protected ClassLoader buildWebClassLoader(ClassLoader parent, String webappsPath, String contextPath) {
		// the class loader to return
		ClassLoader loader = null;
		// list for the urls
		List<URL> urlList = new ArrayList<URL>(13);
		// file pointer to the context dir
		File path = new File(webappsPath, contextPath);
		if (path.isDirectory()) {
			// get libs
			File libDir = new File(path, "WEB-INF/lib");
			// this should not be null but it can happen
			if (libDir != null && libDir.canRead()) {
				ClassLoaderBuilder.JarFileFilter jarFileFilter = new ClassLoaderBuilder.JarFileFilter();
				File[] libs = libDir.listFiles(jarFileFilter);
				log.debug("Webapp lib count: {}", libs.length);
				for (File lib : libs) {
					try {
						urlList.add(lib.toURI().toURL());
					} catch (MalformedURLException e) {
						log.warn("Exception reading webapp libs", e);
					}
				}
			}
			// get classes
			File classesDir = new File(path, "WEB-INF/classes");
			// this also should not be null but it can happen
			if (classesDir != null && classesDir.canRead()) {
				File[] classes = classesDir.listFiles();
				log.debug("Webapp class count: {}", classes.length);
				for (File clas : classes) {
					try {
						urlList.add(clas.toURI().toURL());
					} catch (MalformedURLException e) {
						log.warn("Exception reading webapp classes", e);
					}
				}				
			}
		}		
		// use a child-first class loader scheme for webapps
		loader = new ChildFirstClassLoader(urlList.toArray(new URL[0]), parent);
		return loader;
	}
	
	/**
	 * Parses the web.xml and configures the context.
	 * 
	 * @param webappsPath
	 * @param contextPath
	 * @param info
	 */
	@SuppressWarnings("unchecked")
	public void parseWebXml(String webappsPath, String contextPath, DeploymentInfo info) {
		File path = new File(webappsPath, contextPath);
		File webxml = new File(path, "/WEB-INF/web.xml");
		if (webxml.exists() && webxml.canRead()) {
			try {
				DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
				DocumentBuilder docBuilder = docBuilderFactory.newDocumentBuilder();
				Document doc = docBuilder.parse(webxml);
				// normalize text representation
				doc.getDocumentElement().normalize();
				log.trace("Root element of the doc is {}", doc.getDocumentElement().getNodeName());			
				// to hold our servlets
				Map<String, ServletInfo> servletMap = new HashMap<String, ServletInfo>();
				// to hold our filters
				Map<String, FilterInfo> filterMap = new HashMap<String, FilterInfo>();
				// do context-param - available to the entire scope of the web application
				NodeList listOfElements = doc.getElementsByTagName("context-param");
				int totalElements = listOfElements.getLength();
				log.trace("Total no of context-params: {}", totalElements);
				for (int s = 0; s < totalElements; s++) {
					Node fstNode = listOfElements.item(s);
					if (fstNode.getNodeType() == Node.ELEMENT_NODE) {
						Element fstElmnt = (Element) fstNode;
						NodeList fstNmElmntLst = fstElmnt.getElementsByTagName("param-name");
						Element fstNmElmnt = (Element) fstNmElmntLst.item(0);
						NodeList fstNm = fstNmElmnt.getChildNodes();
						String pName = (fstNm.item(0)).getNodeValue().trim();
						log.trace("Param name: {}", pName);
						NodeList lstNmElmntLst = fstElmnt.getElementsByTagName("param-value");
						Element lstNmElmnt = (Element) lstNmElmntLst.item(0);
						NodeList lstNm = lstNmElmnt.getChildNodes();
						String pValue = (lstNm.item(0)).getNodeValue().trim();
						log.trace("Param value: {}", pValue);
						// add the param
						info.addServletContextAttribute(pName, pValue);
					}
				}
				// do listener
				listOfElements = doc.getElementsByTagName("listener");
				totalElements = listOfElements.getLength();
				log.trace("Total no of listeners: {}", totalElements);
				for (int s = 0; s < totalElements; s++) {
					Node fstNode = listOfElements.item(s);
					if (fstNode.getNodeType() == Node.ELEMENT_NODE) {
						Element fstElmnt = (Element) fstNode;
						NodeList fstNmElmntLst = fstElmnt.getElementsByTagName("listener-class");
						Element fstNmElmnt = (Element) fstNmElmntLst.item(0);
						NodeList fstNm = fstNmElmnt.getChildNodes();
						String pName = (fstNm.item(0)).getNodeValue().trim();
						log.trace("Param name: {}", pName);
						ListenerInfo listener = new ListenerInfo((Class<? extends EventListener>) info.getClassLoader().loadClass(pName));
						info.addListener(listener);
					}
				}
				// do filter
				listOfElements = doc.getElementsByTagName("filter");
				totalElements = listOfElements.getLength();
				log.trace("Total no of filters: {}", totalElements);
				for (int s = 0; s < totalElements; s++) {
					Node fstNode = listOfElements.item(s);
					if (fstNode.getNodeType() == Node.ELEMENT_NODE) {
						Element fstElmnt = (Element) fstNode;
						NodeList fstNmElmntLst = fstElmnt.getElementsByTagName("filter-name");
						Element fstNmElmnt = (Element) fstNmElmntLst.item(0);
						NodeList fstNm = fstNmElmnt.getChildNodes();
						String pName = (fstNm.item(0)).getNodeValue().trim();
						log.trace("Param name: {}", pName);
						NodeList lstNmElmntLst = fstElmnt.getElementsByTagName("filter-class");
						Element lstNmElmnt = (Element) lstNmElmntLst.item(0);
						NodeList lstNm = lstNmElmnt.getChildNodes();
						String pValue = (lstNm.item(0)).getNodeValue().trim();
						log.trace("Param value: {}", pValue);
						// create the filter
						FilterInfo filter = new FilterInfo(pName, (Class<? extends Filter>) info.getClassLoader().loadClass(pValue));	
						// do init-param - available in the context of a servlet or filter in the web application
						listOfElements = fstElmnt.getElementsByTagName("init-param");
						totalElements = listOfElements.getLength();
						log.trace("Total no of init-params: {}", totalElements);
						for (int i = 0; i < totalElements; i++) {
							Node inNode = listOfElements.item(i);
							if (inNode.getNodeType() == Node.ELEMENT_NODE) {
								Element inElmnt = (Element) inNode;
								NodeList inNmElmntLst = inElmnt.getElementsByTagName("param-name");
								Element inNmElmnt = (Element) inNmElmntLst.item(0);
								NodeList inNm = inNmElmnt.getChildNodes();
								String inName = (inNm.item(0)).getNodeValue().trim();
								log.trace("Param name: {}", inName);
								NodeList inValElmntLst = inElmnt.getElementsByTagName("param-value");
								Element inValElmnt = (Element) inValElmntLst.item(0);
								NodeList inVal = inValElmnt.getChildNodes();
								String inValue = (inVal.item(0)).getNodeValue().trim();
								log.trace("Param value: {}", inValue);
								// add the param
								filter.addInitParam(inName, inValue);
							}
						}						
						// do async-supported
						NodeList ldElmntLst = fstElmnt.getElementsByTagName("async-supported");
						if (ldElmntLst != null) {
							Element ldElmnt = (Element) ldElmntLst.item(0);
							NodeList ldNm = ldElmnt.getChildNodes();
							String pAsync = (ldNm.item(0)).getNodeValue().trim();
							log.trace("Async supported: {}", pAsync);
							filter.setAsyncSupported(Boolean.valueOf(pAsync));
						}													
						// add to map
						filterMap.put(pName, filter);
					}
				}
				// do filter mappings
				if (!filterMap.isEmpty()) {				
					listOfElements = doc.getElementsByTagName("filter-mapping");
					totalElements = listOfElements.getLength();
					log.trace("Total no of filter-mappings: {}", totalElements);
					for (int s = 0; s < totalElements; s++) {
						Node fstNode = listOfElements.item(s);
						if (fstNode.getNodeType() == Node.ELEMENT_NODE) {
							Element fstElmnt = (Element) fstNode;
							NodeList fstNmElmntLst = fstElmnt.getElementsByTagName("filter-name");
							Element fstNmElmnt = (Element) fstNmElmntLst.item(0);
							NodeList fstNm = fstNmElmnt.getChildNodes();
							String pName = (fstNm.item(0)).getNodeValue().trim();
							log.trace("Param name: {}", pName);
							// lookup the filter info
							FilterInfo filter = filterMap.get(pName);
							// add the mapping
							if (filter != null) {
								NodeList lstNmElmntLst = fstElmnt.getElementsByTagName("url-pattern");
								Element lstNmElmnt = (Element) lstNmElmntLst.item(0);
								NodeList lstNm = lstNmElmnt.getChildNodes();
								String pValue = (lstNm.item(0)).getNodeValue().trim();
								log.trace("Param value: {}", pValue);
								// TODO email list and find out why this doesnt match servlet style
								//filter.addMapping(pValue);
							} else {
								log.warn("No servlet found for {}", pName);
							}
						}
					}
					// add filters
					info.addFilters(filterMap.values());				
				}
				// do servlet
				listOfElements = doc.getElementsByTagName("servlet");
				totalElements = listOfElements.getLength();
				log.trace("Total no of servlets: {}", totalElements);
				for (int s = 0; s < totalElements; s++) {
					Node fstNode = listOfElements.item(s);
					if (fstNode.getNodeType() == Node.ELEMENT_NODE) {
						Element fstElmnt = (Element) fstNode;
						NodeList fstNmElmntLst = fstElmnt.getElementsByTagName("servlet-name");
						Element fstNmElmnt = (Element) fstNmElmntLst.item(0);
						NodeList fstNm = fstNmElmnt.getChildNodes();
						String pName = (fstNm.item(0)).getNodeValue().trim();
						log.trace("Param name: {}", pName);
						NodeList lstNmElmntLst = fstElmnt.getElementsByTagName("servlet-class");
						Element lstNmElmnt = (Element) lstNmElmntLst.item(0);
						NodeList lstNm = lstNmElmnt.getChildNodes();
						String pValue = (lstNm.item(0)).getNodeValue().trim();
						log.trace("Param value: {}", pValue);
						// create the servlet
						ServletInfo servlet = new ServletInfo(pName, (Class<? extends Servlet>) info.getClassLoader().loadClass(pValue));
						// parse load on startup
						NodeList ldElmntLst = fstElmnt.getElementsByTagName("load-on-startup");
						if (ldElmntLst != null) {
							Element ldElmnt = (Element) ldElmntLst.item(0);
							NodeList ldNm = ldElmnt.getChildNodes();
							String pLoad = (ldNm.item(0)).getNodeValue().trim();
							log.trace("Load on startup: {}", pLoad);
							servlet.setLoadOnStartup(Integer.valueOf(pLoad));
						}						
						// do init-param - available in the context of a servlet or filter in the web application
						listOfElements = fstElmnt.getElementsByTagName("init-param");
						totalElements = listOfElements.getLength();
						log.trace("Total no of init-params: {}", totalElements);
						for (int i = 0; i < totalElements; i++) {
							Node inNode = listOfElements.item(i);
							if (inNode.getNodeType() == Node.ELEMENT_NODE) {
								Element inElmnt = (Element) inNode;
								NodeList inNmElmntLst = inElmnt.getElementsByTagName("param-name");
								Element inNmElmnt = (Element) inNmElmntLst.item(0);
								NodeList inNm = inNmElmnt.getChildNodes();
								String inName = (inNm.item(0)).getNodeValue().trim();
								log.trace("Param name: {}", inName);
								NodeList inValElmntLst = inElmnt.getElementsByTagName("param-value");
								Element inValElmnt = (Element) inValElmntLst.item(0);
								NodeList inVal = inValElmnt.getChildNodes();
								String inValue = (inVal.item(0)).getNodeValue().trim();
								log.trace("Param value: {}", inValue);
								// add the param
								servlet.addInitParam(inName, inValue);
							}
						}	
						// add to the map
						servletMap.put(servlet.getName(), servlet);						
					}
				}			
				// do servlet-mapping
				if (!servletMap.isEmpty()) {
					listOfElements = doc.getElementsByTagName("servlet-mapping");
					totalElements = listOfElements.getLength();
					log.trace("Total no of servlet-mappings: {}", totalElements);
					for (int s = 0; s < totalElements; s++) {
						Node fstNode = listOfElements.item(s);
						if (fstNode.getNodeType() == Node.ELEMENT_NODE) {
							Element fstElmnt = (Element) fstNode;
							NodeList fstNmElmntLst = fstElmnt.getElementsByTagName("servlet-name");
							Element fstNmElmnt = (Element) fstNmElmntLst.item(0);
							NodeList fstNm = fstNmElmnt.getChildNodes();
							String pName = (fstNm.item(0)).getNodeValue().trim();
							log.trace("Param name: {}", pName);
							// lookup the servlet info
							ServletInfo servlet = servletMap.get(pName);
							// add the mapping
							if (servlet != null) {
								NodeList lstNmElmntLst = fstElmnt.getElementsByTagName("url-pattern");
								Element lstNmElmnt = (Element) lstNmElmntLst.item(0);
								NodeList lstNm = lstNmElmnt.getChildNodes();
								String pValue = (lstNm.item(0)).getNodeValue().trim();
								log.trace("Param value: {}", pValue);
								servlet.addMapping(pValue);
							} else {
								log.warn("No servlet found for {}", pName);
							}
						}
					}
					// add servlets to deploy info
					info.addServlets(servletMap.values());
				}
				// do welcome files
				listOfElements = doc.getElementsByTagName("welcome-file-list");
				totalElements = listOfElements.getLength();
				log.trace("Total no of welcome-files: {}", totalElements);
				for (int s = 0; s < totalElements; s++) {
					Node fstNode = listOfElements.item(s);
					if (fstNode.getNodeType() == Node.ELEMENT_NODE) {
						Element fstElmnt = (Element) fstNode;
						NodeList fstNmElmntLst = fstElmnt.getElementsByTagName("welcome-file");
						Element fstNmElmnt = (Element) fstNmElmntLst.item(0);
						NodeList fstNm = fstNmElmnt.getChildNodes();
						String pName = (fstNm.item(0)).getNodeValue().trim();
						log.trace("Param name: {}", pName);
						// add welcome page
						info.addWelcomePage(pName);
					}
				}
				// do display name
				NodeList dNmElmntLst = doc.getElementsByTagName("display-name");
				if (dNmElmntLst.getLength() == 1) {
					Node dNmNode = dNmElmntLst.item(0);
					if (dNmNode.getNodeType() == Node.TEXT_NODE) {
						String dName = dNmNode.getNodeValue().trim();
						log.trace("Display name: {}", dName);				
						info.setDisplayName(dName);
					}
				}
				// TODO add security stuff
								
			} catch (Exception e) {
				log.warn("Error reading web.xml", e);
			}
		}
		webxml = null;		
	}
	
	/**
	 * Set connectors.
	 * 
	 * @param connectors 
	 */
	public void setConnectors(List<UndertowConnector> connectors) {
		log.debug("setConnectors: {}", connectors.size());
		this.connectors = connectors;
	}

	protected void registerJMX() {
		// register with jmx
		MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
		try {
			ObjectName oName = new ObjectName("org.red5.server:type=UndertowLoader");
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
			ObjectName oName = new ObjectName("org.red5.server:type=UndertowLoader");
			mbs.unregisterMBean(oName);
		} catch (Exception e) {
			log.warn("Exception unregistering", e);
		}
	}

	/**
	 * Shut server down.
	 */
	@Override
	public void destroy() throws Exception {
		log.info("Shutting down Undertow context");
		// run through the applications and ensure that spring is told to commence shutdown / disposal
		AbstractApplicationContext absCtx = (AbstractApplicationContext) LoaderBase.getApplicationContext();
		if (absCtx != null) {
			log.debug("Using loader base application context for shutdown");
			// get all the app (web) contexts and shut them down first
			Map<String, IApplicationContext> contexts = LoaderBase.getRed5ApplicationContexts();
			if (contexts.isEmpty()) {
				log.info("No contexts were found to shutdown");
			}
			for (Map.Entry<String, IApplicationContext> entry : contexts.entrySet()) {
				// stop the context
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
			// stop undertow
			server.stop();
		} catch (Exception e) {
			log.warn("Undertow could not be stopped", e);
			throw new RuntimeException("Undertow could not be stopped");
		}
	}

	@Override
	public String toString() {
		return "UndertowLoader [serviceEngineName=" + serviceEngineName + "]";
	}

}
