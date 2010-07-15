// $Id: SuspectEvent.java,v 1.1 2009/07/30 00:58:14 phperret Exp $

package org.jgroups;

/**
 * Represents a suspect event.
 * Gives access to the suspected member.
 */
public class SuspectEvent {
    final Object suspected_mbr;

    public SuspectEvent(Object suspected_mbr) {this.suspected_mbr=suspected_mbr;}

    public Object getMember() {return suspected_mbr;}
    public String toString() {return "SuspectEvent";}
}
