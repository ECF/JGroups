
package org.jgroups.stack;

import org.jgroups.*;
import org.jgroups.conf.ClassConfigurator;
import org.jgroups.util.TimeScheduler;
import org.jgroups.util.Util;

import java.util.*;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;


/**
 * A ProtocolStack manages a number of protocols layered above each other. It creates all
 * protocol classes, initializes them and, when ready, starts all of them, beginning with the
 * bottom most protocol. It also dispatches messages received from the stack to registered
 * objects (e.g. channel, GMP) and sends messages sent by those objects down the stack.<p>
 * The ProtocolStack makes use of the Configurator to setup and initialize stacks, and to
 * destroy them again when not needed anymore
 * @author Bela Ban
 * @version $Id: ProtocolStack.java,v 1.1 2009/07/30 00:58:14 phperret Exp $
 */
public class ProtocolStack extends Protocol implements Transport {
    private Protocol                top_prot=null;
    private Protocol                bottom_prot=null;
    private String                  setup_string;
    private JChannel                channel=null;
    private boolean                 stopped=true;
    public final  TimeScheduler     timer;
    protected ThreadGroup           timer_thread_group=new ThreadGroup(Util.getGlobalThreadGroup(), "Timers");

    /** Locks acquired by protocol below, need to get released on down().
     * See http://jira.jboss.com/jira/browse/JGRP-535 for details */
    private final Map<Thread, ReentrantLock> locks=new ConcurrentHashMap<Thread,ReentrantLock>();

    public static final int         ABOVE=1; // used by insertProtocol()
    public static final int         BELOW=2; // used by insertProtocol()

    private static final String     TIMER_NAME="Timer";


    public ProtocolStack(JChannel channel, String setup_string) throws ChannelException {
        this.setup_string=setup_string;
        this.channel=channel;
        ClassConfigurator.getInstance(true); // will create the singleton
        timer=createTimer();
    }



    /** Only used by Simulator; don't use */
    public ProtocolStack() {
        timer=createTimer();
    }

    public Map<Thread,ReentrantLock> getLocks() {
        return locks;
    }

    public Channel getChannel() {
        return channel;
    }

    public int getTimerThreads() {
        return timer.getCorePoolSize();
    }

    /** Returns all protocols in a list, from top to bottom. <em>These are not copies of protocols,
     so modifications will affect the actual instances !</em> */
    public Vector<Protocol> getProtocols() {
        Protocol         p;
        Vector<Protocol> v=new Vector<Protocol>();

        p=top_prot;
        while(p != null) {
            v.addElement(p);
            p=p.getDownProtocol();
        }
        return v;
    }

    /** Returns the bottom most protocol */
    public Protocol getTransport() {
        Vector<Protocol> prots=getProtocols();
        return !prots.isEmpty()? prots.lastElement() : null;
    }


    /**
     *
     * @return Map<String,Map<key,val>>
     */
    public Map<String,Object> dumpStats() {
        Protocol p;
        Map<String,Object> retval=new HashMap<String,Object>(), tmp;
        String prot_name;

        p=top_prot;
        while(p != null) {
            prot_name=p.getName();
            tmp=p.dumpStats();
            if(prot_name != null && tmp != null)
                retval.put(prot_name, tmp);
            p=p.getDownProtocol();
        }
        return retval;
    }

    public String dumpTimerQueue() {
        return timer.dumpTaskQueue();
    }

    /**
     * Prints the names of the protocols, from the bottom to top. If include_properties is true,
     * the properties for each protocol will also be printed.
     */
    public String printProtocolSpec(boolean include_properties) {
        StringBuilder sb=new StringBuilder();
        Protocol     prot=top_prot;
        Properties   tmpProps;
        String       name;
        Map.Entry    entry;

        while(prot != null) {
            name=prot.getName();
            if(name != null) {
                if("ProtocolStack".equals(name))
                    break;
                sb.append(name);
                if(include_properties) {
                    tmpProps=prot.getProperties();
                    if(tmpProps != null) {
                        sb.append('\n');
                        for(Iterator it=tmpProps.entrySet().iterator(); it.hasNext();) {
                            entry=(Map.Entry)it.next();
                            sb.append(entry).append("\n");
                        }
                    }
                }
                sb.append('\n');

                prot=prot.getDownProtocol();
            }
        }

        return sb.toString();
    }

