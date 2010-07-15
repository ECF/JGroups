package org.jgroups.blocks;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jgroups.Address;
import org.jgroups.Global;
import org.jgroups.Version;
import org.jgroups.stack.IpAddress;
import org.jgroups.util.Util;

import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Shared class for TCP connection tables.
 * @author Scott Marlow
 */
public abstract class BasicConnectionTable {
    final Map<Address,Connection>  conns=new HashMap<Address,Connection>();         // keys: Addresses (peer address), values: Connection
    Receiver              receiver=null;
    boolean               use_send_queues=false;       // max number of messages in a send queue
    int                   send_queue_size=10000;
    InetAddress           bind_addr=null;
    Address               local_addr=null;             // bind_addr + port of srv_sock
    int                   srv_port=7800;
    int                   recv_buf_size=120000;
    int                   send_buf_size=60000;
    final Vector<ConnectionListener>        conn_listeners=new Vector<ConnectionListener>(); // listeners to be notified when a conn is established/torn down
    Reaper                reaper=null;                 // closes conns that have been idle for more than n secs
    long                  reaper_interval=60000;       // reap unused conns once a minute
    long                  conn_expire_time=300000;     // connections can be idle for 5 minutes before they are reaped
    int                   sock_conn_timeout=1000;      // max time in millis to wait for Socket.connect() to return
    ThreadGroup           thread_group=null;
    protected final Log   log= LogFactory.getLog(getClass());
    final byte[]          cookie={'b', 'e', 'l', 'a'};
    boolean               use_reaper=false;            // by default we don't reap idle conns
    static final int      backlog=20;                  // 20 conn requests are queued by ServerSocket (addtl will be discarded)
    volatile ServerSocket srv_sock=null;
    boolean               tcp_nodelay=false;
    int                   linger=-1;

   /**
    * The address which will be broadcast to the group (the externally visible address which this host should
    * be contacted on). If external_addr is null, it will default to the same address that the server socket is bound to.
    */
    InetAddress		    external_addr=null;
    int                 max_port=0;                   // maximum port to bind to (if < srv_port, no limit)
    Thread              acceptor=null;               // continuously calls srv_sock.accept()
    boolean             running=false;

    final static long   MAX_JOIN_TIMEOUT=Global.THREAD_SHUTDOWN_WAIT_TIME;


    public final void setReceiver(Receiver r) {
        receiver=r;
    }

   public void addConnectionListener(ConnectionListener l) {
       if(l != null && !conn_listeners.contains(l))
           conn_listeners.addElement(l);
   }

   public void removeConnectionListener(ConnectionListener l) {
       if(l != null) conn_listeners.removeElement(l);
   }

   public Address getLocalAddress() {
       if(local_addr == null)
           local_addr=bind_addr != null ? new IpAddress(bind_addr, srv_port) : null;
       return local_addr;
   }

   public int getSendBufferSize() {
       return send_buf_size;
   }

   public void setSendBufferSize(int send_buf_size) {
       this.send_buf_size=send_buf_size;
   }

   public int getReceiveBufferSize() {
       return recv_buf_size;
   }

   public void setReceiveBufferSize(int recv_buf_size) {
       this.recv_buf_size=recv_buf_size;
   }

   public int getSocketConnectionTimeout() {
       return sock_conn_timeout;
   }

   public void setSocketConnectionTimeout(int sock_conn_timeout) {
       this.sock_conn_timeout=sock_conn_timeout;
   }

   public int getNumConnections() {
       return conns.size();
   }

    public boolean getTcpNodelay() {
        return tcp_nodelay;
    }

    public void setTcpNodelay(boolean tcp_nodelay) {
        this.tcp_nodelay=tcp_nodelay;
    }

    public int getLinger() {
        return linger;
    }

    public void setLinger(int linger) {
        this.linger=linger;
    }

    public boolean getUseSendQueues() {return use_send_queues;}

   public void setUseSendQueues(boolean flag) {this.use_send_queues=flag;}

    public int getSendQueueSize() {
        return send_queue_size;
    }

    public void setSendQueueSize(int send_queue_size) {
        this.send_queue_size=send_queue_size;
    }

    public void start() throws Exception {
       running=true;
   }

   public void stop() {
       running=false;
   }

   /**
    Remove <code>addr</code>from connection table. This is typically triggered when a member is suspected.
    */
   public void remove(Address addr) {
       Connection conn;

       synchronized(conns) {
           conn=conns.remove(addr);
       }

       if(conn != null) {
           try {
               conn.destroy();  // won't do anything if already destroyed
           }
           catch(Exception e) {
           }
       }
       if(log.isTraceEnabled()) log.trace("removed " + addr + ", connections are " + toString());
   }

