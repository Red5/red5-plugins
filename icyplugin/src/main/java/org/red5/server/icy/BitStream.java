package org.red5.server.icy;

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

import java.io.*;

/**
 * Provides a means to stream media via NSV and shoutcast.
 * For use with NSVCap, Winamp shoutcast dsp, and shoutcast dnas.
 * 
 * @author Paul Gregoire (mondain@gmail.com)
 * @author Andy Shaules (bowljoman@hotmail.com)
 */
public class BitStream {

	private int allocated = 1;

	private long[] bits = new long[allocated];

	public InputStream inputSource;

	private int used = 0;

	private int bitpos = 0;

	private int eof = 0;

	public BitStream() {
	}

	public BitStream(InputStream stream) {
		inputSource = stream;
	}

	/**
	 * only tested 32 bits max per request.
	 * 
	 * @param nbits
	 * @param p_value
	 */
	public void putBits(int nbits, long value) {
		eof = 0;
		while (nbits-- > 0) {
			bits[used / 8] |= (value & 1) << (used & 7);
			if (((++used) & 7) == 0) {
				resize(1);
			}
			value >>= 1;
		}
	}

	/**
	 * only tested 32 bit Maximum per request.
	 * 
	 * @param nbits
	 * @return
	 */
	public int getbits(int nbits) {
		if (used - bitpos < nbits) {
			if (!inputSource.equals(null)) {
				try {
					this.putBits(8, inputSource.read());
					this.putBits(8, inputSource.read());
					this.putBits(8, inputSource.read());
					this.putBits(8, inputSource.read());
				} catch (IOException ex) {
					eof = 1;
					return -1;
				}
			} else {
				eof = 1;
				return -1;
			}
		}
		int ret = 0;
		int sh = 0;
		int t = bitpos / 8;
		for (sh = 0; sh < nbits; sh++) {
			ret |= ((bits[t] >> (bitpos & 7)) & 1) << sh;
			if (((++bitpos) & 7) == 0) {
				t++;
			}
		}
		return ret;
	}

	public int eof() {
		eof = (used - bitpos == 0) ? 1 : 0;
		return eof;
	}

	public int rewind() {
		bitpos = 0;
		return used;
	}

	public int available() {
		return used - bitpos;
	}

	/**
	 * Number of Bytes to add.
	 * 
	 * @param size
	 */
	private void resize(int size) {
		long[] newBits = new long[allocated + size];
		for (int i = 0; i < allocated; i++) {
			newBits[i] = bits[i];
		}
		allocated += size;
		bits = newBits;
	}

	public void seek(int val) {
		bitpos += val;
		bitpos = bitpos < 0 ? 0 : bitpos;
		bitpos = bitpos > used ? used : bitpos;
	}

	public void compact() {
		System.out.println("Compacting");
		int av = used - bitpos;
		long[] newBits = new long[av / 8 + 1];
		for (int i = (bitpos / 8); i < av / 8 + 1; i++) {
			newBits[i] = bits[i];
		}
		allocated = av / 8 + 1;
		bits = newBits;
	}

}
