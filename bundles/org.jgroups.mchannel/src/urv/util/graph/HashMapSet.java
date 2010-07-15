package urv.util.graph;

import java.util.HashMap;
import java.util.HashSet;

/**
 * This class offers methods to store elements in 
 * a list placed into a Hashmap
 * 
 * @author Marcel Arrufat Arias
 */
public class HashMapSet<K,V> extends HashMap<K,HashSet<V>>{

	//	CONSTRUCTORS --
	
	public HashMapSet(){
		super();
	}
	
	//	PUBLIC METHODS --
	
	/**
	 * Adds a new value to the list of the specified key
	 * @param key
	 * @param value
	 */
	public void addToSet(K key,V value){
		
		HashSet<V> l = (HashSet<V>)this.get(key);
		if (l==null) l = new HashSet<V>();
		l.add(value);
		this.put(key,l);
	}	
	/**
	 * Checks whether the given value exists in the set
	 * of the given key
	 * @param key
	 * @param value
	 * @return
	 */
	public boolean existsInList(K key, V value){
		HashSet<V> l = this.get(key);
		if (l==null){
			return false;
		} else {
			return l.contains(value);
		}
	}
	/**
	 * Returns the whole set from a given key
	 * @param key
	 * @return
	 */
	public HashSet<V> getSet(K key){
		return this.get(key);
	}	
	/**
	 * Adds a new set to the specified key
	 * @param key
	 * @param value
	 */
	public void putSet(K key,HashSet<V> value){		
		this.put(key,value);
	}	
	/**
	 * Removes key1 from set key2 and viceversa
	 * @param key1
	 * @param key2
	 */
	public void removeFromBothSets(K key1, K key2){
		getSet(key1).remove(key2);
		getSet(key2).remove(key1);
	}	
	/**
	 * Removes a value from the set of the specified key
	 * @param key
	 * @param value
	 */
	public void removeFromSet(K key,V value){
		HashSet<V> l = (HashSet<V>)this.get(key);
		if (l!=null){
			l.remove(value);
			this.put(key,l);
		}
	}
}