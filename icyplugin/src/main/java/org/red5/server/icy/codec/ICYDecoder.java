package org.red5.server.icy.codec;

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

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.CumulativeProtocolDecoder;
import org.apache.mina.filter.codec.ProtocolDecoderOutput;
import org.red5.server.icy.BitStream;
import org.red5.server.icy.ICYStreamUtil;
import org.red5.server.icy.message.AACFrame;
import org.red5.server.icy.message.Frame;
import org.red5.server.icy.message.MP3Frame;
import org.red5.server.icy.message.NSVFrame;
import org.red5.server.icy.nsv.NSVStreamConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Decoder for data coming from a source.
 * 
 * @author Paul Gregoire (mondain@gmail.com)
 * @author Andy Shaules (bowljoman@hotmail.com)
 */
public class ICYDecoder extends CumulativeProtocolDecoder {

	/**
	 * State enumerator that indicates the reached state in the message
	 * decoding process.
	 */
	public enum ReadState {
		/** Unrecoverable error occurred */
		Failed,
		/** Trying to resync */
		Sync,
		/** Waiting for a command */
		Ready,
		/** Reading interleaved packet */
		Packet,
		/** Not validated (password not yet checked) */
		Notvalidated,
		/** Reading headers */
		Header
	}

	private static Logger log = LoggerFactory.getLogger(ICYDecoder.class);

	private static final byte[] OK_MESSAGE = "OK2\r\nicy-caps:11\r\n\r\n".getBytes();
	
	private static final byte[] BAD_PASSWD_MESSAGE = "invalid password\r\n".getBytes();
	
	private static final Pattern PATTERN_CRLF = Pattern.compile("[\\r|\\n|\u0085|\u2028]{1,2}");

	private static final Pattern PATTERN_HEADER = Pattern.compile("(icy-|content-).{1,}[:]{1}.{1,}", Pattern.DOTALL);
	
	private ThreadLocal<Frame> frameLocal = new ThreadLocal<Frame>();
	