    public String printProtocolSpecAsXML() {
        StringBuilder sb=new StringBuilder();
        Protocol     prot=bottom_prot;
        Properties   tmpProps;
        String       name;
        Map.Entry    entry;
        int len, max_len=30;

        sb.append("<config>\n");
        while(prot != null) {
            name=prot.getName();
            if(name != null) {
                if("ProtocolStack".equals(name))
                    break;
                sb.append("  <").append(name).append(" ");
                tmpProps=prot.getProperties();
                if(tmpProps != null) {
                    len=name.length();
                    String s;
                    for(Iterator it=tmpProps.entrySet().iterator(); it.hasNext();) {
                        entry=(Map.Entry)it.next();
                        s=entry.getKey() + "=\"" + entry.getValue() + "\" ";
                        if(len + s.length() > max_len) {
                            sb.append("\n       ");
                            len=8;
                        }
                        sb.append(s);
                        len+=s.length();
                    }
                }
                sb.append("/>\n");
                prot=prot.getUpProtocol();
            }
        }
        sb.append("</config>");

        return sb.toString();
    }


    public void setup() throws Exception {
        if(top_prot == null) {
            top_prot=Configurator.setupProtocolStack(setup_string, this);
            top_prot.setUpProtocol(this);
            bottom_prot=Configurator.getBottommostProtocol(top_prot);
            Configurator.initProtocolStack(bottom_prot);         // calls init() on each protocol, from bottom to top
        }
    }




    /**
     * Creates a new protocol given the protocol specification.
     * @param prot_spec The specification of the protocol. Same convention as for specifying a protocol stack.
     *                  An exception will be thrown if the class cannot be created. Example:
     *                  <pre>"VERIFY_SUSPECT(timeout=1500)"</pre> Note that no colons (:) have to be
     *                  specified
     * @return Protocol The newly created protocol
     * @exception Exception Will be thrown when the new protocol cannot be created
     */
    public Protocol createProtocol(String prot_spec) throws Exception {
        return Configurator.createProtocol(prot_spec, this);
    }






    /**
     * Inserts an already created (and initialized) protocol into the protocol list. Sets the links
     * to the protocols above and below correctly and adjusts the linked list of protocols accordingly.
     * Note that this method may change the value of top_prot or bottom_prot.
     * @param prot The protocol to be inserted. Before insertion, a sanity check will ensure that none
     *             of the existing protocols have the same name as the new protocol.
     * @param position Where to place the protocol with respect to the neighbor_prot (ABOVE, BELOW)
     * @param neighbor_prot The name of the neighbor protocol. An exception will be thrown if this name
     *                      is not found
     * @exception Exception Will be thrown when the new protocol cannot be created, or inserted.
     */
    public void insertProtocol(Protocol prot, int position, String neighbor_prot) throws Exception {
        Configurator.insertProtocol(prot, position, neighbor_prot, this);
    }





    /**
     * Removes a protocol from the stack. Stops the protocol and readjusts the linked lists of
     * protocols.
     * @param prot_name The name of the protocol. Since all protocol names in a stack have to be unique
     *                  (otherwise the stack won't be created), the name refers to just 1 protocol.
     * @exception Exception Thrown if the protocol cannot be stopped correctly.
     */
    public Protocol removeProtocol(String prot_name) throws Exception {
        return Configurator.removeProtocol(top_prot, prot_name);
    }


    /** Returns a given protocol or null if not found */
    public Protocol findProtocol(String name) {
        Protocol tmp=top_prot;
        String   prot_name;
        while(tmp != null) {
            prot_name=tmp.getName();
            if(prot_name != null && prot_name.equals(name))
                return tmp;
            tmp=tmp.getDownProtocol();
        }
        return null;
    }


