
package urv.app.messages;

import java.io.Serializable;
import java.util.Random;

/**
 * @author Gerard Paris Aixala
 * 
 */
public class RequestObject implements Serializable{

	//	CLASS FIELDS --
	
	private static final long serialVersionUID = 1L;
	private long id;
	private Random rand = new Random();
	
	//	CONSTRUCTORS --
	
    public RequestObject() {
    	rand.setSeed(System.currentTimeMillis());
    	id = rand.nextLong();
    }

    //	OVERRIDDEN METHODS --
    
	public String toString() {
        StringBuffer ret=new StringBuffer();
        ret.append("REQ_" + id);
        return ret.toString();
    }
	
	//	ACCESS METHODS --
	
    /**
	 * @return the id
	 */
	public long getId() {
		return id;
	}
}