package org.red5.stream.http.servlet;

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
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.red5.logging.Red5LoggerFactory;
import org.red5.service.httpstream.SegmenterService;
import org.red5.service.httpstream.model.Segment;
import org.slf4j.Logger;
import org.springframework.context.ApplicationContext;
import org.springframework.web.context.WebApplicationContext;

import com.xuggle.xuggler.ICodec;
import com.xuggle.xuggler.SimpleMediaFile;

/**
 * Provides an http stream playlist in m3u8 format.
 * 
 * HTML status codes used by this servlet:
 * <pre>
 *  400 Bad Request
 *  406 Not Acceptable
 *  412 Precondition Failed
 *  417 Expectation Failed
 * </pre>
 * 
 * @see
 * {@link http://tools.ietf.org/html/draft-pantos-http-live-streaming-03}
 * {@link http://developer.apple.com/iphone/library/documentation/NetworkingInternet/Conceptual/StreamingMediaGuide/HTTPStreamingArchitecture/HTTPStreamingArchitecture.html#//apple_ref/doc/uid/TP40008332-CH101-SW2}
 * 
 * @author Paul Gregoire
 */
public class PlayList extends HttpServlet {
	
	private static final long serialVersionUID = 978137411L;

	private static Logger log = Red5LoggerFactory.getLogger(PlayList.class);

	// create streams upon http request
	private boolean startStreamOnRequest = false;
		
	// number of segments that must exist before displaying any in the playlist
	private int minimumSegmentCount = 1;
	
	// keep track of the streams that have been requested, but that are not available
	private static volatile List<String> requestedStreams = new ArrayList<String>(3);
	
	@Override
	public void init(ServletConfig config) throws ServletException {
		super.init(config);
		/*
		 * To supply a parameter to a servlet, use a config or init param.
		 * The config-param looks like this:
		 * <pre>
		 *     <context-param>
		 *     <param-name>startStreamOnRequest</param-name>
		 *     <param-value>true</param-value>
		 *     </context-param>
		 * </pre>
		 * 
		 * And is accessed like so:
		 * config.getServletContext().getInitParameter("startStreamOnRequest")
		 * 
		 * An init-param looks like this:
		 * <pre>
		 *     <servlet>
		 *     <servlet-name>PlayList</servlet-name>
		 *     <servlet-class>org.red5.stream.http.servlet.PlayList</servlet-class>
		 *     <init-param>
		 *     <param-name>startStreamOnRequest</param-name>
		 *     <param-value>true</param-value>
		 *     </init-param>
		 *     </servlet>
		 * </pre>
		 * 
		 * And is accessed like so:
		 * getInitParameter("startStreamOnRequest");
		 * 
		 */
		String startOnRequestParam = getInitParameter("startStreamOnRequest");
		if (!StringUtils.isEmpty(startOnRequestParam)) {
			if ("true".equals(startOnRequestParam.trim())) {
				startStreamOnRequest = true;
			}
		}
		log.debug("Start stream on request - param: {} value: {}", startOnRequestParam, startStreamOnRequest);
		String minimumSegmentCountParam = getInitParameter("minimumSegmentCount");
		if (!StringUtils.isEmpty(minimumSegmentCountParam)) {
			minimumSegmentCount = Integer.valueOf(minimumSegmentCountParam);
		}
		log.debug("Minimum segment count - param: {} value: {}", minimumSegmentCountParam, minimumSegmentCount);
	}

	/**
	 * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		doPost(request, response);
	}

	/**
	 * @see HttpServlet#doPost(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		log.debug("Playlist requested");
		/*
		 * EXT-X-MEDIA-SEQUENCE
		 * Each media file URI in a Playlist has a unique sequence number.  The sequence number 
		 * of a URI is equal to the sequence number of the URI that preceded it plus one. The 
		 * EXT-X-MEDIA-SEQUENCE tag indicates the sequence number of the first URI that appears
		 * in a Playlist file.
		 * 
		 	#EXTM3U
		 	#EXT-X-ALLOW-CACHE:NO
			#EXT-X-MEDIA-SEQUENCE:0
			#EXT-X-TARGETDURATION:10
			#EXTINF:10,
			http://media.example.com/segment1.ts
			#EXTINF:10,
			http://media.example.com/segment2.ts
			#EXTINF:10,
			http://media.example.com/segment3.ts
			#EXT-X-ENDLIST
			
			Using one large file, testing with ipod touch, this worked (149 == 2:29)
			#EXTM3U
			#EXT-X-TARGETDURATION:149
			#EXT-X-MEDIA-SEQUENCE:0
			#EXTINF:149, no desc
			out0.ts
			#EXT-X-ENDLIST
			
			Using these encoding parameters:
			ffmpeg -i test.mp4 -re -an -vcodec libx264 -b 96k -flags +loop -cmp +chroma -partitions +parti4x4+partp8x8+partb8x8 
			-subq 5 -trellis 1 -refs 1 -coder 0 -me_range 16 -keyint_min 25 -sc_threshold 40 -i_qfactor 0.71 -bt 200k -maxrate 96k 
			-bufsize 96k -rc_eq 'blurCplx^(1-qComp)' -qcomp 0.6 -qmin 10 -qmax 51 -qdiff 4 -level 30 -aspect 320:240 -g 30 -async 2 
			-s 320x240 -f mpegts out.ts

		 */
				
		//get the requested stream
		
