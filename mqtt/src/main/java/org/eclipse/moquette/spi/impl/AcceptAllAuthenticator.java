package org.eclipse.moquette.spi.impl;

import org.red5.server.mqtt.IAuthenticator;

/**
 * Created by andrea on 8/23/14.
 */
public class AcceptAllAuthenticator implements IAuthenticator {
	public boolean checkValid(String username, String password) {
		return true;
	}
}
