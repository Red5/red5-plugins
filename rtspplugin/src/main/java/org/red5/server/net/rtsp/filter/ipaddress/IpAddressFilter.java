package org.red5.server.net.rtsp.filter.ipaddress;

/*
 * RED5 Open Source Flash Server - http://www.osflash.org/red5
 * 
 * Copyright (c) 2006-2008 by respective authors (see below). All rights reserved.
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

/***************************************************************************
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *                                                                         *
 *   Copyright (C) 2005 - Matteo Merli - matteo.merli@gmail.com            *
 *                                                                         *
 ***************************************************************************/

import java.net.InetSocketAddress;

import org.apache.mina.core.filterchain.IoFilterAdapter;
import org.apache.mina.core.session.IoSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Matteo Merli (matteo.merli@gmail.com)
 */
public class IpAddressFilter extends IoFilterAdapter {

	private static Logger log = LoggerFactory.getLogger(IpAddressFilter.class);

	private IpAddressProvider provider;

	private String filterClassName = "org.red5.server.net.rtsp.filter.ipaddress.PlainTextIpAddressProvider";

	public void init() {

		Class<?> providerClass;
		try {
			providerClass = Class.forName(filterClassName);
		} catch (ClassNotFoundException e) {
			log.error("Invalid IpAddressProvider class: {}", filterClassName);
			return;
		}

		// Check if the class implements the IpAddressProvider interfaces
		boolean found = false;
		for (Class<?> interFace : providerClass.getInterfaces()) {
			if (IpAddressProvider.class.equals(interFace)) {
				found = true;
				break;
			}
		}

		if (!found) {
			log.error("Class ({}) does not implement the IpAddressProvider interface.",	provider);
			return;
		}

		try {
			provider = (IpAddressProvider) providerClass.newInstance();
			provider.init();
		} catch (Exception e) {
			log.error("Error starting IpAddressProvider", e);
			return;
		}

		log.info("Using IpAddressFilter ({})", filterClassName);
	}

	@Override
	public void messageReceived(NextFilter nextFilter, IoSession session,
			Object message) throws Exception {
		if (!provider
				.isBlocked(((InetSocketAddress) session.getRemoteAddress())
						.getAddress())) {
			// forward if not blocked
			nextFilter.messageReceived(session, message);
		} else {
			blockSession(session);
		}
	}

	@Override
	public void sessionCreated(NextFilter nextFilter, IoSession session)
			throws Exception {
		if (!provider
				.isBlocked(((InetSocketAddress) session.getRemoteAddress())
						.getAddress())) {
			// forward if not blocked
			nextFilter.sessionCreated(session);
		} else {
			blockSession(session);
		}
	}

	protected void blockSession(IoSession session) {
		log.info("Blocked connection from : " + session.getRemoteAddress());
		session.close(true);
	}

	public String getFilterClassName() {
		return filterClassName;
	}

	public void setFilterClassName(String filterClassName) {
		this.filterClassName = filterClassName;
	}

}
