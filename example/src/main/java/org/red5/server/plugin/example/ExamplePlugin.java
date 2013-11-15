package org.red5.server.plugin.example;

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
import org.red5.server.Server;
import org.red5.server.plugin.Red5Plugin;
import org.slf4j.Logger;
import org.springframework.context.ApplicationContext;

/**
 * Simple plug-in to test functionality.
 * 
 * @author Paul Gregoire
 */
public class ExamplePlugin extends Red5Plugin {

	private static Logger log = Red5LoggerFactory.getLogger(ExamplePlugin.class, "plugins");
	
	public void doStart() throws Exception {
		log.debug("Starttttttttt");
		super.doStart();
	}

	public void doStop() throws Exception {
		log.debug("Stop");
		super.doStop();
	}

	public void setApplicationContext(ApplicationContext context) {
		log.debug("Set application context: {}", context);
		super.setApplicationContext(context);
	}

	public void setServer(Server server) {
		log.debug("Set server: {}", server);
		super.setServer(server);
	}

	public String getName() {
		return "examplePlugin";
	}
		
}