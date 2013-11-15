package org.red5.server.icy.message;

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

/**
 * Simple abstract stream data frame.
 * 
 * @author Paul Gregoire (mondain@gmail.com)
 */
public abstract class Frame {
	
	public long frameNumber = 0;

	public long timeStamp = 0;

	public String videoType;

	public String audioType;

	public int width = 0;

	public int height = 0;

	public double frameRate = 0;

	public int frameRateEncoded = 0x0;

	public int offsetCurrent = 0;

	public byte[] videoData;

	public byte[] audioData;

	public int videoLength = 0;

	public int audioLength = 0;	
	
	public long getFrameNumber() {
		return frameNumber;
	}

	public void setFrameNumber(long frameNumber) {
		this.frameNumber = frameNumber;
	}

}
