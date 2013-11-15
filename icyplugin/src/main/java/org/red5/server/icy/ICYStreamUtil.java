package org.red5.server.icy;

import java.util.concurrent.atomic.AtomicInteger;

import org.red5.server.icy.nsv.NSVStreamConfig;

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
 * NSV constants and utility functions
 * 
 * @author Paul Gregoire (mondain@gmail.com)
 * @author Andy Shaules (bowljoman@hotmail.com)
 */
public class ICYStreamUtil {

	public static final int NSV_MAX_AUDIO_LEN = 0x8000; // 32kb

	public static final long NSV_MAX_VIDEO_LEN = 0x80000;// 512kb

	public static final int NSV_MAX_AUX_LEN = 0x8000; // 32kb for each aux stream

	public static final long NSV_MAX_AUXSTREAMS = 15; // 15 aux streams maximum

	public static final long NSV_SYNC_HEADERLEN_BITS = 192;

	public static final long NSV_NONSYNC_HEADERLEN_BITS = 56;

	public static final int NSV_NONSYNC_WORD = 0xbeef;

	public static final int NSV_SYNC_DWORD = makeType('N', 'S', 'V', 's');

	public static final int NSV_HDR_DWORD = makeType('N', 'S', 'V', 'f');

	private static AtomicInteger streamId = new AtomicInteger(0);
	
	public static double framerateToDouble(int fr) {
		double ret = 0;
		if ((fr & 0x80) == 0) {
			return fr;
		}
		double sc = 0;
		int d = fr & 0x7f >> 2;
		if (d < 16) {
			sc = 1.0 / (d + 1);
		} else {
			sc = d - 15;
		}
		int r = fr & 3;
		switch (r) {
			case 0:
				ret = 30.0 * sc;
				break;
			case 1:
				ret = 30.0 * 1000.0 / 1001.0 * sc;
				break;
			case 2:
				ret = 25.0 * sc;
				break;
			case 3:
				ret = 24.0 * 1000.0 / 1001.0 * sc;
				break;
		}
		return ret;
	}

	public static int makeType(char a, char b, char c, char d) {
		return (a | (b << 8) | (c << 16) | (d << 24));
	}

	/**
	 * Creates a stream config based on given properties.
	 * 
	 * @param vidtype
	 * @param audtype
	 * @param width
	 * @param height
	 * @param frameRate
	 * @return
	 */
	public static NSVStreamConfig createStreamConfig(String videoType, String audioType, int width, int height, double frameRate) {
		NSVStreamConfig newConfig = new NSVStreamConfig();
		newConfig.streamId = streamId.incrementAndGet();
		newConfig.videoFormat = videoType;
		newConfig.audioFormat = audioType;
		newConfig.videoWidth = width;
		newConfig.videoHeight = height;
		newConfig.frameRate = frameRate;
		return newConfig;
	}	
	
}
