// $Id: Retransmitter.java,v 1.1 2009/07/30 00:58:14 phperret Exp $

package org.jgroups.stack;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jgroups.Address;
import org.jgroups.util.TimeScheduler;
import org.jgroups.util.Util;

import java.util.*;
import java.util.concurrent.Future;


/**
 * Maintains a pool of sequence numbers of messages that need to be retransmitted. Messages
 * are aged and retransmission requests sent according to age (linear backoff used). If a
 * TimeScheduler instance is given to the constructor, it will be used, otherwise Reransmitter
 * will create its own. The retransmit timeouts have to be set first thing after creating an instance.
 * The <code>add()</code> method adds a range of sequence numbers of messages to be retransmitted. The
 * <code>remove()</code> method removes a sequence number again, cancelling retransmission requests for it.
 * Whenever a message needs to be retransmitted, the <code>RetransmitCommand.retransmit()</code> method is called.
 * It can be used e.g. by an ack-based scheme (e.g. AckSenderWindow) to retransmit a message to the receiver, or
 * by a nak-based scheme to send a retransmission request to the sender of the missing message.
 *
 * @author John Giorgiadis
 * @author Bela Ban
 * @version $Revision: 1.1 $
 */
public class Retransmitter {

    private static final long SEC=1000;
    /** Default retransmit intervals (ms) - exponential approx. */
    private static long[] RETRANSMIT_TIMEOUTS={2 * SEC, 3 * SEC, 5 * SEC, 8 * SEC};

    private Address              sender=null;
    private final List<Entry>    msgs=new LinkedList<Entry>();  // List<Entry> of elements to be retransmitted
    private RetransmitCommand    cmd=null;
    private boolean              retransmitter_owned;
    private TimeScheduler        timer=null;
    protected static final Log   log=LogFactory.getLog(Retransmitter.class);


    /** Retransmit command (see Gamma et al.) used to retrieve missing messages */
    public interface RetransmitCommand {
        /**
         * Get the missing messages between sequence numbers
         * <code>first_seqno</code> and <code>last_seqno</code>. This can either be done by sending a
         * retransmit message to destination <code>sender</code> (nak-based scheme), or by
         * retransmitting the missing message(s) to <code>sender</code> (ack-based scheme).
         * @param first_seqno The sequence number of the first missing message
         * @param last_seqno  The sequence number of the last missing message
         * @param sender The destination of the member to which the retransmit request will be sent
         *               (nak-based scheme), or to which the message will be retransmitted (ack-based scheme).
         */
        void retransmit(long first_seqno, long last_seqno, Address sender);
    }


    /**
     * Create a new Retransmitter associated with the given sender address
     * @param sender the address from which retransmissions are expected or to which retransmissions are sent
     * @param cmd the retransmission callback reference
     * @param sched retransmissions scheduler
     */
    public Retransmitter(Address sender, RetransmitCommand cmd, TimeScheduler sched) {
        init(sender, cmd, sched, false);
    }


    /**
     * Create a new Retransmitter associated with the given sender address
     * @param sender the address from which retransmissions are expected or to which retransmissions are sent
     * @param cmd the retransmission callback reference
     */
    public Retransmitter(Address sender, RetransmitCommand cmd) {
        init(sender, cmd, new TimeScheduler(), true);
    }


    public void setRetransmitTimeouts(long[] timeouts) {
        if(timeouts != null)
            RETRANSMIT_TIMEOUTS=timeouts;
    }


    /**
     * Add the given range [first_seqno, last_seqno] in the list of
     * entries eligible for retransmission. If first_seqno > last_seqno,
     * then the range [last_seqno, first_seqno] is added instead
     * <p>
     * If retransmitter thread is suspended, wake it up
     */
    public void add(long first_seqno, long last_seqno) {
        if(first_seqno > last_seqno) {
            long tmp=first_seqno;
            first_seqno=last_seqno;
            last_seqno=tmp;
        }

        Entry entry=new Entry(first_seqno, last_seqno, RETRANSMIT_TIMEOUTS);
        synchronized(msgs) {
            msgs.add(entry);
        }
        entry.doSchedule(timer); // Entry adds itself to the timer
    }

