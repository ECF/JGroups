// $Id: ClientGmsImpl.java,v 1.1 2009/07/30 00:58:14 phperret Exp $

package org.jgroups.protocols.pbcast;


import org.jgroups.*;
import org.jgroups.protocols.PingRsp;
import org.jgroups.util.Promise;
import org.jgroups.util.Util;
import org.jgroups.util.Digest;
import org.jgroups.util.MutableDigest;

import java.util.*;


/**
 * Client part of GMS. Whenever a new member wants to join a group, it starts in the CLIENT role.
 * No multicasts to the group will be received and processed until the member has been joined and
 * turned into a SERVER (either coordinator or participant, mostly just participant). This class
 * only implements <code>Join</code> (called by clients who want to join a certain group, and
 * <code>ViewChange</code> which is called by the coordinator that was contacted by this client, to
 * tell the client what its initial membership is.
 * @author Bela Ban
 * @version $Revision: 1.1 $
 */
public class ClientGmsImpl extends GmsImpl {
    private final Vector  initial_mbrs=new Vector(11);
    private boolean       initial_mbrs_received=false;
    private final Promise join_promise=new Promise();


    public ClientGmsImpl(GMS g) {
        super(g);
    }

    public void init() throws Exception {
        super.init();
        synchronized(initial_mbrs) {
            initial_mbrs.clear();
            initial_mbrs_received=false;
        }
        join_promise.reset();
    }


    /**
     * Joins this process to a group. Determines the coordinator and sends a unicast
     * handleJoin() message to it. The coordinator returns a JoinRsp and then broadcasts the new view, which
     * contains a message digest and the current membership (including the joiner). The joiner is then
     * supposed to install the new view and the digest and starts accepting mcast messages. Previous
     * mcast messages were discarded (this is done in PBCAST).<p>
     * If successful, impl is changed to an instance of ParticipantGmsImpl.
     * Otherwise, we continue trying to send join() messages to	the coordinator,
     * until we succeed (or there is no member in the group. In this case, we create our own singleton group).
     * <p>When GMS.disable_initial_coord is set to true, then we won't become coordinator on receiving an initial
     * membership of 0, but instead will retry (forever) until we get an initial membership of > 0.
     * @param mbr Our own address (assigned through SET_LOCAL_ADDRESS)
     */
    public void join(Address mbr) {
        Address coord;
        JoinRsp rsp;
        View    tmp_view;
        leaving=false;

        join_promise.reset();
        while(!leaving) {
            findInitialMembers();
            if(log.isDebugEnabled()) log.debug("initial_mbrs are " + initial_mbrs);
            if(initial_mbrs.isEmpty()) {
                if(gms.disable_initial_coord) {
                    if(log.isTraceEnabled())
                        log.trace("received an initial membership of 0, but cannot become coordinator " +
                                "(disable_initial_coord=true), will retry fetching the initial membership");
                    continue;
                }
                if(log.isDebugEnabled())
                    log.debug("no initial members discovered: creating group as first member");
                becomeSingletonMember(mbr);
                return;
            }

            coord=determineCoord(initial_mbrs);
            if(coord == null) { // e.g. because we have all clients only
                if(gms.handle_concurrent_startup == false) {
                    if(log.isTraceEnabled())
                        log.trace("handle_concurrent_startup is false; ignoring responses of initial clients");
                    becomeSingletonMember(mbr);
                    return;
                }

                if(log.isTraceEnabled())
                    log.trace("could not determine coordinator from responses " + initial_mbrs);

                // so the member to become singleton member (and thus coord) is the first of all clients
                Set<Address> clients=new TreeSet<Address>(); // sorted
                clients.add(mbr); // add myself again (was removed by findInitialMembers())
                for(int i=0; i < initial_mbrs.size(); i++) {
                    PingRsp pingRsp=(PingRsp)initial_mbrs.elementAt(i);
                    Address client_addr=pingRsp.getAddress();
                    if(client_addr != null)
                        clients.add(client_addr);
                }
                if(log.isTraceEnabled())
                    log.trace("clients to choose new coord from are: " + clients);
                Address new_coord=clients.iterator().next();
                if(new_coord.equals(mbr)) {
                    if(log.isTraceEnabled())
                        log.trace("I (" + mbr + ") am the first of the clients, will become coordinator");
                    becomeSingletonMember(mbr);
                    return;
                }
                else {
                    if(log.isTraceEnabled())
                        log.trace("I (" + mbr + ") am not the first of the clients, " +
                                "waiting for another client to become coordinator");
                    Util.sleep(500);
                }
                continue;
            }

            try {
                if(log.isDebugEnabled())
                    log.debug("sending handleJoin(" + mbr + ") to " + coord);
                sendJoinMessage(coord, mbr);
                rsp=(JoinRsp)join_promise.getResult(gms.join_timeout);

                if(rsp == null) {
                    if(log.isWarnEnabled()) log.warn("join(" + mbr + ") sent to " + coord + " timed out, retrying");
                }
                else {
                    // 1. check whether JOIN was rejected
                    String failure=rsp.getFailReason();
                    if(failure != null)
                        throw new SecurityException(failure);

                    // 2. Install digest
                    MutableDigest tmp_digest=new MutableDigest(rsp.getDigest());
                    tmp_view=rsp.getView();
                    if(tmp_digest == null || tmp_view == null) {
                        if(log.isErrorEnabled())
                            log.error("JoinRsp has a null view or digest: view=" + tmp_view + ", digest=" +
                                    tmp_digest + ", skipping it");
                    }
                    else {
                        tmp_digest.incrementHighestDeliveredSeqno(coord); 	// see DESIGN for an explanantion
                        tmp_digest.seal();
                        gms.setDigest(tmp_digest);

                        if(log.isDebugEnabled()) log.debug("[" + gms.local_addr + "]: JoinRsp=" + tmp_view +
                                " [size=" + tmp_view.size() + "]\n\n");

                        if(!installView(tmp_view)) {
                            if(log.isErrorEnabled()) log.error("view installation failed, retrying to join group");
                            Util.sleep(gms.join_retry_timeout);
                            continue;
                        }

                        // send VIEW_ACK to sender of view
                        Message view_ack=new Message(coord, null, null);
                        view_ack.setFlag(Message.OOB);
                        GMS.GmsHeader tmphdr=new GMS.GmsHeader(GMS.GmsHeader.VIEW_ACK);
                        view_ack.putHeader(GMS.name, tmphdr);
                        if(!gms.members.contains(coord))
                            gms.getDownProtocol().down(new Event(Event.ENABLE_UNICASTS_TO, coord));
                        gms.getDownProtocol().down(new Event(Event.MSG, view_ack));

                        gms.getUpProtocol().up(new Event(Event.BECOME_SERVER));
                        gms.getDownProtocol().down(new Event(Event.BECOME_SERVER));
                        return;
                    }
                }
            }
            catch(SecurityException security_ex) {
                throw security_ex;
            }
            catch(IllegalArgumentException illegal_arg) {
                throw illegal_arg;
            }
            catch(Throwable e) {
                if(log.isDebugEnabled()) log.debug("exception=" + e + ", retrying");
            }

            Util.sleep(gms.join_retry_timeout);
        }
    }


