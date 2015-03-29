package org.red5.server.mqtt;

import java.io.File;
import java.util.Map;

import org.eclipse.moquette.spi.impl.AcceptAllAuthenticator;
import org.eclipse.moquette.spi.impl.FileAuthenticator;
import org.eclipse.moquette.spi.impl.SimpleMessaging;
import org.eclipse.moquette.spi.persistence.MapDBPersistentStore;
import org.red5.logging.Red5LoggerFactory;
import org.red5.server.mqtt.net.MQTTTransport;
import org.slf4j.Logger;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

/**
 * MQTT broker.
 * 
 * Responsible for accepting client connections and message routing.
 * 
 * Message Components:
 <pre>
 * Topic
      - Hierarchical – usa/virginia/reston with wildcards # and +
      - Defined by the application

 * Payload
      - Byte array

 * Quality of Service
      - Fire and forget, At least once, Once and only once

 * Client Keep Alive
      - Maintains client session awareness
      - Enforced via client initiated ‘pings’

 * Last Will and Testament
      - Published on behalf of a client

 * Message Retention
      - Tells the broker to hang on to messages

 * Clean Start
      - Tells the broker to forget about the previous client connection

 * MQTT Persistence
      - Allows local persistence of data on the client side
      
 * Starting the broker and configuration options
      - Runs on Linux, Mac, Windows
      - Password setup (who has access)
      - ACL Setup (what clients can pub/sub on – plus support for pattern matching)
      - Basic scaling options (max clients, inflight messages, queued messages)
      - Timing parameters (retry for QOS resending)
      - Persistence options (how to store locally)
      - SQL options (how to authenticate)
 </pre>
 *
 * Initial source and research material courtesy of Moquette from Eclipse.
 *
 * @author Paul Gregoire (mondain@gmail.com)
 */
public class MQTTBroker implements ApplicationContextAware, InitializingBean, DisposableBean {

	private static Logger log = Red5LoggerFactory.getLogger(MQTTBroker.class);	

	private ApplicationContext applicationContext;
	
	private SimpleMessaging messaging;

	private String dbStorePath = System.getProperty("user.home") + File.separator + "mqtt_store.mapdb";
	
	private String passwdFileName;
	
	@Override
    public void afterPropertiesSet() throws Exception {
		messaging = SimpleMessaging.getInstance();
		// setup storage
		MapDBPersistentStore mapStorage = new MapDBPersistentStore(dbStorePath);
		messaging.setMapStorage(mapStorage);
		// setup auth
		IAuthenticator authenticator;
		if (passwdFileName == null || passwdFileName.isEmpty()) {
			authenticator = new AcceptAllAuthenticator();
			log.trace("Authentication accepting all");
		} else {
			authenticator = new FileAuthenticator(System.getProperty("user.home"), passwdFileName);
			log.trace("Authentication using File: {}", passwdFileName);
		}
		messaging.setAuthenticator(authenticator);
		// initialize messaging
	    messaging.init();	    
	    // get mqtt handler and set the messaging instance
	    Map<String, MQTTTransport> transports = applicationContext.getBeansOfType(MQTTTransport.class);
	    for (MQTTTransport transport : transports.values()) {
	    	transport.getHandler().setMessaging(messaging);
	    }
    }

	@Override
    public void destroy() throws Exception {
		messaging.stop();
    }

	public String getDbStorePath() {
		return dbStorePath;
	}

	public void setDbStorePath(String dbStorePath) {
		this.dbStorePath = dbStorePath;
	}

	public String getPasswdFileName() {
		return passwdFileName;
	}

	public void setPasswdFileName(String passwdFileName) {
		this.passwdFileName = passwdFileName;
	}

	@Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = applicationContext; 
    }

}
