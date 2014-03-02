package org.red5.server.jmx.mxbeans;

import javax.management.MXBean;

import org.apache.catalina.Host;
import org.apache.catalina.Valve;

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

/**
 * Simple mbean interface for Tomcat container virtual host loaders.
 *
 * @author The Red5 Project (red5@osflash.org)
 * @author Paul Gregoire (mondain@gmail.com)
 */
@MXBean
public interface TomcatVHostLoaderMXBean {

	public boolean startWebApplication(String applicationName);	
	
	public boolean getAutoDeploy();

	public void setAutoDeploy(boolean autoDeploy);

    public Host getHost();

	public String getDomain();

	public void setDomain(String domain);

	public void addAlias(String alias);

	public void removeAlias(String alias);
	
	public org.apache.catalina.Context addContext(String path, String docBase);

	public void removeContext(String path);
	
	public void addValve(Valve valve);

	public void removeValve(String valveInfo);	
	
	public boolean getLiveDeploy();

	public void setLiveDeploy(boolean liveDeploy);

	public String getName();

	public void setName(String name);

	public boolean getStartChildren();

	public void setStartChildren(boolean startChildren);

	public boolean getUnpackWARs();

	public void setUnpackWARs(boolean unpackWARs);

	public String getWebappRoot();

	public void setWebappRoot(String webappRoot);

}
