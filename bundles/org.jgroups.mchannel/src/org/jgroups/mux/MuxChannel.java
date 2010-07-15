package org.jgroups.mux;

import org.jgroups.*;
import org.jgroups.util.Util;
import org.jgroups.stack.ProtocolStack;

import java.io.Serializable;
import java.util.Map;

/**
 * Multiplexer channel. This is returned as result of calling
 * {@link org.jgroups.ChannelFactory#createMultiplexerChannel(String,String,boolean,String)}. Maintains the multiplexer
 * ID, which is used to add a header to each message, so that the message can be demultiplexed at the receiver
 * @author Bela Ban
 * @version $Id: MuxChannel.java,v 1.1 2009/07/30 00:58:14 phperret Exp $
 */
public class MuxChannel extends JChannel {

    /** the real channel to delegate to */
    final JChannel ch;

    /** The service ID */
    final String id;

    /** a reference back to the factory that created us */
    final JChannelFactory factory;

    /** The name of the JGroups stack, e.g. as defined in stacks.xml */
    final String stack_name;

    /** will be added to each message sent */
    final MuxHeader hdr;

    static final String name="MUX";
    final Multiplexer mux;


    public MuxChannel(JChannelFactory f, JChannel ch, String id, String stack_name, Multiplexer mux) {
        super(false); // don't create protocol stack, queues and threads
        factory=f;
        this.ch=ch;
        this.stack_name=stack_name;
        this.id=id;
        hdr=new MuxHeader(id);
        this.mux=mux;
        closed=!ch.isOpen();
        // connected=ch.isConnected();
    }

    public String getStackName() {return stack_name;}

    public String getId() {return id;}

    public Multiplexer getMultiplexer() {return mux;}

    public String getChannelName() {
        return ch.getClusterName();
    }

    public String getClusterName() {
        return ch.getClusterName();
    }

    public Address getLocalAddress() {
        return ch != null? ch.getLocalAddress() : null;
    }

    /** This should never be used (just for testing) ! */
    public JChannel getChannel() {
        return ch;
    }


    /**
     * Returns the <em>service</em> view, ie. the cluster view (see {@link #getView()}) <em>minus</em> the nodes on
     * which this service is not running, e.g. if S1 runs on A and C, and the cluster view is {A,B,C}, then the service
     * view is {A,C}
     * @return The service view (list of nodes on which this service is running)
     */
    public View getView() {
        return closed || !connected ? null : mux.getServiceView(id);
    }

    /** Returns the JGroups view of a cluster, e.g. if we have nodes A, B and C, then the view will
     * be {A,B,C}
     * @return The JGroups view
     */
    public View getClusterView() {
        return ch != null? ch.getView() : null;
    }

    public ProtocolStack getProtocolStack() {
        return ch != null? ch.getProtocolStack() : null;
    }

    public boolean isOpen() {
        return !closed;
    }

    public boolean isConnected() {
        return connected;
    }

    public Map dumpStats() {
        return ch.dumpStats();
    }


    public void setClosed(boolean f) {
        closed=f;
    }

    public void setConnected(boolean f) {
        connected=f;
    }

    public Object getOpt(int option) {
        return ch.getOpt(option);
    }

    public void setOpt(int option, Object value) {
        ch.setOpt(option, value);
        super.setOpt(option, value);
    }

    public synchronized void connect(String channel_name) throws ChannelException, ChannelClosedException {
        /*make sure the channel is not closed*/
        checkClosed();

        /*if we already are connected, then ignore this*/
        if(connected) {
            if(log.isTraceEnabled()) log.trace("already connected to " + channel_name);
            return;
        }
        
        factory.connect(this);
        notifyChannelConnected(this);
    }


    public synchronized boolean connect(String cluster_name, Address target, String state_id, long timeout) throws ChannelException {
        throw new UnsupportedOperationException("not yet implemented");
    }

    public synchronized void disconnect() {
        try {
            closed=false;
            setConnected(false);
            factory.disconnect(this);
        }
        catch(Throwable t) {
            log.error("disconnecting channel failed", t);
        }
        closed=false;
        setConnected(false);
        notifyChannelDisconnected(this);
    }



    public synchronized void open() throws ChannelException {
        factory.open(this);
        closed=false;
    }

    public synchronized void close() {
        try {
            closed=true;
            setConnected(false);
            factory.close(this);
        }
        finally {
            closed=true;
            setConnected(false);
            closeMessageQueue(true);
        }

        notifyChannelClosed(this);
    }

    protected void _close(boolean disconnect, boolean close_mq) {
        super._close(disconnect, close_mq);
        closed=!ch.isOpen();
        setConnected(ch.isConnected());
        notifyChannelClosed(this);
    }

    public synchronized void shutdown() {
        try {
            factory.shutdown(this);
        }
        finally {
            closed=true;
            setConnected(false);
            closeMessageQueue(true);
        }
    }


    public void send(Message msg) throws ChannelNotConnectedException, ChannelClosedException {
        msg.putHeader(name, hdr);
        ch.send(msg);
    }

    public void send(Address dst, Address src, Serializable obj) throws ChannelNotConnectedException, ChannelClosedException {
        send(new Message(dst, src, obj));
    }


    public void down(Event evt) {
        if(evt.getType() == Event.MSG) {
            Message msg=(Message)evt.getArg();
            msg.putHeader(name, hdr);            
        }
        ch.down(evt);
    }

    public Object downcall(Event evt) {
        if(evt.getType() == Event.MSG) {
            Message msg=(Message)evt.getArg();
            msg.putHeader(name, hdr);
        }
        return ch.downcall(evt);
    }
    

    public boolean getState(Address target, long timeout) throws ChannelNotConnectedException, ChannelClosedException {
        return getState(target, null, timeout);
    }

    public boolean getState(Address target, String state_id, long timeout) throws ChannelNotConnectedException, ChannelClosedException {
        String my_id=id;

        if(state_id != null)
            my_id += "::" + state_id;

        // we're usig service views, so we need to find the first host in the cluster on which our service runs
        // http://jira.jboss.com/jira/browse/JGRP-247
        //
        // unless service runs on a specified target node
        // http://jira.jboss.com/jira/browse/JGRP-401
        Address service_view_coordinator=mux.getStateProvider(target,id);
        Address tmp=getLocalAddress();

        if(service_view_coordinator != null)
            target=service_view_coordinator;

        if(tmp != null && tmp.equals(target)) // this will avoid the "cannot get state from myself" error
            target=null;

        if(!mux.stateTransferListenersPresent())
            return ch.getState(target, my_id, timeout);
        else {
            return mux.getState(target, my_id, timeout);
        }
    }

    public void returnState(byte[] state) {
        ch.returnState(state, id);
    }

    public void returnState(byte[] state, String state_id) {
        String my_id=id;
        if(state_id != null)
            my_id+="::" + state_id;
        ch.returnState(state, my_id);
    }  
}
