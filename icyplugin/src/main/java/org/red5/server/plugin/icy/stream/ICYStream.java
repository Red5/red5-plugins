package org.red5.server.plugin.icy.stream;

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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import org.apache.mina.core.buffer.IoBuffer;
import org.red5.codec.AACAudio;
import org.red5.codec.IAudioStreamCodec;
import org.red5.codec.IStreamCodecInfo;
import org.red5.codec.StreamCodecInfo;
import org.red5.server.api.event.IEvent;
import org.red5.server.api.scope.IScope;
import org.red5.server.api.statistics.IClientBroadcastStreamStatistics;
import org.red5.server.api.statistics.support.StatisticsCounter;
import org.red5.server.api.stream.IBroadcastStream;
import org.red5.server.api.stream.IStreamListener;
import org.red5.server.api.stream.IStreamPacket;
import org.red5.server.api.stream.IVideoStreamCodec;
import org.red5.server.api.stream.ResourceExistException;
import org.red5.server.api.stream.ResourceNotFoundException;
import org.red5.server.icy.IICYEventSink;
import org.red5.server.messaging.IConsumer;
import org.red5.server.messaging.IMessageComponent;
import org.red5.server.messaging.IPipe;
import org.red5.server.messaging.IPipeConnectionListener;
import org.red5.server.messaging.IProvider;
import org.red5.server.messaging.OOBControlMessage;
import org.red5.server.messaging.PipeConnectionEvent;
import org.red5.server.net.rtmp.event.AudioData;
import org.red5.server.net.rtmp.event.IRTMPEvent;
import org.red5.server.net.rtmp.event.Notify;
import org.red5.server.net.rtmp.message.Header;
import org.red5.server.plugin.icy.marshal.transpose.AudioFramer;
import org.red5.server.plugin.icy.marshal.transpose.VideoFramer;
import org.red5.server.stream.IStreamData;
import org.red5.server.stream.PlayEngine;
import org.red5.server.stream.message.RTMPMessage;

/**
 * Output stream used to pipe the icy/nsv content.
 * 
 * @author Wittawas Nakkasem (vittee@hotmail.com)
 * @author Andy Shaules (bowljoman@hotmail.com)
 */
