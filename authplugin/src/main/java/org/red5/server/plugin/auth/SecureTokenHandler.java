package org.red5.server.plugin.auth;

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
import org.red5.server.adapter.ApplicationLifecycle;
import org.red5.server.api.IConnection;
import org.red5.server.net.rtmp.status.StatusCodes;
import org.red5.server.net.rtmp.status.StatusObject;
import org.slf4j.Logger;

/**
 * Provides secure token capabilities using an application listener.
 * 
 * @author Paul Gregoire
 * @author Dan Rossi
 */
public class SecureTokenHandler extends ApplicationLifecycle {

	private static Logger log = Red5LoggerFactory.getLogger(SecureTokenHandler.class, "plugins");

	@Override
	public boolean appConnect(IConnection conn, Object[] params) {
		StatusObject status = new StatusObject(StatusCodes.NC_CONNECT_SUCCESS, StatusObject.STATUS, "Connection succeeded.");
		status.setAdditional("secureToken", "testing secure token status property from RED5 !!!");
		
		//send the status object
		log.debug("Status: {}", status);
		AuthPlugin.writeStatus(conn, status);
		
		return true;
	}

}
