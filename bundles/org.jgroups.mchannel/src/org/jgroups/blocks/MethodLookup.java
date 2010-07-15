package org.jgroups.blocks;

import java.lang.reflect.Method;

/**
 * @author Bela Ban
 * @version $Id: MethodLookup.java,v 1.1 2009/07/30 00:58:11 phperret Exp $
 */
public interface MethodLookup {
    Method findMethod(short id);
}
