// $Id: RequestHandler.java,v 1.1 2009/07/30 00:58:11 phperret Exp $

package org.jgroups.blocks;


import org.jgroups.Message;


public interface RequestHandler {
    Object handle(Message msg);
}
