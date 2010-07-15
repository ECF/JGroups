package org.jgroups.blocks;


/**
 * Thrown by the {@link org.jgroups.blocks.DistributedLockManager#unlock(Object, Object, boolean)} method if a lock is only locally released, because it is locked
 * by multiple DistributedLockManagers. This can happen after a merge for example.
 * 
 * @author Robert Schaffar-Taurok (robert@fusion.at)
 * @version $Id: LockMultiLockedException.java,v 1.1 2009/07/30 00:58:11 phperret Exp $
 */
public class LockMultiLockedException extends Exception {

    private static final long serialVersionUID = 3719208228960070835L;

	public LockMultiLockedException() {
        super();
    }

    public LockMultiLockedException(String s) {
        super(s);
    }

}