   /**
    * Calls the receiver callback. We do not serialize access to this method, and it may be called concurrently
    * by several Connection handler threads. Therefore the receiver needs to be reentrant.
    */
   public void receive(Address sender, byte[] data, int offset, int length) {
       if(receiver != null) {
           receiver.receive(sender, data, offset, length);
       }
       else
           if(log.isErrorEnabled()) log.error("receiver is null (not set) !");
   }

   public String toString() {
       StringBuilder ret=new StringBuilder();
       Address key;
       Connection val;
       Map.Entry entry;
       HashMap<Address,Connection> copy;

       synchronized(conns) {
           copy=new HashMap<Address,Connection>(conns);
       }
       ret.append("local_addr=" + local_addr).append("\n");
       ret.append("connections (" + copy.size() + "):\n");
       for(Iterator it=copy.entrySet().iterator(); it.hasNext();) {
           entry=(Map.Entry)it.next();
           key=(Address)entry.getKey();
           val=(Connection)entry.getValue();
           ret.append("key: " + key + ": " + val + '\n');
       }
       ret.append('\n');
       return ret.toString();
   }

   void notifyConnectionOpened(Address peer) {
       if(peer == null) return;
       for(int i=0; i < conn_listeners.size(); i++)
           conn_listeners.elementAt(i).connectionOpened(peer);
   }

   void notifyConnectionClosed(Address peer) {
       if(peer == null) return;
       for(int i=0; i < conn_listeners.size(); i++)
           conn_listeners.elementAt(i).connectionClosed(peer);
   }

   void addConnection(Address peer, Connection c) {
       conns.put(peer, c);
       if(reaper != null && !reaper.isRunning())
           reaper.start();
   }

   public void send(Address dest, byte[] data, int offset, int length) throws Exception {
       Connection conn;
       if(dest == null) {
           if(log.isErrorEnabled())
               log.error("destination is null");
           return;
       }

       if(data == null) {
           log.warn("data is null; discarding packet");
           return;
       }

       if(!running) {
           if(log.isWarnEnabled())
               log.warn("connection table is not running, discarding message to " + dest);
           return;
       }

       if(dest.equals(local_addr)) {
           receive(local_addr, data, offset, length);
           return;
       }

       // 1. Try to obtain correct Connection (or create one if not yet existent)
       try {
           conn=getConnection(dest);
           if(conn == null) return;
       }
       catch(Throwable ex) {
           throw new Exception("connection to " + dest + " could not be established", ex);
       }

       // 2. Send the message using that connection
       try {
           conn.send(data, offset, length);
       }
       catch(Throwable ex) {
           if(log.isTraceEnabled())
               log.trace("sending msg to " + dest + " failed (" + ex.getClass().getName() + "); removing from connection table", ex);
           remove(dest);
       }
   }

   abstract Connection getConnection(Address dest) throws Exception;

   /**
    * Removes all connections from ConnectionTable which are not in c
    * @param c
    */
   //public void retainAll(Collection c) {
     //  conns.keySet().retainAll(c);
   //}


      /**
       * Removes all connections from ConnectionTable which are not in current_mbrs
       * @param current_mbrs
       */
      public void retainAll(Collection current_mbrs) {
          if(current_mbrs == null) return;
          HashMap<Address,Connection> copy;
          synchronized(conns) {
              copy=new HashMap<Address,Connection>(conns);
              conns.keySet().retainAll(current_mbrs);
          }

          // All of the connections that were not retained must be destroyed
          // so that their resources are cleaned up.
          Map.Entry entry;
          for(Iterator it=copy.entrySet().iterator(); it.hasNext();) {
              entry=(Map.Entry)it.next();
              Object oKey=entry.getKey();
              if(!current_mbrs.contains(oKey)) {    // This connection NOT in the resultant connection set
                  Connection conn=(Connection)entry.getValue();
                  if(null != conn) {    // Destroy this connection
                      if(log.isTraceEnabled())
                          log.trace("Destroy this orphaned connection: " + conn);
                      conn.destroy();
                  }
              }
          }
          copy.clear();
      }



    /** Used for message reception. */
    public interface Receiver {
       void receive(Address sender, byte[] data, int offset, int length);
   }

   /** Used to be notified about connection establishment and teardown. */
   public interface ConnectionListener {
       void connectionOpened(Address peer_addr);
       void connectionClosed(Address peer_addr);
   }

