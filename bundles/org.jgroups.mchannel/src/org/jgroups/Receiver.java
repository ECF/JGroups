package org.jgroups;

/**
 * Defines the callbacks that are invoked when messages, views etc are received on a channel
 * @author Bela Ban
 * @version $Id: Receiver.java,v 1.1 2009/07/30 00:58:14 phperret Exp $
 */
public interface Receiver extends MessageListener, MembershipListener {
}
