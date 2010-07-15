// $Id: JOLSR_UNICAST.java,v 1.1 2009/07/30 00:58:11 phperret Exp $

package org.jgroups.protocols;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Vector;
import java.util.concurrent.locks.ReentrantLock;

import org.jgroups.Address;
import org.jgroups.Event;
import org.jgroups.Global;
import org.jgroups.Header;
import org.jgroups.MergeView;
import org.jgroups.Message;
import org.jgroups.View;
import org.jgroups.stack.AckReceiverWindow;
import org.jgroups.stack.AckSenderWindow;
import org.jgroups.stack.Protocol;
import org.jgroups.stack.StaticInterval;
import org.jgroups.stack.AckSenderWindow.RetransmitCommand;
import org.jgroups.util.BoundedList;
import org.jgroups.util.Streamable;
import org.jgroups.util.TimeScheduler;
import org.jgroups.util.Util;

/**
 * Reliable unicast layer. Uses acknowledgement scheme similar to TCP to provide lossless transmission
 * of unicast messages (for reliable multicast see NAKACK layer). When a message is sent to a peer for
 * the first time, we add the pair <peer_addr, Entry> to the hashtable (peer address is the key). All
 * messages sent to that peer will be added to hashtable.peer_addr.sent_msgs. When we receive a
 * message from a peer for the first time, another entry will be created and added to the hashtable
 * (unless already existing). Msgs will then be added to hashtable.peer_addr.received_msgs.<p> This
 * layer is used to reliably transmit point-to-point messages, that is, either messages sent to a
 * single receiver (vs. messages multicast to a group) or for example replies to a multicast message. The 
 * sender uses an <code>AckSenderWindow</code> which retransmits messages for which it hasn't received
 * an ACK, the receiver uses <code>AckReceiverWindow</code> which keeps track of the lowest seqno
 * received so far, and keeps messages in order.<p>
 * Messages in both AckSenderWindows and AckReceiverWindows will be removed. A message will be removed from
 * AckSenderWindow when an ACK has been received for it and messages will be removed from AckReceiverWindow
 * whenever a message is received: the new message is added and then we try to remove as many messages as
 * possible (until we stop at a gap, or there are no more messages).
 * @author Bela Ban
 */
public class JOLSR_UNICAST extends Protocol implements AckSenderWindow.RetransmitCommand {
    private final static String name="JOLSR_UNICAST";
    private static final long DEFAULT_FIRST_SEQNO=1;
    private final Vector<Address> members=new Vector<Address>(11);
    private final HashMap<Address,Entry> connections=new HashMap<Address,Entry>(11);
    private long[]                timeout={400,800,1600,3200};  // for AckSenderWindow: max time to wait for missing acks
    private Address               local_addr=null;

    private TimeScheduler         timer=null;                    // used for retransmissions (passed to AckSenderWindow)
    private Map<Thread,ReentrantLock> locks;

    // if JOLSR_UNICAST is used without GMS, don't consult the membership on retransmit() if use_gms=false
    // default is true
    private boolean          use_gms=true;

    private boolean          started=false;
    /** whether to loop back messages sent to self (will be removed in the future, default=false) */
    private boolean          loopback=false;

    /** A list of members who left, used to determine when to prevent sending messages to left mbrs */
    private final BoundedList previous_members=new BoundedList(50);
    /** Contains all members that were enabled for unicasts by Event.ENABLE_UNICAST_TO */
    private final BoundedList enabled_members=new BoundedList(100);

    private long num_msgs_sent=0, num_msgs_received=0, num_bytes_sent=0, num_bytes_received=0;
    private long num_acks_sent=0, num_acks_received=0, num_xmit_requests_received=0;


