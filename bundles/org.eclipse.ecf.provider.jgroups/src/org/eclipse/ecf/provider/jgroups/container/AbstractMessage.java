package org.eclipse.ecf.provider.jgroups.container;

import java.io.Serializable;

import org.eclipse.ecf.provider.jgroups.identity.JGroupsID;

public abstract class AbstractMessage implements Serializable {

	private static final long serialVersionUID = 1672381422214019227L;
	private final byte[] data;

	private JGroupsID fromID;
	private JGroupsID targetID;

	AbstractMessage(JGroupsID fromID, JGroupsID targetID, byte[] data) {
		this.fromID = fromID;
		this.targetID = targetID;
		this.data = data;
	}

	public byte[] getData() {
		return this.data;
	}

	public JGroupsID getFromID() {
		return fromID;
	}

	public JGroupsID getTargetID() {
		return targetID;
	}

}
