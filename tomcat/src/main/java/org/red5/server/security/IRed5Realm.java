package org.red5.server.security;

import javax.servlet.ServletContext;
import org.springframework.context.ApplicationContext;

/**
 * Red5 security realm.
 * 
 * @author Paul Gregoire
 */
public interface IRed5Realm {

	/**
	 * Sets a servlet context.
	 * 
	 * @param servletContext
	 */
	void setServletContext(ServletContext servletContext);

	/**
	 * Returns the servlet context.
	 * 
	 * @return servlet context
	 */
	ServletContext getServletContext();

	/**
	 * Sets an application context.
	 * 
	 * @param applicationContext
	 */
	void setApplicationContext(ApplicationContext applicationContext);

	/**
	 * Returns the application context.
	 * 
	 * @return application context
	 */
	ApplicationContext getApplicationContext();
	
}
