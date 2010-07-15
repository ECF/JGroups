// $Id: AckReceiverWindow.java,v 1.1 2009/07/30 00:58:14 phperret Exp $

package org.jgroups.stack;


import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jgroups.Message;

import java.util.HashMap;
import java.util.TreeSet;
import java.util.concurrent.locks.ReentrantLock;


/**
 * Counterpart of AckSenderWindow. Simple FIFO buffer.
 * Every message received is ACK'ed (even duplicates) and added to a hashmap
 * keyed by seqno. The next seqno to be received is stored in <code>next_to_remove</code>. When a message with
 * a seqno less than next_to_remove is received, it will be discarded. The <code>remove()</code> method removes
 * and returns a message whose seqno is equal to next_to_remove, or null if not found.<br>
 * Change May 28 2002 (bela): replaced TreeSet with HashMap. Keys do not need to be sorted, and adding a key to
 * a sorted set incurs overhead.
 *
 * @author Bela Ban
 */
public class AckReceiverWindow {
    long              next_to_remove=0;
    final HashMap     msgs=new HashMap();  // keys: seqnos (Long), values: Messages
    static final Log  log=LogFactory.getLog(AckReceiverWindow.class);
    final ReentrantLock     lock=new ReentrantLock();


    public AckReceiverWindow(long initial_seqno) {
        this.next_to_remove=initial_seqno;
    }

    public ReentrantLock getLock() {
        return lock;
    }

    /** Adds a new message. Message cannot be null
     * @return True if the message was added, false if not (e.g. duplicate, message was already present)
     */
    public boolean add(long seqno, Message msg) {
        if(msg == null)
            throw new IllegalArgumentException("msg must be non-null");
        synchronized(msgs) {
            if(seqno < next_to_remove) {
                if(log.isTraceEnabled())
                    log.trace("discarded msg with seqno=" + seqno + " (next msg to receive is " + next_to_remove + ')');
                return false;
            }
            Long seq=new Long(seqno);
            if(!msgs.containsKey(seq)) { // todo: replace with atomic action once we have util.concurrent (JDK 5)
                msgs.put(seq, msg);
                return true;
            }
            else {
                if(log.isTraceEnabled())
                    log.trace("seqno " + seqno + " already received - dropping it");
                return false;
            }
        }
    }


    /**
     * Removes a message whose seqno is equal to <code>next_to_remove</code>, increments the latter.
     * Returns message that was removed, or null, if no message can be removed. Messages are thus
     * removed in order.
     */
    public Message remove() {
        Message retval;

        synchronized(msgs) {
            Long key=new Long(next_to_remove);
            retval=(Message)msgs.remove(key);
            if(retval != null) {
                if(log.isTraceEnabled())
                    log.trace("removed seqno=" + next_to_remove);
                next_to_remove++;
            }
        }
        return retval;
    }


    public void reset() {
        synchronized(msgs) {
            msgs.clear();
        }
    }

    public int size() {
        return msgs.size();
    }

    public String toString() {
        StringBuffer sb=new StringBuffer();
        sb.append(msgs.size()).append(" msgs (").append("next=").append(next_to_remove).append(")");
        TreeSet s=new TreeSet(msgs.keySet());
        if(s.size() > 0) {
            sb.append(" [").append(s.first()).append(" - ").append(s.last()).append("]");
            sb.append(": ").append(s);
        }
        return sb.toString();
    }


    public String printDetails() {
        StringBuffer sb=new StringBuffer();
        sb.append(msgs.size()).append(" msgs (").append("next=").append(next_to_remove).append(")").
                append(", msgs=" ).append(new TreeSet(msgs.keySet()));
        return sb.toString();
    }


}