    public Object down(Event evt) {
        switch (evt.getType()) {

            case Event.MSG: // Add UnicastHeader, add to AckSenderWindow and pass down
                Message msg=(Message) evt.getArg();
                Address  dst=msg.getDest();
                /* only handle unicast messages */
                if (dst == null || dst.isMulticastAddress()) {
                    break;
                }
                
                //Marc added: if the destination is broadcast,
                //do not add it to retransmit, just pass it down
                if (dst.toString().split(":")[0].equals("255.255.255.255")){
                	return down_prot.down(evt);
                }

                if(!started) {
                    if(log.isWarnEnabled())
                        log.warn("discarded message as start() has not yet been called, message: " + msg);
                    return null;
                }

                // if the dest is self --> pass the message back up
                if(local_addr != null && loopback && local_addr.equals(dst)) {
                    msg.setSrc(local_addr);
                    up_prot.up(evt);
                    num_msgs_sent++;
                    num_bytes_sent+=msg.getLength();
                    return null;
                }

                //if(previous_members.contains(dst)) {
                  //  throw new IllegalArgumentException("discarding message to " + dst + " as this member left the group," +
                    //        " previous_members=" + previous_members);
                //}

                //OMOLSR CHANGE
//                if(!members.contains(dst) && !enabled_members.contains(dst)) {
//                	System.err.println(dst + " is not a member of the group " + members + " (enabled_members=" + enabled_members + ")");
//                    throw new IllegalArgumentException(dst + " is not a member of the group " + members + " (enabled_members=" + enabled_members + ")");
//                }

                Entry entry;
                synchronized(connections) {
                    entry=connections.get(dst);
                    if(entry == null) {
                        entry=new Entry();
                        connections.put(dst, entry);
                        if(log.isTraceEnabled())
                            log.trace(local_addr + ": created new connection for dst " + dst);
                    }
                }

                Message tmp;
                synchronized(entry) { // threads will only sync if they access the same entry
                    long seqno=-2;

                    try {
                        seqno=entry.sent_msgs_seqno;
                        UnicastHeader hdr=new UnicastHeader(UnicastHeader.DATA, seqno);
                        if(entry.sent_msgs == null) { // first msg to peer 'dst'
                        	StaticInterval interval=new StaticInterval(timeout);
                            entry.sent_msgs=new AckSenderWindow((RetransmitCommand)this, interval.getValues(), timer, this.local_addr); // use the protocol stack's timer
                        }
                        msg.putHeader(name, hdr);
                        if(log.isTraceEnabled())
                            log.trace(new StringBuffer().append(local_addr).append(" --> DATA(").append(dst).append(": #").
                                    append(seqno));
                        tmp=msg;
                        entry.sent_msgs.add(seqno, tmp);  // add *including* UnicastHeader, adds to retransmitter
                        entry.sent_msgs_seqno++;
                    }
                    catch(Throwable t) {
                        entry.sent_msgs.ack(seqno); // remove seqno again, so it is not transmitted
                        if(t instanceof Error)
                            throw (Error)t;
                        if(t instanceof RuntimeException)
                            throw (RuntimeException)t;
                        else {
                            throw new RuntimeException("failure adding msg " + msg + " to the retransmit table", t);
                        }
                    }
                }
                // moved passing down of message out of the synchronized block: similar to NAKACK, we do *not* need
                // to send unicast messages in order of sequence numbers because they will be sorted into the correct
                // order at the receiver anyway. Of course, most of the time, the order will be correct (FIFO), so
                // the cost of reordering is minimal. This is part of http://jira.jboss.com/jira/browse/JGRP-303
                try {
                    down_prot.down(new Event(Event.MSG, tmp));
                    num_msgs_sent++;
                    num_bytes_sent+=msg.getLength();
                }
                catch(Throwable t) { // eat the exception, don't pass it up the stack
                    if(log.isWarnEnabled()) {
                        log.warn("failure passing message down", t);
                    }
                }

                msg=null;
                return null; // we already passed the msg down
        }

        return down_prot.down(evt);          // Pass on to the layer below us
    }

    public Map<String, Object> dumpStats() {
        Map<String,Object> m=new HashMap<String,Object>();
        m.put("num_msgs_sent", new Long(num_msgs_sent));
        m.put("num_msgs_received", new Long(num_msgs_received));
        m.put("num_bytes_sent", new Long(num_bytes_sent));
        m.put("num_bytes_received", new Long(num_bytes_received));
        m.put("num_acks_sent", new Long(num_acks_sent));
        m.put("num_acks_received", new Long(num_acks_received));
        m.put("num_xmit_requests_received", new Long(num_xmit_requests_received));
        m.put("num_unacked_msgs", new Long(getNumberOfUnackedMessages()));
        m.put("unacked_msgs", getUnackedMessages());
        m.put("num_msgs_in_recv_windows", new Long(getNumberOfMessagesInReceiveWindows()));
        return m;
    }
    public String getLocalAddress() {return local_addr != null? local_addr.toString() : "null";}
    public String getMembers() {return members != null? members.toString() : "[]";}


