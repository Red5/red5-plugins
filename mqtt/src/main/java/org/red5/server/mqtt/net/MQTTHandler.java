/*
 * RED5 Open Source Flash Server - https://github.com/red5
 * 
 * Copyright 2006-2015 by respective authors (see below). All rights reserved.
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

package org.red5.server.mqtt.net;

import static org.eclipse.moquette.proto.messages.AbstractMessage.CONNECT;
import static org.eclipse.moquette.proto.messages.AbstractMessage.DISCONNECT;
import static org.eclipse.moquette.proto.messages.AbstractMessage.PINGREQ;
import static org.eclipse.moquette.proto.messages.AbstractMessage.PUBACK;
import static org.eclipse.moquette.proto.messages.AbstractMessage.PUBCOMP;
import static org.eclipse.moquette.proto.messages.AbstractMessage.PUBLISH;
import static org.eclipse.moquette.proto.messages.AbstractMessage.PUBREC;
import static org.eclipse.moquette.proto.messages.AbstractMessage.PUBREL;
import static org.eclipse.moquette.proto.messages.AbstractMessage.SUBSCRIBE;
import static org.eclipse.moquette.proto.messages.AbstractMessage.UNSUBSCRIBE;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.apache.mina.core.service.IoHandlerAdapter;
import org.apache.mina.core.session.IoSession;
import org.eclipse.moquette.proto.Utils;
import org.eclipse.moquette.proto.messages.AbstractMessage;
import org.eclipse.moquette.proto.messages.PingRespMessage;
import org.eclipse.moquette.spi.IMessaging;
import org.red5.server.mqtt.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MQTTHandler
 *
 * @author Paul Gregoire
 */
public class MQTTHandler extends IoHandlerAdapter {

	private static final Logger log = LoggerFactory.getLogger(MQTTHandler.class);

    private ConcurrentMap<String, MinaChannel> channelMapper = new ConcurrentHashMap<String, MinaChannel>();
	
    private IMessaging messaging;
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public void messageReceived(IoSession session, Object message) throws Exception {
		log.trace("Message received on session: {}", session.getId());
		if (message instanceof AbstractMessage) {
			AbstractMessage msg = (AbstractMessage) message;
			log.info("Received a message of type {}", Utils.msgType2String(msg.getMessageType()));
			String sessionId = String.format("%d-%d", session.getCreationTime(), session.getId());
	        try {
	            switch (msg.getMessageType()) {
	                case CONNECT:
	                case SUBSCRIBE:
	                case UNSUBSCRIBE:
	                case PUBLISH:
	                case PUBREC:
	                case PUBCOMP:
	                case PUBREL:
	                case DISCONNECT:
	                case PUBACK:
	                	// lookup the channel based on the session or create a new one
                        if (!channelMapper.containsKey(sessionId)) {
                            channelMapper.put(sessionId, new MinaChannel(session));
                        }
                        MinaChannel channel = channelMapper.get(sessionId);
	                    // pass to messaging for handling
	                    messaging.handleProtocolMessage(channel, msg);
	                    break;
	                case PINGREQ:
	                    PingRespMessage pingResp = new PingRespMessage();
	                    session.write(pingResp);
	                    break;
	            }
	        } catch (Exception ex) {
	            log.error("Bad error in processing the message", ex);
	        }
		}
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
    public void messageSent(IoSession session, Object message) throws Exception {
		log.trace("Message sent on session: {}", session.getId());
		log.trace("Session read: {} write: {}", session.getReadBytes(), session.getWrittenBytes());
		if (message instanceof AbstractMessage) {
			AbstractMessage msg = (AbstractMessage) message;
			log.info("Sent a message of type {}", Utils.msgType2String(msg.getMessageType()));
		}
    }
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public void sessionClosed(IoSession session) throws Exception {
		log.trace("Session closed");		
		String sessionId = String.format("%d-%d", session.getCreationTime(), session.getId());
		MinaChannel channel = channelMapper.get(sessionId);
        String clientID = (String) channel.getAttribute(Constants.ATTR_CLIENTID);
        messaging.lostConnection(channel, clientID);
        session.close(false);
        channelMapper.remove(sessionId);
		super.sessionClosed(session);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void exceptionCaught(IoSession session, Throwable cause) throws Exception {
		log.error("exception", cause);
	}	
	
    /**
     * Sets reference to the messaging instance.
     * 
     * @param messaging
     */
    public void setMessaging(IMessaging messaging) {
    	this.messaging = messaging;
    }
    
}
