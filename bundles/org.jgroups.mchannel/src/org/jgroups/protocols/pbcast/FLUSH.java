package org.jgroups.protocols.pbcast;

import org.jgroups.*;
import org.jgroups.annotations.GuardedBy;
import org.jgroups.stack.Protocol;
import org.jgroups.util.Digest;
import org.jgroups.util.Promise;
import org.jgroups.util.Streamable;
import org.jgroups.util.Util;

import java.io.*;
import java.util.*;

/**
 * Flush, as it name implies, forces group members to flush their pending
 * messages while blocking them to send any additional messages. The process of
 * flushing acquiesces the group so that state transfer or a join can be done.
 * It is also called stop-the-world model as nobody will be able to send
 * messages while a flush is in process.
 * 
 * <p>
 * Flush is needed for:
 * <p>
 * (1) State transfer. When a member requests state transfer, the coordinator
 * tells everyone to stop sending messages and waits for everyone's ack. Then it
 * asks the application for its state and ships it back to the requester. After
 * the requester has received and set the state successfully, the coordinator
 * tells everyone to resume sending messages.
 * <p>
 * (2) View changes (e.g.a join). Before installing a new view V2, flushing
 * would ensure that all messages *sent* in the current view V1 are indeed
 * *delivered* in V1, rather than in V2 (in all non-faulty members). This is
 * essentially Virtual Synchrony.
 * 
 * 
 * 
 * @author Vladimir Blagojevic
 * @version $Id: FLUSH.java,v 1.1 2009/07/30 00:58:14 phperret Exp $
 * @since 2.4
 */
public class FLUSH extends Protocol {
	public static final String NAME = "FLUSH";

	@GuardedBy ("sharedLock")
	private View currentView;

	private Address localAddress;

	/**
	 * Group member that requested FLUSH. For view intallations flush
	 * coordinator is the group coordinator For state transfer flush coordinator
	 * is the state requesting member
	 */
	@GuardedBy ("sharedLock")
	private Address flushCoordinator;

	@GuardedBy ("sharedLock")
	private final List<Address> flushMembers;

	@GuardedBy ("sharedLock")
	private final Set<Address> flushOkSet;

	@GuardedBy ("sharedLock")
	private final Map<Address, Digest> flushCompletedMap;

	@GuardedBy ("sharedLock")
	private final Set<Address> stopFlushOkSet;

	@GuardedBy ("sharedLock")
	private final Set<Address> suspected;

	private final Object sharedLock = new Object();

	private final Object blockMutex = new Object();

	/**
	 * Indicates if FLUSH.down() is currently blocking threads Condition
	 * predicate associated with blockMutex
	 */
	@GuardedBy ("blockMutex")
	private boolean isBlockingFlushDown = true;

	/**
	 * Default timeout for a group member to be in
	 * <code>isBlockingFlushDown</code>
	 */
	private long timeout = 8000;
	
	private boolean enable_reconciliation = true;

	@GuardedBy ("sharedLock")
	private boolean receivedFirstView = false;

	@GuardedBy ("sharedLock")
	private boolean receivedMoreThanOneView = false;

	private long startFlushTime;

	private long totalTimeInFlush;

	private int numberOfFlushes;

	private double averageFlushDuration;

	private final Promise flush_promise = new Promise();

	@GuardedBy ("sharedLock")
	private final FlushPhase flushPhase = new FlushPhase();

	@GuardedBy ("sharedLock")
	private final List<Address> reconcileOks = new ArrayList<Address>();

	public FLUSH() {
		super();
		currentView = new View(new ViewId(), new Vector<Address>());
		flushOkSet = new TreeSet<Address>();
		flushCompletedMap = new HashMap<Address, Digest>();
		stopFlushOkSet = new TreeSet<Address>();
		flushMembers = new ArrayList<Address>();
		suspected = new TreeSet<Address>();
	}

	public String getName() {
		return NAME;
	}

	public boolean setProperties(Properties props) {
		super.setProperties(props);

		timeout = Util.parseLong(props, "timeout", timeout);
		enable_reconciliation = Util.parseBoolean(props, "enable_reconciliation", enable_reconciliation);
		String str = props.getProperty("auto_flush_conf");
		if (str != null) {
			log.warn("auto_flush_conf has been deprecated and its value will be ignored");
			props.remove("auto_flush_conf");
		}

		if (!props.isEmpty()) {
			log.error("the following properties are not recognized: " + props);
			return false;
		}
		return true;
	}

