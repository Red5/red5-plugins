package org.red5.stream.http.xuggler;

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

import java.io.RandomAccessFile;
import java.nio.ByteBuffer;

import org.red5.logging.Red5LoggerFactory;
import org.red5.xuggler.IEventIOHandler;
import org.red5.xuggler.Message;
import org.slf4j.Logger;

import com.xuggle.xuggler.ISimpleMediaFile;
import com.xuggle.xuggler.io.IURLProtocolHandler;

/**
 * An implementation of IURLProtocolHandler that converts into a format that ffmpeg can read.
 * 
 * {@link http://en.wikipedia.org/wiki/MPEG_transport_stream}
 * {@link http://wiki.multimedia.cx/index.php?title=MPEG-2_Transport_Stream}
 * {@link http://neuron2.net/library/mpeg2/iso13818-1.pdf}
 * 
 * @author Paul Gregoire
 */
public class MpegTsHandler implements IURLProtocolHandler {

	private final Logger log = Red5LoggerFactory.getLogger(this.getClass());

	//file used for debugging byte stream
	private RandomAccessFile raf;
	
	IEventIOHandler handler;

	String url;

	int openFlags;

	// Only package members can create
	MpegTsHandler(IEventIOHandler handler, ISimpleMediaFile metaInfo, String url, int flags) {
		this.handler = handler;
		this.url = url;
		this.openFlags = flags;
	}

	/**
	 * Packets are normally 188 bytes but may consist of 204 bytes if a 16 byte
	 * Reed-Solomon error correction data block is included.
	 * 
	 * @param in
	 * @return
	 */
	private Message.Type parsePacket(ByteBuffer in) {
		log.trace("parsePacket: {}", in);
		
		//used to return the packet type
		Message.Type type = Message.Type.NULL; //start off as null

		in.mark();
		
		// Sync byte #0 is always 0x47
		byte sync = in.get();

		// Flags - 3 bits
		byte flags = in.get();
		// Transport error indicator (TEI)
		int tei = flags >> (8 - (7 + 1)) & 0x0001;
		// Payload unit start indicator - 1 = start of PES data or PSI, 0 otherwise
		int psi = flags >> (8 - (6 + 1)) & 0x0001;
		// Transport priority - 1 = higher priority than packets with same PID
		int tp = flags >> (8 - (5 + 1)) & 0x0001;
		
		log.trace("Packet info - sync: 0x{} tei: {} psi: {} tp: {}", new Object[]{Integer.toHexString(sync), tei, psi, tp});
		
		byte flags2 = in.get();
		
		// Packet id (PID) - 13 bits
		int pid = ((flags << 8)|(flags2 & 0xff)) & 0x1fff;
		log.trace("Packet id: {}", Integer.toHexString(pid));
		
		// Continuity counter - 4 bits		

		switch (pid) {
			case 100:
				//fairly sure this is an h.264 video packet
				log.trace("Got video");
				//assume they are all key frames for now
				type = Message.Type.KEY_FRAME;
				break;
			case 0x0000:
				log.trace("Got Program Association Table (PAT)");
				type = Message.Type.CONFIG_PAT;				
				break;
			case 0x0001:
				log.trace("Got Conditional Access Table");
				type = Message.Type.CONFIG;				
				break;
			case 0x0002:
				log.trace("Got Transport Stream Description Table");
				type = Message.Type.CONFIG;				
				break;
			case 0x1fff:
				log.trace("Got Null packet");
				break;
			default:
				if (pid >= 0x0003 && pid <= 0x000f) {
					log.trace("Got PID in reserved range");
				} else if (pid >= 0x0010 && pid <= 0x1ffe) {
					log.trace("Got PID from other range");
				} else {
					log.trace("Got unknown PID");
				}
				type = Message.Type.OTHER;				
		}
		
		in.reset();
		
		return type;
	}

	@SuppressWarnings("unused")
	private static boolean isBitSet(byte b, int bit) {
	    return (b & (1 << bit)) != 0;
	}

	public String toString() {
		return this.getClass().getName() + ':' + url;
	}

	public int open(String url, int flags) {
		int retval = -1;
		try {
			// and send a header message
			handler.write(new Message(Message.Type.HEADER, null));
			// For an open, we assume the ProtocolManager has done it's job correctly and we're working on the 
			// right input and output streams.
			this.url = url;
			this.openFlags = flags;
			retval = 0;
		} catch (Exception ex) {
			log.warn("Exception during open: {}", ex);
		}
		if (log.isTraceEnabled()) {
    		//write to a file for debugging
    		try {
    			raf = new RandomAccessFile(String.format("test%s.ts", System.currentTimeMillis()), "rwd");
    		} catch (Exception e) {
    			e.printStackTrace();
    		}		
		}
		log.trace("open({}, {}); {}", new Object[] { url, flags, retval });
		return retval;
	}
	
	/*
	 * These following methods all wrap the unsafe methods in try {} catch {}
	 * blocks to ensure we don't pass an exception back to the native C++
	 * function that calls these.
	 */
	public int close() {
		log.debug("Close {}", url);
		int retval = -1;
		try {
			// As a convention, we send a IMediaDataWrapper object wrapping NULL
			// for end of streams
			handler.write(new Message(Message.Type.END_STREAM, null));
			retval = 0;
		} catch (Exception ex) {
			log.warn("Exception during close: {}", ex);
		}
		if (log.isTraceEnabled()) {
			//close debugging file
			try {
				raf.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}	
		log.trace("close({}); {}", url, retval);
		return retval;
	}

	public int read(byte[] buf, int size) {
		int retval = -1;
		try {
			retval = 0;
		} catch (Exception ex) {
			log.warn("Exception during read: {}", ex);
		}
		log.trace("read({}, {}); {}", new Object[] { url, size, retval });
		return retval;
	}

	public long seek(long offset, int whence) {
		log.trace("seek({}, {}, {});", new Object[] { url, offset, whence });
		// Unsupported
		return -1;
	}
	
	public int write(byte[] buf, int size) {
		log.debug("Write size: {}", size);
		int retval = -1;
		if (log.isDebugEnabled()) {
			//write to a file for debugging
			try {
				raf.write(buf);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		try {
			//expect chunks of 188 bytes from ffmpeg
			//204 and 208 are also valid but not supported by this version
			if (size % 188 == 0) {
				log.debug("Data appears to be of the correct size");
			}
			// make sure we break the data into 188 byte chunks
			int chunks = size / 188;
			log.debug("Chunks: {}", chunks);
			// wrap the bytes that FFMPEG just sent us
			ByteBuffer buffer = ByteBuffer.wrap(buf);
			// break it up into chunks
			byte[] chunk = new byte[188];
			for (int i = 0; i < chunks; i++) {
				buffer.get(chunk);
				// append the new data to the end of our buffer
				ByteBuffer chunkBuffer = ByteBuffer.wrap(chunk);
				Message.Type type = parsePacket(chunkBuffer);
				if (type != Message.Type.NULL) {
					//send off to the handler
    				handler.write(new Message(type, chunkBuffer));
    				if (log.isTraceEnabled()) {
        				log.trace("Chunk: {}", new String(chunk));
    				}
				} else {
					chunkBuffer.clear();
				}
			}
			buffer.clear();
			// return that we read size
			retval = size;
		} catch (Exception ex) {
			log.warn("Exception during write: {}", ex);
		}
		log.trace("write({}, {}); {}", new Object[] { url, size, retval });

		return retval;
	}

	public boolean isStreamed(String url, int flags) {
		boolean retval = true;
		log.trace("isStreamed({}, {}); {}", new Object[] { url, flags, retval });
		return retval;
	}
	
}
