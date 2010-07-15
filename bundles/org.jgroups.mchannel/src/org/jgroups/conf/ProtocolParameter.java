
package org.jgroups.conf;

/**
 * Data holder for protocol data
 *
 * @author Filip Hanik (<a href="mailto:filip@filip.net">filip@filip.net)
 * @author Bela Ban
 * @version $Id: ProtocolParameter.java,v 1.1 2009/07/30 00:58:11 phperret Exp $
 */

public class ProtocolParameter {

    private final String mParameterName;
    private String mParameterValue;

    public ProtocolParameter(String parameterName,
                             String parameterValue) {
        mParameterName=parameterName;
        mParameterValue=parameterValue;
    }

    public String getName() {
        return mParameterName;
    }

    public String getValue() {
        return mParameterValue;
    }

    public void setValue(String replacement) {
        mParameterValue=replacement;
    }

    public int hashCode() {
        if(mParameterName != null)
            return mParameterName.hashCode();
        else
            return -1;
    }

    public boolean equals(Object another) {
        if(another instanceof ProtocolParameter)
            return getName().equals(((ProtocolParameter)another).getName());
        else
            return false;
    }

    public String getParameterString() {
        StringBuffer buf=new StringBuffer(mParameterName);
        if(mParameterValue != null)
            buf.append('=').append(mParameterValue);
        return buf.toString();
    }

    public String getParameterStringXml() {
        StringBuffer buf=new StringBuffer(mParameterName);
        if(mParameterValue != null)
            buf.append("=\"").append(mParameterValue).append('\"');
        return buf.toString();
    }


}
