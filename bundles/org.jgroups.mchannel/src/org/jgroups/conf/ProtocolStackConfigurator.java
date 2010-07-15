// $Id: ProtocolStackConfigurator.java,v 1.1 2009/07/30 00:58:11 phperret Exp $

package org.jgroups.conf;

/**
 * @author Filip Hanik (<a href="mailto:filip@filip.net">filip@filip.net)
 * @version 1.0
 */

public interface ProtocolStackConfigurator
{
    String         getProtocolStackString();
    ProtocolData[] getProtocolStack();
}