   class Connection implements Runnable {
       Socket           sock=null;                // socket to/from peer (result of srv_sock.accept() or new Socket())
       String           sock_addr=null;           // used for Thread.getName()
       DataOutputStream out=null;                 // for sending messages
       DataInputStream  in=null;                  // for receiving messages
       Thread           receiverThread=null;      // thread for receiving messages
       Address          peer_addr=null;           // address of the 'other end' of the connection
       final Lock       send_lock=new ReentrantLock();  // serialize send()
       long             last_access=System.currentTimeMillis(); // last time a message was sent or received

       /** Bounded queue of data to be sent to the peer of this connection */
       BlockingQueue<byte[]> send_queue=null;
       Sender                sender=null;
       boolean               is_running=false;


       private String getSockAddress() {
           if(sock_addr != null)
               return sock_addr;
           if(sock != null) {
               StringBuffer sb;
               sb=new StringBuffer();
               sb.append(sock.getLocalAddress().getHostAddress()).append(':').append(sock.getLocalPort());
               sb.append(" - ").append(sock.getInetAddress().getHostAddress()).append(':').append(sock.getPort());
               sock_addr=sb.toString();
           }
           return sock_addr;
       }




       Connection(Socket s, Address peer_addr) {
           sock=s;
           this.peer_addr=peer_addr;

           if(use_send_queues) {
               send_queue=new LinkedBlockingQueue<byte[]>(send_queue_size);
               sender=new Sender();
           }

           try {
               // out=new DataOutputStream(sock.getOutputStream());
               // in=new DataInputStream(sock.getInputStream());

               // The change to buffered input and output stream yielded a 400% performance gain !
               // bela Sept 7 2006
               out=new DataOutputStream(new BufferedOutputStream(sock.getOutputStream()));
               in=new DataInputStream(new BufferedInputStream(sock.getInputStream()));
               if(sender != null)
                   sender.start();
           }
           catch(Exception ex) {
               if(log.isErrorEnabled()) log.error("exception is " + ex);
           }
       }


       boolean established() {
           return receiverThread != null;
       }


       void setPeerAddress(Address peer_addr) {
           this.peer_addr=peer_addr;
       }

       Address getPeerAddress() {return peer_addr;}

       void updateLastAccessed() {
           last_access=System.currentTimeMillis();
       }

       void init() {
           is_running=true;
           if(receiverThread == null || !receiverThread.isAlive()) {
               // Roland Kurmann 4/7/2003, put in thread_group
               receiverThread=new Thread(thread_group, this, "ConnectionTable.Connection.Receiver [" + getSockAddress() + "]");
               // receiverThread.setDaemon(true);
               receiverThread.start();
               if(log.isTraceEnabled())
                   log.trace("receiver started: " + receiverThread);
           }

       }


       void destroy() {
           is_running=false;
           closeSocket(); // should terminate handler as well
           if(sender != null)
               sender.stop();
           Thread tmp=receiverThread;
           receiverThread=null;
           if(tmp != null) {
               Util.interruptAndWaitToDie(tmp);
           }
       }


       /**
        *
        * @param data Guaranteed to be non null
        * @param offset
        * @param length
        */
       void send(byte[] data, int offset, int length) {
           if(!is_running) {
               if(log.isWarnEnabled())
                   log.warn("Connection is not running, discarding message");
               return;
           }
           if(use_send_queues) {
               try {
                   // we need to copy the byte[] buffer here because the original buffer might get changed meanwhile
                   byte[] tmp=new byte[length];
                   System.arraycopy(data, offset, tmp, 0, length);
                   send_queue.put(tmp);
               }
               catch(InterruptedException e) {
                   Thread.currentThread().interrupt();
               }
           }
           else
               _send(data, offset, length, true);
       }


       /**
        * Sends data using the 'out' output stream of the socket
        * @param data
        * @param offset
        * @param length
        * @param acquire_lock
        */
       private void _send(byte[] data, int offset, int length, boolean acquire_lock) {
           if(acquire_lock)
               send_lock.lock();

           try {
               doSend(data, offset, length);
               updateLastAccessed();
           }
           catch(InterruptedException iex) {
               Thread.currentThread().interrupt(); // set interrupt flag again
           }
           catch(Throwable ex) {
               if(log.isErrorEnabled()) log.error("exception is " + ex);
           }
           finally {
               if(acquire_lock)
                   send_lock.unlock();
           }
       }