	@Override
	protected boolean doDecode(IoSession session, IoBuffer in, ProtocolDecoderOutput out) throws Exception {
		String hex = in.getHexDump();
		log.debug("doDecode dump: {}", hex);
		
		boolean result = false;
		
		//check state
		ReadState state = (ReadState) session.getAttribute("state");
		if (state == null) {
			state = ReadState.Notvalidated;
		}

		//collect the current incoming bytes
		IoBuffer curBuffer = IoBuffer.wrap(in.buf());

		//index of the line-feed in the byte stream
		int lfIndex = -1;
		
		//get any previously read / unused bytes and put them in the front
		byte[] prevBuffer = (byte[]) session.removeAttribute("prev");
		if (prevBuffer != null) {
			//wrap previous bytes
			IoBuffer tmp = IoBuffer.wrap(prevBuffer);
			tmp.setAutoExpand(true);
			//jump to the end
			tmp.position(tmp.limit());
			//add current buffer to the end
			tmp.put(curBuffer);
			//flip
			tmp.flip();
			//free the current buffer we are replacing
			curBuffer.free();
			//replace with our new buffer
			curBuffer = tmp;
			log.debug("Current buffer dump (prep): {}", curBuffer.getHexDump());
		}
		
		//most common action should live at the top of the switch!
		switch (state) {
			case Packet:
				//do normal media packet handling here
				log.trace("Hex (pkt): {}", curBuffer.getHexDump());
				//we need at least 7 bytes to build a frame
				if (curBuffer.remaining() >= 7) { 
    				byte[] prefix = new byte[2];
    				curBuffer.get(prefix);
    				//log.trace("Prefix: {}", new String(prefix));
    				//looking for 0xef, 0xbe bytes
    				if (prefix[0] == (byte) 0xef && prefix[1] == (byte) 0xbe) {
    					int position = curBuffer.position();
    					//read data for the stream
    					if (chnkFrame(session, curBuffer)) {
    						Frame frame = frameLocal.get();
    						if (frame != null) {
    	    					//write frame as decoder output
    	    					out.write(frame);
    	    					//clear thread local
    	    					frameLocal.remove();
    						}						
    					} else {
    						//rewind back to original position + prefix length
    						log.trace("Frame creation failed");
    						curBuffer.position(position - 2);						
    					}
    				} else if (prefix[0] == (byte) 0xff && prefix[1] == (byte) 0xfb) { 
    					//look for MP3 sync FF FB
    					log.debug("MP3 sync frame found");
    					int position = curBuffer.position();
    					//read all the configuration info for the stream
    					if (syncFrameMP3(session, curBuffer)) {
    						Frame frame = frameLocal.get();
    						if (frame != null) {
    	    					//write frame as decoder output
    	    					out.write(frame);
    	    					//clear thread local
    	    					frameLocal.remove();
    						} else {
    							//let the handler know it should handle the "ready" stuff
    							out.write(Boolean.TRUE);
    						}
    					} else {
    						//rewind back to original position + sync dword length
    						log.trace("MP3 Sync frame creation failed");
    						curBuffer.position(position - 2);						
    					}						
    				} else if (prefix[0] == (byte) 0xff && prefix[1] == (byte) 0xf9) { 
    					//look for AAC sync FF F9 5C 80 or FF F9 50 40
    					log.debug("AAC sync frame found");
    					int position = curBuffer.position();
    					//read all the configuration info for the stream
    					if (syncFrameAAC(session, curBuffer)) {
    						Frame frame = frameLocal.get();
    						if (frame != null) {
    	    					//write frame as decoder output
    	    					out.write(frame);
    	    					//clear thread local
    	    					frameLocal.remove();
    						} else {
    							//let the handler know it should handle the "ready" stuff
    							out.write(Boolean.TRUE);
    						}
    					} else {
    						//rewind back to original position + sync dword length
    						log.trace("AAC Sync frame creation failed");
    						curBuffer.position(position - 2);						
    					}							
    				} else {
    					//rewind it back 2
    					log.trace("Data frame prefix not found - 0: {} 1: {}", prefix[0], prefix[1]);
    					curBuffer.position(curBuffer.position() - 2);
    					
        				//look for sync frame
        				byte[] sync = new byte[4];
        				curBuffer.get(sync);
        				log.trace("Sync: {}", new String(sync));
        				int nsvSync = (sync[0] | (sync[1] << 8) | (sync[2] << 16) | (sync[3] << 24));
        				//we need at least 20 bytes
        				if (curBuffer.remaining() >= 20 && nsvSync == ICYStreamUtil.NSV_SYNC_DWORD) {
        					int position = curBuffer.position();
        					//read all the configuration info for the stream
        					if (syncFrame(session, curBuffer)) {
        						Frame frame = frameLocal.get();
        						if (frame != null) {
        	    					//write frame as decoder output
        	    					out.write(frame);
        	    					//clear thread local
        	    					frameLocal.remove();
        						} else {
        							//let the handler know it should handle the "ready" stuff
        							out.write(Boolean.TRUE);
        						}						
        					} else {
        						//rewind back to original position + sync dword length
        						log.trace("Sync frame creation failed");
        						curBuffer.position(position - 4);						
        					}
        				} else {
        					//rewind it back 4 
        					log.trace("Not enough data available for sync frame or wrong frame type");
        					curBuffer.position(curBuffer.position() - 4);
        				}
        				
    				}    				
				} else {
					log.trace("Not enough data available for framing");
				}
				break;			
			case Ready:
				//look for NSV prefix
				log.trace("Hex (rdy): {}", curBuffer.getHexDump());
				
				//do nsv handling, magic bytes = 4E 53 56 73

				//consume bytes
				byte[] sync = new byte[4];
				curBuffer.get(sync);
				log.trace("Sync: {}", new String(sync));
				int nsvSync = (sync[0] | (sync[1] << 8) | (sync[2] << 16) | (sync[3] << 24));
				log.trace("Sync: {} Dword: {}", nsvSync, ICYStreamUtil.NSV_SYNC_DWORD);
				//we need at least 20 bytes
				if (curBuffer.remaining() >= 20 && nsvSync == ICYStreamUtil.NSV_SYNC_DWORD) {
					int position = curBuffer.position();
					//read all the configuration info for the stream
					if (syncFrame(session, curBuffer)) {
						Frame frame = frameLocal.get();
						if (frame != null) {
	    					//write frame as decoder output
	    					out.write(frame);
	    					//clear thread local
	    					frameLocal.remove();
						} else {
							//let the handler know it should handle the "ready" stuff
							out.write(Boolean.TRUE);
						}						
					} else {
						//rewind back to original position + sync dword length
						log.trace("Sync frame creation failed");
						curBuffer.position(position - 4);						
					}
				} else if (sync[0] == (byte) 0xef && sync[1] == (byte) 0xbe) {
					//if we get a "data" frame here fail
					log.debug("Data frame found while awaiting initial sync frame, failing out");
					state = ReadState.Failed;
				} else if (sync[0] == (byte) 0xff && sync[1] == (byte) 0xfb) { 
					//look for MP3 sync FF FB
					log.debug("MP3 sync frame found");
					int position = curBuffer.position();
					//read all the configuration info for the stream
					if (syncFrameMP3(session, curBuffer)) {
						Frame frame = frameLocal.get();
						if (frame != null) {
	    					//write frame as decoder output
	    					out.write(frame);
	    					//clear thread local
	    					frameLocal.remove();
						} else {
							//let the handler know it should handle the "ready" stuff
							out.write(Boolean.TRUE);
						}
					} else {
						//rewind back to original position + sync dword length
						log.trace("MP3 Sync frame creation failed");
						curBuffer.position(position - 4);						
					}						
				} else if (sync[0] == (byte) 0xff && sync[1] == (byte) 0xf9) { 
					//look for AAC sync FF F9 5C 80 or FF F9 50 40
					log.debug("AAC sync frame found");
					int position = curBuffer.position();
					//read all the configuration info for the stream
					if (syncFrameAAC(session, curBuffer)) {
						Frame frame = frameLocal.get();
						if (frame != null) {
	    					//write frame as decoder output
	    					out.write(frame);
	    					//clear thread local
	    					frameLocal.remove();
						} else {
							//let the handler know it should handle the "ready" stuff
							out.write(Boolean.TRUE);
						}
					} else {
						//rewind back to original position + sync dword length
						log.trace("AAC Sync frame creation failed");
						curBuffer.position(position - 4);						
					}							
				} else {
					//rewind it back 4 
					log.trace("Not enough data available for sync frame or wrong frame type");
					curBuffer.position(curBuffer.position() - 4);
				}
				
				//drop any remaining current buffer data into the session
				if (curBuffer.hasRemaining()) {
					log.debug("Had left over bytes after sync, adding to session");
					//get the buffer info
					int pos = curBuffer.position();
					int len = curBuffer.limit();
					log.trace("Current pos: {} len: {} size: {}", new Object[]{pos, len, (len - pos)});
					//consume bytes
					byte[] bf = new byte[(len - pos)];
					curBuffer.get(bf);
					//put bytes into the session
					session.setAttribute("prev", bf);
				}	
				
				break;
			case Notvalidated: 
				//need to check password
				lfIndex = curBuffer.indexOf((byte) 0x0a);
				if (lfIndex > 0) {
					//get data as a string
					byte[] buf = new byte[lfIndex + 1];
					curBuffer.get(buf);
					String msg = new String(buf, "US-ASCII");
					log.debug("Not validated, check password {}", msg);
					//pull password from session
					String password = (String) session.getAttribute("password");
					log.debug("Password from session: {}", password);
					String[] arr = null;
					try {
						arr = PATTERN_CRLF.split(msg);
						log.debug("Password data count: {}", arr.length);
					} catch (PatternSyntaxException ex) {
						log.warn("", ex);
					}
					if (password.equals(arr[0])) {
						log.debug("Passwords match!");
						state = ReadState.Header;
						out.write(OK_MESSAGE);
						//check for remaining data
						if (curBuffer.hasRemaining()) {
							log.debug("Had left over bytes, adding to session");
							//get the buffer info
							int pos = curBuffer.position();
							int len = curBuffer.limit();
							log.trace("Current pos: {} len: {} size: {}", new Object[]{pos, len, (len - pos)});
							//consume bytes
							byte[] bf = new byte[(len - pos)];
							curBuffer.get(bf);
							//put bytes into the session
							session.setAttribute("prev", bf);			
						}
					} else {
						log.info("Invalid password {}, reset and close", arr[0]);
						state = ReadState.Failed;
						out.write(BAD_PASSWD_MESSAGE);
					}			
					//
					result = true;
				}
				break;
			case Header:
				//max bytes to read when looking for LF
				int maxSearchBytes = 128;
				//search count
				int searched = 0;
				//buffer for storing bytes which may be headers
				IoBuffer headerBuf = IoBuffer.allocate(maxSearchBytes);
				//nio bytebuffer seems to work better for locating LF
				ByteBuffer byteBuffer = curBuffer.buf();
				while (true) {
					//read a byte
					byte b = byteBuffer.get();
					searched++;
					log.trace("Byte: {} searched: {}", b, searched);
					//make sure we dont search too far
					if (searched > maxSearchBytes) {
						log.debug("Searched past maximum range");						
						//add to prev bytes (outside this loop)
						break;
					}
					//store our byte for header checking
					headerBuf.put(b);
					//LF found
					if (b == 0x0a) {
						//flip so we can do some reading
    					headerBuf.flip();
    					//get the buffer info
    					log.trace("Current pos: {} len: {}", curBuffer.position(), curBuffer.limit());
    					log.trace("ByteBuffer pos: {} len: {}", byteBuffer.position(), byteBuffer.limit());
    					log.trace("Hex: {}", curBuffer.getHexDump());
    
    					//get data as a string
    					byte[] buf = null;
    					
    					//look for the end of the header marker
    					hex = headerBuf.getHexDump();
						log.trace("Buffer head1: {}", hex);
    					state = detectEndOfHeader(hex, state);
    					if (state != ReadState.Ready) {
    						//size our array
    						buf = new byte[headerBuf.limit()];
    						//consume bytes
    						headerBuf.get(buf);
    						log.trace("Buffer head2: {}", headerBuf.getHexDump());
    						String header = new String(buf, "US-ASCII");					
    						log.debug("Message {}", header);
    						//pull out the headers and put into meta data
    						parseHeader(session, header);			
    						//set result to true
    						result = true;
    						//check if theres remaining data to parse in current buffer
    						if (curBuffer.hasRemaining()) {
    							//clear already read data
    							headerBuf.clear();    							
    						} else {
    							//no more to read for now
    							break;
    						}
    					} else if (state == ReadState.Ready) {
    						log.debug("End of header found during header parse");
    						//consume the EOH bytes
    						buf = hex.startsWith("0D 0A") ? new byte[2] : new byte[1];
    						headerBuf.get(buf);
    						//set result to true just in-case no headers were read
    						result = true;
    						//drop any remaining current buffer data into the session
    						if (curBuffer.hasRemaining()) {
        						log.debug("Had left over bytes after EOH, adding to session");
        						//get the buffer info
        						int pos = curBuffer.position();
        						int len = curBuffer.limit();
        						log.trace("Current pos: {} len: {} size: {}", new Object[]{pos, len, (len - pos)});
        						//consume bytes
        						byte[] bf = new byte[(len - pos)];
        						curBuffer.get(bf);
        						//put bytes into the session
        						session.setAttribute("prev", bf);
    						}
    						//exit header read loop
    						break;
    					}
    					//reset searched counter
    					searched = 0;
    				}					
				}
				
				if (headerBuf.hasRemaining()) {
					log.debug("Had left over bytes, adding to session");
					//get the buffer info
					int pos = headerBuf.position();
					int len = headerBuf.limit();
					log.trace("Header buffer pos: {} len: {} size: {}", new Object[]{pos, len, (len - pos)});
					//consume bytes
					byte[] bf = new byte[(len - pos)];
					headerBuf.get(bf);
					//put bytes into the session
					session.setAttribute("prev", bf);
				}
				
				break;
			case Failed:
				log.info("Stream error, closing");
        		session.close(true);
        		break;
			default:
				log.warn("Unhandled state: {}", state);
		}
		
		// save attributes in session
		session.setAttribute("state", state);

		return result;
	}