    /** All protocol names have to be unique ! */
    public String  getName() {return name;}

    public long getNumAcksReceived() {
        return num_acks_received;
    }

    public long getNumAcksSent() {
        return num_acks_sent;
    }

    public int getNumberOfMessagesInReceiveWindows() {
        int num=0;
        synchronized(connections) {
            for(Entry entry: connections.values()) {
                if(entry.received_msgs != null)
                    num+=entry.received_msgs.size();
            }
        }
        return num;
    }

    public long getNumberOfRetransmitRequestsReceived() {
        return num_xmit_requests_received;
    }

    /** The number of messages in all Entry.sent_msgs tables (haven't received an ACK yet) */
    public int getNumberOfUnackedMessages() {
        int num=0;
        synchronized(connections) {
            for(Entry entry: connections.values()) {
                if(entry.sent_msgs != null)
                    num+=entry.sent_msgs.size();
            }
        }
        return num;
    }

    public long getNumBytesReceived() {
        return num_bytes_received;
    }

    public long getNumBytesSent() {
        return num_bytes_sent;
    }


    public long getNumMessagesReceived() {
        return num_msgs_received;
    }


    public long getNumMessagesSent() {
        return num_msgs_sent;
    }

    public String getUnackedMessages() {
        StringBuilder sb=new StringBuilder();
        Entry e;
        Object member;
        synchronized(connections) {
            for(Map.Entry<Address,Entry> entry: connections.entrySet()) {
                member=entry.getKey();
                e=entry.getValue();
                sb.append(member).append(": ");
                if(e.sent_msgs != null)
                    sb.append(e.sent_msgs.toString()).append("\n");
            }
        }
        return sb.toString();
    }


    public String printConnections() {
        StringBuilder sb=new StringBuilder();
        for(Map.Entry<Address,Entry> entry: connections.entrySet()) {
            sb.append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
        }
        return sb.toString();
    }

    public void resetStats() {
        num_msgs_sent=num_msgs_received=num_bytes_sent=num_bytes_received=num_acks_sent=num_acks_received=0;
        num_xmit_requests_received=0;
    }

    /** Called by AckSenderWindow to resend messages for which no ACK has been received yet */
    public void retransmit(long seqno, Message msg) {
        Object  dst=msg.getDest();

        // bela Dec 23 2002:
        // this will remove a member on a MERGE request, e.g. A and B merge: when A sends the unicast
        // request to B and there's a retransmit(), B will be removed !

        //          if(use_gms && !members.contains(dst) && !prev_members.contains(dst)) {
        //
        //                  if(log.isWarnEnabled()) log.warn("UNICAST.retransmit()", "seqno=" + seqno + ":  dest " + dst +
        //                             " is not member any longer; removing entry !");

        //              synchronized(connections) {
        //                  removeConnection(dst);
        //              }
        //              return;
        //          }

        if(log.isTraceEnabled())
            log.trace("[" + local_addr + "] --> XMIT(" + dst + ": #" + seqno + ')');
        System.err.println("*** Resending message: msgSrc:"+msg.getSrc()+" msgDst:"+msg.getDest()+" from " + local_addr + " to --> XMIT(" + dst + ": #" + seqno + ')');
        down_prot.down(new Event(Event.MSG, msg));
        num_xmit_requests_received++;
    }

    public boolean setProperties(Properties props) {
        String     str;
        long[]     tmp;

        super.setProperties(props);
        str=props.getProperty("timeout");
        if(str != null) {
        tmp=Util.parseCommaDelimitedLongs(str);
        if(tmp != null && tmp.length > 0)
        timeout=tmp;
            props.remove("timeout");
        }

        str=props.getProperty("window_size");
        if(str != null) {
            props.remove("window_size");
            log.warn("window_size is deprecated and will be ignored");
        }

        str=props.getProperty("min_threshold");
        if(str != null) {
            props.remove("min_threshold");
            log.warn("min_threshold is deprecated and will be ignored");
        }

        str=props.getProperty("use_gms");
        if(str != null) {
            use_gms=Boolean.valueOf(str).booleanValue();
            props.remove("use_gms");
        }

        str=props.getProperty("loopback");
        if(str != null) {
            loopback=Boolean.valueOf(str).booleanValue();
            props.remove("loopback");
        }

        if(!props.isEmpty()) {
            log.error("these properties are not recognized: " + props);
            return false;
        }
        return true;
    }