       void doSend(byte[] data, int offset, int length) throws Exception {
           try {
               // we're using 'double-writes', sending the buffer to the destination in 2 pieces. this would
               // ensure that, if the peer closed the connection while we were idle, we would get an exception.
               // this won't happen if we use a single write (see Stevens, ch. 5.13).
               if(out != null) {
                   out.writeInt(length); // write the length of the data buffer first
                   Util.doubleWrite(data, offset, length, out);
                   out.flush();  // may not be very efficient (but safe)
               }
           }
           catch(Exception ex) {
               remove(peer_addr);
               throw ex;
           }
       }


       /**
        * Reads the peer's address. First a cookie has to be sent which has to match my own cookie, otherwise
        * the connection will be refused
        */
       Address readPeerAddress(Socket client_sock) throws Exception {
           Address     client_peer_addr=null;
           byte[]      input_cookie=new byte[cookie.length];
           int         client_port=client_sock != null? client_sock.getPort() : 0;
           short       version;
           InetAddress client_addr=client_sock != null? client_sock.getInetAddress() : null;

           if(in != null) {
               initCookie(input_cookie);

               // read the cookie first
               in.read(input_cookie, 0, input_cookie.length);
               if(!matchCookie(input_cookie))
                   throw new SocketException("ConnectionTable.Connection.readPeerAddress(): cookie sent by " +
                                             client_peer_addr + " does not match own cookie; terminating connection");
               // then read the version
               version=in.readShort();

               if(Version.isBinaryCompatible(version) == false) {
                   if(log.isWarnEnabled())
                       log.warn(new StringBuffer("packet from ").append(client_addr).append(':').append(client_port).
                              append(" has different version (").append(Version.print(version)).append(") from ours (").
                                append(Version.printVersion()).append("). This may cause problems"));
               }
               client_peer_addr=new IpAddress();
               client_peer_addr.readFrom(in);

               updateLastAccessed();
           }
           return client_peer_addr;
       }


       /**
        * Send the cookie first, then the our port number. If the cookie doesn't match the receiver's cookie,
        * the receiver will reject the connection and close it.
        */
       void sendLocalAddress(Address local_addr) {
           if(local_addr == null) {
               if(log.isWarnEnabled()) log.warn("local_addr is null");
               return;
           }
           if(out != null) {
               try {
                   // write the cookie
                   out.write(cookie, 0, cookie.length);

                   // write the version
                   out.writeShort(Version.version);
                   local_addr.writeTo(out);
                   out.flush(); // needed ?
                   updateLastAccessed();
               }
               catch(Throwable t) {
                   if(log.isErrorEnabled()) log.error("exception is " + t);
               }
           }
       }


       void initCookie(byte[] c) {
           if(c != null)
               for(int i=0; i < c.length; i++)
                   c[i]=0;
       }

       boolean matchCookie(byte[] input) {
           if(input == null || input.length < cookie.length) return false;
           for(int i=0; i < cookie.length; i++)
               if(cookie[i] != input[i]) return false;
           return true;
       }


       String printCookie(byte[] c) {
           if(c == null) return "";
           return new String(c);
       }


       public void run() {
           byte[] buf=new byte[256]; // start with 256, increase as we go
           int len=0;

           while(receiverThread != null && receiverThread.equals(Thread.currentThread()) && is_running) {
               try {
                   if(in == null) {
                       if(log.isErrorEnabled()) log.error("input stream is null !");
                       break;
                   }
                   len=in.readInt();
                   if(len > buf.length)
                       buf=new byte[len];
                   in.readFully(buf, 0, len);
                   updateLastAccessed();
                   receive(peer_addr, buf, 0, len); // calls receiver.receive(msg)
               }
               catch(OutOfMemoryError mem_ex) {
                   if(log.isWarnEnabled()) log.warn("dropped invalid message, closing connection");
                   break; // continue;
               }
               catch(EOFException eof_ex) {  // peer closed connection
                   if(log.isTraceEnabled()) log.trace("exception is " + eof_ex);
                   notifyConnectionClosed(peer_addr);
                   break;
               }
               catch(IOException io_ex) {
                   if(log.isTraceEnabled()) log.trace("exception is " + io_ex);
                   notifyConnectionClosed(peer_addr);
                   break;
               }
               catch(Throwable e) {
                   if(log.isWarnEnabled()) log.warn("exception is " + e);
               }
           }
           if(log.isTraceEnabled())
               log.trace("ConnectionTable.Connection.Receiver terminated");
           receiverThread=null;
           closeSocket();
           // remove(peer_addr);
       }


