package org.red5.server.net.rtsp;

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

import java.util.ArrayList;
import java.util.List;

/**
 * Represent a list of transport headers.
 * 
 * @author Matteo Merli (matteo.merli@gmail.com)
 */
public class RTSPTransportList {

	private List<RTSPTransport> transportList;

	/**
	 * Constructor. Creates a list of transport type.
	 */
	public RTSPTransportList(String transportHeader) {
		transportList = new ArrayList<RTSPTransport>();

		for (String transport : transportHeader.split(",")) {
			transportList.add(new RTSPTransport(transport));
		}
	}

	public List<RTSPTransport> getList() {
		return transportList;
	}

	public RTSPTransport get(int index) {
		return transportList.get(index);
	}

	/**
	 * @return The number of transports defined.
	 */
	public int count() {
		return transportList.size();
	}

	public String toString() {
		StringBuilder buf = new StringBuilder();
		int i = 0;
		for (RTSPTransport t : transportList) {
			if (i++ != 0)
				buf.append(",");
			buf.append(t.toString());
		}
		return buf.toString();
	}

}
