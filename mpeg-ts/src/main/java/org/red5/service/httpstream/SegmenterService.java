package org.red5.service.httpstream;

/*
 * RED5 Open Source Flash Server - http://www.osflash.org/red5
 * 
 * Copyright (c) 2006-2008 by respective authors (see below). All rights reserved.
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
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Observable;
import java.util.Observer;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.red5.logging.Red5LoggerFactory;
import org.red5.service.httpstream.model.MediaFrame;
import org.red5.service.httpstream.model.Segment;
import org.red5.service.httpstream.model.VideoFrame;
import org.red5.stream.http.xuggler.MpegTsHandlerFactory;
import org.red5.stream.http.xuggler.MpegTsIoHandler;
import org.red5.xuggler.Message;
import org.slf4j.Logger;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

import com.xuggle.xuggler.Configuration;
import com.xuggle.xuggler.ICodec;
import com.xuggle.xuggler.IContainer;
import com.xuggle.xuggler.IContainerFormat;
import com.xuggle.xuggler.IPacket;
import com.xuggle.xuggler.IPixelFormat;
import com.xuggle.xuggler.IRational;
import com.xuggle.xuggler.ISimpleMediaFile;
import com.xuggle.xuggler.IStream;
import com.xuggle.xuggler.IStreamCoder;
import com.xuggle.xuggler.IVideoPicture;
import com.xuggle.xuggler.SimpleMediaFile;

/**
 * Creates, updates, locates, and manages media segments.
 * 
 * @author Paul Gregoire
 */
public class SegmenterService implements InitializingBean, DisposableBean, Observer {

	private static Logger log = Red5LoggerFactory.getLogger(SegmenterService.class);

	// map of currently available (in-memory) segments, keyed by stream name
	private static ConcurrentMap<String, SegmentFacade> segmentMap = new ConcurrentHashMap<String, SegmentFacade>();

	// queue to hold the items from the observables
	private static ConcurrentLinkedQueue<QueuedItem> queue = new ConcurrentLinkedQueue<QueuedItem>();

	// 
	private volatile boolean shutdown;

	// length of a segment in milliseconds
	private long segmentTimeLimit = 2000; //default = 2 seconds

	// where to write segment files
	private String segmentDirectory;
	
	// whether to use files or memory for segments
	private boolean memoryMapped;
	
	// time to sleep between queue checks
	private long queueSleepTime = 250L;
	
	// maximum number of segments to keep available per stream
	private int maxSegmentsPerFacade = 3;
	
	public long getSegmentTimeLimit() {
		return segmentTimeLimit;
	}

	public void setSegmentTimeLimit(long segmentTimeLimit) {
		this.segmentTimeLimit = segmentTimeLimit;
	}

	public String getSegmentDirectory() {
		return segmentDirectory;
	}

	public void setSegmentDirectory(String segmentDirectory) {
		this.segmentDirectory = segmentDirectory;
	}

	public boolean isMemoryMapped() {
		return memoryMapped;
	}

	public void setMemoryMapped(boolean memoryMapped) {
		this.memoryMapped = memoryMapped;
	}

	public long getQueueSleepTime() {
		return queueSleepTime;
	}

	public void setQueueSleepTime(long queueSleepTime) {
		this.queueSleepTime = queueSleepTime;
	}

	public int getMaxSegmentsPerFacade() {
		return maxSegmentsPerFacade;
	}

	public void setMaxSegmentsPerFacade(int maxSegmentsPerFacade) {
		this.maxSegmentsPerFacade = maxSegmentsPerFacade;
	}

	public int getSegmentCount(String streamName) {
    	SegmentFacade facade = segmentMap.get(streamName);
    	return facade.getSegmentCount();
    }	
	
	public Segment getSegment(String streamName) {
		SegmentFacade facade = segmentMap.get(streamName);
		return facade.getSegment();
	}
	
	public Segment getSegment(String streamName, int index) {
    	SegmentFacade facade = segmentMap.get(streamName);
    	return facade.getSegment(index);
    }	
	
	public boolean isAvailable(String streamName) {
		return segmentMap.containsKey(streamName);
	}

	public void afterPropertiesSet() throws Exception {
		//create our runnable and submit to an executor
		QueueWorker worker = new QueueWorker();
		//Application.executorService.execute(worker);
		// this worker should check the queue at intervals and route messages
	}

	public void destroy() throws Exception {
		shutdown = true;
	}

