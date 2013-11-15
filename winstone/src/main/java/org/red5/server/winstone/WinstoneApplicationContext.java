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
import org.red5.server.api.IApplicationContext;
import org.slf4j.Logger;
import org.springframework.web.context.ConfigurableWebApplicationContext;
import org.springframework.web.context.WebApplicationContext;

import winstone.WebAppConfiguration;

/**
 * Class that wraps a Winstone webapp context.
 * 
 * @author The Red5 Project (red5@osflash.org)
 * @author Paul Gregoire (mondain@gmail.com)
 */
public class WinstoneApplicationContext implements IApplicationContext {

	protected static Logger log = Red5LoggerFactory.getLogger(WinstoneApplicationContext.class);

	/** Store a reference to the Winstone webapp */
	private WebAppConfiguration context;
	
	/**
	 * Wrap the passed Winstone webapp context.
	 * 
	 * @param context
	 */
	protected WinstoneApplicationContext(WebAppConfiguration context) {
		log.debug("new context: {}", context);
		this.context = context;
	}
	
	public void stop() {
		log.debug("stop");
		try {
			Object o = context.getAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE);
			if (o != null) {
				log.debug("Spring context for {} was found", context.getContextName());
				ConfigurableWebApplicationContext appCtx = (ConfigurableWebApplicationContext) o;
				//close the red5 app
				if (appCtx.isRunning()) {
					log.debug("Context was running, attempting to stop");
					appCtx.stop();
				}
				if (appCtx.isActive()) {
					log.debug("Context is active, attempting to close");
					appCtx.close();
				}
			} else {
				log.warn("Spring context for {} was not found", context.getContextName());
			}
		} catch (Exception e) {
			log.error("Could not stop spring context", e);
		}			
		context.destroy();
	}

}