	/**
	 * Look for the end of the header block. This is different depending on the
	 * client.
	 * <br />
	 * Winamp dsp sends (0A 0A)
	 * <br />
	 * NSVCAP sends (0D 0D 0A 0D 0D 0A 0D 0A)
	 * <br />
	 * 
	 * @param hex
	 * @param state
	 * @return
	 */
	private ReadState detectEndOfHeader(String hex, ReadState state) {
		if (hex.indexOf("0A") == 0 || hex.indexOf("0D 0A") == 0) {
			log.debug("End of header detected");
			//set to ready state
			state = ReadState.Ready;
		} else if (hex.indexOf("4E 53 56") == 0) {
			//check also for NSV
			log.debug("End of header detected, found NSV");
			//set to ready state
			state = ReadState.Ready;
		}
		return state;
	}

	@SuppressWarnings("unchecked")
	private void parseHeader(IoSession session, String header) {
		//lookup the metadata in the session
		Map<String, Object> metaData = (Map<String, Object>) session.getAttribute("meta");
		if (metaData == null) {
			metaData = new HashMap<String, Object>();
			session.setAttribute("meta", metaData);
		}
		//log.trace("Header length: {}", header.length());
		if (header.length() > 0 && PATTERN_HEADER.matcher(header).matches()) {
			String key = header.substring(header.indexOf('-') + 1, header.indexOf(':'));
			String value = header.substring(header.indexOf(':') + 1);
			log.debug("Meta: {}={}", key, value);
			metaData.put(key, value.trim());
		} else {
			//ignore 0 length headers 
			if (header.length() > 0) {
				log.trace("Unrecognized header: {}", header);			
			}
		}
	}	
	
