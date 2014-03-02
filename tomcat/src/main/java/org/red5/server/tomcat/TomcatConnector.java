package org.red5.server.tomcat;

import java.net.InetSocketAddress;
import java.util.Map;

import org.apache.catalina.connector.Connector;
import org.apache.catalina.core.AprLifecycleListener;
import org.apache.coyote.ProtocolHandler;
import org.apache.coyote.http11.Http11NioProtocol;
import org.apache.coyote.http11.Http11Protocol;
import org.red5.logging.Red5LoggerFactory;
import org.slf4j.Logger;

/**
 * Model object to contain a connector, socket address, and connection properties for a Tomcat connection.
 * 
 * @author Paul Gregoire
 */
public class TomcatConnector {

	private static Logger log = Red5LoggerFactory.getLogger(TomcatConnector.class);

	private Connector connector;

	private Map<String, String> connectionProperties;

	private String protocol = "org.apache.coyote.http11.Http11NioProtocol";

	private InetSocketAddress address;

	private int redirectPort = 443;

	private boolean useIPVHosts = true;

	private String URIEncoding = "UTF-8";

	private boolean secure;
	
	private boolean initialized;

	public void init() {
		try {
			// create a connector
			connector = new Connector(protocol);
			connector.setRedirectPort(redirectPort);
			connector.setUseIPVHosts(useIPVHosts);
			connector.setURIEncoding(URIEncoding);
			// set the bind address to local if we dont have an address property
			if (address == null) {
				address = bindLocal(connector.getPort());
			}
			// set port
			connector.setPort(address.getPort());
			// set connection properties
			if (connectionProperties != null) {
				for (String key : connectionProperties.keySet()) {
					connector.setProperty(key, connectionProperties.get(key));
				}
			}
			// turn off native apr support
			AprLifecycleListener listener = new AprLifecycleListener();
			listener.setSSLEngine("off");
			connector.addLifecycleListener(listener);
			// determine if https support is requested
			if (secure) {
				// set connection properties
				connector.setSecure(true);
				connector.setScheme("https");
			}
			// apply the bind address to the handler
			ProtocolHandler handler = connector.getProtocolHandler();
			if (handler instanceof Http11Protocol) {
				((Http11Protocol) handler).setAddress(address.getAddress());
			} else if (handler instanceof Http11NioProtocol) {
				((Http11NioProtocol) handler).setAddress(address.getAddress());
			}
			// set initialized flag
			initialized = true;
		} catch (Throwable t) {
			log.error("Exception during connector creation", t);
		}
	}

	/**
	 * Returns a local address and port.
	 * 
	 * @param port
	 * @return
	 */
	private InetSocketAddress bindLocal(int port) throws Exception {
		return new InetSocketAddress("127.0.0.1", port);
	}

	/**
	 * @return the connector
	 */
	public Connector getConnector() {
		if (!initialized) {
			init();
		}
		return connector;
	}

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
	 * @param protocol the protocol to set
	 */
	public void setProtocol(String protocol) {
		this.protocol = protocol;
	}

	/**
	 * @param useIPVHosts the useIPVHosts to set
	 */
	public void setUseIPVHosts(boolean useIPVHosts) {
		this.useIPVHosts = useIPVHosts;
	}

	/**
	 * @param uRIEncoding the uRIEncoding to set
	 */
	public void setURIEncoding(String uRIEncoding) {
		URIEncoding = uRIEncoding;
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
	 * @param redirectPort the redirectPort to set
	 */
	public void setRedirectPort(int redirectPort) {
		this.redirectPort = redirectPort;
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
		return "TomcatConnector [connector=" + connector + ", connectionProperties=" + connectionProperties + ", address=" + address + "]";
	}

}
