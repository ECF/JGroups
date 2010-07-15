// $Id: NakAckHeader.java,v 1.1 2009/07/30 00:58:14 phperret Exp $

package org.jgroups.protocols.pbcast;


import org.jgroups.Address;
import org.jgroups.Global;
import org.jgroups.Header;
import org.jgroups.util.Range;
import org.jgroups.util.Streamable;
import org.jgroups.util.Util;

import java.io.*;


public class NakAckHeader extends Header implements Streamable {
    public static final byte MSG=1;       // regular msg
    public static final byte XMIT_REQ=2;  // retransmit request
    public static final byte XMIT_RSP=3;  // retransmit response (contains one or more messages)


    byte  type=0;
    long  seqno=-1;        // seqno of regular message (MSG)
    Range range=null;      // range of msgs to be retransmitted (XMIT_REQ) or retransmitted (XMIT_RSP)
    Address sender;        // the original sender of the message (for XMIT_REQ)


    public NakAckHeader() {
    }


    /**
     * Constructor for regular messages
     */
    public NakAckHeader(byte type, long seqno) {
        this.type=type;
        this.seqno=seqno;
    }

    /**
     * Constructor for retransmit requests/responses (low and high define the range of msgs)
     */
    public NakAckHeader(byte type, long low, long high) {
        this.type=type;
        range=new Range(low, high);
    }


    public NakAckHeader(byte type, long low, long high, Address sender) {
        this(type, low, high);
        this.sender=sender;
    }




    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeByte(type);
        out.writeLong(seqno);
        if(range != null) {
            out.writeBoolean(true);  // wasn't here before, bad bug !
            range.writeExternal(out);
        }
        else
            out.writeBoolean(false);
        out.writeObject(sender);
    }


    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        boolean read_range;
        type=in.readByte();
        seqno=in.readLong();
        read_range=in.readBoolean();
        if(read_range) {
            range=new Range();
            range.readExternal(in);
        }
        sender=(Address)in.readObject();
    }

    public void writeTo(DataOutputStream out) throws IOException {
        out.writeByte(type);
        if(type != XMIT_RSP)
            out.writeLong(seqno);
        Util.writeStreamable(range, out);
        Util.writeAddress(sender, out);
    }

    public void readFrom(DataInputStream in) throws IOException, IllegalAccessException, InstantiationException {
        type=in.readByte();
        if(type != XMIT_RSP)
            seqno=in.readLong();
        range=(Range)Util.readStreamable(Range.class, in);
        sender=Util.readAddress(in);
    }

    public int size() {
        // type (1 byte) + seqno (8 bytes)
        int retval=Global.BYTE_SIZE;

        if(type != XMIT_RSP) // we don't send the seqno if this is an XMIT_RSP
            retval+=Global.LONG_SIZE;

        retval+=Global.BYTE_SIZE; // presence for range
        if(range != null)
            retval+=2 * Global.LONG_SIZE; // 2 times 8 bytes for seqno
        retval+=Util.size(sender);
        return retval;
    }


    public NakAckHeader copy() {
        NakAckHeader ret=new NakAckHeader(type, seqno);
        ret.range=range;
        ret.sender=sender;
        return ret;
    }


    public static String type2Str(byte t) {
        switch(t) {
            case MSG:
                return "MSG";
            case XMIT_REQ:
                return "XMIT_REQ";
            case XMIT_RSP:
                return "XMIT_RSP";
            default:
                return "<undefined>";
        }
    }


    public String toString() {
        StringBuffer ret=new StringBuffer();
        ret.append("[").append(type2Str(type));
        switch(type) {
            case MSG:           // seqno and sender
                ret.append(", seqno=").append(seqno);
                break;
            case XMIT_REQ:  // range and sender
            case XMIT_RSP:  // range and sender
                if(range != null)
                    ret.append(", range=").append(range);
                break;
        }

        if(sender != null) ret.append(", sender=").append(sender);
        ret.append(']');
        return ret.toString();
    }


}
