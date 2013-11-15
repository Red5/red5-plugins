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

import org.red5.server.net.rtmp.RTMPConnection;

/**
 * @author Steven Gong (steven.gong@gmail.com)
 */
public interface IMRTMPManager {
	/**
	 * Map a client to an Edge/Origin MRTMP connection.
	 * On Edge, the server will find an Origin connection per routing logic.
	 * On Origin, the server will send to the original in-coming connection
	 * if the client connection type is persistent. Or the latest in-coming
	 * connection will be used.
	 * @param conn
	 * @return the IMRTMPConnection
	 */
	IMRTMPConnection lookupMRTMPConnection(RTMPConnection conn);
	
	/**
	 * Register a MRTMP connection so that it can be later
	 * been looked up.
	 * @param conn
	 * @return whether the registration is successful
	 */
	boolean registerConnection(IMRTMPConnection conn);
	
	/**
	 * Unregister a MRTMP connection.
	 * @param conn
	 * @return whether the registration is successful
	 */
	boolean unregisterConnection(IMRTMPConnection conn);
}
