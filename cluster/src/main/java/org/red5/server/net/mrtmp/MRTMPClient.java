/*
 * RED5 Open Source Flash Server - http://code.google.com/p/red5/
 * 
 * Copyright 2006-2012 by respective authors (see below). All rights reserved.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.red5.server.net.mrtmp;

import java.net.InetSocketAddress;

import org.apache.mina.core.future.ConnectFuture;
import org.apache.mina.core.service.IoConnector;
import org.apache.mina.core.service.IoHandler;
import org.apache.mina.core.session.IdleStatus;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.transport.socket.SocketSessionConfig;
import org.apache.mina.transport.socket.nio.NioSocketConnector;

/**
 * @author Steven Gong (steven.gong@gmail.com)
 */
public class MRTMPClient implements Runnable {
	private IoHandler ioHandler;
	private IoHandler ioHandlerWrapper;
	private String server;
	private int port;
	private Thread connectThread;
	private boolean needReconnect;
	
	public String getServer() {
		return server;
	}
	public void setServer(String address) {
		this.server = address;
	}
	public IoHandler getIoHandler() {
		return ioHandler;
	}
	public void setIoHandler(IoHandler ioHandler) {
		this.ioHandler = ioHandler;
	}
	public int getPort() {
		return port;
	}
	public void setPort(int port) {
		this.port = port;
	}
	
	public void start() {
		needReconnect = true;
		ioHandlerWrapper = new IoHandlerWrapper(ioHandler);
		connectThread = new Thread(this, "MRTMPClient");
		connectThread.setDaemon(true);
		connectThread.start();
	}
	
	public void run() {
		while (true) {
			synchronized (ioHandlerWrapper) {
				if (needReconnect) {
					doConnect();
					needReconnect = false;
				}
				try {
					ioHandlerWrapper.wait();
				} catch (Exception e) {}
			}
		}
	}
	
	private void doConnect() {
		IoConnector connector = new NioSocketConnector();
		connector.setHandler(ioHandlerWrapper);
		SocketSessionConfig sessionConf = (SocketSessionConfig) connector.getSessionConfig();
		sessionConf.setTcpNoDelay(true);
		while (true) {
			ConnectFuture future = connector.connect(new InetSocketAddress(server, port));
			future.awaitUninterruptibly(500);
			if (future.isConnected()) {
				break;
			}
			try {
				Thread.sleep(500);
			} catch (Exception e) {}
		}
	}
	
	private void reconnect() {
		synchronized (ioHandlerWrapper) {
			needReconnect = true;
			ioHandlerWrapper.notifyAll();
		}
	}
	
	private class IoHandlerWrapper implements IoHandler {
		private IoHandler wrapped;
		
		public IoHandlerWrapper(IoHandler wrapped) {
			this.wrapped = wrapped;
		}

		public void exceptionCaught(IoSession session, Throwable cause) throws Exception {
			wrapped.exceptionCaught(session, cause);
			MRTMPClient.this.reconnect();
		}

		public void messageReceived(IoSession session, Object message) throws Exception {
			wrapped.messageReceived(session, message);
		}

		public void messageSent(IoSession session, Object message) throws Exception {
			wrapped.messageSent(session, message);
		}

		public void sessionClosed(IoSession session) throws Exception {
			wrapped.sessionClosed(session);
		}

		public void sessionCreated(IoSession session) throws Exception {
			wrapped.sessionCreated(session);
		}

		public void sessionIdle(IoSession session, IdleStatus status) throws Exception {
			wrapped.sessionIdle(session, status);
		}

		public void sessionOpened(IoSession session) throws Exception {
			wrapped.sessionOpened(session);
		}
	}
}
