package org.red5.server.undertow;

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

import io.undertow.servlet.api.ServletContainer;

import org.red5.logging.Red5LoggerFactory;
import org.red5.server.api.IApplicationLoader;
import org.slf4j.Logger;
import org.springframework.context.ApplicationContext;

/**
 * Class that can load new applications in Undertow.
 * 
 * @author The Red5 Project 
 * @author Paul Gregoire (mondain@gmail.com)
 */
public class UndertowApplicationLoader implements IApplicationLoader {

	// Initialize Logging
	protected static Logger log = Red5LoggerFactory.getLogger(UndertowApplicationLoader.class);	
	
	/** Stores reference to the root ApplicationContext. */
	private ApplicationContext rootCtx;
	
	@SuppressWarnings("unused")
	private ServletContainer container;
	
	@SuppressWarnings("unused")
	private String defaultEncoding = "UTF-8";

	/**
	 * Wrap Undertow container and root context.
	 * 
	 * @param container
	 * @param rootCtx
	 */
	protected UndertowApplicationLoader(ServletContainer container, ApplicationContext rootCtx) {
		this.container = container;
		this.rootCtx = rootCtx;
	}
	
	/**
	 * Wrap Undertow container and root context.
	 * 
	 * @param container
	 * @param rootCtx
	 * @param defaultEncoding
	 */
	protected UndertowApplicationLoader(ServletContainer container, ApplicationContext rootCtx, String defaultEncoding) {
		this.container = container;
		this.rootCtx = rootCtx;
		this.defaultEncoding = defaultEncoding;
	}

	/** {@inheritDoc} */
	public ApplicationContext getRootContext() {
		log.debug("getRootContext");
		return rootCtx;
	}

	/** {@inheritDoc} */
	public void loadApplication(String contextPath, String virtualHosts, String directory) throws Exception {
		log.debug("Load application - context path: {} directory: {} virtual hosts: {}", new Object[]{contextPath, directory, virtualHosts});
	}

}
