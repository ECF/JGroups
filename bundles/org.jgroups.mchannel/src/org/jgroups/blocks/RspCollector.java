// $Id: RspCollector.java,v 1.1 2009/07/30 00:58:11 phperret Exp $

package org.jgroups.blocks;

import org.jgroups.Address;
import org.jgroups.Message;
import org.jgroups.View;


public interface RspCollector {
    void receiveResponse(Object response_value, Address sender);
    void suspect(Address mbr);
    void viewChange(View new_view);
}
