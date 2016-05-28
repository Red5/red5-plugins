package org.red5.server.plugin.security;

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

import org.apache.mina.core.buffer.IoBuffer;
import org.red5.io.amf.AMF;
import org.red5.io.amf.Output;
import org.red5.logging.Red5LoggerFactory;
import org.red5.server.adapter.MultiThreadedApplicationAdapter;
import org.red5.server.api.IConnection;
import org.red5.server.net.rtmp.RTMPConnection;
import org.red5.server.net.rtmp.event.IRTMPEvent;
import org.red5.server.net.rtmp.event.Notify;
import org.red5.server.net.rtmp.message.Header;
import org.red5.server.net.rtmp.message.Packet;
import org.red5.server.net.rtmp.status.StatusObject;
import org.red5.server.plugin.Red5Plugin;
import org.slf4j.Logger;

/**
 * Provides security token features.
 * 
 * @author Paul Gregoire
 * @author Dan Rossi
 */
public class SecurityPlugin extends Red5Plugin {

    private static Logger log = Red5LoggerFactory.getLogger(SecurityPlugin.class, "plugins");
    
    private MultiThreadedApplicationAdapter application;
    

    public void doStart() throws Exception {
        log.debug("Start");
    }

    public void doStop() throws Exception {
        log.debug("Stop");
    }

    public String getName() {
        return "securityPlugin";
    }
    
    public void setApplication(MultiThreadedApplicationAdapter app) {   
        log.trace("Setting application adapter: {}", app);
        this.application = app;
    }
    
    //methods specific to this plug-in
    
    public PlaybackSecurityHandler getPlaybackSecurityHandler() {
        PlaybackSecurityHandler ph = null;
        try {
            ph = (PlaybackSecurityHandler) Class.forName("org.red5.server.plugin.security.PlaybackSecurityHandler").newInstance();
            ph.setApplication(application);
        } catch (Exception e) {
            log.error("PlaybackSecurityHandler could not be loaded", e);
        }
        return ph;      
    }
    
    public PublishSecurityHandler getPublishSecurityHandler() {
        PublishSecurityHandler ps = null;
        try {
            ps = (PublishSecurityHandler) Class.forName("org.red5.server.plugin.security.PublishSecurityHandler").newInstance();
            ps.setApplication(application);
        } catch (Exception e) {
            log.error("PublishSecurityHandler could not be loaded", e);
        }
        return ps;      
    }
    
    public SharedObjectSecurityHandler getSharedObjectSecurityHandler() {
        SharedObjectSecurityHandler sh = null;
        try {
            sh = (SharedObjectSecurityHandler) Class.forName("org.red5.server.plugin.security.SharedObjectSecurityHandler").newInstance();
            sh.setApplication(application);
        } catch (Exception e) {
            log.error("SharedObjectSecurityHandler could not be loaded", e);
        }
        return sh;      
    }

}