    public void leave(Address mbr) {
        leaving=true;
        wrongMethod("leave");
    }


    public void handleJoinResponse(JoinRsp join_rsp) {
        join_promise.setResult(join_rsp); // will wake up join() method
    }

    public void handleLeaveResponse() {
    }


    public void suspect(Address mbr) {
    }

    public void unsuspect(Address mbr) {
    }


    public void handleMembershipChange (Collection newMembers, Collection leavingMembers, Collection suspectedMembers) {
    }


    /**
     * Does nothing. Discards all views while still client.
     */
    public synchronized void handleViewChange(View new_view, Digest digest) {
        if(log.isTraceEnabled())
            log.trace("view " + new_view.getVid() + " is discarded as we are not a participant");
    }


    /**
     * Called by join(). Installs the view returned by calling Coord.handleJoin() and
     * becomes coordinator.
     */
    private boolean installView(View new_view) {
        Vector mems=new_view.getMembers();
         if(log.isDebugEnabled()) log.debug("new_view=" + new_view);
        if(gms.local_addr == null || mems == null || !mems.contains(gms.local_addr)) {
            if(log.isErrorEnabled()) log.error("I (" + gms.local_addr +
                                                       ") am not member of " + mems + ", will not install view");
            return false;
        }
        gms.installView(new_view);
        gms.becomeParticipant();
        gms.getUpProtocol().up(new Event(Event.BECOME_SERVER));
        gms.getDownProtocol().down(new Event(Event.BECOME_SERVER));
        return true;
    }


    /** Returns immediately. Clients don't handle suspect() requests */
    // public void handleSuspect(Address mbr) {
    // }


    public boolean handleUpEvent(Event evt) {
        Vector tmp;

        switch(evt.getType()) {

            case Event.FIND_INITIAL_MBRS_OK:
                tmp=(Vector)evt.getArg();
                synchronized(initial_mbrs) {
                    if(tmp != null && !tmp.isEmpty()) {
                        initial_mbrs.addAll(tmp);
                    }
                    initial_mbrs_received=true;
                    initial_mbrs.notifyAll();
                }
                return false;  // don't pass up the stack
        }
        return true;
    }





