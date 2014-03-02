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

import javax.servlet.ServletContext;

import org.apache.catalina.Context;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.core.StandardContext;
import org.red5.logging.Red5LoggerFactory;
import org.red5.server.api.IApplicationContext;
import org.slf4j.Logger;
import org.springframework.web.context.ConfigurableWebApplicationContext;
import org.springframework.web.context.WebApplicationContext;

/**
 * Class that wraps a Tomcat webapp context.
 * 
 * @author The Red5 Project (red5@osflash.org)
 * @author Joachim Bauch (jojo@struktur.de)
 * @author Paul Gregoire
 */
public class TomcatApplicationContext implements IApplicationContext {

	protected static Logger log = Red5LoggerFactory.getLogger(TomcatApplicationContext.class);

	/** Store a reference to the Tomcat webapp context. */
	private Context context;
	
	/**
	 * Wrap the passed Tomcat webapp context.
	 * 
	 * @param context
	 */
	protected TomcatApplicationContext(Context context) {
		log.debug("new context: {}", context);
		this.context = context;
	}
	
	/**
	 * Stop the application and servlet contexts.
	 */
	public void stop() {
		log.debug("stop");
		try {
			ServletContext servlet = context.getServletContext();
			Object o = servlet.getAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE);
			if (o != null) {
				log.debug("Spring context for {} was found", context.getName());
				ConfigurableWebApplicationContext appCtx = (ConfigurableWebApplicationContext) o;
				// close the red5 app
				if (appCtx.isRunning()) {
					log.debug("Context was running, attempting to stop");
					appCtx.stop();
				}
				if (appCtx.isActive()) {
					log.debug("Context is active, attempting to close");
					appCtx.close();
				}
			} else {
				log.warn("Spring context for {} was not found", context.getName());
			}
		} catch (Exception e) {
			log.error("Could not stop spring context", e);
		}			
		context.getParent().removeChild(context);
		if (context instanceof StandardContext) {
			StandardContext ctx = (StandardContext) context;
			LifecycleState state = ctx.getState();
			if (state != LifecycleState.DESTROYED && state != LifecycleState.DESTROYING) {
    			try {
    				if (state != LifecycleState.STOPPED && state != LifecycleState.STOPPING) {
        				// stop the tomcat context
        				ctx.stop();
    				}
    			} catch (Exception e) {
    				log.error("Could not stop context", e);
    			} finally {
            		try {
            			ctx.destroy();
            		} catch (Exception e) {
            			log.error("Could not destroy context", e);
            		}
    			}
			}
		}
	}

}