		ApplicationContext appCtx = (ApplicationContext) getServletContext().getAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE);
		SegmenterService service = (SegmenterService) appCtx.getBean("segmenter.service");
		
		String servletPath = request.getServletPath();

		if (log.isTraceEnabled()) {
			String serverName = request.getServerName();
			int portNumber = request.getServerPort();
			String contextPath = request.getContextPath();
    		log.trace("Request - serverName: {} port: {} contextPath: {} servletPath: {}", new Object[]{serverName, portNumber, contextPath, servletPath});
		}
		
		//for debugging
		//String url = String.format("%s:%s%s", serverName, portNumber, contextPath);;
		final String streamName = servletPath.substring(1, servletPath.indexOf(".m3u8"));
		log.debug("Request for stream: {} playlist", streamName);

		//write the playlist
        PrintWriter writer = response.getWriter();

        response.setContentType("application/x-mpegURL");
        
        writer.println("#EXTM3U\n#EXT-X-ALLOW-CACHE:NO\n");
		
		//check for the stream
		if (service.isAvailable(streamName)) {		
			log.debug("Stream: {} is available", streamName);
			// remove it from requested list if its there
			if (requestedStreams.contains(streamName)) {
				requestedStreams.remove(requestedStreams.indexOf(streamName));
			}			    		
    		// get the segment count
    		int count = service.getSegmentCount(streamName);
    		log.debug("Segment count: {}", count);
    		// check for minimum segment count and if we dont match or exceed
    		// wait for (minimum segment count * segment duration) before returning
    		if (count < minimumSegmentCount) {
    			log.debug("Starting wait loop for segment availability");
        		long maxWaitTime = minimumSegmentCount * service.getSegmentTimeLimit();
        		long start = System.currentTimeMillis();
        		do {
        			try {
						Thread.sleep(500);
					} catch (InterruptedException e) {
					}
					if ((System.currentTimeMillis() - start) >= maxWaitTime) {
						log.info("Maximum segment wait time exceeded for {}", streamName);
						break;
					}
        		} while ((count = service.getSegmentCount(streamName)) < minimumSegmentCount);
    		}
    		// get the count one last time
    		count = service.getSegmentCount(streamName);
    		log.debug("Segment count: {}", count);    		
    		if (count >= minimumSegmentCount) {
        		//get segment duration in seconds
        		long segmentDuration = service.getSegmentTimeLimit() / 1000;  	    		
        		//get the current segment
        		Segment segment = service.getSegment(streamName);
        		//get current sequence number
        		int sequenceNumber = segment.getIndex();	
        		log.trace("Current sequence number: {}", sequenceNumber);
        		/*
        		HTTP streaming spec section 3.2.2
        		Each media file URI in a Playlist has a unique sequence number.  The sequence number of a URI is equal to the sequence number
        		of the URI that preceded it plus one. The EXT-X-MEDIA-SEQUENCE tag indicates the sequence number of the first URI that appears 
        		in a Playlist file.
        		*/     		
        		// determine the lowest sequence number
                int lowestSequenceNumber = Math.max(-1, sequenceNumber - service.getMaxSegmentsPerFacade()) + 1;
                log.trace("Lowest sequence number: {}", lowestSequenceNumber);
                // for logging
                StringBuilder sb = new StringBuilder();
        		// create the heading
                String playListHeading = String.format("#EXT-X-TARGETDURATION:%s\n#EXT-X-MEDIA-SEQUENCE:%s\n", segmentDuration, lowestSequenceNumber);   		
                writer.println(playListHeading);
                sb.append(playListHeading);
                //loop through the x closest segments
            	for (int s = lowestSequenceNumber; s <= sequenceNumber; s++) {
            		String playListEntry = String.format("#EXTINF:%s, no desc\n%s%s.ts\n", segmentDuration, streamName, s);   		
                    writer.println(playListEntry);
                    sb.append(playListEntry);            	            		
            	}
            	// are we on the last segment?
                if (segment.isLast()) {
                	log.debug("Last segment");
                	writer.println("#EXT-X-ENDLIST\n");
                	sb.append("#EXT-X-ENDLIST\n");
                }	
            	log.debug(sb.toString());
    		} else {
    			log.trace("Minimum segment count not yet reached, currently at: {}", count);
    		}    
		} else {
			log.debug("Stream: {} is not available", streamName);
			// look for flag to indicate that we should spawn the requested stream
			if (startStreamOnRequest) {
				if (requestedStreams.contains(streamName)) {
					log.debug("Stream has already been requested and is not yet available: {}", streamName);					
				} else {
					// add to the requested list
    				requestedStreams.add(streamName);    				
    				// perform the actions required for starting up a stream
    				log.debug("A stream that is not yet available will be spawned");    				
    				// TODO create a SimpleMediaFile that represents our stream   				
    				SimpleMediaFile smf = new SimpleMediaFile();
    				smf.setHasAudio(false);
    				smf.setHasVideo(true);
    				smf.setVideoCodec(ICodec.ID.CODEC_ID_H264);
    				// TODO if on-demand creation is wanted in your application, the Observed class
    				// must be instanced here and the SegmenterService must be added as an Observer of the class
    				
    				// TODO create a thread to clean up if the stream is not created within x time
    				/*
    				// start a thread to remove the requested stream name from the list after 2 minutes   				
    				Application.executorService.execute(new Runnable() {
    					public void run() {
    						try {
								Thread.sleep(120000);
							} catch (InterruptedException e) {
							}
    						if (requestedStreams.contains(streamName)) {
    							requestedStreams.remove(requestedStreams.indexOf(streamName));
    						}	
    					}
    				});
    				*/    				
				}
			} else {
				writer.println("#EXT-X-ENDLIST\n");			
			}
		}

		writer.flush();
		
	}

}
