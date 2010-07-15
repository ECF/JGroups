package org.jgroups;

/**
 * @author Bela Ban
 * @version $Id: ReceiverAdapter.java,v 1.1 2009/07/30 00:58:13 phperret Exp $
 */
public class ReceiverAdapter implements Receiver {

    public void receive(Message msg) {
    }

    public byte[] getState() {
        return null;
    }

    public void setState(byte[] state) {
    }

    public void viewAccepted(View new_view) {
    }

    public void suspect(Address suspected_mbr) {
    }

    public void block() {
    }
}