public class ICYStream implements IBroadcastStream, IProvider, IPipeConnectionListener, IICYEventSink,
		IClientBroadcastStreamStatistics {

	private Set<IStreamListener> mListeners = new CopyOnWriteArraySet<IStreamListener>();

	private String mPublishedName;

	private IPipe mLivePipe;

	private IScope mScope;

	private StreamCodecInfo mCodecInfo;

	private List<IConsumer> newComsumers = new ArrayList<IConsumer>();

	private StatisticsCounter subscriberStats = new StatisticsCounter();

	private int audioTime;

	private long bytesReceived = 0;

	private long creationTime;

	private IAudioStreamCodec audioReader;

	private IVideoStreamCodec videoReader;

	public AudioFramer audioFramer;

	public VideoFramer videoFramer;

	private IRTMPEvent _metaDataEvent;

	public ICYStream(String name, boolean video, boolean audio) {
		mPublishedName = name;
		mLivePipe = null;
		mCodecInfo = new StreamCodecInfo();
		mCodecInfo.setHasAudio(audio);
		mCodecInfo.setHasVideo(video);

	}

	@Override
	public void addStreamListener(IStreamListener listener) {
		//log.debug("addStreamListener(listener: {})", listener);
		mListeners.add(listener);
	}

	@Override
	public IProvider getProvider() {
		//log.debug("getProvider()");
		return this;
	}

	@Override
	public String getPublishedName() {
		return mPublishedName;
	}

	@Override
	public String getSaveFilename() {
		throw new Error("unimplemented method");
	}

	@Override
	public Collection<IStreamListener> getStreamListeners() {
		return mListeners;
	}

	@Override
	public void removeStreamListener(IStreamListener listener) {
		mListeners.remove(listener);
	}

	@Override
	public void saveAs(String arg0, boolean arg1) throws IOException, ResourceNotFoundException, ResourceExistException {
		throw new Error("unimplemented method");
	}

	@Override
	public void setPublishedName(String name) {
		//log.debug("setPublishedName(name:{})", name);
		mPublishedName = name;
	}

	@Override
	public void close() {
		//	log.debug("close()");
	}

	@Override
	public IStreamCodecInfo getCodecInfo() {
		return mCodecInfo;
	}

	@Override
	public String getName() {
		return mPublishedName;
	}

	@Override
	public IScope getScope() {
		return mScope;
	}

	public void setScope(IScope scope) {
		mScope = scope;
	}

	@Override
	public void start() {
		bytesReceived = 0;
		audioTime = 0;
		creationTime = System.currentTimeMillis();
	}

	@Override
	public void stop() {

	}

	@Override
	public void onOOBControlMessage(IMessageComponent arg0, IPipe arg1, OOBControlMessage arg2) {

	}

	public void setAudioReader(IAudioStreamCodec codecReader) {
		this.audioReader = codecReader;
	}

	public IAudioStreamCodec getCodecReader() {
		return audioReader;
	}

	public void setVideoReader(IVideoStreamCodec codecReader) {
		this.videoReader = codecReader;
	}

	public IVideoStreamCodec getVideoReader() {
		return videoReader;
	}

	@SuppressWarnings("unused")
	@Override
	public void onPipeConnectionEvent(PipeConnectionEvent event) {

		switch (event.getType()) {
			case PipeConnectionEvent.PROVIDER_CONNECT_PUSH:
				if ((event.getProvider() == this) && (event.getParamMap() == null)) {
					mLivePipe = (IPipe) event.getSource();
					//log.info("mLivePipe {}", mLivePipe);
					for (IConsumer consumer : mLivePipe.getConsumers()) {
						subscriberStats.increment();
					}
				}
				break;
			case PipeConnectionEvent.PROVIDER_DISCONNECT:
				if (mLivePipe == event.getSource()) {
					mLivePipe = null;
				}
				break;
			case PipeConnectionEvent.CONSUMER_CONNECT_PUSH:
				if (mLivePipe != null) {
					List<IConsumer> consumers = mLivePipe.getConsumers();
					int count = consumers.size();
					if (count > 0) {
						newComsumers.add(consumers.get(count - 1));
					}
					subscriberStats.increment();
				}
				break;

			case PipeConnectionEvent.CONSUMER_DISCONNECT:
				subscriberStats.decrement();
				break;
			default:
				break;
		}
	}

	private void sendConfig() {

		while (newComsumers.size() > 0) {
			IConsumer consumer = newComsumers.remove(0);
			if (consumer instanceof PlayEngine) {

				if (audioReader instanceof AACAudio) { // Audio pay-load
					IoBuffer buffer = IoBuffer.allocate(10);
					buffer.setAutoExpand(true);
					buffer.put((byte) 0xaf);
					buffer.put((byte) 0x00);
					buffer.put(audioFramer.getAACSpecificConfig());
					//buffer.put((byte) 0x06);
					buffer.flip();
					
					AudioData data = new AudioData(buffer);
					data.setHeader(new Header());
					RTMPMessage msg = RTMPMessage.build(data);
//					msg.setBody(data);

					try {
						((PlayEngine) consumer).pushMessage(null, msg);
					} catch (IOException e) {

					}
				}
				if (_metaDataEvent != null) {
					dispatchEvent(_metaDataEvent);
				}
			}
		}
	}

	public void dispatchEvent(IEvent event) {

		if (event instanceof IRTMPEvent) {
			IRTMPEvent rtmpEvent = (IRTMPEvent) event;

			int eventTime = 0;

			IoBuffer buf = null;
			if (rtmpEvent instanceof IStreamData && (buf = ((IStreamData) rtmpEvent).getData()) != null) {
				bytesReceived += buf.limit();
			}

			if (rtmpEvent instanceof AudioData) {
				audioTime += rtmpEvent.getTimestamp();
				eventTime = audioTime;
				sendConfig();
			} else {
				eventTime = audioTime;
			}

			if (mLivePipe != null) {
				RTMPMessage msg = RTMPMessage.build(rtmpEvent);
				msg.getBody().setTimestamp(eventTime);
				try {
					mLivePipe.pushMessage(msg);
				} catch (Exception e) {
					//log.info("dispatchEvent {}, error: {}", event, e);
				}
			}
			// Notify listeners about received packet
			if (rtmpEvent instanceof IStreamPacket) {
				for (IStreamListener listener : getStreamListeners()) {
					try {
						listener.packetReceived(this, (IStreamPacket) rtmpEvent);
					} catch (Exception e) {
						//log.info("Error while notifying listener {}", listener, e);
					}
				}
			}
		}
	}

	@Override
	public int getActiveSubscribers() {
		return subscriberStats.getCurrent();
	}

	@Override
	public long getBytesReceived() {
		return bytesReceived;
	}

	@Override
	public int getMaxSubscribers() {
		return subscriberStats.getMax();
	}

	@Override
	public int getTotalSubscribers() {
		return subscriberStats.getTotal();
	}

	@Override
	public int getCurrentTimestamp() {
		return audioTime;
	}

	@Override
	public long getCreationTime() {
		return creationTime;
	}

	@Override
	public void reset() {
		newComsumers.addAll(mLivePipe.getConsumers());
	}

	public void setMetaDataEvent(IRTMPEvent event) {
		_metaDataEvent = event;
	}

	@Override
	public Notify getMetaData() {
		// TODO Auto-generated method stub
		return null;
	}

}