    /**
     * Remove the given sequence number from the list of seqnos eligible
     * for retransmission. If there are no more seqno intervals in the
     * respective entry, cancel the entry from the retransmission
     * scheduler and remove it from the pending entries
     */
    public void remove(long seqno) {
        Entry e;

        synchronized(msgs) {
            for(ListIterator<Entry> it=msgs.listIterator(); it.hasNext();) {
                e=it.next();
                if(seqno < e.low || seqno > e.high) continue;
                e.remove(seqno);
                if(e.low > e.high) {
                    e.cancel();
                    it.remove();
                }
                break;
            }
        }
    }

    /**
     * Reset the retransmitter: clear all msgs and cancel all the
     * respective tasks
     */
    public void reset() {
        synchronized(msgs) {
            for(Entry entry: msgs) {
                entry.cancel();
            }
            msgs.clear();
        }
    }

    /**
     * Stop the rentransmition and clear all pending msgs.
     * <p>
     * If this retransmitter has been provided  an externally managed
     * scheduler, then just clear all msgs and the associated tasks, else
     * stop the scheduler. In this case the method blocks until the
     * scheduler's thread is dead. Only the owner of the scheduler should
     * stop it.
     */
    public void stop() {
        // i. If retransmitter is owned, stop it else cancel all tasks
        // ii. Clear all pending msgs
        synchronized(msgs) {
            if(retransmitter_owned) {
                try {
                    timer.stop();
                }
                catch(InterruptedException ex) {
                    if(log.isErrorEnabled()) log.error("failed stopping retransmitter", ex);
                    Thread.currentThread().interrupt(); // set interrupt flag again
                }
            }
            else {
                for(Entry entry: msgs) {
                    entry.cancel();
                }
            }
            msgs.clear();
        }
    }


    public String toString() {
        synchronized(msgs) {
            int size=size();
            StringBuilder sb=new StringBuilder();
            sb.append(size).append(" messages to retransmit: ").append(msgs);
            return sb.toString();
        }
    }


    public int size() {
        int size=0;
        synchronized(msgs) {
            for(Entry entry: msgs) {
                size+=entry.size();
            }
        }
        return size;
    }




    /* ------------------------------- Private Methods -------------------------------------- */

    /**
     * Init this object
     *
     * @param sender the address from which retransmissions are expected
     * @param cmd the retransmission callback reference
     * @param sched retransmissions scheduler
     * @param sched_owned whether the scheduler parameter is owned by this
     * object or is externally provided
     */
    private void init(Address sender, RetransmitCommand cmd, TimeScheduler sched, boolean sched_owned) {
        this.sender=sender;
        this.cmd=cmd;
        retransmitter_owned=sched_owned;
        timer=sched;
    }


    /* ---------------------------- End of Private Methods ------------------------------------ */



    /**
     * The retransmit task executed by the scheduler in regular intervals
     */
    private static abstract class Task implements TimeScheduler.Task {
        private final Interval intervals;
        private Future         future;

        protected Task(long[] intervals) {
            this.intervals=new StaticInterval(intervals);
        }

        public long nextInterval() {
            return intervals.next();
        }

        public void doSchedule(TimeScheduler timer) {
            future=timer.scheduleWithDynamicInterval(this);
        }

        public void cancel() {
            if(future != null)
                future.cancel(false);
        }
    }


    /**
     * The entry associated with an initial group of missing messages
     * with contiguous sequence numbers and with all its subgroups.<br>
     * E.g.
     * - initial group: [5-34]
     * - msg 12 is acknowledged, now the groups are: [5-11], [13-34]
     * <p>
     * Groups are stored in a list as long[2] arrays of the each group's
     * bounds. For speed and convenience, the lowest & highest bounds of
     * all the groups in this entry are also stored separately
     */
    private class Entry extends Task {
        private long low;
        private long high;
        /** List<long[2]> of ranges to be retransmitted */
        final java.util.List<long[]> list=new ArrayList<long[]>();

        public Entry(long low, long high, long[] intervals) {
            super(intervals);
            this.low=low;
            this.high=high;
            list.add(new long[]{low, high});
        }

