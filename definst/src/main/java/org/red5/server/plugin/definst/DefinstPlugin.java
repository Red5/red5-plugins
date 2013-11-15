/*
 * RED5 Open Source Flash Server - http://code.google.com/p/red5/
 * 
 * Copyright 2006-2012 by respective authors (see below). All rights reserved.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.red5.server.plugin.definst;


import org.red5.logging.Red5LoggerFactory;
import org.red5.server.ClientRegistry;
import org.red5.server.Context;
import org.red5.server.MappingStrategy;
import org.red5.server.api.scope.IScope;
import org.red5.server.plugin.Red5Plugin;
import org.red5.server.scope.GlobalScope;
import org.red5.server.scope.Scope;
import org.red5.server.scope.ScopeResolver;
import org.red5.server.service.ServiceInvoker;
import org.slf4j.Logger;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.FileSystemXmlApplicationContext;

/**
 * Default instance for Red5
 * 
 * @author Paul Gregoire
 */
public class DefinstPlugin extends Red5Plugin {

	private static Logger log = Red5LoggerFactory.getLogger(DefinstPlugin.class);

	private DefinstHandler handler = null;
	
	private ApplicationContext parentContext;
	
	private ApplicationContext appContext;

	private String appName = "definst";
	
	private String hostName = "localhost";

	@Override
	public void doStart() throws Exception {
		super.doStart();
			
		// add the context to the parent, this will be red5.xml
		ConfigurableBeanFactory factory = ((ConfigurableApplicationContext) context).getBeanFactory();
		// if parent context was not set then lookup red5.common
		log.debug("Lookup common - bean:{} local:{} singleton:{}", new Object[] { factory.containsBean("red5.common"), factory.containsLocalBean("red5.common"), factory.containsSingleton("red5.common"), });
		parentContext = (ApplicationContext) factory.getBean("red5.common");
		
		//create app context
		appContext = new FileSystemXmlApplicationContext(new String[]{"classpath:/definst.xml"}, true, parentContext);	

		//get a ref to the "default" global scope
		GlobalScope global = (GlobalScope) server.getGlobal("default");
		
		//create a scope resolver
		ScopeResolver scopeResolver = new ScopeResolver();
		scopeResolver.setGlobalScope(global);
				
		//create a context - this takes the place of the previous web context
		Context ctx = new Context(appContext, appName);	
		ctx.setClientRegistry(new ClientRegistry());
		ctx.setMappingStrategy(new MappingStrategy());
		ctx.setPersistanceStore(global.getStore());
		ctx.setScopeResolver(scopeResolver);
		ctx.setServiceInvoker(new ServiceInvoker());
		
		//create a handler
		handler = new DefinstHandler();
		
		//create a scope for the admin
//		Scope scope = new Scope.Builder((IScope) global, "scope", appName, false).build();
//		scope.setContext(ctx);
//		scope.setHandler(handler);
				
		server.addMapping(hostName, appName, "default");
		
//		if (global.addChildScope(scope)) {
//			log.info("Scope was added to global (default) scope");
//		} else {
//			log.warn("Scope was not added to global (default) scope");
//		}
//		
//		//start the scope
//		scope.start();

	}

	@Override
	public void doStop() throws Exception {
		super.doStop();
		//clean up / unregister everything
		server.removeMapping(hostName, appName);
//		handler.stop(null);
	}

	@Override
	public String getName() {
		return appName;
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
