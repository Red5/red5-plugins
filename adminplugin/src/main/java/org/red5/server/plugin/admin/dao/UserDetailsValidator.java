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

import org.apache.commons.lang3.StringUtils;
import org.red5.logging.Red5LoggerFactory;
import org.red5.server.plugin.admin.domain.UserDetails;
import org.slf4j.Logger;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

public class UserDetailsValidator implements Validator {

	private static Logger log = Red5LoggerFactory.getLogger(UserDetailsValidator.class, "admin");

	private int minLength = 4;

	@SuppressWarnings("unchecked")
	public boolean supports(Class clazz) {
		return UserDetails.class.equals(clazz);
	}

	public void validate(Object obj, Errors errors) {
		log.debug("validate");
		UserDetails ud = (UserDetails) obj;
		if (ud == null) {
			log.debug("User details were null");
			errors.rejectValue("username", "error.not-specified", null, "Value required.");
		} else {
			log.debug("User details were null");
			if (StringUtils.isEmpty(ud.getUsername())) {
				errors.rejectValue("username", "error.missing-username", new Object[] {}, "Username Required.");
			}
			if (StringUtils.isEmpty(ud.getPassword())) {
				errors.rejectValue("password", "error.missing-password", new Object[] {}, "Password Required.");
			} else if (ud.getPassword().length() < minLength) {
				errors.rejectValue("password", "error.too-low", new Object[] { new Integer(minLength) },
						"Password Length Is Too Small.");
			}
		}
	}

	public void setMinLength(int i) {
		minLength = i;
	}

	public int getMinLength() {
		return minLength;
	}
}