	public void start() throws Exception {
		Map<String,Boolean> map = new HashMap<String,Boolean>();
		map.put("flush_supported", Boolean.TRUE);
		up_prot.up(new Event(Event.CONFIG, map));
		down_prot.down(new Event(Event.CONFIG, map));

		synchronized (sharedLock) {
			receivedFirstView = false;
			receivedMoreThanOneView = false;
		}
		synchronized (blockMutex) {
			isBlockingFlushDown = true;
		}
	}

	public void stop() {
		synchronized (sharedLock) {
			currentView = new View(new ViewId(), new Vector<Address>());
			flushCompletedMap.clear();
			flushOkSet.clear();
			stopFlushOkSet.clear();
			flushMembers.clear();
			suspected.clear();
			flushCoordinator = null;
		}
	}

	/* -------------------JMX attributes and operations --------------------- */

	public double getAverageFlushDuration() {
		return averageFlushDuration;
	}

	public long getTotalTimeInFlush() {
		return totalTimeInFlush;
	}

	public int getNumberOfFlushes() {
		return numberOfFlushes;
	}

	public boolean startFlush(long timeout) {		
		Map atts = new HashMap();	           	
     	atts.put("timeout", new Long(timeout));
		return startFlush(new Event(Event.SUSPEND,atts), 3, false);
	}

	private boolean startFlush(Event evt, int numberOfAttempts, boolean isRetry) {
		boolean successfulFlush = false;
		if (!flushPhase.isFlushInProgress() || isRetry) {
			flush_promise.reset();
			Map atts = (Map) evt.getArg();			
			long timeout = ((Long)atts.get("timeout")).longValue();		
			if (log.isDebugEnabled()){
				if(isRetry)
					log.debug("Retrying FLUSH at " + localAddress + ", "+ evt + ". Attempts left " + numberOfAttempts);
				else
					log.debug("Received " + evt+ " at " + localAddress + ". Running FLUSH...");				
			}

			onSuspend((View)atts.get("view"));
			try {
				Boolean r = (Boolean) flush_promise.getResultWithTimeout(timeout);
				successfulFlush = r.booleanValue();
			} catch (TimeoutException e) {
				if (log.isTraceEnabled())
					log.trace("At " + localAddress
							+ " timed out waiting for flush responses after "
							+ timeout + " msec");
			}
		}

		if (!successfulFlush && numberOfAttempts > 0) {
			long backOffSleepTime = Util.random(5);
			backOffSleepTime = backOffSleepTime < 2 ? backOffSleepTime + 2: backOffSleepTime;
			if (log.isTraceEnabled())
				log.trace("At " + localAddress + ". Backing off for "
						+ backOffSleepTime + " sec. Attempts left "
						+ numberOfAttempts);

			Util.sleep(backOffSleepTime * 1000);
			Boolean succeededWhileWeSlept = (Boolean)flush_promise.getResult(1);
			boolean shouldRetry = !(succeededWhileWeSlept !=null && succeededWhileWeSlept.booleanValue());
			if(shouldRetry)
				successfulFlush = startFlush(evt, --numberOfAttempts, true);
		}
		return successfulFlush;
	}

	public void stopFlush() {
		down(new Event(Event.RESUME));
	}

	/*
	 * ------------------- end JMX attributes and operations
	 * ---------------------
	 */

	public Object down(Event evt) {
		switch (evt.getType()) {
		case Event.MSG:
			Message msg = (Message) evt.getArg();
			FlushHeader fh = (FlushHeader) msg.getHeader(getName());
			if (fh != null && fh.type == FlushHeader.FLUSH_BYPASS) {
				return down_prot.down(evt);
			} else {
				blockMessageDuringFlush();
			}
			break;
		case Event.GET_STATE:
			blockMessageDuringFlush();
			break;

		case Event.CONNECT:
			sendBlockUpToChannel();
			break;

		case Event.SUSPEND:
			return startFlush(evt, 3, false);

		case Event.RESUME:
			onResume();
			return null;
		}
		return down_prot.down(evt);
	}

