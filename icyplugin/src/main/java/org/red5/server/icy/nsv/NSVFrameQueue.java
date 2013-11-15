package org.red5.server.icy.nsv;

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

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.ReadLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock;

import org.red5.server.icy.message.Frame;

/**
 * Simple concurrent queue for storing NSV frames.
 * 
 * TODO: ensure frames are in the correct order, check comparable
 * TODO: look at using a fifo queue instead of list
 * 
 * @author Paul Gregoire (mondain@gmail.com)
 * @author Andy Shaules (bowljoman@hotmail.com)
 */
public class NSVFrameQueue {

	public AtomicLong totalFrames = new AtomicLong(0);
	
	public volatile ArrayList<Frame> frames = new ArrayList<Frame>();

	private ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

	private ReadLock readLock = lock.readLock();

	private WriteLock writeLock = lock.writeLock();

	public void addFrame(Frame frame) {
		long frameNumber = totalFrames.incrementAndGet();
		frame.setFrameNumber(frameNumber);
		try {
			writeLock.lock();
			frames.add(frame);
		} finally {
			writeLock.unlock();
		}
	}

	public Frame getFrame() {
		try {
			writeLock.lock();
			return frames.remove(0);
		} finally {
			writeLock.unlock();
		}
	}

	public boolean hasFrames() {
		try {
			readLock.lock();
			return (frames.isEmpty()) ? false : true;
		} finally {
			readLock.unlock();
		}
	}

	public int count() {
		try {
			readLock.lock();
			return frames.size();
		} finally {
			readLock.unlock();
		}
	}

	public void flush() {
		try {
			writeLock.lock();
			frames.clear();
		} finally {
			writeLock.unlock();
		}
	}
	
}
