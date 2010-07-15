
package org.jgroups.stack;



/**
 * Interface which returns a time series, one value at a time calling next()
 * @author Bela Ban
 * @version $Id: Interval.java,v 1.1 2009/07/30 00:58:14 phperret Exp $
 */
public interface Interval {
    /** @return the next interval */
    public long next() ;

    /** Returns a copy of the state. If there is no state, this method may return a ref to itself */
    Interval copy();
}