	@SuppressWarnings({ "unchecked", "unused" })
	private void parseHeaders(IoSession session, String[] headers) {
		//lookup the metadata in the session
		Map<String, Object> metaData = (Map<String, Object>) session.getAttribute("meta");
		if (metaData == null) {
			metaData = new HashMap<String, Object>();
			session.setAttribute("meta", metaData);
		}
		for (String header : headers) {
			parseHeader(session, header);
		}
	}	
    
	/**
	 * Called when sync frame header is found. This may occur more than once during streaming.
	 * The first 136 bits / 17 bytes should not change but the remaining 16 bits / 2 bytes may
	 * change.
	 * 
	 * @param session
	 * @param ioBuffer contains nsv bitstream
	 * @return true if frame create is successful, false otherwise
	 */
	private boolean syncFrame(IoSession session, IoBuffer ioBuffer) {
		boolean result = false;
		
		//get the buffer info
		int pos = ioBuffer.position();
		int len = ioBuffer.limit();
		int remain = ioBuffer.remaining();
		log.trace("Buffer pos: {} len: {} size: {} remaining: {}", new Object[]{pos, len, (len - pos), remain});
		
		//byte holders
		byte[] twoBytes = new byte[2];
		byte[] fourBytes = new byte[4];
		//stream configuration data
		NSVStreamConfig config = (NSVStreamConfig) session.getAttribute("nsvconfig");
		if (config == null) {
    		//first frame with full data
    		ioBuffer.get(fourBytes);
    		String videoType = new String(fourBytes);
    		ioBuffer.get(fourBytes);
    		String audioType = new String(fourBytes);
    		log.debug("Types - video: {} audio: {}", videoType, audioType);
    		//get dimensions
    		ioBuffer.get(twoBytes);
    		int width = (twoBytes[0] & 0xff) | ((twoBytes[1] & 0xff) << 8);
    		ioBuffer.get(twoBytes);
    		int height = (twoBytes[0] & 0xff) | ((twoBytes[1] & 0xff) << 8);
    		int frameRateEncoded = ioBuffer.get();
    		//convert the fps
    		double frameRate = ICYStreamUtil.framerateToDouble(frameRateEncoded);
    		log.debug("Width: {} Height: {} Framerate: {}", new Object[]{width, height, frameRate});
    		//create a stream config
    		config = ICYStreamUtil.createStreamConfig(videoType, audioType, width, height, frameRate);
    		config.frameRateEncoded = frameRateEncoded;
    		//add stream config to the session
    		session.setAttribute("nsvconfig", config);
		} else {
			//read the first 13 bytes / 'N','S','V','s' already consumed
			ioBuffer.get(new byte[13]);
		}
		
		//create a sync frame
		NSVFrame frame = new NSVFrame(config, ICYStreamUtil.NSV_SYNC_DWORD);
		ioBuffer.get(twoBytes);
		//the a/v sync offset (number of milliseconds ahead of the video the audio is at this frame)
		//(treat as signed)
		frame.offsetCurrent = (twoBytes[0] & 0xff) | ((twoBytes[1] & 0xff) << 8);
		log.trace("a/v sync offset: {}", frame.offsetCurrent);

		//build the frames payload
		result = framePayload(session, ioBuffer, frame);
		
		return result;
	}

