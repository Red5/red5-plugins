package org.red5.server.icy.message;

import org.red5.server.icy.BitStream;
import org.red5.server.icy.ICYStreamUtil;
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
 * Represents a single frame of NSV data.
 *  
 * @author Andy Shaules (bowljoman@hotmail.com)
 */
public class NSVFrame extends Frame {

	public long frameType = 0x8080;//empty	

	public int parser_info = 0;

	public int frameRateEncoded = 0x0;

	public int offsetCurrent = 0;

	public int numberAux = 0;

	public NSVFrame(NSVStreamConfig id, long type) {
		frameType = type;
		audioType = id.audioFormat;
		videoType = id.videoFormat;
		width = id.videoWidth;
		height = id.videoHeight;
		frameRate = id.frameRate;
		frameRateEncoded = id.frameRateEncoded;
	}

	/**
	 * For output back to shoutcast server. 
	 */
	public int[] toBitStream() {
		int[] ret = new int[1];
		ret[0] = 0;
		int length = 0;
		BitStream bs = new BitStream();
		switch ((frameType == ICYStreamUtil.NSV_SYNC_DWORD) ? 1 : 2) {
			case 1:
				length = 24 + (videoLength + audioLength);
				ret = new int[length];
				ret[0] = 'N';
				ret[1] = 'S';
				ret[2] = 'V';
				ret[3] = 's';
				ret[4] = (byte) videoType.charAt(0);
				ret[5] = (byte) videoType.charAt(1);
				ret[6] = (byte) videoType.charAt(2);
				ret[7] = (byte) videoType.charAt(3);
				ret[8] = (byte) audioType.charAt(0);
				ret[9] = (byte) audioType.charAt(1);
				ret[10] = (byte) audioType.charAt(2);
				ret[11] = (byte) audioType.charAt(3);
				ret[12] = ((width << 8) >> 8);
				ret[13] = ((width) >> 8);
				ret[14] = ((height << 8) >> 8);
				ret[15] = ((height) >> 8);
				ret[16] = frameRateEncoded;//frame rate
				ret[17] = ((offsetCurrent << 8) >> 8);
				ret[18] = ((offsetCurrent) >> 8);

				bs.putBits(4, numberAux);
				bs.putBits(20, videoLength);
				bs.putBits(16, audioLength);

				for (int i = 0; i < 5; i++) {
					ret[19 + i] = bs.getbits(8);
				}
				for (int i = 0; i < videoLength; i++) {
					ret[24 + i] = videoData[i];
				}
				for (int i = 0; i < audioLength; i++) {
					ret[(24 + videoLength + i)] = audioData[i];
				}

				break;
			case 2:
				length = 7 + (videoLength + audioLength);
				ret = new int[length];

				ret[0] = 0xef;
				ret[1] = 0xbe;

				bs.putBits(4, numberAux);
				bs.putBits(20, videoLength);
				bs.putBits(16, audioLength);

				for (int i = 0; i < 5; i++) {
					ret[2 + i] = bs.getbits(8);
				}
				for (int i = 0; i < videoLength; i++) {
					ret[7 + i] = videoData[i];
				}
				for (int i = 0; i < audioLength; i++) {
					ret[(7 + videoLength + i)] = audioData[i];
				}
				break;
		}
		return ret;
	}

}