	private void blockMessageDuringFlush() {
		boolean shouldSuspendByItself = false;
		long start = 0, stop = 0;
		synchronized (blockMutex) {
			while (isBlockingFlushDown) {
				if (log.isDebugEnabled())
					log.debug("FLUSH block at " + localAddress + " for "
							+ (timeout <= 0 ? "ever" : timeout + "ms"));
				try {
					start = System.currentTimeMillis();
					if (timeout <= 0)
						blockMutex.wait();
					else
						blockMutex.wait(timeout);
					stop = System.currentTimeMillis();
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt(); // set interrupt flag
					// again
				}
				if (isBlockingFlushDown) {
					isBlockingFlushDown = false;
					shouldSuspendByItself = true;
					blockMutex.notifyAll();
				}
			}
		}
		if (shouldSuspendByItself) {
			log.warn("unblocking FLUSH.down() at " + localAddress
					+ " after timeout of " + (stop - start) + "ms");
			flush_promise.setResult(Boolean.TRUE);
		}
	}

	public Object up(Event evt) {

		switch (evt.getType()) {
		case Event.MSG:
			Message msg = (Message) evt.getArg();
			FlushHeader fh = (FlushHeader) msg.getHeader(getName());
			if (fh != null) {
				if (fh.type == FlushHeader.FLUSH_BYPASS) {
					return up_prot.up(evt);
				} else if (fh.type == FlushHeader.START_FLUSH) {
					handleStartFlush(msg, fh);
				} else if (fh.type == FlushHeader.FLUSH_RECONCILE) {
					handleFlushReconcile(msg, fh);
				} else if (fh.type == FlushHeader.FLUSH_RECONCILE_OK) {
					onFlushReconcileOK(msg);
				} else if (fh.type == FlushHeader.STOP_FLUSH) {					
					onStopFlush();
				} else if (fh.type == FlushHeader.ABORT_FLUSH) {
					// abort current flush
					flush_promise.setResult(Boolean.FALSE);
				} else if (isCurrentFlushMessage(fh)) {
					if (fh.type == FlushHeader.FLUSH_OK) {
						onFlushOk(msg.getSrc(), fh.viewID);
					} else if (fh.type == FlushHeader.STOP_FLUSH_OK) {
						onStopFlushOk(msg.getSrc());
					} else if (fh.type == FlushHeader.FLUSH_COMPLETED) {
						onFlushCompleted(msg.getSrc(), fh.digest);
					}
				} else {
					if (log.isDebugEnabled())
						log.debug(localAddress
								+ " received outdated FLUSH message " + fh
								+ ",ignoring it.");
				}
				return null; // do not pass FLUSH msg up
			}
			break;

		case Event.VIEW_CHANGE:
			// if this is channel's first view and its the only member of the
			// group then the
			// goal is to pass BLOCK,VIEW,UNBLOCK to application space on the
			// same thread as VIEW.
			View newView = (View) evt.getArg();
			boolean firstView = onViewChange(newView);
			boolean singletonMember = newView.size() == 1
					&& newView.containsMember(localAddress);
			if (firstView && singletonMember) {
				up_prot.up(evt);
				synchronized (blockMutex) {
					isBlockingFlushDown = false;
					blockMutex.notifyAll();
				}
				if (log.isDebugEnabled())
					log.debug("At "
							+ localAddress
							+ " unblocking FLUSH.down() and sending UNBLOCK up");

				up_prot.up(new Event(Event.UNBLOCK));
				return null;
			}
			break;
			
		case Event.TMP_VIEW:					
			/*
			 * April 25, 2007
			 * 
			 * Accomodating current NAKACK (1.127)
			 * 
			 * Updates field currentView of a leaving coordinator.
			 * Leaving coordinator, after it sends out the view,
			 * does not need to participate in second flush phase.
			 * 
			 * see onStopFlush();
			 * 
			 * TODO: revisit if still needed post NAKACK 1.127
			 *  
			 */
			View tmpView = (View) evt.getArg();
			if(!tmpView.containsMember(localAddress)){
				onViewChange(tmpView);
			}
			break;					

		case Event.SET_LOCAL_ADDRESS:
			localAddress = (Address) evt.getArg();
			break;

		case Event.SUSPECT:
			onSuspect((Address) evt.getArg());
			break;

		case Event.SUSPEND:			           	         
			return startFlush(evt, 3, false);

		case Event.RESUME:
			onResume();
			return null;

		}

		return up_prot.up(evt);
	}

