package org.red5.server.net.rtsp.messages;

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

import java.nio.CharBuffer;
import java.util.Properties;

import org.red5.server.api.Red5;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base abstract class for RTSP messages.
 * 
 * @author Matteo Merli (matteo.merli@gmail.com)
 */
public abstract class RTSPMessage {

	/**
	 * RTSP Message Type
	 */
	public enum Type {
		/** Generic message (internal use) */
		TypeNone,
		/** Request message */
		TypeRequest,
		/** Response message */
		TypeResponse
	};

	private static Logger log = LoggerFactory.getLogger(RTSPMessage.class);

	// CRLF
	public static final String CRLF = "\r\n";
	
	private int sequenceNumber;

	private Properties headers;

	private StringBuffer buffer;

	private final static String serverSignature;

	static {
		serverSignature = Red5.getVersion().split("[$]")[0] + " ("
				+ System.getProperty("os.name") + " / "
				+ System.getProperty("os.version") + " / "
				+ System.getProperty("os.arch") + ")";
		log.debug("RTSP signature set to: {}", serverSignature);
	}

	/**
	 * Constructor.
	 */
	public RTSPMessage() {
		sequenceNumber = 0;
		headers = new Properties();
		buffer = new StringBuffer();
	}

	/**
	 * @return the RTSP type of the message
	 */
	public Type getType() {
		return Type.TypeNone;
	}

	/**
	 * Adds a new header to the RTSP message.
	 * 
	 * @param key
	 *            The name of the header
	 * @param value
	 *            Its value
	 */
	public void setHeader(String key, String value) {
		// Handle some bad formatted headers
		if (key.compareToIgnoreCase("content-length") == 0) {
			headers.setProperty("Content-Length", value);
		} else {
			headers.setProperty(key, value);
		}
	}

	/**
	 * @param key
	 *            Header name
	 * @return the value of the header
	 */
	public String getHeader(String key) {
		return headers.getProperty(key);
	}

	/**
	 * 
	 * @param key
	 *            Header name
	 * @param defaultValue
	 *            the default value
	 * @return the value of the header of <i>defaultValue</i> if header is not
	 *         found
	 */
	public String getHeader(String key, String defaultValue) {
		String value = getHeader(key);
		if (value == null)
			return defaultValue;
		else
			return value;
	}

	/**
	 * Remove an header from the message headers collection
	 * 
	 * @param key
	 *            the name of the header
	 */
	public void removeHeader(String key) {
		headers.remove(key);
	}

	/**
	 * Formats all the headers into a string ready to be sent in a RTSP message.
	 * 
	 * <pre>
	 * Header1: Value1
	 * Header2: value 2
	 * ... 
	 * </pre>
	 * 
	 * @return a string containing the serialized headers
	 */
	public String getHeadersString() {
		StringBuilder buf = new StringBuilder();
		for (Object key : headers.keySet()) {
			String value = headers.getProperty((String) key);
			buf.append(key + ": " + value + CRLF);
		}
		return buf.toString();
	}

	/**
	 * 
	 * @return the number of headers owned by the message
	 */
	public int getHeadersCount() {
		return headers.size();
	}

	/**
	 * Sets common headers like <code>Server</code> and <code>Via</code>.
	 */
	public void setCommonHeaders() {
		if (getHeader("Server") != null) {
			setHeader("Via", serverSignature);
		} else {
			setHeader("Server", serverSignature);
		}
	}

	/**
	 * 
	 * @param buffer
	 *            StringBuffer containing the contents
	 */
	public void setBuffer(StringBuffer buffer) {
		this.buffer = buffer;
	}

	/**
	 * @param other
	 *            buffer with content to be appended
	 */
	public void appendToBuffer(StringBuffer other) {
		this.buffer.append(other);
	}

	/**
	 * @param other
	 *            buffer with content to be appended
	 */
	public void appendToBuffer(CharBuffer other) {
		this.buffer.append(other);
	}

	/**
	 * @return the content buffer
	 */
	public StringBuffer getBuffer() {
		return buffer;
	}

	/**
	 * @return the size of the content buffer
	 */
	public int getBufferSize() {
		return buffer.length();
	}

	/**
	 * @return Returns the sequenceNumber.
	 */
	public int getSequenceNumber() {
		return sequenceNumber;
	}

	/**
	 * @param sequenceNumber
	 *            The sequenceNumber to set.
	 */
	public void setSequenceNumber(int sequenceNumber) {
		this.sequenceNumber = sequenceNumber;
	}
}
