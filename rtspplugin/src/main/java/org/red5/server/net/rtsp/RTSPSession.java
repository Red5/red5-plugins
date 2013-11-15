package org.red5.server.net.rtsp;

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

/***************************************************************************
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *                                                                         *
 *   Copyright (C) 2005 - Matteo Merli - matteo.merli@gmail.com            *
 *                                                                         *
 ***************************************************************************/

import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

import org.red5.io.object.UnsignedLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * RTSP is primarily a connection-less protocol, that means that RTSP request can
 * be made over multiples TCP connections. To identify such a "session", a
 * 64-bit identifier is used.
 * 
 * @author Matteo Merli (matteo.merli@gmail.com)
 */
public class RTSPSession {

	private static Logger log = LoggerFactory.getLogger(RTSPSession.class);

	private static Map<String, RTSPSession> sessions = new ConcurrentHashMap<String, RTSPSession>();

	// Members
	/** Session ID */
	private String sessionId;

	/** Session associated tracks */
	private Map<String, Track> tracks = new ConcurrentHashMap<String, Track>();

	/**
	 * Creates a new empty RTSPSession and stores it.
	 * 
	 * @param sessionId
	 *        Session identifier
	 * @return The newly created session
	 */
	static public RTSPSession create(String sessionId) {

		if (sessions.get(sessionId) != null) {
			log.error("Session key conflit!!");
			return null;
		}
		RTSPSession session = new RTSPSession(sessionId);
		sessions.put(sessionId, session);
		log.debug("New session created - id=" + sessionId);
		return session;
	}

	/**
	 * @return a new RTSPSession with a new random ID
	 */
	static public RTSPSession create() {
		return create(newSessionID());
	}

	/**
	 * Access an opened session.
	 * 
	 * @param id
	 *        Session identifier
	 * @return The RTSPSession identified by id or null if not present
	 */
	static public RTSPSession get(String id) {
		if (id == null)
			return null;

		long key = Long.valueOf(id);
		return sessions.get(key);
	}

	/**
	 * Close a session and remove resources.
	 * 
	 * @param id
	 *        Session identifier
	 */
	static public void close(String id) {
		long key = Long.valueOf(id);
		close(key);
	}

	/**
	 * Close the session and removes it.
	 * 
	 * @param id
	 *        the session ID
	 */
	static public void close(long id) {
		sessions.remove(id);
	}

	protected RTSPSession(String sessionId) {
		this.sessionId = sessionId;
	}

	/**
	 * @return the session ID
	 */
	public String getId() {
		return sessionId;
	}

	/**
	 * @param control
	 *        the key to access the track
	 * @return the track
	 */
	public Track getTrack(String control) {
		return tracks.get(control);
	}

	/**
	 * @return the number of track contained in this sessions
	 */
	public int getTracksCount() {
		return tracks.size();
	}

	/**
	 * Adds a new track to the session
	 * 
	 * @param track
	 *        a Track object
	 */
	public void addTrack(Track track) {
		String control = track.getControl();
		tracks.put(control, track);
	}

	// / Session ID generation

	private static Random random = new Random();

	/**
	 * Creates a unique session ID
	 * 
	 * @return the session ID
	 */
	private static String newSessionID() {
		String id;
		synchronized (random) {
			while (true) {

				id = new UnsignedLong(random).toString();
				if (sessions.get(id) == null) {
					// Ok, the id is unique
					return id;
				}
			}
			// try with another id
		}
	}
}