	/**
	 * Receives a media frame and queues it for later processing by a
	 * worker thread.
	 * 
	 * @param observed provider of media frames, usually a ReStreamer
	 * @param frame a media frame
	 */
	public void update(Observable observed, Object frame) {
		//log.trace("Update from {} of {}", observed, frame);
		if (frame != null) {
    		if (frame instanceof MediaFrame) {
    			//the stream name must be supplied, a good place to pull this from is the
    			//observed class
    			String streamName = "mpegtsstream"; //((MyObservableClass) observed).getStreamName();
    			//queue the data
    			queue.add(new QueuedItem(streamName, (MediaFrame) frame));
    		} else {
    			log.warn("Unknown media frame received: {}", frame);
    		}
		} else {
			//if we get a null frame, that indicates that the source is done
			//sending data to us
			String streamName = "mpegtsstream"; //((MyObservableClass) observed).getStreamName();
			SegmentFacade facade = segmentMap.remove(streamName);
			if (facade != null) {
				Segment segment = facade.getSegment();
				segment.setLast(true);
				segment.close();
			} else {
				log.warn("Segment facade not found for {}", streamName);
			}
		}
	}
	
	/**
	 * Common location for segment related objects.
	 */
	private class SegmentFacade {
		
		// map of currently available segments
		ConcurrentLinkedQueue<Segment> segments = new ConcurrentLinkedQueue<Segment>();
		
		// segment currently being written to
		volatile Segment segment;
		
		// segment index counter
		AtomicInteger counter = new AtomicInteger();
		
		final String streamName;
		
		MpegTsIoHandler outputHandler;
		
		IContainer contOutp;
		
		IStream strOutp;
		
		IStreamCoder strmCoderOutp;
		
		SegmentFacade(String streamName, MediaFrame frame) {
			this.streamName = streamName;
			// setup output
			setupOutput(frame);
		}
		
		public int getSegmentCount() {
			return segments.size();
		}

