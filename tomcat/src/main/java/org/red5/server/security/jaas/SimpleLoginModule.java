package org.red5.server.security.jaas;

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

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.security.auth.Subject;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.auth.login.FailedLoginException;
import javax.security.auth.login.LoginException;
import javax.security.auth.spi.LoginModule;

import org.red5.logging.Red5LoggerFactory;
import org.slf4j.Logger;

/**
 * This LoginModule authenticates a user with a password.
 * This module comes pre-loaded with one user / password pair and the ability for implementers
 * to add additional pairs. The pre-loaded user is "red5" and their password is "password".
 * 
 * @author Paul Gregoire (mondain@gmail.com)
 */
public class SimpleLoginModule implements LoginModule {

	private static Logger log = Red5LoggerFactory.getLogger(SimpleLoginModule.class);
	
	// initial state
	private Subject subject;

	private CallbackHandler callbackHandler;

	private Map<String, SimplePrincipal> principals = new HashMap<String, SimplePrincipal>();
	
	// the authentication status
	private boolean succeeded = false;

	private boolean commitSucceeded = false;

	private ThreadLocal<SimplePrincipal> userPrincipal = new ThreadLocal<SimplePrincipal>();

	{
		// add our default user
		SimplePrincipal prince = new SimplePrincipal("red5", "password");
		principals.put("red5", prince);
	}
	
	/** {@inheritDoc} */
	public void initialize(Subject subject, CallbackHandler callbackHandler, Map<String, ?> sharedState, Map<String, ?> options) {
		log.debug("initialize - subject: {}", subject);
		this.subject = subject;
		this.callbackHandler = callbackHandler;
	}

	/** {@inheritDoc} */
	public boolean login() throws LoginException {
		// prompt for a user name and password
		if (callbackHandler == null) {
			throw new LoginException("Error: no CallbackHandler available, required to hold authentication information from the user");
		}
		Callback[] callbacks = new Callback[2];
		callbacks[0] = new NameCallback("User name: ");
		callbacks[1] = new PasswordCallback("Password: ", false);
		String userName;
		char[] passwd;
		try {
			callbackHandler.handle(callbacks);
			userName = ((NameCallback) callbacks[0]).getName();
			char[] tmpPassword = ((PasswordCallback) callbacks[1]).getPassword();
			if (tmpPassword == null) {
				// treat a NULL password as an empty password
				tmpPassword = new char[0];
			}
			passwd = new char[tmpPassword.length];
			System.arraycopy(tmpPassword, 0, passwd, 0, tmpPassword.length);
			((PasswordCallback) callbacks[1]).clearPassword();
		} catch (IOException ioe) {
			throw new LoginException(ioe.toString());
		} catch (UnsupportedCallbackException uce) {
			throw new LoginException("Error: " + uce.getCallback().toString() + " not available to hold authentication information");
		}
		String password = new String(passwd);
		log.debug("User name: {} password: {}", userName, password);
		// verify the username/password
		SimplePrincipal prince = new SimplePrincipal(userName, password);
		// look for a matching user
		SimplePrincipal tmp = principals.get(userName);
		// checks user name match
		if (tmp != null && tmp.equals(prince)) {
			// check passwords
			if (tmp.getPassword().equals(prince.getPassword())) {
				userPrincipal.set(prince);
				log.debug("Authentication succeeded");
				succeeded = true;
				return true;
			}
		}
		succeeded = false;
		throw new FailedLoginException("Authentication failed");
	}

	/** {@inheritDoc} */
	public boolean commit() throws LoginException {
		if (!succeeded) {
			return false;
		} else {
			SimplePrincipal prince = userPrincipal.get();
			if (!subject.getPrincipals().contains(prince)) {
				subject.getPrincipals().add(prince);
			}
			log.debug("Added principal to the subject");
			commitSucceeded = true;
			return true;
		}
	}

	/** {@inheritDoc} */
	public boolean abort() throws LoginException {
		if (!succeeded) {
			return false;
		} else if (succeeded && !commitSucceeded) {
			// login succeeded but overall authentication failed
			succeeded = false;
			userPrincipal.remove();
		} else {
			// overall authentication succeeded and commit succeeded,
			// but someone else's commit failed
			logout();
		}
		return true;
	}

	/** {@inheritDoc} */
	public boolean logout() throws LoginException {
		SimplePrincipal prince = userPrincipal.get();
		subject.getPrincipals().remove(prince);
		userPrincipal.remove();
		succeeded = false;
		succeeded = commitSucceeded;
		return true;
	}
	
	/**
	 * Adds a new Principal, given a user name and password.
	 * 
	 * @param userName
	 * @param password
	 */
	public void add(String userName, String password) {
		SimplePrincipal prince = new SimplePrincipal(userName, password);
		principals.put(userName, prince);
	}
	
}
