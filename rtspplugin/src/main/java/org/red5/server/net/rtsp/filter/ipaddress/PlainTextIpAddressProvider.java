package org.red5.server.net.rtsp.filter.ipaddress;

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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.net.InetAddress;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Implementation of the IpAddressFilter that is based on plain 
 * text file containing instruction on "allowed" and "denied" 
 * addresses and hosts.
 * 
 * @author Matteo Merli (matteo.merli@gmail.com)
 */
public class PlainTextIpAddressProvider implements IpAddressProvider {

	private static Logger log = LoggerFactory.getLogger(PlainTextIpAddressProvider.class);

	private enum RuleType {
		Allow, Deny
	};

	private static class Rule {

		public RuleType type;

		public Pattern pattern;
	}

	private String configFile = "conf/ipfilter.txt";
	
	private static List<Rule> rules = new LinkedList<Rule>();

	/* (non-Javadoc)
	 * @see rtspproxy.auth.IpAddressProvider#init()
	 */
	public void init() throws Exception {
		// Load rules from file
		loadRules(new FileReader(new File(configFile)));
	}

	/* (non-Javadoc)
	 * @see rtspproxy.auth.IpAddressProvider#shutdown()
	 */
	public void shutdown() throws Exception {
		rules.clear();
	}

	/* (non-Javadoc)
	 * @see rtspproxy.auth.IpAddressProvider#isBlocked(java.net.InetAddress)
	 */
	public boolean isBlocked(InetAddress address) {
		boolean blocked = true; // by default the address is blocked
		String[] hostip = address.toString().split("/");
		String host = hostip[0];
		String ip = hostip[1];

		for (Rule rule : rules) {
			if (blocked && rule.type == RuleType.Deny)
				// Don't need to check, up to now this IP is already
				// blocked
				continue;

			if (rule.pattern.matcher(ip).matches()
					|| rule.pattern.matcher(host).matches())
				// the address matches the pattern 
				// check if it's allow or deny
				blocked = (rule.type == RuleType.Allow) ? false : true;
		}

		return blocked;
	}

	/** 
	 * Reads the rules from a file
	 * @param reader Reader of a file containing the access rules
	 * @throws IOException
	 */
	protected void loadRules(Reader reader) throws IOException {
		BufferedReader in = new BufferedReader(reader);

		String line;
		int lineNumber = 0;
		try {
			while ((line = in.readLine()) != null) {
				line = line.replaceAll("\t", " "); // replace tabs 
				line = line.trim();
				++lineNumber;

				if (line.length() == 0)
					continue; // Ignore empty lines 
				if (line.startsWith("#"))
					continue; // Ignore comments
				RuleType ruleType = null;
				if (line.startsWith("Allow"))
					ruleType = RuleType.Allow;
				else if (line.startsWith("Deny"))
					ruleType = RuleType.Deny;
				else
					throw new IOException("Invalid filter pattern (line "
							+ lineNumber + ")");

				// read the pattern
				String[] patternSplit = line.split(" ", 2);
				if (patternSplit.length != 2)
					throw new IOException("Invalid filter pattern (line "
							+ lineNumber + ")");
				String pattern = patternSplit[1];
				log.debug("Rule: {} {}", ruleType, pattern);

				// Transform the patterns escaping "." and "*" characters
				pattern = pattern.replaceAll("\\.", "\\\\.");
				pattern = pattern.replaceAll("\\*", ".*");

				Rule rule = new Rule();
				rule.type = ruleType;
				rule.pattern = Pattern.compile(pattern);
				rules.add(rule);
			}
		} catch (IOException e) {
			log.error("Error reading IpAddressFilter rules: ", e);
			throw e;
		}
	}

	public String getConfigFile() {
		return configFile;
	}

	public void setConfigFile(String configFile) {
		this.configFile = configFile;
	}
		
}
