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

import org.apache.mina.core.buffer.IoBuffer;
import org.red5.server.net.rtsp.messages.RTSPMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Wraps up a RTSP response message.
 * 
 * @author Matteo Merli (matteo.merli@gmail.com)
 */
public class RTSPResponse extends RTSPMessage {

	private static Logger log = LoggerFactory.getLogger(RTSPResponse.class);

	RTSPCode code;

	RTSPRequest.Verb requestVerb = RTSPRequest.Verb.None;

	public RTSPResponse() {
		super();
		code = RTSPCode.OK;
	}

	public Type getType() {
		return Type.TypeResponse;
	}

	public RTSPCode getCode() {
		return code;
	}

	public void setCode(RTSPCode code) {
		this.code = code;
	}

	public void setRequestVerb(RTSPRequest.Verb requestVerb) {
		this.requestVerb = requestVerb;
	}

	public RTSPRequest.Verb getRequestVerb() {
		return requestVerb;
	}

	/**
	 * Serialize the RTSP response to a string.
	 * 
	 * <pre>
	 *    &quot;RTSP/1.0&quot; SP [code] SP [reason] CRLF
	 *    [headers] CRLF
	 *    CRLF
	 *    [buf] 
	 * </pre>
	 */
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("RTSP/1.0 ").append(code.value()).append(" ");
		sb.append(code.description()).append(CRLF);
		sb.append(getHeadersString());

		// Insert a blank line
		sb.append(CRLF);

		if (getBufferSize() > 0) {
			sb.append(getBuffer());

			log.debug("Buffer Size: " + getBufferSize());
		}

		return sb.toString();
	}

	/**
	 * serialize the RTSP response message into a byte buffer.
	 */
	public IoBuffer toByteBuffer() throws Exception {
		try {
			String msg = this.toString();
			IoBuffer buffer = IoBuffer.wrap(msg.getBytes("UTF-8"));

			return buffer;
		} catch (Exception e) {
			log.error("failed to serialize message to byte buffer", e);

			throw e;
		}
	}

	/**
	 * Construct a new RTSPResponse error message.
	 * 
	 * @param errorCode
	 *        the RTSP error code to be sent
	 * @return a RTSP response message
	 */
	public static RTSPResponse errorResponse(RTSPCode errorCode) {
		RTSPResponse response = new RTSPResponse();
		response.setCode(errorCode);
		return response;
	}

}