        /**
         * Remove the given seqno and resize or partition groups as
         * necessary. The algorithm is as follows:<br>
         * i. Find the group with low <= seqno <= high
         * ii. If seqno == low,
         *	a. if low == high, then remove the group
         *	Adjust global low. If global low was pointing to the group
         * deleted in the previous step, set it to point to the next group.
         * If there is no next group, set global low to be higher than
         * global high. This way the entry is invalidated and will be removed
         * all together from the pending msgs and the task scheduler
         * iii. If seqno == high, adjust high, adjust global high if this is
         * the group at the tail of the list
         * iv. Else low < seqno < high, break [low,high] into [low,seqno-1]
         * and [seqno+1,high]
         *
         * @param seqno the sequence number to remove
         */
        public void remove(long seqno) {
            int i;
            long[] bounds=null, newBounds;

            synchronized(list) {
                for(i=0; i < list.size(); ++i) {
                    bounds=list.get(i);
                    if(seqno < bounds[0] || seqno > bounds[1]) continue;
                    break;
                }
                if(i == list.size()) return;

                if(seqno == bounds[0]) {
                    if(bounds[0] == bounds[1])
                        list.remove(i);
                    else
                        bounds[0]++;
                    if(i == 0)
                        low=list.isEmpty()? high + 1 : list.get(i)[0];
                }
                else if(seqno == bounds[1]) {
                    bounds[1]--;
                    if(i == list.size() - 1) high=list.get(i)[1];
                }
                else {
                    newBounds=new long[2];
                    newBounds[0]=seqno + 1;
                    newBounds[1]=bounds[1];
                    bounds[1]=seqno - 1;
                    list.add(i + 1, newBounds);
                }
            }
        }

        /**
         * Retransmission task:<br>
         * For each interval, call the retransmission callback command
         */
        public void run() {
            long[] bounds;
            List copy;

            synchronized(list) {
                copy=new LinkedList<long[]>(list);
            }

            for(Iterator it=copy.iterator(); it.hasNext();) {
                bounds=(long[])it.next();
                try {
                    cmd.retransmit(bounds[0], bounds[1], sender);
                }
                catch(Throwable t) {
                    log.error("failure asking " + cmd + " for retransmission", t);
                }
            }
        }

        int size() {
            int size=0;
            long diff;
            long[] tmp;
            synchronized(list) {
                for(Iterator it=list.iterator(); it.hasNext();) {
                    tmp=(long[])it.next();
                    diff=tmp[1] - tmp[0] +1;
                    size+=diff;
                }
            }

            return size;
        }


        public String toString() {
            StringBuilder sb=new StringBuilder();
            synchronized(list) {
                long[] range;
                boolean first=true;
                for(Iterator it=list.iterator(); it.hasNext();) {
                    range=(long[])it.next();
                    if(first) {
                        first=false;
                    }
                    else {
                        sb.append(", ");
                    }
                    sb.append(range[0]).append('-').append(range[1]);
                }
            }

            return sb.toString();
        }

    }


    public static void main(String[] args) {
        Retransmitter xmitter;
        Address sender;

        try {
            sender=new org.jgroups.stack.IpAddress("localhost", 5555);
            xmitter=new Retransmitter(sender, new MyXmitter());
            xmitter.setRetransmitTimeouts(new long[]{1000, 2000, 4000, 8000});

            xmitter.add(1, 10);
            System.out.println("retransmitter: " + xmitter);
            xmitter.remove(1);
            System.out.println("retransmitter: " + xmitter);
            xmitter.remove(2);
            System.out.println("retransmitter: " + xmitter);
            xmitter.remove(4);
            System.out.println("retransmitter: " + xmitter);

            Util.sleep(3000);
            xmitter.remove(3);
            System.out.println("retransmitter: " + xmitter);

            Util.sleep(1000);
            xmitter.remove(10);
            System.out.println("retransmitter: " + xmitter);
            xmitter.remove(8);
            System.out.println("retransmitter: " + xmitter);
            xmitter.remove(6);
            System.out.println("retransmitter: " + xmitter);
            xmitter.remove(7);
            System.out.println("retransmitter: " + xmitter);
            xmitter.remove(9);
            System.out.println("retransmitter: " + xmitter);
            xmitter.remove(5);
            System.out.println("retransmitter: " + xmitter);
        }
        catch(Exception e) {
            log.error(e);
        }
    }


    static class MyXmitter implements Retransmitter.RetransmitCommand {

        public void retransmit(long first_seqno, long last_seqno, Address sender) {
            System.out.println("-- " + new java.util.Date() + ": retransmit(" + first_seqno + ", " +
                               last_seqno + ", " + sender + ')');
        }
    }

    static void sleep(long timeout) {
        Util.sleep(timeout);
    }

}