	private void onFlushReconcileOK(Message msg) {
		if (log.isDebugEnabled())
			log.debug(localAddress + " received reconcile ok from "
					+ msg.getSrc());

		synchronized (sharedLock) {
			reconcileOks.add(msg.getSrc());
			if (reconcileOks.size() >= flushMembers.size()) {
				flush_promise.setResult(Boolean.TRUE);
				if (log.isDebugEnabled())
					log.debug("All FLUSH_RECONCILE_OK received at "
							+ localAddress);
			}
		}
	}

	private void handleFlushReconcile(Message msg, FlushHeader fh) {
		Address requester = msg.getSrc();
		Digest reconcileDigest = fh.digest;

		if (log.isDebugEnabled())
			log.debug("Received FLUSH_RECONCILE at " + localAddress
					+ " passing digest to NAKACK " + reconcileDigest);

		// Let NAKACK reconcile missing messages
		down_prot.down(new Event(Event.REBROADCAST, reconcileDigest));

		if (log.isDebugEnabled())
			log.debug("Returned from FLUSH_RECONCILE at " + localAddress
					+ " Sending RECONCILE_OK to " + requester + ", thread "
					+ Thread.currentThread());

		Message reconcileOk = new Message(requester);
		reconcileOk.setFlag(Message.OOB);
		reconcileOk.putHeader(getName(), new FlushHeader(FlushHeader.FLUSH_RECONCILE_OK));
		down_prot.down(new Event(Event.MSG, reconcileOk));
	}

	private void handleStartFlush(Message msg, FlushHeader fh) {
		byte oldPhase = flushPhase.transitionToFirstPhase();
		if (oldPhase == FlushPhase.START_PHASE) {
			sendBlockUpToChannel();
			onStartFlush(msg.getSrc(), fh);
		} else if (oldPhase == FlushPhase.FIRST_PHASE) {
			Address flushRequester = msg.getSrc();
			Address coordinator = null;
			synchronized (sharedLock) {
				if(flushCoordinator != null)
					coordinator = flushCoordinator;
				else
					coordinator = flushRequester;
			}

			if (flushRequester.compareTo(coordinator) < 0) {
				rejectFlush(fh.viewID, coordinator);
				if (log.isDebugEnabled()) {
					log.debug("Rejecting flush at " + localAddress
							+ " to current flush coordinator " + coordinator
							+ " and switching flush coordinator to "
							+ flushRequester);
				}
				synchronized (sharedLock) {
					flushCoordinator = flushRequester;
				}
			} else if (flushRequester.compareTo(coordinator) > 0)  {
				rejectFlush(fh.viewID, flushRequester);
				if (log.isDebugEnabled()) {
					log.debug("Rejecting flush at " + localAddress
							+ " to flush requester " + flushRequester + " coordinator is " + coordinator);
				}
			}
			else if (flushRequester.equals(coordinator)){
				if (log.isDebugEnabled()) {
					log.debug("Accepting flush at " + localAddress
							+ ", proceeding with flush");
				}
				onStartFlush(msg.getSrc(), fh);
			}
		} else if (oldPhase == FlushPhase.SECOND_PHASE) {
			Address flushRequester = msg.getSrc();
			rejectFlush(fh.viewID, flushRequester);
			if (log.isDebugEnabled()) {
				log.debug("Rejecting flush in second phase at " + localAddress
						+ " to flush requester " + flushRequester);
			}
		}
	}

	public Vector<Integer> providedDownServices() {
		Vector<Integer> retval = new Vector<Integer>(2);
		retval.addElement(new Integer(Event.SUSPEND));
		retval.addElement(new Integer(Event.RESUME));
		return retval;
	}

	private void rejectFlush(long viewId, Address flushRequester) {
		Message reject = new Message(flushRequester, localAddress, null);
		reject.putHeader(getName(), new FlushHeader(FlushHeader.ABORT_FLUSH,viewId));
		down_prot.down(new Event(Event.MSG, reject));
	}

