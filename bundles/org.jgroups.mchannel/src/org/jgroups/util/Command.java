// $Id: Command.java,v 1.1 2009/07/30 00:58:11 phperret Exp $

package org.jgroups.util;

/**
  * The Command patttern (see Gamma et al.). Implementations would provide their
  * own <code>execute</code> method.
  * @author Bela Ban
  */
public interface Command {
    boolean execute() throws Exception;
}
