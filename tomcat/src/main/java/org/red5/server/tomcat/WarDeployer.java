/*
 * RED5 Open Source Flash Server - https://github.com/Red5/
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

package org.red5.server.tomcat;

import java.io.File;
import java.io.FilenameFilter;
import java.lang.management.ManagementFactory;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.management.JMX;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.servlet.ServletException;

import org.red5.server.api.scheduling.IScheduledJob;
import org.red5.server.api.scheduling.ISchedulingService;
import org.red5.server.jmx.mxbeans.LoaderMXBean;
import org.red5.server.util.FileUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This service provides the means to auto-deploy a war.
 * 
 * @author Paul Gregoire (mondain@gmail.com)
 */
public final class WarDeployer {

	private Logger log = LoggerFactory.getLogger(WarDeployer.class);

	private ISchedulingService scheduler;

	/**
	 * How often to check for new war files
	 */
	private int checkInterval = 600000; //ten minutes

	/**
	 * Deployment directory
	 */
	private String webappFolder;

	/**
	 * Expand WAR files in the webapps directory prior to start up
	 */
	protected boolean expandWars;

	private static String jobName;

	//that wars are currently being installed
	private static AtomicBoolean deploying = new AtomicBoolean(false);

	{
		log.info("War deployer service created");
	}

	public void init() {
		// create the job and schedule it
		jobName = scheduler.addScheduledJobAfterDelay(checkInterval, new DeployJob(), 60000);
		// check the deploy from directory
		log.debug("Webapps directory: {}", webappFolder);
		File dir = new File(webappFolder);
		if (!dir.exists()) {
			log.warn("Source directory not found");
		} else {
			if (!dir.isDirectory()) {
				log.warn("Source directory is not a directory");
			}
		}
		dir = null;
		// expand wars if so requested
		if (expandWars) {
			log.debug("Deploying wars");
			deploy(false);
		}
	}

	private void deploy(boolean startApplication) {
		if (deploying.compareAndSet(false, true)) {
			// short name
			String application = null;
			// file name
			String applicationWarName = null;
			// get webapp location
			String webappsDir = System.getProperty("red5.webapp.root");
			log.debug("Webapp folder: {}", webappsDir);
			// look for web application archives
			File dir = new File(webappFolder);
			// get a list of wars
			File[] files = dir.listFiles(new DirectoryFilter());
			for (File f : files) {
				// get the war name
				applicationWarName = f.getName();

				int dashIndex = applicationWarName.indexOf('-');
				if (dashIndex != -1) {
					// strip everything except the applications name
					application = applicationWarName.substring(0, dashIndex);
				} else {
					// grab every char up to the last '.'
					application = applicationWarName.substring(0, applicationWarName.lastIndexOf('.'));
				}
				log.debug("Application name: {}", application);
				// setup context
				String contextPath = '/' + application;
				String contextDir = webappsDir + contextPath;
				log.debug("Web context: {} context directory: {}", contextPath, contextDir);
				// verify this is a unique app
				File appDir = new File(webappsDir, application);
				if (appDir.exists()) {
					if (appDir.isDirectory()) {
						log.debug("Application directory exists");
					} else {
						log.warn("Application destination is not a directory");
					}
					log.info("Application {} already installed, please un-install before attempting another install", application);
				} else {
					log.debug("Unwaring and starting...");
					// un-archive it to app dir
					FileUtil.unzip(webappFolder + '/' + applicationWarName, contextDir);
					// get the webapp loader
					LoaderMXBean loader = getLoader();
					if (loader != null) {
						// load and start the context
						try {
							if (startApplication) {
								loader.startWebApplication(application);
							}
						} catch (ServletException e) {
							log.error("Unexpected error while staring web application", e);
						}
					}
					// remove the war file
					File warFile = new File(webappFolder, applicationWarName);
					if (warFile.delete()) {
						log.debug("{} was deleted", warFile.getName());
					} else {
						log.debug("{} was not deleted", warFile.getName());
						warFile.deleteOnExit();
					}
					warFile = null;
				}
				appDir = null;
			}
			dir = null;
			// reset sentinel
			deploying.set(false);
		}
	}

	public void shutdown() {
		scheduler.removeScheduledJob(jobName);
	}

	public void setCheckInterval(int checkInterval) {
		this.checkInterval = checkInterval;
	}

	public int getCheckInterval() {
		return checkInterval;
	}

	public ISchedulingService getScheduler() {
		return scheduler;
	}

	public void setScheduler(ISchedulingService scheduler) {
		this.scheduler = scheduler;
	}

	public String getWebappFolder() {
		return webappFolder;
	}

	public void setWebappFolder(String webappFolder) {
		this.webappFolder = webappFolder;
	}

	/**
	 * Whether or not to expand war files prior to start up.
	 * 
	 * @param expandWars
	 *            to expand or not
	 */
	public void setExpandWars(boolean expandWars) {
		this.expandWars = expandWars;
	}

	/**
	 * Returns the LoaderMBean.
	 * 
	 * @return LoadeerMBean
	 */
	@SuppressWarnings("cast")
	public LoaderMXBean getLoader() {
		MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
		// proxy class
		LoaderMXBean proxy = null;
		ObjectName oName;
		try {
			// TODO support all loaders
			oName = new ObjectName("org.red5.server:type=TomcatLoader");
			if (mbs.isRegistered(oName)) {
				proxy = JMX.newMXBeanProxy(mbs, oName, LoaderMXBean.class, true);
				log.debug("Loader was found");
			} else {
				log.warn("Loader not found");
			}
		} catch (Exception e) {
			log.error("Exception getting loader", e);
		}
		return proxy;
	}

	/**
	 * Filters directory content
	 */
	protected class DirectoryFilter implements FilenameFilter {
		/**
		 * Check whether file matches filter rules
		 * 
		 * @param dir
		 *            Directory
		 * @param name
		 *            File name
		 * @return true If file does match filter rules, false otherwise
		 */
		public boolean accept(File dir, String name) {
			File f = new File(dir, name);
			log.trace("Filtering: {} name: {}", dir.getName(), name);
			// filter out all but war files
			boolean result = f.getName().endsWith("war");
			// nullify
			f = null;
			return result;
		}
	}

	private class DeployJob implements IScheduledJob {

		public void execute(ISchedulingService service) {
			log.debug("Starting scheduled deployment of wars");
			deploy(true);
		}

	}

}