	private void sendBlockUpToChannel() {	
		up_prot.up(new Event(Event.BLOCK));		
	}

	private boolean isCurrentFlushMessage(FlushHeader fh) {
		return fh.viewID == currentViewId();
	}

	private long currentViewId() {
		long viewId = -1;
		synchronized (sharedLock) {
			ViewId view = currentView.getVid();
			if (view != null) {
				viewId = view.getId();
			}
		}
		return viewId;
	}

	private boolean onViewChange(View view) {
		boolean amINewCoordinator = false;
		boolean isThisOurFirstView = false;
		synchronized (sharedLock) {
			if (receivedFirstView) {
				receivedMoreThanOneView = true;
			}
			if (!receivedFirstView) {
				receivedFirstView = true;
			}
			isThisOurFirstView = receivedFirstView && !receivedMoreThanOneView;
			suspected.retainAll(view.getMembers());
			currentView = view;
			boolean coordinatorLeft = flushCoordinator != null && !view.containsMember(flushCoordinator);
		
			if(coordinatorLeft){
				flushCoordinator = view.getMembers().get(0);				
				amINewCoordinator = localAddress.equals(flushCoordinator);			
			}			
		}		

		// If coordinator leaves, its STOP FLUSH message will be discarded by
		// other members at NAKACK layer. Remaining members will be hung,
		// waiting
		// for STOP_FLUSH message. If I am new coordinator I will complete the
		// FLUSH and send STOP_FLUSH on flush callers behalf.
		if (amINewCoordinator) {
			if (log.isDebugEnabled())
				log.debug("Coordinator left, " + localAddress
						+ " will complete flush");
			onResume();
		}

		if (log.isDebugEnabled())
			log.debug("Installing view at  " + localAddress + " view is "
					+ view);

		return isThisOurFirstView;
	}

	private void onStopFlush() {
		flushPhase.transitionToSecondPhase();
		if (stats) {
			long stopFlushTime = System.currentTimeMillis();
			totalTimeInFlush += (stopFlushTime - startFlushTime);
			if (numberOfFlushes > 0) {
				averageFlushDuration = totalTimeInFlush / (double) numberOfFlushes;
			}
		}	
		
		/*
		 * April 25, 2007
		 * 
		 * Accomodating current NAKACK (1.127)
		 *
		 * ack this STOP_FLUSH only if we are surviving member
		 * otherwise we get runtime exception from NAKACK
		 * 
		 * TODO: revisit if still needed post NAKACK 1.127 
		 *  
		 */
		boolean amISurvivingMember = false;		
		synchronized(sharedLock){
			amISurvivingMember = currentView.containsMember(localAddress);					
		}
		if(amISurvivingMember){
			Message msg = new Message(null, localAddress, null);
			msg.putHeader(getName(), new FlushHeader(FlushHeader.STOP_FLUSH_OK,currentViewId()));		
			down_prot.down(new Event(Event.MSG, msg));			
			if (log.isDebugEnabled())
				log.debug("Received STOP_FLUSH and sent STOP_FLUSH_OK from "
						+ localAddress);
		}
	}

	private void onSuspend(View view) {
		Message msg = null;
		Collection<Address> participantsInFlush = null;
		synchronized (sharedLock) {
			// start FLUSH only on group members that we need to flush
			if (view != null) {
				participantsInFlush = new ArrayList<Address>(view.getMembers());
				participantsInFlush.retainAll(currentView.getMembers());
			} else {
				participantsInFlush = new ArrayList<Address>(currentView.getMembers());
			}
			msg = new Message(null, localAddress, null);
			msg.putHeader(getName(), new FlushHeader(FlushHeader.START_FLUSH,
					currentViewId(), participantsInFlush));
		}
		if (participantsInFlush.isEmpty()) {
			flush_promise.setResult(Boolean.TRUE);
		} else {
			down_prot.down(new Event(Event.MSG, msg));
			if (log.isDebugEnabled())
				log.debug("Flush coordinator " + localAddress
						+ " is starting FLUSH with participants " + participantsInFlush);
		}
	}

