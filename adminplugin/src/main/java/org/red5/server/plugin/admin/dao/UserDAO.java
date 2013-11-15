package org.red5.server.plugin.admin.dao;

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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;

import javax.sql.DataSource;

import org.red5.logging.Red5LoggerFactory;
import org.red5.server.plugin.admin.domain.UserDetails;
import org.slf4j.Logger;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.GrantedAuthorityImpl;

/**
 * Simple DAO for manipulation of the user database.
 * 
 * @author Paul Gregoire (mondain@gmail.com)
 */
public class UserDAO {

	private static Logger log = Red5LoggerFactory.getLogger(UserDAO.class, "admin");
	
	public static boolean addUser(String username, String hashedPassword) {
		boolean result = false;
				
		Connection conn = null;
		PreparedStatement stmt = null;
		try {
            // JDBC stuff
            DataSource ds = UserDatabase.getDataSource();
            
			conn = ds.getConnection();
			//make a statement
			stmt = conn.prepareStatement("INSERT INTO APPUSER (username, password, enabled) VALUES (?, ?, 'enabled')");
			stmt.setString(1, username);
			stmt.setString(2, hashedPassword);
			log.debug("Add user: {}", stmt.execute());			
			//add role
			stmt = conn.prepareStatement("INSERT INTO APPROLE (username, authority) VALUES (?, 'ROLE_SUPERVISOR')");
			stmt.setString(1, username);
			log.debug("Add role: {}", stmt.execute());			
			//
			result = true;
		} catch (Exception e) {
			log.error("Error connecting to db", e);
		} finally {
			if (stmt != null) {
				try {
					stmt.close();
				} catch (SQLException e) {
				}
			}
			if (conn != null) {
				try {
					conn.close();
				} catch (SQLException e) {
				}
			}
		}
		return result;
	}
	
	public static UserDetails getUser(String username) {
		UserDetails details = null;
		Connection conn = null;
		PreparedStatement stmt = null;
		try {
            // JDBC stuff
            DataSource ds = UserDatabase.getDataSource();
			
			conn = ds.getConnection();
			//make a statement
			stmt = conn.prepareStatement("SELECT * FROM APPUSER WHERE username = ?");
			stmt.setString(1, username);
			ResultSet rs = stmt.executeQuery();
			if (rs.next()) {
				log.debug("User found");			
				details = new UserDetails();
				details.setEnabled("enabled".equals(rs.getString("enabled")));
				details.setPassword(rs.getString("password"));
				details.setUserid(rs.getInt("userid"));
				details.setUsername(rs.getString("username"));
				//
				rs.close();
				//get role				
				stmt = conn.prepareStatement("SELECT authority FROM APPROLE WHERE username = ?");
				stmt.setString(1, username);
				rs = stmt.executeQuery();
				if (rs.next()) {
	            	Collection<? extends GrantedAuthority> authorities;
//	            	authorities.addAll((Collection<?>) new GrantedAuthorityImpl(rs.getString("authority")));
//	            	details.setAuthorities(authorities);
	            	//
	            	//if (daoAuthenticationProvider != null) {
    	            	//User usr = new User(username, details.getPassword(), true, true, true, true, authorities);
    	            	//daoAuthenticationProvider.getUserCache().putUserInCache(usr);					
	            	//}
				}			
			}
			rs.close();
		} catch (Exception e) {
			log.error("Error connecting to db", e);
		} finally {
			if (stmt != null) {
				try {
					stmt.close();
				} catch (SQLException e) {
				}
			}
			if (conn != null) {
				try {
					conn.close();
				} catch (SQLException e) {
				}
			}
		}
		return details;
	}
	
}
