package org.red5.server.net.rtsp.filter;

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

import org.apache.mina.core.filterchain.IoFilter;
import org.apache.mina.core.filterchain.IoFilterChain;
import org.apache.mina.core.filterchain.IoFilterChainBuilder;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.ProtocolCodecFactory;
import org.apache.mina.filter.codec.ProtocolCodecFilter;
import org.apache.mina.filter.codec.ProtocolDecoder;
import org.apache.mina.filter.codec.ProtocolEncoder;
import org.red5.server.net.rtsp.codec.RTSPDecoder;
import org.red5.server.net.rtsp.codec.RTSPEncoder;
import org.red5.server.net.rtsp.filter.ipaddress.IpAddressFilter;
import org.red5.server.net.rtsp.filter.rewrite.RequestUrlRewritingImpl;

/**
 * Base class for filter chains based on configuration settings.
 * 
 * @author Matteo Merli (matteo.merli@gmail.com)
 */
public abstract class RTSPFilters implements IoFilterChainBuilder {

	private static ProtocolCodecFactory codecFactory = new ProtocolCodecFactory() {

		// Decoders can be shared
		private ProtocolEncoder rtspEncoder = new RTSPEncoder();

		private ProtocolDecoder rtspDecoder = new RTSPDecoder();

		public ProtocolEncoder getEncoder(IoSession session) {
			return rtspEncoder;
		}

		public ProtocolDecoder getDecoder(IoSession session) {
			return rtspDecoder;
		}
	};

	private static IoFilter codecFilter = new ProtocolCodecFilter(codecFactory);

	// These filters are instanciated only one time, when requested
	private static IpAddressFilter ipAddressFilter = null;
	
	private boolean enableIpAddressFilter = false;
	
	private String rewritingFilterClassName = null;

	/**
	 * IP Address filter.
	 * <p>
	 * This needs to be the first filter in the chain to block blacklisted host
	 * in the early stage of the connection, preventing network and computation
	 * load from unwanted hosts.
	 */
	protected void addIpAddressFilter(IoFilterChain chain) {
		if (enableIpAddressFilter) {
			if (ipAddressFilter == null) {
				ipAddressFilter = new IpAddressFilter();
			}
			chain.addLast("ipAddressFilter", ipAddressFilter);
		}
	}

	/**
	 * The RTSP codec filter is always present. Translates the incoming streams
	 * into RTSP messages.
	 */
	protected void addRTSPCodecFilter(IoFilterChain chain) {
		chain.addLast("codec", codecFilter);
	}

	protected void addRewriteFilter(IoFilterChain chain) {
		try {
			if (rewritingFilterClassName != null) {
				chain.addLast("requestUrlRewriting", new RequestUrlRewritingImpl(rewritingFilterClassName));
			}
		} catch (Exception e) {
		}
	}

	public boolean isEnableIpAddressFilter() {
		return enableIpAddressFilter;
	}

	public void setEnableIpAddressFilter(boolean enableIpAddressFilter) {
		this.enableIpAddressFilter = enableIpAddressFilter;
	}

	public String getRewritingFilterClassName() {
		return rewritingFilterClassName;
	}

	public void setRewritingFilterClassName(String rewritingFilterClassName) {
		this.rewritingFilterClassName = rewritingFilterClassName;
	}
	
}
