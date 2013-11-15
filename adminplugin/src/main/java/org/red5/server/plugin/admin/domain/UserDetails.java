package org.red5.server.plugin.admin.domain;

import java.util.Collection;

import org.springframework.security.core.GrantedAuthority;

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



public class UserDetails implements org.springframework.security.core.userdetails.UserDetails {
	
	private final static long serialVersionUID = 2801983490L;
	
//	private GrantedAuthority[] authorities = new GrantedAuthority[1];
	
    private int userid;	

	private String username;

	private String password;
	
	private Boolean enabled;

	private Collection<? extends GrantedAuthority> authorities;

	public UserDetails() {		
	}

	public UserDetails(int userid) {
		this.userid = userid;
	}
	
	public int getUserid() {
		return userid;
	}

	public void setUserid(int userid) {
		this.userid = userid;
	}

	public void setUsername(String value) {
		username = value;
	}

	public String getUsername() {
		return username;
	}

	public void setPassword(String value) {
		password = value;
	}

	public String getPassword() {
		return password;
	}

    public Boolean getEnabled() {
		return enabled;
	}

	public void setEnabled(Boolean enabled) {
		this.enabled = enabled;
	}

	public void setEnabled(Integer enabledInt) {
		this.enabled = (enabledInt == 1);
	}

	public void setEnabled(String enabledStr) {
		this.enabled = "enabled".equals(enabledStr);
	}
	
	public void setAuthorities(Collection<? extends GrantedAuthority> authorities) {
		this.authorities = authorities;
	}

//	public GrantedAuthority[] getAuthorities() {
//		return authorities;
//	}
	
	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		// TODO Auto-generated method stub
		return authorities;
	}

	public boolean isAccountNonExpired() {
		return true;
	}

	public boolean isAccountNonLocked() {
		return true;
	}

	public boolean isCredentialsNonExpired() {
		return true;
	}

	public boolean isEnabled() {
		return enabled;
	}

	/**
     * Returns a hash code value for the object.  This implementation computes
     * a hash code value based on the id fields in this object.
     * @return a hash code value for this object.
     */
    @Override
    public int hashCode() {
        return userid;
    }	

    @Override
    public boolean equals(Object object) {
        // TODO: Warning - this method won't work in the case the id fields are not set
        if (!(object instanceof UserDetails)) {
            return false;
        }
        UserDetails other = (UserDetails) object;
        if (this.userid != other.userid) {
        	return false;
        }
        return true;
    }


    
}