	private void onResume() {
		long viewID = currentViewId();
		Message msg = new Message(null, localAddress, null);
		msg.putHeader(getName(), new FlushHeader(FlushHeader.STOP_FLUSH, viewID));
		down_prot.down(new Event(Event.MSG, msg));
		if (log.isDebugEnabled())
			log.debug("Received RESUME at " + localAddress
					+ ", sent STOP_FLUSH to all");
	}

	private void onStartFlush(Address flushStarter, FlushHeader fh) {
		if (stats) {
			startFlushTime = System.currentTimeMillis();
			numberOfFlushes += 1;
		}
		boolean amIParticipant = false;
		synchronized (sharedLock) {
			flushCoordinator = flushStarter;
			flushMembers.clear();
			if (fh.flushParticipants != null) {
				flushMembers.addAll(fh.flushParticipants);
			}
			flushMembers.removeAll(suspected);
			amIParticipant = flushMembers.contains(localAddress);
		}
		if (amIParticipant) {
			Message msg = new Message(null);
			msg.putHeader(getName(), new FlushHeader(FlushHeader.FLUSH_OK,fh.viewID));
			down_prot.down(new Event(Event.MSG, msg));
			if (log.isDebugEnabled())
				log.debug("Received START_FLUSH at " + localAddress
						+ " responded with FLUSH_OK");
		}
	}

	private void onFlushOk(Address address, long viewID) {

		boolean flushOkCompleted = false;
		boolean amIParticipant = false;
		Message m = null;
		synchronized (sharedLock) {
			amIParticipant = flushMembers.contains(address);
			flushOkSet.add(address);
			flushOkCompleted = flushOkSet.containsAll(flushMembers);
			if (flushOkCompleted) {
				m = new Message(flushCoordinator);
			}
			if (log.isDebugEnabled())
				log.debug("At " + localAddress + " FLUSH_OK from " + address
						+ ",completed " + flushOkCompleted + ",  flushOkSet "
						+ flushOkSet);	
		}		

		if (flushOkCompleted && amIParticipant) {
			synchronized (blockMutex) {
				isBlockingFlushDown = true;
			}
			Digest digest = (Digest) down_prot.down(new Event(Event.GET_DIGEST));
			FlushHeader fh = new FlushHeader(FlushHeader.FLUSH_COMPLETED,viewID);
			fh.addDigest(digest);
			m.putHeader(getName(), fh);
			if (log.isDebugEnabled())
				log.debug(localAddress
						+ " is blocking FLUSH.down(). Sending FLUSH_COMPLETED message to "
						+ flushCoordinator);
			down_prot.down(new Event(Event.MSG, m));
			
		}
	}

	private void onStopFlushOk(Address address) {

		boolean stopFlushOkCompleted = false;
		synchronized (sharedLock) {
			stopFlushOkSet.add(address);
			TreeSet<Address> membersCopy = new TreeSet<Address>(currentView.getMembers());
			membersCopy.removeAll(suspected);
			stopFlushOkCompleted = stopFlushOkSet.containsAll(membersCopy);
			
			if (log.isDebugEnabled())
				log.debug("At " + localAddress + " STOP_FLUSH_OK from " + address
						+ ",completed " + stopFlushOkCompleted
						+ ",  stopFlushOkSet " + stopFlushOkSet);
		}		

		if (stopFlushOkCompleted) {
			synchronized (sharedLock) {
				flushPhase.transitionToStart();
				flushCompletedMap.clear();
				flushOkSet.clear();
				stopFlushOkSet.clear();
				flushMembers.clear();
				suspected.clear();
				flushCoordinator = null;			
			}			

			if (log.isDebugEnabled())
				log.debug("At " + localAddress
						+ " unblocking FLUSH.down() and sending UNBLOCK up");

			synchronized (blockMutex) {
				isBlockingFlushDown = false;
				blockMutex.notifyAll();
			}
			up_prot.up(new Event(Event.UNBLOCK));
		}
	}

