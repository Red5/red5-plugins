package org.red5.server.net.policy;

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

import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.HashSet;
import java.util.Set;

import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.service.IoAcceptor;
import org.apache.mina.core.service.IoHandlerAdapter;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.transport.socket.nio.NioSocketAcceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

/**
 * Provides the socket policy file.
 *
 * @see "http://www.adobe.com/devnet/flashplayer/articles/socket_policy_files.html"
 * 
 * @author Paul Gregoire (mondain@gmail.com)
 */
public class SocketPolicyHandler extends IoHandlerAdapter implements InitializingBean, DisposableBean {

	protected static Logger log = LoggerFactory.getLogger(SocketPolicyHandler.class);

	private String host = "0.0.0.0";

	private int port = 843;

	private String policyFilePath = "flashpolicy.xml";

	private static IoAcceptor acceptor;

	private static IoBuffer policyData;

	@Override
	public void afterPropertiesSet() throws Exception {
		log.debug("Starting socket policy file server");
		try {
			// get the file
			InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(policyFilePath);
			if (is != null) {
				//read the policy file
				policyData = IoBuffer.allocate(1024);
				byte[] b = new byte[4];
				while (is.read(b) != -1) {
					policyData.put(b);
				}
				policyData.flip();
				is.close();
				log.info("Policy file read successfully");
				// create accept socket
				acceptor = new NioSocketAcceptor();
				acceptor.setHandler(this);
				Set<SocketAddress> addresses = new HashSet<SocketAddress>();
				addresses.add(new InetSocketAddress(host, port));
				acceptor.bind(addresses);
				log.info("Socket policy file server listening on port {}", port);
			} else {
				log.error("Policy file was not found");
			}
		} catch (Exception e) {
			log.error("Exception initializing socket policy server", e);
		}
	}

	@Override
	public void destroy() throws Exception {
		log.debug("Stopping socket policy file server");
		if (acceptor != null) {
			acceptor.unbind();
		}
	}

	@Override
	public void messageReceived(IoSession session, Object message) throws Exception {
		log.info("Incomming: {}", session.getRemoteAddress().toString());
		session.write(policyData);
		session.close(true);
	}

	@Override
	public void exceptionCaught(IoSession session, Throwable ex) throws Exception {
		log.info("Exception: {}", session.getRemoteAddress().toString(), ex);
	}

	public String getHost() {
		return host;
	}

	public void setHost(String host) {
		this.host = host;
	}

	public int getPort() {
		return port;
	}

	public void setPort(int port) {
		this.port = port;
	}

	/**
	 * @return the policyFilePath
	 */
	public String getPolicyFilePath() {
		return policyFilePath;
	}

	/**
	 * @param policyFilePath the policyFilePath to set
	 */
	public void setPolicyFilePath(String policyFilePath) {
		this.policyFilePath = policyFilePath;
	}

}
