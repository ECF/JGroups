// $Id: ChannelNotConnectedException.java,v 1.1 2009/07/30 00:58:13 phperret Exp $

package org.jgroups;

/**
 * Thrown if an operation is attemped on an unconnected channel.
 */
public class ChannelNotConnectedException extends ChannelException {

    private static final long serialVersionUID = -6701630538465783064L;

	public ChannelNotConnectedException() {
    }

    public ChannelNotConnectedException(String reason) {
        super(reason);
    }

    public String toString() {
        return "ChannelNotConnectedException";
    }
}