       public String toString() {
           StringBuilder ret=new StringBuilder();
           InetAddress local=null, remote=null;
           String local_str, remote_str;

           if(sock == null)
               ret.append("<null socket>");
           else {
               //since the sock variable gets set to null we want to make
               //make sure we make it through here without a nullpointer exception
               Socket tmp_sock=sock;
               local=tmp_sock.getLocalAddress();
               remote=tmp_sock.getInetAddress();
               local_str=local != null ? Util.shortName(local) : "<null>";
               remote_str=remote != null ? Util.shortName(remote) : "<null>";
               ret.append('<' + local_str + ':' + tmp_sock.getLocalPort() +
                          " --> " + remote_str + ':' + tmp_sock.getPort() + "> (" +
                          ((System.currentTimeMillis() - last_access) / 1000) + " secs old)");
               tmp_sock=null;
           }

           return ret.toString();
       }


       void closeSocket() {
           Util.close(sock); // should actually close in/out (so we don't need to close them explicitly)
           sock=null;
           Util.close(out);  // flushes data
           // removed 4/22/2003 (request by Roland Kurmann)
           // out=null;
           Util.close(in);
       }


       class Sender implements Runnable {
           Thread senderThread;
           private boolean is_it_running=false;

           void start() {
               if(senderThread == null || !senderThread.isAlive()) {
                   senderThread=new Thread(thread_group, this, "ConnectionTable.Connection.Sender local_addr=" + local_addr + " [" + getSockAddress() + "]");
                   senderThread.setDaemon(true);
                   is_it_running=true;
                   senderThread.start();
                   if(log.isTraceEnabled())
                       log.trace("sender thread started: " + senderThread);
               }
           }

           void stop() {
               is_it_running=false;
               if(send_queue != null)
                   send_queue.clear();
               if(senderThread != null) {
                   Thread tmp=senderThread;
                   senderThread=null;
                   Util.interruptAndWaitToDie(tmp);
               }
           }

           boolean isRunning() {
               return is_it_running && senderThread != null;
           }

           public void run() {
               byte[] data;
               while(senderThread != null && senderThread.equals(Thread.currentThread()) && is_it_running) {
                   try {
                       data=send_queue.take();
                       if(data == null)
                           continue;
                       // we don't need to serialize access to 'out' as we're the only thread sending messages
                       _send(data, 0, data.length, false);
                   }
                   catch(InterruptedException e) {
                       ;
                   }
               }
               is_it_running=false;
               if(log.isTraceEnabled())
                   log.trace("ConnectionTable.Connection.Sender thread terminated");
           }
       }


   }

   class Reaper implements Runnable {
       Thread t=null;

       Reaper() {
           ;
       }

       // return true if we have zero connections
       private boolean haveZeroConnections() {
           synchronized(conns) {
               return conns.isEmpty();
           }
       }

       public void start() {

           if(haveZeroConnections())
               return;
           if(t != null && !t.isAlive())
               t=null;
           if(t == null) {
               //RKU 7.4.2003, put in threadgroup
               t=new Thread(thread_group, this, "ConnectionTable.ReaperThread");
               t.setDaemon(true); // will allow us to terminate if all remaining threads are daemons
               t.start();
           }
       }

       public void stop() {
           Thread tmp=t;
           if(t != null)
               t=null;
           if(tmp != null) {
               Util.interruptAndWaitToDie(tmp);
           }
       }


       public boolean isRunning() {
           return t != null;
       }

       public void run() {
           Connection value;
           Map.Entry entry;
           long curr_time;

           if(log.isDebugEnabled()) log.debug("connection reaper thread was started. Number of connections=" +
                   conns.size() + ", reaper_interval=" + reaper_interval + ", conn_expire_time=" +
                   conn_expire_time);

           while(!haveZeroConnections() && t != null && t.equals(Thread.currentThread())) {
               Util.sleep(reaper_interval);
               if(t == null || !Thread.currentThread().equals(t))
                   break;
               synchronized(conns) {
                   curr_time=System.currentTimeMillis();
                   for(Iterator it=conns.entrySet().iterator(); it.hasNext();) {
                       entry=(Map.Entry)it.next();
                       value=(Connection)entry.getValue();
                       if(log.isTraceEnabled()) log.trace("connection is " +
                                                        ((curr_time - value.last_access) / 1000) + " seconds old (curr-time=" +
                                                        curr_time + ", last_access=" + value.last_access + ')');
                       if(value.last_access + conn_expire_time < curr_time) {
                           if(log.isTraceEnabled()) log.trace("connection " + value +
                                                            " has been idle for too long (conn_expire_time=" + conn_expire_time +
                                                            "), will be removed");
                           value.destroy();
                           it.remove();
                       }
                   }
               }
           }
           if(log.isDebugEnabled()) log.debug("reaper terminated");
           t=null;
       }
   }
}