	/**
	 * Called when chunk frame header is found.
	 * 
	 * @param session
	 * @param ioBuffer contains nsv bitstream
	 * @return true if frame create is successful, false otherwise
	 */
	private boolean chnkFrame(IoSession session, IoBuffer ioBuffer) {
		boolean result = false;

		//get the buffer info
		int pos = ioBuffer.position();
		int len = ioBuffer.limit();
		int remain = ioBuffer.remaining();
		log.trace("Buffer pos: {} len: {} size: {} remaining: {}", new Object[]{pos, len, (len - pos), remain});
		
		//add stream config to the session
		NSVStreamConfig config = (NSVStreamConfig) session.getAttribute("nsvconfig");
		
		NSVFrame frame = new NSVFrame(config, ICYStreamUtil.NSV_NONSYNC_WORD);

		//build the frames payload
		result = framePayload(session, ioBuffer, frame);
		
		return result;
	}	
		
	/**
	 * Constructs the frames payload.
	 * 
	 * @param session
	 * @param ioBuffer
	 * @param frame
	 * @return
	 */
	private boolean framePayload(IoSession session, IoBuffer ioBuffer, NSVFrame frame) {
		boolean result = false;
		
		BitStream bs0 = new BitStream();
		bs0.putBits(8, ioBuffer.get());
		bs0.putBits(8, ioBuffer.get());
		bs0.putBits(8, ioBuffer.get());
		
		//number of auxiliary data chunks
		int numAux = bs0.getbits(4);
		//aux length + video length. maximum 524288 + numAux * (32768 + 6) 
		int videoAndAuxLen = bs0.getbits(20);

		bs0.putBits(8, ioBuffer.get());
		bs0.putBits(8, ioBuffer.get());
		//audio length. maximum allowed 32768
		int audioLen = bs0.getbits(16); 

		int bytesNeeded = videoAndAuxLen + audioLen;
		log.trace("Lengths - audio: {} video+aux: {} needed: {}", new Object[]{audioLen, videoAndAuxLen, bytesNeeded});
		if (ioBuffer.remaining() >= bytesNeeded) {
			if (videoAndAuxLen > ICYStreamUtil.NSV_MAX_VIDEO_LEN / 8 || audioLen > ICYStreamUtil.NSV_MAX_AUDIO_LEN / 8) {
				log.debug("Video or audio length exceeds max allowed");
			} else {
				//all is well, proceed
				
				int totalAuxUsed = 0;
				
				Map<String, IoBuffer> aux = null;
				
				if (numAux > 0) {
					log.debug("Number of aux: {}", numAux);
					byte[] twoBytes = new byte[2];
					byte[] fourBytes = new byte[4];
					aux = new HashMap<String, IoBuffer>(numAux);
					for (int a = 0; a < numAux; a++) {						
						ioBuffer.get(twoBytes);
						//aux length. maximum allowed 32768
						int auxLen = (twoBytes[0] & 0xff) | ((twoBytes[1] & 0xff) << 8);
						totalAuxUsed += auxLen + 6;
						ioBuffer.get(fourBytes);
						String auxType = new String(fourBytes);
						log.debug("Aux type: {}", auxType);
						IoBuffer buffer = IoBuffer.allocate(auxLen);
						byte[] auxBytes = new byte[auxLen];
						ioBuffer.get(auxBytes);
						buffer.put(auxBytes);
						buffer.flip();
						buffer.position(0);
						//add to the map
						aux.put(auxType, buffer);
					}
					session.setAttribute("aux", aux);
				}

				frame.videoLength = videoAndAuxLen;
				frame.videoData = new byte[videoAndAuxLen - totalAuxUsed];
				ioBuffer.get(frame.videoData);
				
				frame.audioLength = audioLen;
				frame.audioData = new byte[audioLen];		
				ioBuffer.get(frame.audioData);
				
				frameLocal.set(frame);
				
				result = true;
			}
		}
		return result;
	}	
	