    public void start() throws Exception {
        timer=stack != null ? stack.timer: null;
        if(timer == null)
            throw new Exception("timer is null");
        locks=stack.getLocks();
        started=true;
    }



    public void stop() {
        started=false;
        removeAllConnections();
    }


    public Object up(Event evt) {
        Message        msg;
        Address        dst, src;
        UnicastHeader  hdr;

        switch(evt.getType()) {

        case Event.MSG:
            msg=(Message)evt.getArg();
            dst=msg.getDest();
            
            if(dst == null || dst.isMulticastAddress())  // only handle unicast messages
                break;  // pass up

            // changed from removeHeader(): we cannot remove the header because if we do loopback=true at the
            // transport level, we will not have the header on retransmit ! (bela Aug 22 2006)
            hdr=(UnicastHeader)msg.getHeader(name);
            if(hdr == null)
                break;
            src=msg.getSrc();
            switch(hdr.type) {
            case UnicastHeader.DATA:      // received regular message
               if(handleDataReceived(src, hdr.seqno, msg))
                    sendAck(src, hdr.seqno); // only send an ACK if added to the received_msgs table (bela Aug 2006)
                
                return null; // we pass the deliverable message up in handleDataReceived()
            case UnicastHeader.ACK:  // received ACK for previously sent message
                handleAckReceived(src, hdr.seqno);
                break;
            default:
                log.error("UnicastHeader type " + hdr.type + " not known !");
                break;
            }
            return null;

        case Event.SET_LOCAL_ADDRESS:
            local_addr=(Address)evt.getArg();
            break;
            
        case Event.VIEW_CHANGE:  // remove connections to peers that are not members anymore !
            View view=(View)evt.getArg();
            Vector<Address> new_members=view.getMembers();
            Vector<Address> left_members;
            synchronized(members) {
                left_members=Util.determineLeftMembers(members, new_members);
                members.clear();
                if(new_members != null)
                    members.addAll(new_members);
            }

            // Remove all connections for members that left between the current view and the new view
            // See DESIGN for details
            boolean rc;
            if(use_gms && !left_members.isEmpty()) {
                Address mbr;
                for(int i=0; i < left_members.size(); i++) {
                    mbr=left_members.elementAt(i);
                    rc=removeConnection(mbr); // adds to previous_members
                    if(rc && log.isTraceEnabled())
                        log.trace("removed " + mbr + " from connection table, member(s) " + left_members + " left");
                }
            }
            // code by Matthias Weber May 23 2006
            Enumeration en1= previous_members.elements();
            for( ; en1.hasMoreElements(); ) {
                Address mbr=(Address)en1.nextElement();
                if(members.contains(mbr)) {
                    if( (Boolean) previous_members.removeElement((mbr) )) {
                        if(log.isTraceEnabled())
                            log.trace("removed " + mbr + " from previous_members as result of VIEW_CHANGE event, " +
                                    "previous_members=" + previous_members);
                    }
                }
            }
            // remove all members from enabled_members
            synchronized(members) {
                for(Address mbr: members) {
                    enabled_members.removeElement(mbr);
                }
            }

            synchronized(previous_members) {
            	Enumeration en=previous_members.elements();
                for(; en.hasMoreElements(); ) {
                    Address mbr=(Address)en.nextElement();
                    enabled_members.removeElement(mbr);
                }
            }

            // trash connections to/from members who are in the merge view, fix for: http://jira.jboss.com/jira/browse/JGRP-348
            if(view instanceof MergeView) {
                if(log.isTraceEnabled())
                    log.trace("removing all connections for the current members due to a merge");
                removeConnections(members);
            }

            break;

        case Event.ENABLE_UNICASTS_TO:
            Address member=(Address)evt.getArg();
            if(!enabled_members.contains(member))
                enabled_members.add(member);
            Object obj=previous_members.removeElement(member);
            if(obj != null && log.isTraceEnabled())
                log.trace("removing " + member + " from previous_members as result of ENABLE_UNICAST_TO event, " +
                        "previous_members=" + previous_members);
            break;

        case Event.DISABLE_UNICASTS_TO:
            member=(Address)evt.getArg();
            removeConnection(member);
            enabled_members.removeElement(member);
            previous_members.removeElement(member);
            break;
        }

        return up_prot.up(evt);   // Pass up to the layer above us
    }


