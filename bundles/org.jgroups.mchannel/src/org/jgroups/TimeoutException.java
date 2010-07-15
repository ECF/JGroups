// $Id: TimeoutException.java,v 1.1 2009/07/30 00:58:14 phperret Exp $

package org.jgroups;

/**
 * Thrown if members fail to respond in time.
 */
public class TimeoutException extends Exception {
    private static final long serialVersionUID = -3555655828017487825L;

    public TimeoutException() {
        super("TimeoutException");
    }

    public TimeoutException(String msg) {
        super(msg);
    }


    public String toString() {
        return super.toString();
    }
}
