package org.red5.server.icy;

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

/**
 * While the nsv consumer is running you can retrieve its current mode which will 
 * indicate content type. When creating an nsv consumer, you only need to specify
 * client or server mode. It will determine the proper content type from the actual
 * shoutcast headers.
 *  
 * @author Andy Shaules (bowljoman@hotmail.com)
 */
public class ServerTypes {

	/**
	 * Acting like a shoutcast server for nsv tv encoder.
	 */
	public static int MODE_NSV_SERVER = 0;

	/**
	 * Acting like a shoutcast client for nsv tv.
	 */
	public static int MODE_NSV_CLIENT = 1;

	/**
	 * Acting like a shoutcast client.
	 */
	public static int MODE_SHOUT_CAST_CLIENT = 2;

	/**
	 * Acting like a shoutcast server for the encoder.
	 */
	public static int MODE_SHOUT_CAST_SERVER = 3;
	
}