    /** Add the ACK to hashtable.sender.sent_msgs */
    private void handleAckReceived(Address sender, long seqno) {
        Entry           entry;
        AckSenderWindow win;
        //OMOLSR CHANGE
       // System.err.println("Received ack from "+sender+" to "+local_addr+": #"+seqno+")");
        if(log.isTraceEnabled())
            log.trace(new StringBuffer().append(local_addr).append(" <-- ACK(").append(sender).
                      append(": #").append(seqno).append(')'));
        synchronized(connections) {
            entry=connections.get(sender);
        }
        if(entry == null || entry.sent_msgs == null)
            return;
        win=entry.sent_msgs;
        //OLSR CHANGE
        ////System.err.println("And removing seqno #"+seqno+" for sender"+sender+" from retransmission list");
        win.ack(seqno); // removes message from retransmission
        num_acks_received++;
    }


    /**
     * Check whether the hashtable contains an entry e for <code>sender</code> (create if not). If
     * e.received_msgs is null and <code>first</code> is true: create a new AckReceiverWindow(seqno) and
     * add message. Set e.received_msgs to the new window. Else just add the message.
     * @return boolean True if we can send an ack, false otherwise
     */
    private boolean handleDataReceived(Address sender, long seqno, Message msg) {
        if(log.isTraceEnabled())
            log.trace(new StringBuffer().append(local_addr).append(" <-- DATA(").append(sender).append(": #").append(seqno));

        if(previous_members.contains(sender)) {
            // we don't want to see messages from departed members
            if(seqno > DEFAULT_FIRST_SEQNO) {
                if(log.isTraceEnabled())
                    log.trace("discarding message " + seqno + " from previous member " + sender);
                return false; // don't ack this message so the sender keeps resending it !
            }
            if(log.isTraceEnabled())
                log.trace("removed " + sender + " from previous_members as we received a message from it");
            previous_members.removeElement(sender);
        }

        Entry    entry;
        AckReceiverWindow win;
        synchronized(connections) {
            entry=connections.get(sender);
            if(entry == null) {
                entry=new Entry();
                connections.put(sender, entry);
                if(log.isTraceEnabled())
                    log.trace(local_addr + ": created new connection for dst " + sender);
            }
            win=entry.received_msgs;
            if(win == null) {
                win=new AckReceiverWindow(DEFAULT_FIRST_SEQNO);
                entry.received_msgs=win;
            }
        }

        boolean added=win.add(seqno, msg); // entry.received_msgs is guaranteed to be non-null if we get here
        num_msgs_received++;
        num_bytes_received+=msg.getLength();

        // message is passed up if OOB. Later, when remove() is called, we discard it. This affects ordering !
        // http://jira.jboss.com/jira/browse/JGRP-377
        if(msg.isFlagSet(Message.OOB) && added) {
            up_prot.up(new Event(Event.MSG, msg));
        }

        // Try to remove (from the AckReceiverWindow) as many messages as possible as pass them up
        Message  m;

        // Prevents concurrent passing up of messages by different threads (http://jira.jboss.com/jira/browse/JGRP-198);
        // this is all the more important once we have a threadless stack (http://jira.jboss.com/jira/browse/JGRP-181),
        // where lots of threads can come up to this point concurrently, but only 1 is allowed to pass at a time
        // We *can* deliver messages from *different* senders concurrently, e.g. reception of P1, Q1, P2, Q2 can result in
        // delivery of P1, Q1, Q2, P2: FIFO (implemented by UNICAST) says messages need to be delivered only in the
        // order in which they were sent by their senders
        ReentrantLock lock=win.getLock();
        lock.lock(); // we don't block on entry any more (http://jira.jboss.com/jira/browse/JGRP-485)
        try {
            locks.put(Thread.currentThread(), lock);
            while((m=win.remove()) != null) {
                // discard OOB msg as it has already been delivered (http://jira.jboss.com/jira/browse/JGRP-377)
                if(m.isFlagSet(Message.OOB)) {
                    continue;
                }
                up_prot.up(new Event(Event.MSG, m));
            }
        }
        finally {
            locks.remove(Thread.currentThread());
            if(lock.isHeldByCurrentThread())
                lock.unlock();
        }
        return true; // msg was successfully received - send an ack back to the sender
    }