		/**
		 * Set up the output using the segment and the first video frame.
		 * 
		 * @param segment
		 * @param frame
		 */
		private void setupOutput(MediaFrame frame) {
			log.debug("setupOutput - name: {} frame: {}", streamName, frame);

			VideoFrame videoFrame = (VideoFrame) frame;

			ISimpleMediaFile outputStreamInfo = new SimpleMediaFile();
			outputStreamInfo.setHasAudio(false);
			outputStreamInfo.setHasVideo(true);
			outputStreamInfo.setVideoWidth(videoFrame.getWidth());
			outputStreamInfo.setVideoHeight(videoFrame.getHeight());
			outputStreamInfo.setVideoBitRate(200000);
			outputStreamInfo.setVideoCodec(ICodec.ID.CODEC_ID_H264);
			outputStreamInfo.setVideoGlobalQuality(0);
			
			// output to a custom handler
			String urlOutp = MpegTsHandlerFactory.DEFAULT_PROTOCOL + ':' + streamName;
			outputStreamInfo.setURL(urlOutp);

			//setup our rtmp io handler
			outputHandler = new MpegTsIoHandler(streamName) {
				
				// store the latest PAT data
				ByteBuffer patData = null;
				
				@Override
				public void write(Message message) throws InterruptedException {
					ByteBuffer data = message.getData();
					if (data != null) {
						log.trace("[{}] Writing message to segment: {}", streamName, data.toString());
						//keep track of the latest PAT data
						if (message.getType() == Message.Type.CONFIG_PAT) {
							data.mark();
							byte[] copy = new byte[data.limit()];
							data.get(copy);
							patData = ByteBuffer.wrap(copy);
							data.reset();
						}
						// indicates whether or not a new segment was created for this iteration
						boolean newSegment = false;
						// check active segment
						if (segment == null) {
							log.debug("Segment not found for {}, creating new instance", streamName);
							// create a segment - default is memory mapped
							segment = new Segment(segmentDirectory, streamName, counter.getAndIncrement(), memoryMapped);
							// add to the map for lookup
							segments.add(segment);
							// flag that we created a new segment
							newSegment = true;						
						} else {
							//see if we need a new segment
							if (System.currentTimeMillis() - segment.getCreated() >= segmentTimeLimit) {
								log.debug("Starting a new segment for {} (time limit reached {} ms)", streamName,
										segmentTimeLimit);
								// close active segment
								if (!segment.close()) {
									log.info("[{}] Problem closing segment, index: {}", streamName, segment.getIndex());
								}
								// create a segment
								segment = new Segment(segmentDirectory, streamName, counter.getAndIncrement(), memoryMapped);
								// add the new segment
								segments.add(segment);
								// flag that we created a new segment
								newSegment = true;
							}
						}						
						//write the data to the segment				
						if (newSegment) {
    						// write the PAT
    						if (patData != null) {
    							if (!segment.write(patData)) {
    								log.warn("[{}] Write of PAT to segment failed", streamName);
    							}
    						} else {
    							log.debug("[{}] PAT data was null for new segment", streamName);
    						}
    						// enforce segment list length
    						if (segments.size() > maxSegmentsPerFacade) {
    							//get current segments index minux max
    							int index = segment.getIndex() - maxSegmentsPerFacade;
    							for (Segment seg : segments) {
    								if (seg.getIndex() <= index) {
    									segments.remove(seg);
    	    							// access to the segment is no longer required
    									seg.dispose();
    								}
    							}
    							
    						}
						}
						// prevent writing the PAT twice
						if (message.getType() != Message.Type.CONFIG_PAT && newSegment) {
    						if (!segment.write(data)) {
    							log.warn("[{}] Write to segment failed", streamName);
    						}
						} else {
    						if (!segment.write(data)) {
    							log.warn("[{}] Write to segment failed", streamName);
    						}							
						}
					} else if (message.getType() == Message.Type.END_STREAM) {
						if (segment != null) {
							segment.setLast(true);
							segment.close();
						}
					}
				}
			};
			MpegTsHandlerFactory.getFactory().registerStream(outputHandler, outputStreamInfo);

			IContainerFormat contOutpFormat = IContainerFormat.make();
			contOutpFormat.setOutputFormat("mpegts", urlOutp, null);
			if (log.isTraceEnabled()) {
				log.trace("Codecs for mpeg-ts output type");
				for (ICodec.ID id : contOutpFormat.getOutputCodecsSupported()) {
					log.trace("{}", id);
				}
				//log.trace("Container supports H264: {}", contOutpFormat.isCodecSupportedForOutput(ICodec.ID.CODEC_ID_H264));
				//contOutpFormat.establishOutputCodecId(ICodec.ID.CODEC_ID_H264);
				//log.trace("Container supports H264: {}", contOutpFormat.isCodecSupportedForOutput(ICodec.ID.CODEC_ID_H264));
			}		

			log.debug("Stream to output: {}", urlOutp);

			// Open the output container for writing.
			contOutp = IContainer.make();
			int retVal = contOutp.open(urlOutp, IContainer.Type.WRITE, contOutpFormat);
			if (retVal < 0) {
				throw new RuntimeException("Could not open output container at URL: " + urlOutp);
			}

			// We're only trying to output one video stream, so create it in the
			// container
			strOutp = contOutp.addNewStream(0);
			strmCoderOutp = strOutp.getStreamCoder();

			// Setup output stream coder now. After demuxing and decoding, we encode
			// video data into MPEG-TS format.
			strmCoderOutp.setCodec(ICodec.ID.CODEC_ID_H264);
			                                                                                 	
			try {
				InputStream in = SegmenterService.class.getResourceAsStream("mpegts-ipod320.properties");
				Properties props = new Properties();
				props.load(in);
				log.trace("partitions: {}", props.get("partitions"));
				log.trace("flags: {}", props.get("flags"));
				int retval = Configuration.configure(props, strmCoderOutp);
				if (retval < 0) {
				   throw new RuntimeException("Could not configure coder from preset file");
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
			
			int origWidth = outputStreamInfo.getVideoWidth();
			int origHeight = outputStreamInfo.getVideoHeight();
			if (origWidth <= 0 || origHeight <= 0) {
				throw new RuntimeException("Couldn't find width or height in original video stream: " + streamName);
			}
			
			strmCoderOutp.setBitRate(outputStreamInfo.getVideoBitRate());
			strmCoderOutp.setBitRateTolerance(outputStreamInfo.getVideoBitRate() / 8);
			strmCoderOutp.setProperty("level", 30);
			strmCoderOutp.setProperty("async", 2);		
			//strmCoderOutp.setProperty("re", true);		
			strmCoderOutp.setProperty("an", true);		
			
			strmCoderOutp.setWidth(origWidth);
			strmCoderOutp.setHeight(origHeight);
			strmCoderOutp.setPixelType(IPixelFormat.Type.YUV420P);
			strmCoderOutp.setGlobalQuality(outputStreamInfo.getVideoGlobalQuality());
			strmCoderOutp.setNumPicturesInGroupOfPictures(3);
			strmCoderOutp.setFlag(IStreamCoder.Flags.FLAG_QSCALE, true);

			//dynamic FPS - http://wiki.xuggle.com/Concepts
			//IRational frameRate = IRational.make(videoFrame.getFps(), 1000);
			IRational frameRate = IRational.make(10, 1);
			strmCoderOutp.setFrameRate(frameRate);
			// use the inverse of the framerate
			strmCoderOutp.setTimeBase(IRational.make(frameRate.getDenominator(), frameRate.getNumerator()));
			log.debug("[{}] Output rational values - num: {} denom: {}", new Object[] { streamName, frameRate.getNumerator(),
					frameRate.getDenominator() });
			frameRate = null;

			if (log.isTraceEnabled()) {
    			log.trace("=== Output coder configured, info: ===");
    			log.trace("Type: {}", strmCoderOutp.getCodecType());
    			log.trace("Codec: {}", strmCoderOutp.getCodecID());
    			log.trace("Stream timebase: {} / {}", strOutp.getTimeBase().getNumerator(), strOutp.getTimeBase()
    					.getDenominator());
    			log.trace("Coder timebase: {} / {}", strmCoderOutp.getTimeBase().getNumerator(), strmCoderOutp.getTimeBase()
    					.getDenominator());
    			log.trace("Video width: {}", strmCoderOutp.getWidth());
    			log.trace("Video height: {}", strmCoderOutp.getHeight());
    			log.trace("Video pixel type: {}", strmCoderOutp.getPixelType());
    			log.trace("Frame rate: {}", strmCoderOutp.getFrameRate().getDouble());
    			log.trace("=== END OF STREAM INFO ===");
			}
			
			retVal = strmCoderOutp.open();
			if (retVal < 0) {
				throw new RuntimeException("Couldn't open output encoder for stream: " + streamName);
			}

			retVal = contOutp.writeHeader();
			if (retVal < 0) {
				throw new RuntimeException("Couldn't write output header: " + streamName);
			}
			
			log.debug("setupOutput - name: {} finished", streamName);
		}

		/**
		 * Returns the active segment.
		 * 
		 * @return segment currently being written to
		 */
		public Segment getSegment() {
			return segment;
		}

		/**
		 * Returns a segment matching the requested index.
		 * 
		 * @return segment matching the index or null
		 */
		public Segment getSegment(int index) {
			Segment result = null;
			for (Segment seg : segments) {
				if (seg.getIndex() == index) {
					result = seg;
					break;
				}
			}
			return result;
		}
		
		public void encode(MediaFrame frame) {			
			IPacket packet = IPacket.make();
			if (frame instanceof VideoFrame) {
				VideoFrame videoFrame = (VideoFrame) frame;
				ByteBuffer buf = videoFrame.getData();
				byte[] data = new byte[buf.limit()];
				buf.get(data);
				IVideoPicture picture = IVideoPicture.make(IPixelFormat.Type.YUV420P, videoFrame.getWidth(), videoFrame.getHeight());				
				picture.put(data, 0, 0, data.length);
				picture.setComplete(true, IPixelFormat.Type.YUV420P, videoFrame.getWidth(), videoFrame.getHeight(), videoFrame.getTimestamp());
				strmCoderOutp.encodeVideo(packet, picture, 0);
			//} else if (frame instanceof AudioFrame) {
				//strmCoderOutp.encodeAudio(packet, audioSamples, sampleToStartFrom)
			}
			contOutp.writePacket(packet);
		}		
		
	}

	/**
	 * Stores the data from the observable until it is routed to a segment.
	 */
	private class QueuedItem {

		private String streamName;

		private MediaFrame frame;

		public QueuedItem(String streamName, MediaFrame frame) {
			this.streamName = streamName;
			this.frame = frame;
		}

		public String getStreamName() {
			return streamName;
		}

		public MediaFrame getFrame() {
			return frame;
		}

	}

	/**
	 * Routes the queued data to the segments.
	 */
	private class QueueWorker implements Runnable {

		String lastLogMessage = null;

		@Override
		public void run() {
			do {
				if (log.isTraceEnabled()) {
    				String logMessage = String.format("Queue worker running.. queue size: %s", queue.size());
    				if (!logMessage.equals(lastLogMessage)) {
    					log.trace(logMessage);
    				}
    				lastLogMessage = logMessage;
				}
				if (!queue.isEmpty()) {
					List<QueuedItem> processedItems = new ArrayList<QueuedItem>();
					for (QueuedItem item : queue) {
						String streamName = item.getStreamName();
						MediaFrame frame = item.getFrame();
						// lookup the associated segment
						SegmentFacade facade = segmentMap.get(streamName);
						if (facade == null) {
							log.debug("Segment facade not found for {}, creating new instance", streamName);
							//create a facade
							facade = new SegmentFacade(streamName, frame);
							segmentMap.put(streamName, facade);
						}
						//encode and add the frame to the segment
						facade.encode(frame);
						//add to removal list
						processedItems.add(item);
					}
					//clean out the queue
					queue.removeAll(processedItems);
				} else {
					try {
						Thread.sleep(queueSleepTime);
					} catch (InterruptedException e) {
						log.error("", e);
					}
				}
			} while (!shutdown);
		}
	}

}
