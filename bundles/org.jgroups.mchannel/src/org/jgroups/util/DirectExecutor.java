package org.jgroups.util;

import java.util.concurrent.Executor;

/**
 * @author Bela Ban
 * @version $Id: DirectExecutor.java,v 1.1 2009/07/30 00:58:11 phperret Exp $
 */
public class DirectExecutor implements Executor {
    public void execute(Runnable command) {
        command.run();
    }
}
