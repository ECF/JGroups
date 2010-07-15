package urv.util.graph;

/**
 * Class that represents a file id.
 * This id is read from net file (such as Pajek files)
 * for latency model support.
 * 
 * @author Marcel Arrufat
 */
public class Node {

	//	CLASS FIELDS --
	
	private Integer id;
	
	//	CONSTRUCTORS --
	
	public Node(int i){
		id = new Integer(i);
	}

	//	OVERRIDDEN METHODS --
	
	public boolean equals(Object o){
		Node n = (Node) o;		
		return id.equals(n.id);
		
	}
	public String toString(){
		return this.id.toString();
	}
	
	//	ACCESS METHODS --
	
	public Integer getId(){
		return this.id;
	}	
	public int hashCode(){
		return id.hashCode();
	}	
	public void setId(Integer id){		
		this.id=id;		
	}
	public Node setValue(Object o){		
		id=(Integer)o;
		return this;
	}
}