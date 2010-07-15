package org.jgroups.jmx;

import java.util.Map;
import java.util.Properties;

/**
 * @author Bela Ban
 * @version $Id: ProtocolMBean.java,v 1.1 2009/07/30 00:58:14 phperret Exp $
 */
public interface ProtocolMBean {
    String getName();
    String getPropertiesAsString();
    void setProperties(Properties p);
    boolean getStatsEnabled();
    void setStatsEnabled(boolean flag);
    void resetStats();
    String printStats();
    Map dumpStats();
    void create() throws Exception;
    void start() throws Exception;
    void stop();
    void destroy();

}