	/**
	 * Called when an AAC sync frame is found. 
	 * 
	 * @param session
	 * @param ioBuffer contains aac data
	 * @return true if frame create is successful, false otherwise
	 */
	private boolean syncFrameAAC(IoSession session, IoBuffer ioBuffer) {
		boolean result = false;
		
		//get the buffer info
		int pos = ioBuffer.position();
		int len = ioBuffer.limit();
		int remain = ioBuffer.remaining();
		log.trace("Buffer pos: {} len: {} size: {} remaining: {}", new Object[]{pos, len, (len - pos), remain});
		
		//rewind
		ioBuffer.position(0);
		
		//grab all the bytes available
		byte[] all = new byte[ioBuffer.remaining()];
		ioBuffer.get(all);
		
		//create a sync frame
		AACFrame frame = new AACFrame(all);

		frameLocal.set(frame);
		
		//we may return false for some reason in the future
		result = true;
		
		return result;
	}	
	
	/**
	 * Called when an MP3 sync frame is found. 
	 * 
	 * @param session
	 * @param ioBuffer contains mp3 data
	 * @return true if frame create is successful, false otherwise
	 */
	private boolean syncFrameMP3(IoSession session, IoBuffer ioBuffer) {
		boolean result = false;
		
		//get the buffer info
		int pos = ioBuffer.position();
		int len = ioBuffer.limit();
		int remain = ioBuffer.remaining();
		log.trace("Buffer pos: {} len: {} size: {} remaining: {}", new Object[]{pos, len, (len - pos), remain});
		
		/*
		 * 0xffe => synchronization bits
		 * 0x1b  => 11010b (11b == MPEG1 | 01b == Layer III | 0b == no CRC)
		 * 0x9   => 128kbps
		 * 0x00  => 00b == 44100 Hz | 0b == no padding | 0b == private bit
		 * 0x44  => 0010b 0010b (00b == stereo | 10b == (unused) mode extension)
		 *                      (0b == no copyright bit | 0b == original bit)
		 *                      (00b == no emphasis)
		 * Such a frame (MPEG1 Layer III) contains 1152 samples, its size is thus:
		 * (1152*(128000/8))/44100 = 417.96 rounded to the next smaller integer, i.e.
		 * 417.
		 * 
		 * There are also 32 bytes (ie 8 32 bits values) to skip after the header for such frames
		 */
		
		//rewind
		ioBuffer.position(0);
		
		//grab all the bytes available
		byte[] all = new byte[ioBuffer.remaining()];
		ioBuffer.get(all);
		
		//create a sync frame
		MP3Frame frame = new MP3Frame(all);
		
		frameLocal.set(frame);
		
		//we may return false for some reason in the future
		result = true;
		
		return result;
	}	
	
}