package urv.olsr.data;

import java.util.Hashtable;
import java.util.Iterator;
import java.util.Set;

/**
 * Table which stores some information that may eventually expire
 * if no updates are received during a certain time span
 * The access to the table is synchronized
 * 
 * @author Marcel Arrufat Arias
 */
public abstract class ExpiringEntryTable <K,V> extends Hashtable<K,Integer>{
	
	//	CLASS FIELDS --
	
	//Key will be OLSRNode and V NeighbourTableEntry
	private Hashtable<K,V> dataTable; 
	private Object lock = new Object();
	
	//	CONSTRUCTORS --
	
	/**
	 * Creates a new table that will remove an entry which
	 * has not receive an update in <code>expiringTime</code>
	 * milliseconds 
	 * @param expiringTime
	 */
	public ExpiringEntryTable() {
		super();
		dataTable = new Hashtable<K,V>();	
	}	
	
	//	OVERRIDDEN METHODS --
	
	public Set<K> keySet(){		
		return dataTable.keySet();		
	}
	public String toString(){
		StringBuffer buff = new StringBuffer();
		synchronized (lock) {
			for (K key:dataTable.keySet()){
				buff.append(key+" (t="+super.get(key)+")\n");
				buff.append("\t"+getEntry(key));
				buff.append("\n");
			}
		}
		buff.append("\n");
		return buff.toString();
	}
	
	//	PUBLIC METHODS --
	
	/**
	 * Adds a new entry in the table
	 * Mark the entry as information up-to-date
	 * @param key
	 * @param value
	 * @param timestamp the validity time of this entry in milliseconds
	 */
	public void addEntryWithTimestamp(K key,V value,int timestamp){		
		synchronized (lock) {
			dataTable.put(key, value);
			//TODO: check the value of timestamp is correct
			this.put(key, timestamp);
		}
	}
	/**
	 * Decreases the valid time for all entries and
	 * removes the ones which exceed the maximum time
	 * @param refreshTime
	 */
	public void decreaseTimeAndProcessEntries(long refreshTime){
		
		synchronized (lock) {
			Iterator<K> it = this.keySet().iterator();
			while (it.hasNext()) {
				K key = it.next(); 
				
				int currentTime = this.get(key).intValue();
				currentTime -= refreshTime;

				// Remove if current time has exceeded max time
				if (currentTime <= 0) {
					it.remove();
					V value= dataTable.remove(key);
					onTableChange(); // TODO Should we call this method??
				}
				// Or update currentTime
				else {
					this.put(key, new Integer(currentTime));
				}
			}
		}
	}
	/**
	 * Return the entry corresponding with 
	 * the given key
	 * @param key
	 */
	public V getEntry(K key){
		V value;
		synchronized (lock) {
			value = dataTable.get(key);
		}
		return value;
	}
	public abstract void onTableChange();	
	/**
	 * Overwrite an existing entry in the table
	 * Mark the entry as information up-to-date
	 * @param key
	 * @param value
	 * @param timestamp the validity time of this entry in milliseconds
	 */
	public void overwriteEntryWithTimestamp(K key,V value,int timestamp){
		addEntryWithTimestamp(key, value, timestamp);
	}	
	/**
	 * Removes an entry from the table
	 * @param key
	 */
	public void removeEntry(K key){		
		synchronized (lock) {
			dataTable.remove(key);
			this.remove(key);
		}
	}
	public void updateTimestampInEntry(K key,int msec){
		synchronized (lock) {
			Integer i = this.get(key);
			i = new Integer(msec);
			this.put(key,i);
		}
	}
	
	//	ACCESS METHODS --
	
	/**
	 * @return Returns the lock.
	 */
	public Object getLock() {
		return lock;
	}
}