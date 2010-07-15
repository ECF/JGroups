package urv.util.graph;

import java.io.Serializable;

/**
 * Class
 * 
 * @author Marcel Arrufat
 */
public class Edge<N> implements Serializable {

	//	CLASS FIELDS --
	
	private N source;
	private N target;
	private Weight weight;

	//	CONSTRUCTORS --
	
	public Edge(N source, N target, Weight weight){
		this.source = source;
		this.target = target;
		this.weight = weight;
	}

	//	OVERRIDDEN METHODS --
	
	public Object clone(){
		Edge<N> newEdge = new Edge<N>(this.source,this.target,this.weight); 
		// TODO The N & Weight objects are not cloned!
		return newEdge;
	}
	public boolean equals(Object obj){
		Edge<N> e = (Edge<N>)obj;		
		return this.source.equals(e.source) && this.target.equals(e.target);
	}	
	public String toString(){
		return "src-dst: "+source+"-"+target+" W:"+weight;
	}
	
	//	ACCESS METHODS --

	public N getSource() {
		return source;
	}	
	public N getTarget() {
		return target;
	}	
	public Weight getWeight() {
		return weight;
	}
}