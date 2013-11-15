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
public interface IMRTMPOriginManager extends IMRTMPManager {
	/**
	 * Associate the client to a MRTMP connection so that the packet
	 * will be sent via this MRTMP connection.
	 * The association has different impacts on persistent and polling
	 * connections. For persistent connection, the mapping is static while
	 * for polling connection, the mapping is dynamic and might not be
	 * honored.
	 * @param rtmpConn rtmp connection
	 * @param mrtmpConn mrtmp connection
	 */
	void associate(RTMPConnection rtmpConn, IMRTMPConnection mrtmpConn);
	
	/**
	 * Deassociate the client from the MRTMP connection previously
	 * associated to.
	 * @param rtmpConn
	 */
	void dissociate(RTMPConnection rtmpConn);
}
