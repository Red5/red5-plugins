package org.red5.net.websocket.server;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.stream.Stream;

import javax.websocket.Extension;
import javax.websocket.HandshakeResponse;
import javax.websocket.server.HandshakeRequest;
import javax.websocket.server.ServerEndpointConfig;

import org.red5.net.websocket.WebSocketPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Red5 implementation of the WebSocket JSR365 <tt>ServerEndpointConfig.Configurator</tt>.
 * 
 * @author Paul Gregoire
 */
public class DefaultServerEndpointConfigurator extends ServerEndpointConfig.Configurator {

    private final Logger log = LoggerFactory.getLogger(DefaultServerEndpointConfigurator.class);

    // Cross-origin policy enable/disabled (defaults to the plugin's setting)
    private boolean crossOriginPolicy = WebSocketPlugin.isCrossOriginPolicy();

    // Cross-origin names (defaults to the plugin's setting)
    private String[] allowedOrigins = WebSocketPlugin.getAllowedOrigins();

    // holds handshake modification implementations
    private CopyOnWriteArraySet<HandshakeModifier> handshakeModifiers = new CopyOnWriteArraySet<>();
    
    @Override
    public <T> T getEndpointInstance(Class<T> clazz) throws InstantiationException {
        log.debug("getEndpointInstance: {}", clazz.getName());
        try {
            return clazz.getConstructor().newInstance();
        } catch (InstantiationException e) {
            throw e;
        } catch (ReflectiveOperationException e) {
            InstantiationException ie = new InstantiationException();
            ie.initCause(e);
            throw ie;
        }
    }

    @Override
    public String getNegotiatedSubprotocol(List<String> supported, List<String> requested) {
        log.debug("getNegotiatedSubprotocol - supported: {} requested: {}", supported, requested);
        for (String request : requested) {
            if (supported.contains(request)) {
                return request;
            }
        }
        return "";
    }

    @Override
    public List<Extension> getNegotiatedExtensions(List<Extension> installed, List<Extension> requested) {
        log.debug("getNegotiatedExtensions - installed: {} requested: {}", installed, requested);
        Set<String> installedNames = new HashSet<>();
        for (Extension e : installed) {
            installedNames.add(e.getName());
        }
        List<Extension> result = new ArrayList<>();
        for (Extension request : requested) {
            if (installedNames.contains(request.getName())) {
                result.add(request);
            }
        }
        return result;
    }

    @Override
    public boolean checkOrigin(String originHeaderValue) {
        log.debug("checkOrigin: {}", originHeaderValue);
        // if CORS is enabled
        if (crossOriginPolicy) {
            log.debug("allowedOrigins: {}", Arrays.toString(allowedOrigins));
            // allow "*" == any / all or origin suffix matches
            Optional<String> opt = Stream.of(allowedOrigins).filter(origin -> "*".equals(origin) || origin.endsWith(originHeaderValue)).findFirst();
            // non-match fail
            if (!opt.isPresent()) {
                log.info("Origin: {} did not match the allowed: {}", originHeaderValue, allowedOrigins);
                return false;
            }
        }
        return true;
    }

    @Override
    public void modifyHandshake(ServerEndpointConfig sec, HandshakeRequest request, HandshakeResponse response) {
        //log.debug("modifyHandshake - config: {} req: {} resp: {}", sec, request, response);
        handshakeModifiers.forEach(modifier -> {
            modifier.modifyHandshake(request, response);
        });
        super.modifyHandshake(sec, request, response);
    }

    public boolean isCrossOriginPolicy() {
        return crossOriginPolicy;
    }

    public void setCrossOriginPolicy(boolean crossOriginPolicy) {
        this.crossOriginPolicy = crossOriginPolicy;
    }

    public String[] getAllowedOrigins() {
        return allowedOrigins;
    }

    /**
     * Sets the allowed origins for this instance.
     * 
     * @param allowedOrigins
     */
    public void setAllowedOrigins(String[] allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
        log.debug("allowedOrigins: {}", Arrays.toString(allowedOrigins));
    }

    /**
     * Adds a HandshakeModifier implementation to the instances modifiers.
     * 
     * @param modifier
     * @return true if added and false otherwise
     */
    public boolean addHandshakeModifier(HandshakeModifier modifier) {
       return handshakeModifiers.add(modifier);
    }

    /**
     * Removes a HandshakeModifier implementation from the instances modifiers.
     * 
     * @param modifier
     * @return true if removed and false otherwise
     */
    public boolean removeHandshakeModifier(HandshakeModifier modifier) {
       return handshakeModifiers.remove(modifier);
    }
    
}
