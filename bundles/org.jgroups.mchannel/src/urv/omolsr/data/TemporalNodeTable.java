package urv.omolsr.data;

import urv.olsr.data.ExpiringEntryTable;
import urv.olsr.data.OLSRNode;

/**
 * @author Gerard Paris Aixala
 *
 */
public class TemporalNodeTable extends ExpiringEntryTable<OLSRNode,Object> {
	
	//	CONSTRUCTORS --
	
	public TemporalNodeTable(){}	

	// OVERRIDDEN METHODS --
	
	@Override
	public void onTableChange() {
		System.out.println("Entry removed from the temporal_node_table");

	}
	public String toString(){
		return "TEMPORAL_NODE_TABLE"+"\n"+super.toString();
	}
}