    /* --------------------------- Private Methods ------------------------------------ */



    void sendJoinMessage(Address coord, Address mbr) {
        Message msg;
        GMS.GmsHeader hdr;

        msg=new Message(coord, null, null);
        hdr=new GMS.GmsHeader(GMS.GmsHeader.JOIN_REQ, mbr);
        msg.putHeader(gms.getName(), hdr);
        
        // we have to enable unicasts to coord, as coord is not in our membership (the unicast message would get dropped)
        gms.getDownProtocol().down(new Event(Event.ENABLE_UNICASTS_TO, coord));
        gms.getDownProtocol().down(new Event(Event.MSG, msg));
    }


    /**
     * Pings initial members. Removes self before returning vector of initial members.
     * Uses IP multicast or gossiping, depending on parameters.
     */
    void findInitialMembers() {
        PingRsp ping_rsp;

        synchronized(initial_mbrs) {
            initial_mbrs.removeAllElements();
            initial_mbrs_received=false;
            gms.getDownProtocol().down(new Event(Event.FIND_INITIAL_MBRS));

            // the initial_mbrs_received flag is needed when down_prot.down() is executed on the same thread, so when
            // it returns, a response might actually have been received (even though the initial_mbrs might still be empty)
            if(initial_mbrs_received == false) {
                try {
                    initial_mbrs.wait();
                }
                catch(Exception e) {
                }
            }

            for(int i=0; i < initial_mbrs.size(); i++) {
                ping_rsp=(PingRsp)initial_mbrs.elementAt(i);
                if(ping_rsp.own_addr != null && gms.local_addr != null &&
                        ping_rsp.own_addr.equals(gms.local_addr)) {
                    initial_mbrs.removeElementAt(i);
                    break;
                }
            }
        }
    }


    /**
     The coordinator is determined by a majority vote. If there are an equal number of votes for
     more than 1 candidate, we determine the winner randomly.
     */
    private Address determineCoord(Vector mbrs) {
        PingRsp mbr;
        Hashtable votes;
        int count, most_votes;
        Address winner=null, tmp;

        if(mbrs == null || mbrs.size() < 1)
            return null;

        votes=new Hashtable(5);

        // count *all* the votes (unlike the 2000 election)
        for(int i=0; i < mbrs.size(); i++) {
            mbr=(PingRsp)mbrs.elementAt(i);
            if(mbr.is_server && mbr.coord_addr != null) {
                if(!votes.containsKey(mbr.coord_addr))
                    votes.put(mbr.coord_addr, new Integer(1));
                else {
                    count=((Integer)votes.get(mbr.coord_addr)).intValue();
                    votes.put(mbr.coord_addr, new Integer(count + 1));
                }
            }
        }

        if(votes.size() > 1) {
            if(log.isWarnEnabled()) log.warn("there was more than 1 candidate for coordinator: " + votes);
        }
        else {
            if(log.isDebugEnabled()) log.debug("election results: " + votes);
        }

        // determine who got the most votes
        most_votes=0;
        for(Enumeration e=votes.keys(); e.hasMoreElements();) {
            tmp=(Address)e.nextElement();
            count=((Integer)votes.get(tmp)).intValue();
            if(count > most_votes) {
                winner=tmp;
                // fixed July 15 2003 (patch submitted by Darren Hobbs, patch-id=771418)
                most_votes=count;
            }
        }
        votes.clear();
        return winner;
    }


    void becomeSingletonMember(Address mbr) {
        Digest initial_digest;
        ViewId view_id;
        Vector mbrs=new Vector(1);

        // set the initial digest (since I'm the first member)
        initial_digest=new Digest(gms.local_addr, 0, 0); // initial seqno mcast by me will be 1 (highest seen +1)
        gms.setDigest(initial_digest);

        view_id=new ViewId(mbr);       // create singleton view with mbr as only member
        mbrs.addElement(mbr);
        gms.installView(new View(view_id, mbrs));
        gms.becomeCoordinator(); // not really necessary - installView() should do it

        gms.getUpProtocol().up(new Event(Event.BECOME_SERVER));
        gms.getDownProtocol().down(new Event(Event.BECOME_SERVER));
        if(log.isDebugEnabled()) log.debug("created group (first member). My view is " + gms.view_id +
                                           ", impl is " + gms.getImpl().getClass().getName());
    }


}