    private void removeAllConnections() {
        synchronized(connections) {
            for(Entry entry: connections.values()) {
                entry.reset();
            }
            connections.clear();
        }
    }





    /** Removes and resets from connection table (which is already locked). Returns true if member was found, otherwise false */
    private boolean removeConnection(Address mbr) {
        Entry entry;

        synchronized(connections) {
            entry=connections.remove(mbr);
            if(!previous_members.contains(mbr))
                previous_members.add(mbr);
        }
        if(entry != null) {
            entry.reset();
            return true;
        }
        else
            return false;
    }


    private void removeConnections(List<Address> mbrs) {
        Entry entry;
        synchronized(connections) {
            for(Address mbr: mbrs) {
                entry=connections.remove(mbr);
                if(entry != null)
                    entry.reset();
            }
        }
    }



    private void sendAck(Address dst, long seqno) {
        Message ack=new Message(dst);
        ack.setFlag(Message.OOB);
        ack.putHeader(name, new UnicastHeader(UnicastHeader.ACK, seqno));
        //OMOLSR CHANGE
       // System.err.println("Sending ack from "+local_addr+" --> ACK("+(dst)+": #"+seqno+")");
        if(log.isTraceEnabled())
            log.trace(new StringBuffer().append(local_addr).append(" --> ACK(").append(dst).
                      append(": #").append(seqno).append(')'));
        down_prot.down(new Event(Event.MSG, ack));
        num_acks_sent++;
    }






    public static class UnicastHeader extends Header implements Streamable {
        public static final byte DATA=0;
        public static final byte ACK=1;

        private static final long serialVersionUID=-5590873777959784299L;
        static final int serialized_size=Global.BYTE_SIZE + Global.LONG_SIZE;

        byte    type=DATA;
        long    seqno=0;


        public UnicastHeader() {} // used for externalization

        public UnicastHeader(byte type, long seqno) {
            this.type=type;
            this.seqno=seqno;
        }

        public static String type2Str(byte t) {
            switch(t) {
                case DATA: return "DATA";
                case ACK: return "ACK";
                default: return "<unknown>";
            }
        }

        public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
            type=in.readByte();
            seqno=in.readLong();
        }

        public void readFrom(DataInputStream in) throws IOException, IllegalAccessException, InstantiationException {
            type=in.readByte();
            seqno=in.readLong();
        }


        public final int size() {
            return serialized_size;
        }


        public String toString() {
            return "[JOLSR_UNICAST: " + type2Str(type) + ", seqno=" + seqno + ']';
        }

        public void writeExternal(ObjectOutput out) throws IOException {
            out.writeByte(type);
            out.writeLong(seqno);
        }

        public void writeTo(DataOutputStream out) throws IOException {
            out.writeByte(type);
            out.writeLong(seqno);
        }
    }

    private static final class Entry {
        AckReceiverWindow  received_msgs=null;  // stores all msgs rcvd by a certain peer in seqno-order
        AckSenderWindow    sent_msgs=null;      // stores (and retransmits) msgs sent by us to a certain peer
        long               sent_msgs_seqno=DEFAULT_FIRST_SEQNO;   // seqno for msgs sent by us

        public String toString() {
            StringBuilder sb=new StringBuilder();
            if(sent_msgs != null)
                sb.append("sent_msgs=").append(sent_msgs).append('\n');
            if(received_msgs != null)
                sb.append("received_msgs=").append(received_msgs).append('\n');
            return sb.toString();
        }


        void reset() {
            if(sent_msgs != null)
                sent_msgs.reset();
            if(received_msgs != null)
                received_msgs.reset();
            sent_msgs_seqno=DEFAULT_FIRST_SEQNO;
        }
    }


}
