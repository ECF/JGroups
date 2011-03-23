/*******************************************************************************
 * Copyright (c) 2007 Composent, Inc. and others. All rights reserved. This
 * program and the accompanying materials are made available under the terms of
 * the Eclipse Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors: Composent, Inc. - initial API and implementation
 ******************************************************************************/
package org.eclipse.ecf.provider.jgroups.identity;

import java.net.URI;
import java.util.StringTokenizer;

import org.eclipse.core.runtime.Assert;
import org.eclipse.ecf.core.identity.BaseID;
import org.eclipse.ecf.core.identity.IDCreateException;
import org.eclipse.ecf.core.identity.Namespace;
import org.eclipse.osgi.util.NLS;
import org.jgroups.Address;

/**
 * 
 */
public class JGroupsID extends BaseID {

	public static final String STACK_NAME = "stackName";

	public static final String STACK_CONFIG_ID = "stackConfigID";

	private static final long serialVersionUID = -1237654704481532873L;

	public static final String DEFAULT_STACK_FILE = "stacks.xml";

	public static final String DEFAULT_STACK_NAME = "udp";

	private Address address = null;

	private final URI uri;

	private String stackConfigID = null;

	private String stackName = DEFAULT_STACK_NAME;

	private String getPathNoSlashes() {
		String path = this.uri.getRawPath();
		if (path != null)
			while (path.startsWith("/"))
				path = path.substring(1);
		return path;
	}

	private void setStackInfo() {
		final String query = uri.getRawQuery();
		if (query != null) {
			final StringTokenizer st = new StringTokenizer(query, "&");
			while (st.hasMoreTokens()) {
				final String tok = st.nextToken();
				final StringTokenizer equalsTok = new StringTokenizer(tok, "=");
				if (equalsTok.countTokens() == 2) {
					final String paramName = equalsTok.nextToken();
					final String paramValue = equalsTok.nextToken();
					if (paramName != null && paramValue != null) {
						if (paramName.equals(STACK_CONFIG_ID))
							this.stackConfigID = paramValue;
						if (paramName.equals(STACK_NAME))
							this.stackName = paramValue;
					}
				}
			}
		}
	}

	public JGroupsID(Namespace ns, URI uri) throws IDCreateException {
		super(ns);
		Assert.isNotNull(uri);
		this.uri = uri;
		final String scheme = this.uri.getScheme();
		if (scheme == null || !scheme.equalsIgnoreCase(ns.getScheme()))
			throw new IDCreateException(NLS.bind("scheme must be {0}", ns
					.getScheme()));
		;
		final String path = getPathNoSlashes();
		if (path == null || path.length() < 1)
			throw new IDCreateException("channel name not valid");
		setStackInfo();
	}

	public String getUserInfo() {
		return uri.getRawUserInfo();
	}

	public String getHost() {
		return uri.getHost();
	}

	public int getPort() {
		return uri.getPort();
	}

	public String getChannelName() {
		return getPathNoSlashes();
	}

	public String getStackConfigID() {
		return stackConfigID;
	}

	public String getStackName() {
		return stackName;
	}

	public Address getAddress() {
		return this.address;
	}

	public void setAddress(Address address) {
		this.address = address;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.eclipse.ecf.core.identity.BaseID#namespaceCompareTo(org.eclipse.ecf
	 * .core.identity.BaseID)
	 */
	protected int namespaceCompareTo(BaseID o) {
		if (!(o instanceof JGroupsID))
			return -1;
		return uri.compareTo(((JGroupsID) o).uri);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.eclipse.ecf.core.identity.BaseID#namespaceEquals(org.eclipse.ecf.
	 * core.identity.BaseID)
	 */
	protected boolean namespaceEquals(BaseID o) {
		if (!(o instanceof JGroupsID))
			return false;
		return uri.equals(((JGroupsID) o).uri);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.ecf.core.identity.BaseID#namespaceGetName()
	 */
	protected String namespaceGetName() {
		return uri.toString();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.ecf.core.identity.BaseID#namespaceHashCode()
	 */
	protected int namespaceHashCode() {
		return uri.hashCode();
	}

	public String toString() {
		final StringBuffer buf = new StringBuffer("JGroupsID[");
		buf.append(getName()).append(";").append(address).append("]");
		return buf.toString();
	}
}
