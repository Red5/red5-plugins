package org.red5.server.net.rtsp.codec;

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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.CharBuffer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.ProtocolDecoder;
import org.apache.mina.filter.codec.ProtocolDecoderException;
import org.apache.mina.filter.codec.ProtocolDecoderOutput;
import org.red5.server.net.rtsp.RTSPCode;
import org.red5.server.net.rtsp.RTSPRequest;
import org.red5.server.net.rtsp.RTSPResponse;
import org.red5.server.net.rtsp.messages.RTSPMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Matteo Merli (matteo.merli@gmail.com)
 */
public class RTSPDecoder implements ProtocolDecoder {

	/**
	 * State enumerator that indicates the reached state in the RTSP message
	 * decoding process.
	 */
	public enum ReadState {
		/** Unrecoverable error occurred */
		Failed,
		/** Trying to resync */
		Sync,
		/** Waiting for a command */
		Ready,
		/** Reading interleaved packet */
		Packet,
		/** Reading command (request or command line) */
		Command,
		/** Reading headers */
		Header,
		/** Reading body (entity) */
		Body,
		/** Fully formed message */
		Dispatch
	}

	private static Logger log = LoggerFactory.getLogger(RTSPDecoder.class);

	private static final Pattern rtspRequestPattern = Pattern
			.compile("([A-Z_]+) ([^ ]+) RTSP/1.0");

	private static final Pattern rtspResponsePattern = Pattern
			.compile("RTSP/1.0 ([0-9]+) .+");

	private static final Pattern rtspHeaderPattern = Pattern
			.compile("([a-zA-Z\\-]+[0-9]?):\\s?(.*)");

	/**
	 * Do the parsing on the incoming stream. If the stream does not contain the
	 * entire RTSP message wait for other data to arrive, before dispatching the
	 * message.
	 * 
	 */
	public void decode(IoSession session, IoBuffer buffer,
			ProtocolDecoderOutput out) throws ProtocolDecoderException {
		BufferedReader reader = null;
		try {
			reader = new BufferedReader(new InputStreamReader(buffer
					.asInputStream(), "US-ASCII"));
		} catch (UnsupportedEncodingException e1) {
		}

		// Retrieve status from session
		ReadState state = (ReadState) session.getAttribute("state");
		if (state == null)
			state = ReadState.Command;
		RTSPMessage rtspMessage = (RTSPMessage) session
				.getAttribute("rtspMessage");

		try {

			while (true) {

				if (state != ReadState.Command && state != ReadState.Header)
					// the "while" loop is only used to read commands and
					// headers
					break;

				String line = reader.readLine();
				if (line == null)
					// there's no more data in the buffer
					break;

				if (line.length() == 0) {
					// This is the empty line that marks the end
					// of the headers section
					state = ReadState.Body;
					break;
				}

				switch (state) {

					case Command:
						// log.debug( "Command line: " + line );
						if (line.startsWith("RTSP")) {
							// this is a RTSP response
							Matcher m = rtspResponsePattern.matcher(line);
							if (!m.matches())
								throw new ProtocolDecoderException(
										"Malformed response line: " + line);

							RTSPCode code = RTSPCode.fromString(m.group(1));
							rtspMessage = new RTSPResponse();
							((RTSPResponse) (rtspMessage)).setCode(code);
							RTSPRequest.Verb verb = (RTSPRequest.Verb) session
									.getAttribute("lastRequestVerb");
							((RTSPResponse) (rtspMessage)).setRequestVerb(verb);

						} else {
							// this is a RTSP request
							Matcher m = rtspRequestPattern.matcher(line);
							if (!m.matches())
								throw new ProtocolDecoderException(
										"Malformed request line: " + line);

							String verb = m.group(1);
							String strUrl = m.group(2);
							URL url = null;
							if (!strUrl.equalsIgnoreCase("*")) {
								try {
									url = new URL(strUrl);
								} catch (MalformedURLException e) {
									log.error("", e);
									url = null;
									session.setAttribute("state",
											ReadState.Failed);
									throw new ProtocolDecoderException(
											"Invalid URL");
								}
							}
							rtspMessage = new RTSPRequest();
							((RTSPRequest) rtspMessage).setVerb(verb);

							if (((RTSPRequest) rtspMessage).getVerb() == RTSPRequest.Verb.None) {
								session.setAttribute("state", ReadState.Failed);
								throw new ProtocolDecoderException(
										"Invalid method: " + verb);
							}

							((RTSPRequest) rtspMessage).setUrl(url);
						}
						state = ReadState.Header;
						break;

					case Header:
						// this is an header
						Matcher m = rtspHeaderPattern.matcher(line);

						if (!m.matches())
							throw new ProtocolDecoderException(
									"RTSP header not valid");

						rtspMessage.setHeader(m.group(1), m.group(2));
						break;

				}
			}

			if (state == ReadState.Body) {
				// Read the message body
				int bufferLen = Integer.parseInt(rtspMessage.getHeader(
						"Content-Length", "0"));
				if (bufferLen == 0) {
					// there's no buffer to be read
					state = ReadState.Dispatch;

				} else {
					// we have a content buffer to read
					int bytesToRead = bufferLen - rtspMessage.getBufferSize();

					// if ( bytesToRead < reader. decodeBuf.length() ) {
					// log.warn( "We are reading more bytes than
					// Content-Length." );
					// }

					// read the content buffer
					CharBuffer bufferContent = CharBuffer.allocate(bytesToRead);
					reader.read(bufferContent);
					bufferContent.flip();
					rtspMessage.appendToBuffer(bufferContent);
					if (rtspMessage.getBufferSize() >= bufferLen) {
						// The RTSP message parsing is completed
						state = ReadState.Dispatch;
					}
				}
			}
		} catch (IOException e) {
			/*
			 * error on input stream should not happen since the input stream is
			 * coming from a bytebuffer.
			 */
			log.error("", e);
			return;

		} finally {
			try {
				reader.close();
			} catch (Exception e) {
			}
		}

		if (state == ReadState.Dispatch) {
			// The message is already formed
			// send it
			session.removeAttribute("state");
			session.removeAttribute("rtspMessage");
			out.write(rtspMessage);
			return;
		}

		// log.debug( "INCOMPLETE MESSAGE \n" + rtspMessage );

		// Save attributes in session
		session.setAttribute("state", state);
		session.setAttribute("rtspMessage", rtspMessage);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.apache.mina.filter.codec.ProtocolDecoder#dispose(org.apache.mina.common.IoSession)
	 */
	public void dispose(IoSession session) throws Exception {
		// Do nothing
	}

	/** {@inheritDoc} */
    public void finishDecode(IoSession session, ProtocolDecoderOutput out)
			throws Exception {
		// TODO Auto-generated method stub	
	}	
}
