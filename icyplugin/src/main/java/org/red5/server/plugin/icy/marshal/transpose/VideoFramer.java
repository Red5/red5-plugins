package org.red5.server.plugin.icy.marshal.transpose;

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
import org.red5.server.icy.IICYEventSink;
import org.red5.server.net.rtmp.event.IRTMPEvent;
import org.red5.server.net.rtmp.event.VideoData;
import org.red5.server.net.rtmp.message.Header;

/**
 * Provides video in pre-defined containers.
 * 
 * @author Wittawas Nakkasem (vittee@hotmail.com)
 * @author Andy Shaules (bowljoman@hotmail.com)
 */
public class VideoFramer {
	
	private IICYEventSink output;

	public VideoFramer(IICYEventSink outputSink) {
		output = outputSink;
	}

	/**
	 * Take raw vp6 and format it for flv. May need adjustments. 
	 * 
	 * @param frame
	 * @param timecode
	 */
	public void pushVP6Frame(byte[] frame, int timecode) {
		IoBuffer buffV = IoBuffer.allocate(frame.length);
		buffV.setAutoExpand(true);
		byte flags;
		byte crops = 0x00;
		boolean key = ((frame[0] & 0x80) == 0);
		if (!key) {
			flags = (0x03) << 4 | (0x04);
		} else {
			flags = (0x01) << 4 | (0x04);
		}
		buffV.put(flags);
		buffV.put(crops);
		buffV.put(frame);
		buffV.flip();
		buffV.position(0);

		IRTMPEvent video = new VideoData(buffV);
		video.setHeader(new Header());
		video.getHeader().setTimer((int) timecode & 0xffffff);
		//video.getHeader().setTimerRelative(false);
		video.setTimestamp((int) timecode & 0xffffff);
		output.dispatchEvent(video);
	}

	/**
	 * Take raw h264 and format it for flv
	 * @param frame
	 * @param timecode
	 */
	public void pushAVCFrame(byte[] frame, int timecode) {

	}
	
}
