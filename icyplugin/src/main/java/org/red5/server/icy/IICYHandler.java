package org.red5.server.icy;

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

import java.util.Map;

import org.apache.mina.core.buffer.IoBuffer;
import org.red5.server.icy.message.Frame;

/**
 * Lifecycle.
 * 
 * @author Wittawas Nakkasem (vittee@hotmail.com)
 * @author Andy Shaules (bowljoman@hotmail.com)
 */
public interface IICYHandler {

	public void onConnected(String videoType, String audioType);

	public void onDisconnected();

	public void onAuxData(String fourCC, IoBuffer buffer);

	public void onAudioData(byte[] data);

	public void onVideoData(byte[] data);

	public void onMetaData(Map<String, Object> metaData);

	public void onFrameData(Frame frame);
	
	public void reset(String content, String type);

	public int queueSize();

}
