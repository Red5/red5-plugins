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

import java.util.Arrays;

import org.apache.mina.core.buffer.IoBuffer;
import org.red5.server.icy.IICYEventSink;
import org.red5.server.net.rtmp.event.AudioData;
import org.red5.server.net.rtmp.event.IRTMPEvent;
import org.red5.server.net.rtmp.message.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provides audio in pre-defined containers.
 * 
 * @author Wittawas Nakkasem (vittee@hotmail.com)
 * @author Andy Shaules (bowljoman@hotmail.com)
 */
public class AudioFramer {
	
	private static Logger log = LoggerFactory.getLogger(AudioFramer.class);	

	public static final int[] AAC_SAMPLERATES = { 96000, 88200, 64000, 48000, 44100, 32000, 24000, 22050, 16000, 12000,
			11025, 8000, 7350 };

	private int saveAACfrequency = -1;

	private int saveAACsamplesPerFrame = -1;

	private int aacTimecodeOffset = 0;

	private int lastSample = 0;

	private boolean isFirst;

	private int lastTimecode = 0;

	private byte[] tail = null;

	private boolean frameSynched;

	private int profile = 0;

	private int sampleRateIndex = 0;

	private int channels = 0;

	private int currentFrameLeft = 0;

	private int raw_data_block = 0;

	private IoBuffer buffer = null;

	public IICYEventSink output;

	public AudioFramer(IICYEventSink outputSink) {
		output = outputSink;
	}

	public void reset() {
		tail = null;
		buffer = null;
		currentFrameLeft = 0;
		frameSynched = false;
		isFirst = true;
		saveAACfrequency = -1;
		saveAACsamplesPerFrame = -1;
		aacTimecodeOffset = 0;
		lastTimecode = 0;
		lastSample = 0;
	}

	public void onAACData(byte[] feed) {

		byte[] data;
		// merge previous tail
		if (tail != null) {
			IoBuffer bb = IoBuffer.allocate(tail.length + feed.length);
			bb.put(tail);
			for (int i = 0; i < feed.length; i++) {
				bb.put(feed[i]);
			}
			data = bb.array();
			tail = null;
		} else {
			IoBuffer bb = IoBuffer.allocate(feed.length);
			for (int i = 0; i < feed.length; i++) {
				bb.put(feed[i]);
			}
			data = bb.array();
		}

		// parse
		int offset = 0;
		int adtsSkipped = 0;
		while ((data.length - offset) > 7) {
			if (!frameSynched) {
				if ((data[offset++] & 0xff) == 0xff) {
					if ((data[offset++] & 0xf6) == 0xf0) {
						profile = (data[offset] & 0xC0) >> 6;
						sampleRateIndex = (data[offset] & 0x3C) >> 2;
						channels = ((data[offset] & 0x01) << 2) | ((data[offset + 1] & 0xC0) >> 6);

						log.trace("Profile: {} sample rate index: {} channels: {}", new Object[]{profile, sampleRateIndex, channels});
						
						// frame length, ADTS excluded
						currentFrameLeft = (((data[offset + 1] & 0x03) << 8) | ((data[offset + 2] & 0xff) << 3) | ((data[offset + 3] & 0xff) >>> 5)) - 7;
						raw_data_block = data[(offset + 4)] & 0x3;

						log.trace("Current frame left: {} raw: {}", currentFrameLeft, raw_data_block);

						offset += 5; // skip ADTS		
						adtsSkipped += 7;
						frameSynched = true;
					}
				}

			} else {
				int remain = (data.length - offset);
				int bytesToRead = currentFrameLeft;
				if (bytesToRead > remain)
					bytesToRead = remain;

				if (buffer == null) {
					buffer = IoBuffer.allocate(2);
					buffer.setAutoExpand(true);
					buffer.put(new byte[] { (byte) 0xAF, (byte) 0x01 });
				}

				buffer.put(data, offset, bytesToRead);

				offset += bytesToRead;
				currentFrameLeft -= bytesToRead;

				log.trace("Current frame left: {}", currentFrameLeft);

				if (currentFrameLeft == 0) {
					try {
						IoBuffer newBuffer = IoBuffer.allocate(buffer.limit());
						buffer.flip();
						newBuffer.put(buffer);
						if (AAC_SAMPLERATES.length <= sampleRateIndex) {
							isFirst = false;
							buffer = null;
							frameSynched = false;
							return;
						}

						deliverAACFrame(newBuffer, AAC_SAMPLERATES[sampleRateIndex], (raw_data_block + 1) * 1024);

					} finally {
						isFirst = false;
						buffer = null;
						frameSynched = false;
					}
				}
			}
		}

		// keep tail
		int remain = data.length - offset;
		if (remain > 0) {
			try {
				tail = Arrays.copyOfRange(data, offset, data.length);
			} catch (Exception e) {

			}
		}
	}

	public void onMP3Data(byte[] data) {
		//TODO

	}
	
	private void deliverAACFrame(IoBuffer buffer, int sampleRate, int sampleCount) {

		if (saveAACfrequency == -1) {
			saveAACfrequency = sampleRate;
			saveAACsamplesPerFrame = sampleCount;
		}

		if ((saveAACfrequency != sampleRate) || (saveAACsamplesPerFrame != sampleCount)) {
			saveAACfrequency = sampleRate;
			saveAACsamplesPerFrame = sampleCount;
			aacTimecodeOffset = lastTimecode;
			lastSample = 0;
		}

		long timeSpan = 0;
		if (isFirst) {
			timeSpan = 0;
			lastSample = 0;
			lastTimecode = 0;
		} else {
			lastSample += sampleCount;
			timeSpan = aacTimecodeOffset + sample2TimeCode(lastSample, sampleRate) - lastTimecode;
			lastTimecode += timeSpan;
		}
		
		IRTMPEvent audio = new AudioData(buffer);
		audio.setTimestamp((int) timeSpan);
		audio.setHeader(new Header());
		audio.getHeader().setTimer((int) timeSpan & 0xffffff);
		//audio.getHeader().setTimerRelative(true);
		output.dispatchEvent(audio);
	}

	@SuppressWarnings("unused")
	private IRTMPEvent deliverMP3Frame(IoBuffer buffer, int sampleRate, int sampleCount) {
		//TODO
		return null;
	}

	public byte[] getAACSpecificConfig() {
		return new byte[] { (byte) (/*0x10 |*/((profile > 2) ? 2 : profile << 3) | ((sampleRateIndex >> 1) & 0x03)),
				(byte) (((sampleRateIndex & 0x01) << 7) | ((channels & 0x0F) << 3)) };

	}

	private long sample2TimeCode(long time, int sampleRate) {
		return (time * 1000L / sampleRate);
	}

}
