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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import org.red5.server.net.rtmp.IRTMPConnManager;
import org.red5.server.net.rtmp.RTMPConnection;

/**
 * A simple Edge connection manager that only manages one Edge/Origin connection.
 * @author Steven Gong (steven.gong@gmail.com)
 * @version $Id$
 */
public class SimpleMRTMPEdgeManager implements IMRTMPEdgeManager {
	private IRTMPConnManager rtmpConnManager;
	private List<IMRTMPConnection> connList = new ArrayList<IMRTMPConnection>();
	
	public void setRtmpConnManager(IRTMPConnManager rtmpConnManager) {
		this.rtmpConnManager = rtmpConnManager;
	}

	public boolean registerConnection(IMRTMPConnection conn) {
		return connList.add(conn);
	}

	public boolean unregisterConnection(IMRTMPConnection conn) {
		boolean ret = connList.remove(conn);
		if (ret) {
			Collection<RTMPConnection> rtmpConns = rtmpConnManager.removeConnections();
			for (Iterator<RTMPConnection> iter = rtmpConns.iterator(); iter.hasNext(); ) {
				RTMPConnection rtmpConn = iter.next();
				rtmpConn.close();
			}
		}
		return ret;
	}

	public IMRTMPConnection lookupMRTMPConnection(RTMPConnection conn) {
		if (connList.size() > 0) {
			return connList.get(0);
		} else {
			return null;
		}
	}
	
}
