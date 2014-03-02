package org.red5.server.security.jaas;

/*
 * RED5 Open Source Flash Server - http://www.osflash.org/red5
 *
 * Copyright (c) 2006-2011 by respective authors (see below). All rights reserved.
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

import java.io.Serializable;
import java.security.Principal;

/**
 * Represents a user.
 * <br />
 * Principals may be associated with a particular <code>Subject</code>
 * to augment it with an additional identity. Authorization decisions can be based upon 
 * the Principals associated with a <code>Subject</code>.
 * 
 * @see java.security.Principal
 * @see javax.security.auth.Subject
 */
public class SimplePrincipal implements Principal, Serializable {

	private static final long serialVersionUID = -5845179654012035528L;

	/**
	 * @serial
	 */
	private String name;
	
	private String passwd;

	/**
	 * Create a Principal with the given name.
	 * 
	 * @param name the username for this user
	 * @param password the password for this user
	 * @exception NullPointerException if the name is null.
	 */
	public SimplePrincipal(String name, String password) {
		if (name == null) {
			throw new NullPointerException("Name cannot be null");
		}
		this.name = name;
		this.passwd = password;
	}

	/** {@inheritDoc} */
	public String getName() {
		return name;
	}

	/**
	 * @return the passwd
	 */
	public String getPassword() {
		return passwd;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((name == null) ? 0 : name.hashCode());
		return result;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		SimplePrincipal other = (SimplePrincipal) obj;
		if (name == null) {
			if (other.name != null)
				return false;
		} else if (!name.equals(other.name))
			return false;
		return true;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return "SimplePrincipal [name=" + name + ", password=" + passwd + "]";
	}

}
