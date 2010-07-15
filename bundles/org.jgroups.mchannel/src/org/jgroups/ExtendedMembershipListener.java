package org.jgroups;

/**
 * @author Bela Ban
 * @version $Id: ExtendedMembershipListener.java,v 1.1 2009/07/30 00:58:14 phperret Exp $
 */
public interface ExtendedMembershipListener extends MembershipListener {

    /**
     * Called <em>after</em> the FLUSH protocol has unblocked previously blocked senders, and messages can be sent again. This
     * callback only needs to be implemented if we require a notification of that.
     */
    void unblock();
}
