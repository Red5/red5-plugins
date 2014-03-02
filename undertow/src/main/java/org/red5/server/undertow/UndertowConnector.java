package org.red5.server.undertow;

import java.net.InetSocketAddress;
import java.util.Map;

import org.red5.logging.Red5LoggerFactory;
import org.slf4j.Logger;

/**
 * Model object to contain a socket address, and connection properties for a Undertow connection.
 * 
 * @author Paul Gregoire
 */
public class UndertowConnector {

	private static Logger log = Red5LoggerFactory.getLogger(UndertowConnector.class);

	private Map<String, String> connectionProperties;

	private InetSocketAddress address;

	private boolean secure;

	/**
	 * Set connection properties for the connector.
	 * 
	 * @param props connection properties to set
	 */
	public void setConnectionProperties(Map<String, String> props) {
		this.connectionProperties.putAll(props);
	}

	/**
	 * @return the connectionProperties
	 */
	public Map<String, String> getConnectionProperties() {
		return connectionProperties;
	}
	
	/**
	 * The address to which we will bind the connector.
	 * 
	 * @param address
	 */
	public void setAddress(String addressAndPort) {
		try {
			String addr = "0.0.0.0";
			int port = 5080;
			if (addressAndPort != null && addressAndPort.indexOf(':') != -1) {
				String[] parts = addressAndPort.split(":");
				addr = parts[0];
				port = Integer.valueOf(parts[1]);
			}
			this.address = new InetSocketAddress(addr, port);
		} catch (Exception e) {
			log.warn("Exception configuring address", e);
		}
	}

	/**
	 * @return the socket address as string
	 */
	public String getAddress() {
		return String.format("%s:%d", address.getHostName(), address.getPort());
	}

	/**
	 * @return the socket address
	 */
	public InetSocketAddress getSocketAddress() {
		return address;
	}

	/**
	 * @return the secure
	 */
	public boolean isSecure() {
		return secure;
	}

	/**
	 * @param secure the secure to set
	 */
	public void setSecure(boolean secure) {
		this.secure = secure;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return "UndertowConnector [connectionProperties=" + connectionProperties + ", address=" + address + ", secure=" + secure + "]";
	}

}
