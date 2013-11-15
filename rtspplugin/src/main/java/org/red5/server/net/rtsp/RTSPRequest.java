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

import java.net.URL;

import org.red5.server.net.rtsp.messages.RTSPMessage;

/**
 * @author Matteo Merli (matteo.merli@gmail.com)
 */
public class RTSPRequest extends RTSPMessage {

	public enum Verb {
		None, ANNOUNCE, DESCRIBE, GET_PARAMETER, OPTIONS, PAUSE, PLAY, RECORD, REDIRECT, SETUP, SET_PARAMETER, TEARDOWN
	};

	private Verb verb;

	private URL url;

	/**
	 * 
	 */
	public RTSPRequest() {
		super();
		verb = Verb.None;
	}

	public Type getType() {
		return Type.TypeRequest;
	}

	public String getVerbString() {
		return verb.toString();
	}

	public void setVerb(Verb verb) {
		this.verb = verb;
	}

	public Verb getVerb() {
		return verb;
	}

	/**
	 * Sets the verb of the request from a string.
	 * 
	 * @param strVerb
	 *        String containing the the verb
	 */
	public void setVerb(String strVerb) {
		try {
			this.verb = Verb.valueOf(strVerb);
		} catch (Exception e) {
			this.verb = Verb.None;
			// System.out.println( "Invalid verb: " + strVerb );
		}
	}

	public void setUrl(URL url) {
		this.url = url;
	}

	public URL getUrl() {
		return url;
	}

	/**
	 * Return a serialized version of the RTSP request message that will be sent
	 * over the network. The message is in the form:
	 * 
	 * <pre>
	 * [verb] SP [url] SP "RTSP/1.0" CRLF
	 * [headers] CRLF
	 * CRLF 
	 * [buffer]
	 * </pre>
	 */
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(getVerbString() + " ");
		sb.append(url != null ? url : "*");
		sb.append(" RTSP/1.0\r\n");
		sb.append(getHeadersString());

		// Insert a blank line
		sb.append(CRLF);

		if (getBufferSize() > 0) {
			sb.append(getBuffer());
		}

		return sb.toString();
	}

}
