package org.red5.server.net.rtsp.filter.rewrite;

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

import java.net.MalformedURLException;
import java.net.URL;

import org.apache.mina.core.filterchain.IoFilterAdapter;
import org.apache.mina.core.session.IoSession;
import org.red5.server.net.rtsp.RTSPRequest;
import org.red5.server.net.rtsp.RTSPResponse;
import org.red5.server.net.rtsp.messages.RTSPMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author bieniekr
 */
public class RequestUrlRewritingImpl extends IoFilterAdapter {
	/**
	 * Logger for this class
	 */
	private static final Logger logger = LoggerFactory.getLogger(RequestUrlRewritingImpl.class);

	// the filter instance
	private RequestUrlRewritingFilter filter;

	/**
	 * construct the IoFilter around the filter class denoted by the clazz name
	 * parameter.
	 * 
	 * TODO: This may become obsolete if moving to OSGi bundles TODO: Make
	 * filter parametrizeable. Could be done by moving from properties to XML
	 * config file.
	 */
	public RequestUrlRewritingImpl(String clazzName) throws Exception {

		try {
			Class<?> filterClazz = Class.forName(clazzName);

			this.filter = (RequestUrlRewritingFilter) filterClazz.newInstance();
			logger.info("using request URL rewriter " + clazzName);
		} catch (Exception e) {
			logger.error("", e);
			throw e;
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.apache.mina.common.IoFilterAdapter#messageReceived(org.apache.mina.common.IoFilter.NextFilter,
	 *      org.apache.mina.common.IoSession, java.lang.Object)
	 */
	@Override
	public void messageReceived(NextFilter nextFilter, IoSession session,
			Object message) throws Exception {
		RTSPMessage rtspMessage = (RTSPMessage) message;

		logger.debug("Received (pre-rewriting) message:\n" + message);
		if (rtspMessage.getType() == RTSPMessage.Type.TypeRequest) {
			RTSPRequest request = (RTSPRequest) rtspMessage;
			URL rewritten = this.filter.rewriteRequestUrl(request.getUrl());

			if (rewritten != null) {
				logger.debug("changed request URL from '" + request.getUrl()
						+ "' to '" + rewritten + "'");

				request.setUrl(rewritten);
			}
		} else if (rtspMessage.getType() == RTSPMessage.Type.TypeResponse) {
			RTSPResponse resp = (RTSPResponse) rtspMessage;

			switch (resp.getRequestVerb()) {
				case DESCRIBE:
					rewriteUrlHeader("Content-base", resp);
					break;
				case PLAY:
					// rewriteUrlHeader("RTP-Info", resp);
					break;
			}
		}
		logger.debug("Sent (post-rewriting) message:\n" + message);

		nextFilter.messageReceived(session, message);
	}

	/**
	 * rewrite a header
	 */
	private void rewriteUrlHeader(String headerName, RTSPResponse resp) {
		String oldHeader = resp.getHeader(headerName);

		if (oldHeader != null) {
			logger.debug("old content " + headerName + " header value: "
					+ oldHeader);

			try {
				URL header = this.filter.rewriteResponseHeaderUrl(new URL(
						oldHeader));

				if (header != null) {
					logger.debug("changed header " + headerName + " to "
							+ header);

					resp.setHeader(headerName, header.toString());
				}
			} catch (MalformedURLException mue) {
				logger.error("failed to parse " + headerName + " header", mue);
			}
		}

	}
}