    public void destroy() {
        if(top_prot != null) {
            Configurator.destroyProtocolStack(top_prot);           // destroys msg queues and threads
            top_prot=null;
        }        
        try {
            timer.stop();
        }
        catch(Exception ex) {
        }        
    }



    /**
     * Start all layers. The {@link Protocol#start()} method is called in each protocol,
     * <em>from top to bottom</em>.
     * Each layer can perform some initialization, e.g. create a multicast socket
     */
    public void startStack() throws Exception {
        if(stopped == false) return;

        timer.start();
        Configurator.startProtocolStack(top_prot);
        stopped=false;
    }




    /**
     * Iterates through all the protocols <em>from top to bottom</em> and does the following:
     * <ol>
     * <li>Waits until all messages in the down queue have been flushed (ie., size is 0)
     * <li>Calls stop() on the protocol
     * </ol>
     */
    public void stopStack() {       
        if(stopped) return;
        Configurator.stopProtocolStack(top_prot);
        stopped=true;
    }

    /**
     * Not needed anymore, just left in here for backwards compatibility with JBoss AS
     * @deprecated
     */
    public void flushEvents() {

    }



    /*--------------------------- Transport interface ------------------------------*/

    public void send(Message msg) throws Exception {
        down(new Event(Event.MSG, msg));
    }

    public Object receive(long timeout) throws Exception {
        throw new Exception("ProtocolStack.receive(): not implemented !");
    }
    /*------------------------- End of  Transport interface ---------------------------*/





    /*--------------------------- Protocol functionality ------------------------------*/
    public String getName()  {return "ProtocolStack";}




    public Object up(Event evt) {
        return channel.up(evt);
    }



    public Object down(Event evt) {
        switch(evt.getType()) {
            case Event.CONNECT:
            case Event.DISCONNECT:
                Object retval=top_prot.down(evt);
                renameTimerThreads(TIMER_NAME);
                return retval;
        }
        ReentrantLock lock=locks.remove(Thread.currentThread());
        if(lock != null && lock.isHeldByCurrentThread()) {
            lock.unlock();
            if(log.isTraceEnabled())
                log.trace("released lock held by " + Thread.currentThread());
        }
        if(top_prot != null)
            return top_prot.down(evt);
        return null;
    }



    /*----------------------- End of Protocol functionality ---------------------------*/

    private TimeScheduler createTimer() {
        ThreadFactory factory=new ThreadFactory() {

            public Thread newThread(Runnable command) {
                Thread thread=new Thread(timer_thread_group, command, TIMER_NAME);
                thread.setDaemon(true);
                renameThread(TIMER_NAME, thread);
                return thread;
            }
        };
        return new TimeScheduler(factory);
    }

    private void renameTimerThreads(String base_name) {
        if(timer_thread_group == null)
            return;
        String cluster_name=getClusterName();
        Address local_addr=getLocalAddress();
        int num_threads=timer_thread_group.activeCount();
        Thread[] timers=new Thread[num_threads];
        num_threads=timer_thread_group.enumerate(timers);
        for(int i=0; i < num_threads; i++) {
            Thread thread=timers[i];
            renameThread(base_name, thread, cluster_name, local_addr);
        }
    }

    private String renameThread(String base_name, Thread runner) {
        return renameThread(base_name, runner, getClusterName(), getLocalAddress());
    }


    private String renameThread(String base_name, Thread runner, String cluster_name, Address local_addr) {
        String oldName = null;
        if(runner!=null){
            oldName=runner.getName();

            StringBuilder threadName=new StringBuilder();
            threadName.append(base_name);

            if(threadName.length() > 0)
                threadName.append(',');
            if(cluster_name == null)
                cluster_name=getClusterName();
            threadName.append(cluster_name);
            if(threadName.length() > 0)
                threadName.append(',');
            if(local_addr == null)
                local_addr=getLocalAddress();
            threadName.append(local_addr);

            runner.setName(threadName.toString());
        }
        return oldName;
    }

    private Address getLocalAddress() {
        return channel != null? channel.getLocalAddress() : null;
    }

    private String getClusterName() {
        return channel != null? channel.getClusterName() : "n/a";
    }


}
