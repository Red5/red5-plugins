package org.red5.server.plugin.icy.marshal;

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

import java.util.HashMap;
import java.util.Map;

import org.apache.mina.core.buffer.IoBuffer;
import org.red5.codec.AACAudio;
import org.red5.io.amf.Output;
import org.red5.io.object.Serializer;
import org.red5.logging.Red5LoggerFactory;
import org.red5.server.api.IContext;
import org.red5.server.api.scope.IBroadcastScope;
import org.red5.server.api.scope.IScope;
import org.red5.server.icy.IICYMarshal;
import org.red5.server.icy.message.Frame;
import org.red5.server.icy.nsv.NSVFrameQueue;
import org.red5.server.net.rtmp.event.IRTMPEvent;
import org.red5.server.net.rtmp.event.Notify;
import org.red5.server.plugin.icy.StreamManager;
import org.red5.server.plugin.icy.marshal.transpose.AudioFramer;
import org.red5.server.plugin.icy.marshal.transpose.VideoFramer;
import org.red5.server.plugin.icy.stream.ICYStream;
import org.red5.server.scope.BroadcastScope;
import org.red5.server.stream.IProviderService;
import org.slf4j.Logger;

/**
 * This class registers the stream name in the provided scope and packages the buffers into rtmp events.
 * 
 * @author Wittawas Nakkasem (vittee@hotmail.com)
 * @author Andy Shaules (bowljoman@hotmail.com)
 * @author Paul Gregoire (mondain@gmail.com)
 */
public class ICYMarshal implements IICYMarshal {
	
	private static Logger log = Red5LoggerFactory.getLogger(ICYMarshal.class, "plugins");

	private AudioFramer audioFramer;

	private VideoFramer videoFramer;

	private IScope scope;

	private String name;

	private ICYStream stream;

	@SuppressWarnings("unused")
	private String content;

	private String type;

	private String fourCCAudio;

	private String fourCCVideo;

	private Map<String, Object> metaData;

	private NSVFrameQueue queue;
	
	private NSVSenderThread	sender;
	
	public ICYMarshal(IScope outputScope, String outputName) {
		log.debug("ICYMarshal created - name: {} scope: {}", outputName, outputScope.getName());
		scope = outputScope;
		name = outputName;
		stream = new ICYStream(name, true, true);
		stream.setScope(outputScope);

		IContext context = outputScope.getContext();
		IProviderService providerService = (IProviderService) context.getBean(IProviderService.BEAN_NAME);
		if (providerService.registerBroadcastStream(outputScope, stream.getPublishedName(), stream)) {
			IBroadcastScope bsScope = (BroadcastScope) providerService.getLiveProviderInput(outputScope, stream.getPublishedName(), true);
//			bsScope.setAttribute(IBroadcastScope.STREAM_ATTRIBUTE, stream);
		}
		audioFramer = new AudioFramer(stream);
	}

	public AudioFramer getAudioFramer() {
		return audioFramer;
	}

	public VideoFramer getVideoFramer() {
		return videoFramer;
	}

	public IScope getScope() {
		return scope;
	}

	public ICYStream getStream() {
		return stream;
	}

	public String getContentType() {
		return type;
	}

	public String getAudioType() {
		return fourCCAudio;
	}

	public String getVideoType() {
		return fourCCVideo;
	}

	public void reset(String content, String type) {
		log.debug("Reset - content: {} type: {}", content, type);
		this.content = content;
		this.type = type;
		stream.reset();
		audioFramer.reset();

		if (content.equals("audio")) {
			if (type.startsWith("aac")) {
				AACAudio audioCodec = new AACAudio();
				stream.setAudioReader(audioCodec);
				stream.audioFramer = audioFramer;
			} else if (type.equals("mpeg")) {
				//MP3Audio
			} else {
				log.debug("Unsupported content type at reset: {}", type);
			}
		} else if (content.equals("video")) {
			videoFramer = new VideoFramer(stream);
		}

	}

	public void onAuxData(String fourCC, IoBuffer buffer) {
		log.debug("onAuxData - fourCC: {} buffer: {}", fourCC, buffer.getHexDump(32));
		buffer.free();
	}

	public void onConnected(String videoType, String audioType) {
		log.debug("onConnected - video type: {} audio type: {}", videoType, audioType);
		fourCCAudio = audioType;
		if (fourCCAudio.startsWith("AAC")) {
			AACAudio audioCodec = new AACAudio();
			stream.setAudioReader(audioCodec);
			stream.audioFramer = audioFramer;
		} else if (fourCCAudio.equals("MP3")) {
			//TODO MP3Audio
		} else {
			log.debug("Unsupported audio type: {}", audioType);
		}
		fourCCVideo = videoType;
		if (fourCCVideo.startsWith("VP6") || fourCCVideo.startsWith("H264")) {
			//
    	} else {
    		log.debug("Unsupported video type: {}", videoType);
    	}
	}

	public void onAudioData(byte[] data) {
		log.debug("onAudioData - length: {}", data.length);
		String codecName = stream.getCodecReader().getName();
		if ("AAC".equals(codecName)) {
			audioFramer.onAACData(data);
		} else if ("MP3".equals(codecName)) {
			audioFramer.onMP3Data(data);
		} else if ("PCM".equals(codecName)) {
			log.info("PCM is not yet supported");
			//audioFramer.onPCMData(data);
		} else if ("SPX".equals(codecName)) {
			log.info("Speex is not yet supported");
			//audioFramer.onSpeexData(data);
		}
	}
	
	public void onVideoData(byte[] data) {
		log.debug("onVideoData - length: {}", data.length);
		if (fourCCVideo.startsWith("VP6")) {
			videoFramer.pushVP6Frame(data, 0);
		} else if (fourCCVideo.startsWith("H264")) {
			videoFramer.pushAVCFrame(data, 0);
		}
	}

	public void onMetaData(Map<String, Object> metaData) {
		log.debug("onMetaData: {}", metaData);
		this.metaData = metaData;
		IRTMPEvent event = getMetaDataEvent();
		if (event != null) {
			stream.setMetaDataEvent(event);
		}
		stream.dispatchEvent(event);
	}

	public void onFrameData(Frame frame) {
		if (queue == null) {
			queue = new NSVFrameQueue();
			sender = new NSVSenderThread();
		}
		queue.addFrame(frame);
		if (sender != null && !sender.isRunning()) {
    		//now that the sender has a config, submit it for execution
    		StreamManager.submit(sender);
		}			
	}
	
	public void onDisconnected() {
	}
	
	private IRTMPEvent getMetaDataEvent() {
		if (metaData == null) {
			return null;
		}

		IoBuffer buf = IoBuffer.allocate(1024);
		buf.setAutoExpand(true);
		Output out = new Output(buf);
		out.writeString("onMetaData");

		Map<Object, Object> props = new HashMap<Object, Object>();
		props.putAll(metaData);
		props.put("canSeekToEnd", false);

		out.writeMap(props);
		buf.flip();

		return new Notify(buf);
	}

	public int queueSize() {
		if (queue == null) {
			return 0;
		} else {
			return queue.count();
		}
	}
	
	final class NSVSenderThread implements Runnable {
				
		private boolean running;

		@Override
		public void run() {
			running = true;
			while (queue.hasFrames()) {
				Frame frame = queue.getFrame();
				onAudioData(frame.audioData);
				if (fourCCVideo == null) {
					continue;
				}
				onVideoData(frame.videoData);
			}
			log.debug("Sender thread exiting");
			running = false;
		}

		public boolean isRunning() {
			return running;
		}
		
	}	

}
