package org.red5.server.plugin.auth;

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

import java.security.Security;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.apache.commons.codec.binary.Base64;
import org.bouncycastle.crypto.digests.MD5Digest;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.red5.logging.Red5LoggerFactory;
import org.red5.server.adapter.ApplicationLifecycle;
import org.red5.server.api.IConnection;
import org.red5.server.exception.ClientRejectedException;
import org.red5.server.net.rtmp.status.StatusCodes;
import org.red5.server.net.rtmp.status.StatusObject;
import org.slf4j.Logger;

/**
 * Provides FMS-style authentication using an application listener.
 * 
 * @author Paul Gregoire
 * @author Dan Rossi
 * @author Gavriloaie Eugen-Andrei
 */
public class FMSAuthenticationHandler extends ApplicationLifecycle {

	private static Logger log = Red5LoggerFactory.getLogger(FMSAuthenticationHandler.class, "plugins");
	
	private static StatusObject rejectMissingAuth = new StatusObject(StatusCodes.NC_CONNECT_REJECTED,
			StatusObject.ERROR, "[ code=403 .need auth; authmod=adobe ]");

	private static StatusObject invalidAuthMod = new StatusObject(StatusCodes.NC_CONNECT_REJECTED, StatusObject.ERROR,
			"[ AccessManager.Reject ] : [ authmod=adobe ] : ?reason=invalid_authmod&opaque=-");

	private static StatusObject noSuchUser = new StatusObject(StatusCodes.NC_CONNECT_REJECTED, StatusObject.ERROR,
			"[ AccessManager.Reject ] : [ authmod=adobe ] : ?reason=nosuchuser&opaque=sTQAAA=");

	/*
	private static StatusObject invalidSessionId = new StatusObject(StatusCodes.NC_CONNECT_REJECTED,
			StatusObject.ERROR, "[ AccessManager.Reject ] : [ authmod=adobe ] : ?reason=invalid_session_id&opaque=-");
	*/

	//test password - testing only - user passwords should be looked up in a real implementation
	private static final String password = "test";
		
	private static ConcurrentMap<String, AuthSession> sessions = new ConcurrentHashMap<String, AuthSession>();
	
	static {
		//get security provider
		Security.addProvider(new BouncyCastleProvider());		
	}
	
	public boolean appConnect(IConnection conn, Object[] params) {

        log.info("appConnect");

		boolean result = false;

		log.debug("Connection: {}", conn);
		log.debug("Params: {}", params);
		
		StatusObject status = null;
		
		Map<String, Object> connectionParams = conn.getConnectParams();
		log.debug("Connection params: {}", connectionParams);
		
		if (!connectionParams.containsKey("queryString")) {
			//set as missing auth notification
			status = rejectMissingAuth;
		} else {
			//get the raw query string
    		String rawQueryString = (String) connectionParams.get("queryString");
    		try {
    			//parse into a usable query string
    			UrlQueryStringMap<String, String> queryString = UrlQueryStringMap.parse(rawQueryString);
    			
    			//get the values we want
    			String user = queryString.get("user");
    			log.debug("User: {}", user);
    			
    			String authmod = queryString.get("authmod");    			
    			log.debug("Authmod: {}", authmod);
    			
    			//make sure they requested adobe auth
    			if ("adobe".equals(authmod)) {
        			String response = queryString.get("response");
        			log.debug("Response: {}", response);
        			//no response yet, send salt etc.
        			if (response != null) {
        				//lookup session and remove at the same time
        				AuthSession session = sessions.remove(user);
        				//verify response
        				if (session != null) {
            				//1. construct the first part
        					String str1 = user + session.salt + password;
        					log.trace("Part 1: {}", str1);
        					//2. md5 and base64 encode
        					String hash1 = calculateMD5(str1);
        					log.trace("Hash 1: {}", hash1);
        					//3. construct second part using challenge from client
        					//String str2 = hash1 + session.challenge + queryString.get("challenge");
        					String str2 = hash1 + queryString.get("opaque") + queryString.get("challenge");
        					log.trace("Part 2: {}", str2);
        					//4. md5 and base64 encode
        					String hash2 = calculateMD5(str2);
        					log.trace("Hash 2: {}", hash2);
        					//5. compare response with hash2
        					if (hash2.equals(response)) {
        						log.debug("Response is valid");
                				//return success
                				result = true;
        					} else {
        						log.info("Response {} did not match hash {}", response, hash2);
        					}
        				} else {
        					status = noSuchUser;
        				}
        			} else {
        				//create auth session
        				AuthSession session = new AuthSession();
        				sessions.put(user, session);
        				//set as rejected
        				status = new StatusObject(StatusCodes.NC_CONNECT_REJECTED, StatusObject.ERROR, 
        						String.format("[ AccessManager.Reject ] : [ authmod=adobe ] : ?reason=needauth&user=%s&salt=%s&challenge=%s&opaque=%s", user, session.salt, session.challenge, session.opaque));
        			}    				
    			} else {
    				status = invalidAuthMod;
    			}

    		} catch (Exception e) {
    			log.error("Error authenticating", e);
    		}
		}
		
		//status.setAdditional("secureToken", "testing secure token status property from RED5 !!!");
		
		//send the status object
		log.debug("Status: {}", status);
		if (!result) {
			throw new ClientRejectedException(status);
        }
    		
		return result;
	}
	
	/**
	 * Generate an MD5 hash and return encoded with Base64.
	 * 
	 * @param input
	 * @return
	 */
	private String calculateMD5(String input) {
		String result = null;
		MD5Digest md5;
		try {
			md5 = new MD5Digest();
			byte[] output = null;
			try {
				byte[] buf = input.getBytes();
				md5.update(buf, 0, buf.length); 
				output = new byte[md5.getDigestSize()]; 
				md5.doFinal(output, 0); 
			} catch (Exception e) {
				log.error("State error", e);
			}
			
	
			//encode in b64 and strip any cr/lf
		
			
			byte[] res = Base64.encodeBase64(output);
			
			result = new String(res).replaceAll("(\r\n|\r|\n|\n\r)", "");
			
			//result = Base64.encodeBase64String(output).replaceAll("(\r\n|\r|\n|\n\r)", "");
		} catch (SecurityException e) {
			log.error("Security exception when getting MD5", e);
		} catch (Exception e) {
			log.error("Error using MD5", e);
		}				
		return result;
	}
	
	private final class AuthSession {
		
		public String salt;
		
		public String challenge;
		
		public String opaque;

		@SuppressWarnings("unused")
		public long created = System.currentTimeMillis();
		
		{
			salt = calculateMD5("red5rox");
			challenge = calculateMD5("red5");
			//these are equal for now
			opaque = challenge;
		}
		
	}

}
