package urv.util.graph;

import java.io.Serializable;

/**
 * Class that represents weight in net files containing
 * latency model information
 * 
 * @author Marcel Arrufat
 *
 */
public class Weight implements Comparable, Serializable{

	//	CLASS FIELDS --
	
	static private Weight negativeInfinityWeight = new Weight().setNegativeInfinityValue();
	private Float weight;
	
	//	CONSTRUCTORS --
	
	public Weight (){}

	//	STATIC METHODS --
	
	static public Weight add(Weight w1, Weight w2){		
		Weight w = new Weight();
		w.setValue(new Float(w1.weight.floatValue()+w2.weight.floatValue()));
		return w;
	}	
	/**
	 * Returns a unique negative infinity Weight object, necessary for Dijkstra comparisons
	 * @return
	 */
	static public Weight getNegativeInfinityWeight(){
		return negativeInfinityWeight; 
	}	

	//	OVERRIDDEN METHODS --
	
	/**
	 * Compares weight values
	 */
	public int compareTo(Object arg0) {
		
		if (this.weight.floatValue() < ((Weight)arg0).weight.floatValue())
			return -1;
		else if (this.weight.floatValue() > ((Weight)arg0).weight.floatValue())
			return 1;
		else return 0;
	
	}

	//	ACCESS METHODS --
	
	public Float getValue(){
		return this.weight;
	}	
	public boolean isSet() {
		if (this.weight!=null){
			return true;
		}
		else {
			return false;
		}
	}	
	/**
	 * Sets Weight to its maximum value. Needed for Dijkstra's Algorithm
	 * @return
	 */
	public Weight setMaxValue(){		
		this.weight = new Float(Float.MAX_VALUE);
		return this;
	}	
	public Weight setNegativeInfinityValue(){		
		this.weight = new Float(Float.NEGATIVE_INFINITY);
		return this;		
	}	
	public Weight setValue(Object o){
		this.weight=(Float)o;
		return this;
	}
	public Weight setZeroValue(){		
		this.weight = new Float(0.0f);
		return this;
		
	}	
	/**
	 * String containing weight
	 */
	public String toString(){		
		return ""+weight;		
	}	
}