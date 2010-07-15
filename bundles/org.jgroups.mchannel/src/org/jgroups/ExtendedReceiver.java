package org.jgroups;

/**
 * Extends Receiver, plus the partial state transfer methods.
 * This interface will disappear (be merged with Receiver) in 3.0.
 * @author Bela Ban
 * @version $Id: ExtendedReceiver.java,v 1.1 2009/07/30 00:58:14 phperret Exp $
 */
public interface ExtendedReceiver extends Receiver, ExtendedMessageListener, ExtendedMembershipListener {
}
