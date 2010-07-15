package org.jgroups.protocols;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.HashSet;
import java.util.Set;

import org.jgroups.Address;
import org.jgroups.Header;
import org.jgroups.stack.IpAddress;

import urv.olsr.data.OLSRNode;
import urv.util.graph.HashMapSet;

/**
 * Header for unicast messages sent in OMOLSR:
  * @author Marcel Arrufat Arias
 */
public class OMOLSRHeader extends Header {

	public static final byte DATA=1;   // arg = null
    public static final byte CONTROL=2;   // arg = PingRsp(local_addr, coord_addr)
    
    public byte type;
    
    //TODO: check if it is needed in OMOLSR
    public IpAddress groupId;
    
    public IpAddress srcAddress;
	/**
     * A list of following destinations 
     * <Address>
     */
    private HashMapSet<OLSRNode,OLSRNode> forwardingTable = new HashMapSet<OLSRNode,OLSRNode>();
    
	/**
	 * @param type
	 */
	public OMOLSRHeader() {
		
	}
	
	public static String type2Str(byte t) {
		switch (t) {
			case DATA :
				return "DATA";
			case CONTROL :
				return "CONTROL";
			default :
				return "<undefined>";
		}
	}

	/**
	 * Gets the list of all nodes that must receive the 
	 * packet (from all nodes)
	 * @return list
	 */
	public HashMapSet<OLSRNode,OLSRNode> getForwardingTable(){
		return this.forwardingTable;
	}
	
	/**
	 * Gets the list of all nodes that must receive the 
	 * packet (from one node)
	 * @param node
	 * @return
	 */
	public HashSet<OLSRNode> getForwardingTableEntry(OLSRNode node){
		return this.forwardingTable.get(node);
	}
	
	/**
	 * @return Returns the srcAddress.
	 */
	public Address getSrcAddress() {
		return srcAddress;
	}

	/* (non-Javadoc)
	 * @see java.io.Externalizable#readExternal(java.io.ObjectInput)
	 */
	public void readExternal(ObjectInput in) throws IOException,
			ClassNotFoundException {
		type = in.readByte();
		groupId = (IpAddress)in.readObject();
		srcAddress = (IpAddress)in.readObject();
		forwardingTable = (HashMapSet<OLSRNode,OLSRNode>)in.readObject();
		
	}
	
	
    
	/**
	 * Sets the list of all nodes that must receive the
	 * packet
	 * @param list
	 */
	public void setForwardingTable(HashMapSet<OLSRNode,OLSRNode> forwardingTable){
		this.forwardingTable = forwardingTable;
	}

	/**
	 * @param groupId the groupId to set
	 */
	public void setGroupId(Address groupId) {
		this.groupId = (IpAddress)groupId;
	}

	
	/**
	 * @param srcAddress The srcAddress to set.
	 */
	public void setSrcAddress(Address srcAddress) {
		this.srcAddress = (IpAddress)srcAddress;
	}
	
	public void setType(byte type){
		this.type = type;
	}


	public String toString() {
		StringBuffer ret = new StringBuffer();
		ret.append("["
				+ type2Str(type));
		if (groupId!=null){
			ret.append(", mcast=" + groupId);
		}
		if (srcAddress!=null){
			ret.append(", src=" + srcAddress);
		}
		if (type == DATA){
			// TODO show the list of successive destinations
			//ret.append(", something=" + something);
			ret.append("\n");
			for(OLSRNode node:forwardingTable.keySet()){
				Set<OLSRNode> nodeSet = forwardingTable.get(node);
				ret.append("+ VN-Node:"+node+" is responsible for: \n");
				for(OLSRNode nodeInSet:nodeSet){
					ret.append("\t - Node "+nodeInSet+"\n");
					
				}
				
			}
		}
		if (type == CONTROL){
			//ret.append(", something=" + something);
		}
		ret.append(']');

		return ret.toString();
	}

	/* (non-Javadoc)
	 * @see java.io.Externalizable#writeExternal(java.io.ObjectOutput)
	 */
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeByte(type);
		out.writeObject(groupId);
		out.writeObject(srcAddress);
		out.writeObject(forwardingTable);
		

	}





}