	private void onFlushCompleted(Address address, Digest digest) {
		boolean flushCompleted = false;
		Message msg = null;
		boolean needsReconciliationPhase = false;		
		synchronized (sharedLock) {
			flushCompletedMap.put(address, digest);
			if (flushCompletedMap.size() >= flushMembers.size()) {
				flushCompleted = flushCompletedMap.keySet().containsAll(flushMembers);
			}
			
			if (log.isDebugEnabled())
				log.debug("At " + localAddress + " FLUSH_COMPLETED from " + address
						+ ",completed " + flushCompleted + ",flushCompleted "
						+ flushCompletedMap.keySet());
			
			needsReconciliationPhase = enable_reconciliation && flushCompleted && hasVirtualSynchronyGaps();
			if (needsReconciliationPhase){
				
				Digest d = findHighestSequences();			
				msg = new Message();
				msg.setFlag(Message.OOB);				
				FlushHeader fh = new FlushHeader(FlushHeader.FLUSH_RECONCILE, currentViewId(), flushMembers);
				reconcileOks.clear();
				fh.addDigest(d);
				msg.putHeader(getName(), fh);
				
				if (log.isTraceEnabled())
					log.trace("Reconciling flush mebers due to virtual synchrony gap, digest is "
									+ d + " flush members are " + flushMembers);
				
				flushCompletedMap.clear();
			}
		}	
		if(needsReconciliationPhase){
			down_prot.down(new Event(Event.MSG, msg));
		}else if(flushCompleted){
			flush_promise.setResult(Boolean.TRUE);
			if (log.isDebugEnabled())
				log.debug("All FLUSH_COMPLETED received at "
								+ localAddress);
		}		
	}

	private boolean hasVirtualSynchronyGaps() {
		ArrayList <Digest> digests = new ArrayList<Digest>();
		digests.addAll(flushCompletedMap.values());					
		Digest firstDigest = digests.get(0);
		List<Digest> remainingDigests = digests.subList(1, digests.size());
		for (Digest digest : remainingDigests) {
			Digest diff = firstDigest.difference(digest);
			if (diff != Digest.EMPTY_DIGEST) {
				return true;
			}
		}
		return false;	
	}

	private Digest findHighestSequences() {
		Digest result = null;		
		List<Digest> digests = new ArrayList<Digest>(flushCompletedMap.values());

        result =digests.get(0);
		List<Digest> remainingDigests = digests.subList(1, digests.size());

		for (Digest digestG : remainingDigests) {
			result = result.highestSequence(digestG);
		}		
		return result;
	}

	private void onSuspect(Address address) {
		boolean flushOkCompleted = false;
		Message m = null;
		long viewID = 0;
		synchronized (sharedLock) {
			suspected.add(address);
			flushMembers.removeAll(suspected);
			viewID = currentViewId();
			flushOkCompleted = !flushOkSet.isEmpty() && flushOkSet.containsAll(flushMembers);
			if (flushOkCompleted) {
				m = new Message(flushCoordinator, localAddress, null);
			}
			if (log.isDebugEnabled())
				log.debug("Suspect is " + address + ",completed "
						+ flushOkCompleted + ",  flushOkSet " + flushOkSet
						+ " flushMembers " + flushMembers);
		}
		if (flushOkCompleted) {
			Digest digest = (Digest) down_prot.down(new Event(Event.GET_DIGEST));
			FlushHeader fh = new FlushHeader(FlushHeader.FLUSH_COMPLETED,viewID);
			fh.addDigest(digest);
			m.putHeader(getName(), fh);
			down_prot.down(new Event(Event.MSG, m));
			if (log.isDebugEnabled())
				log.debug(localAddress + " sent FLUSH_COMPLETED message to "
						+ flushCoordinator);
		}		
	}

	private class FlushPhase {
		private byte phase = 0;

		public static final byte START_PHASE = 0;

		public static final byte FIRST_PHASE = 1;

		public static final byte SECOND_PHASE = 2;

		FlushPhase() {
		}

		public byte transitionToFirstPhase() {
			byte oldPhase = -1;
			synchronized(sharedLock){
				oldPhase = phase;
				if(oldPhase == START_PHASE){
					phase = FIRST_PHASE;
				}
			}
			return oldPhase;
		}

		public void transitionToStart() {
			synchronized(sharedLock){
				phase = START_PHASE;
			}
		}

		public void transitionToSecondPhase() {
			synchronized(sharedLock){
				phase = SECOND_PHASE;
			}
		}

