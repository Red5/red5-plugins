package org.red5.server.plugin.oflaDemo;

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
import org.red5.server.ClientRegistry;
import org.red5.server.Context;
import org.red5.server.MappingStrategy;
import org.red5.server.api.scope.IScope;
import org.red5.server.api.scope.ScopeType;
import org.red5.server.plugin.Red5Plugin;
import org.red5.server.scope.GlobalScope;
import org.red5.server.scope.Scope;
import org.red5.server.scope.ScopeResolver;
import org.red5.server.service.ServiceInvoker;
import org.slf4j.Logger;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.FileSystemXmlApplicationContext;

/**
 * OflaDemo for Red5
 * 
 * @author Paul Gregoire
 */
public class OflaDemoPlugin extends Red5Plugin {

	private static Logger log = Red5LoggerFactory.getLogger(OflaDemoPlugin.class);

	private OflaDemoHandler handler = null;
	
	private ApplicationContext oflaDemoContext;

	private String hostName = "localhost";

	@Override
	public void doStart() throws Exception {
		super.doStart();
		
		//create a handler
		handler = new OflaDemoHandler();
		
		ApplicationContext commonCtx = (ApplicationContext) context.getBean("red5.common");
		
		if (context.containsBean("playlistSubscriberStream")) {
			//create app context
			oflaDemoContext = new FileSystemXmlApplicationContext(new String[]{"classpath:/oflaDemo.xml"}, true, context);	
		} else if (commonCtx.containsBean("playlistSubscriberStream")) {
			//create app context
			oflaDemoContext = new FileSystemXmlApplicationContext(new String[]{"classpath:/oflaDemo.xml"}, true, commonCtx);	
		} else {
			log.error("Playlist subscriber stream bean could not be located");
		}

		//set the context
		handler.setContext(oflaDemoContext);

		//get a ref to the "default" global scope
		GlobalScope global = (GlobalScope) server.getGlobal("default");
		
		//create a scope resolver
		ScopeResolver scopeResolver = new ScopeResolver();
		scopeResolver.setGlobalScope(global);
				
		//create a context - this takes the place of the previous web context
		Context ctx = new Context(oflaDemoContext, "oflaDemo");	
		ctx.setClientRegistry(new ClientRegistry());
		ctx.setMappingStrategy(new MappingStrategy());
		ctx.setPersistanceStore(global.getStore());
		ctx.setScopeResolver(scopeResolver);
		ctx.setServiceInvoker(new ServiceInvoker());
		
		//create a scope for the admin
		Scope scope = new Scope.Builder((IScope) global, ScopeType.APPLICATION, "oflaDemo", false).build();
		scope.setContext(ctx);
		scope.setHandler(handler);
		
		//set the scope on the handler
		handler.setScope(scope);
		
		server.addMapping(hostName, "oflaDemo", "default");
		
		if (global.addChildScope(scope)) {
			log.info("oflaDemo scope was added to global (default) scope");
			
		} else {
			log.warn("oflaDemo scope was not added to global (default) scope");
		}
	}

	@Override
	public void doStop() throws Exception {
		super.doStop();
		//clean up / unregister everything
		server.removeMapping(hostName, "oflaDemo");
		handler.stop(null);
	}

	@Override
	public String getName() {
		return "oflaDemoPlugin";
	}

	@Override
	public void init() {
		log.debug("Initializing");
		super.init();
	}

	public String getHostName() {
		return hostName;
	}

	public void setHostName(String hostName) {
		this.hostName = hostName;
	}

}
