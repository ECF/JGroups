// $Id: Promise.java,v 1.1 2009/07/30 00:58:11 phperret Exp $

package org.jgroups.util;

import org.jgroups.TimeoutException;


/**
 * Allows a thread to submit an asynchronous request and to wait for the result. The caller may choose to check
 * for the result at a later time, or immediately and it may block or not. Both the caller and responder have to
 * know the promise.
 * @author Bela Ban
 */
public class Promise {
    Object result=null;
    boolean hasResult=false;


    /**
     * Blocks until a result is available, or timeout milliseconds have elapsed
     * @param timeout
     * @return An object
     * @throws TimeoutException If a timeout occurred (implies that timeout > 0)
     */
    public Object getResultWithTimeout(long timeout) throws TimeoutException {
        synchronized(this) {
            try {
                return _getResultWithTimeout(timeout);
            }
            finally {
                notifyAll();
            }
        }
    }


    /**
     * Blocks until a result is available, or timeout milliseconds have elapsed. Needs to be called with
     * a lock held on 'this'
     * @param timeout
     * @return An object
     * @throws TimeoutException If a timeout occurred (implies that timeout > 0)
     */
    private Object _getResultWithTimeout(long timeout) throws TimeoutException {
        Object  ret=null;
        long    time_to_wait=timeout, start;
        boolean timeout_occurred=false;

        start=System.currentTimeMillis();
        while(hasResult == false) {
            if(timeout <= 0) {
                doWait();
            }
            else {
                if(time_to_wait <= 0) {
                    timeout_occurred=true;
                    break; // terminate the while loop
                }
                else {
                    doWait(time_to_wait);
                    time_to_wait=timeout - (System.currentTimeMillis() - start);
                }
            }
        }

        ret=result;
        result=null;
        hasResult=false;
        if(timeout_occurred)
            throw new TimeoutException();
        else
            return ret;
    }

    public Object getResult() {
        try {
            return getResultWithTimeout(0);
        }
        catch(TimeoutException e) {
            return null;
        }
    }

    /**
     * Returns the result, but never throws a TimeoutException; returns null instead.
     * @param timeout
     * @return Object
     */
    public Object getResult(long timeout) {
        try {
            return getResultWithTimeout(timeout);
        }
        catch(TimeoutException e) {
            return null;
        }
    }


    void doWait() {
        try {wait();} catch(InterruptedException e) {}
    }

    void doWait(long timeout) {
        try {wait(timeout);} catch(InterruptedException e) {}
    }




    /**
     * Checks whether result is available. Does not block.
     */
    public boolean hasResult() {
        synchronized(this) {
            return hasResult;
        }
    }

    /**
     * Sets the result and notifies any threads
     * waiting for it
     */
    public void setResult(Object obj) {
        synchronized(this) {
            result=obj;
            hasResult=true;
            notifyAll();
        }
    }


    /**
     * Causes all waiting threads to return
     */
    public void reset() {
        synchronized(this) {
            result=null;
            hasResult=false;
            notifyAll();
        }
    }


    public String toString() {
        return "hasResult=" + hasResult + ",result=" + result;
    }


}