		public boolean isFlushInProgress() {
			synchronized(sharedLock){
				return phase != START_PHASE;
			}
		}
	}

	public static class FlushHeader extends Header implements Streamable {
		public static final byte START_FLUSH = 0;

		public static final byte FLUSH_OK = 1;

		public static final byte STOP_FLUSH = 2;

		public static final byte FLUSH_COMPLETED = 3;

		public static final byte STOP_FLUSH_OK = 4;

		public static final byte ABORT_FLUSH = 5;

		public static final byte FLUSH_BYPASS = 6;

		public static final byte FLUSH_RECONCILE = 7;

		public static final byte FLUSH_RECONCILE_OK = 8;

		byte type;

		long viewID;

		Collection<Address> flushParticipants;

		Digest digest = null;

		public FlushHeader() {
			this(START_FLUSH, 0);
		} // used for externalization

		public FlushHeader(byte type) {
			this(type, 0);
		}

		public FlushHeader(byte type, long viewID) {
			this(type, viewID, null);
		}

		public FlushHeader(byte type, long viewID, Collection<Address> flushView) {
			this.type = type;
			this.viewID = viewID;
			this.flushParticipants = flushView;
		}

		public void addDigest(Digest digest) {
			this.digest = digest;
		}

		public String toString() {
			switch (type) {
			case START_FLUSH:
				return "FLUSH[type=START_FLUSH,viewId=" + viewID + ",members="
						+ flushParticipants + "]";
			case FLUSH_OK:
				return "FLUSH[type=FLUSH_OK,viewId=" + viewID + "]";
			case STOP_FLUSH:
				return "FLUSH[type=STOP_FLUSH,viewId=" + viewID + "]";
			case STOP_FLUSH_OK:
				return "FLUSH[type=STOP_FLUSH_OK,viewId=" + viewID + "]";
			case ABORT_FLUSH:
				return "FLUSH[type=ABORT_FLUSH,viewId=" + viewID + "]";
			case FLUSH_COMPLETED:
				return "FLUSH[type=FLUSH_COMPLETED,viewId=" + viewID + "]";
			case FLUSH_BYPASS:
				return "FLUSH[type=FLUSH_BYPASS,viewId=" + viewID + "]";
			case FLUSH_RECONCILE:
				return "FLUSH[type=FLUSH_RECONCILE,viewId=" + viewID
						+ ",digest=" + digest + "]";
			case FLUSH_RECONCILE_OK:
				return "FLUSH[type=FLUSH_RECONCILE_OK,viewId=" + viewID + "]";
			default:
				return "[FLUSH: unknown type (" + type + ")]";
			}
		}

		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeByte(type);
			out.writeLong(viewID);
			out.writeObject(flushParticipants);
			out.writeObject(digest);
		}

		public void readExternal(ObjectInput in) throws IOException,
				ClassNotFoundException {
			type = in.readByte();
			viewID = in.readLong();
			flushParticipants = (Collection) in.readObject();
			digest = (Digest) in.readObject();
		}

		public void writeTo(DataOutputStream out) throws IOException {
			out.writeByte(type);
			out.writeLong(viewID);
			if (flushParticipants != null && !flushParticipants.isEmpty()) {
				out.writeShort(flushParticipants.size());
				for (Iterator<Address> iter = flushParticipants.iterator(); iter.hasNext();) {
					Address address = iter.next();
					Util.writeAddress(address, out);
				}
			} else {
				out.writeShort(0);
			}
			if (digest != null) {
				out.writeBoolean(true);
				Util.writeStreamable(digest, out);
			} else {
				out.writeBoolean(false);
			}
		}

		public void readFrom(DataInputStream in) throws IOException,
				IllegalAccessException, InstantiationException {
			type = in.readByte();
			viewID = in.readLong();
			int flushParticipantsSize = in.readShort();
			if (flushParticipantsSize > 0) {
				flushParticipants = new ArrayList<Address>(flushParticipantsSize);
				for (int i = 0; i < flushParticipantsSize; i++) {
					flushParticipants.add(Util.readAddress(in));
				}
			}
			boolean hasDigest = in.readBoolean();
			if (hasDigest) {
				digest = (Digest) Util.readStreamable(Digest.class, in);
			}
		}
	}
}
