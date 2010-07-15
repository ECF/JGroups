// $Id: UpHandler.java,v 1.1 2009/07/30 00:58:13 phperret Exp $

package org.jgroups;

/**
 * Provides a way of taking over a channel's tasks. 
 */
public interface UpHandler {
	/**
	 * Invoked for all channel events except connection management and state transfer.
	 * @param evt
	 */
    Object up(Event evt);
}
