package urv.app.messages;

import java.io.Serializable;

/**
 * @author Gerard Paris Aixala
 *
 */
public class ReplyObject implements Serializable{

	//	CLASS FIELDS --
	
	private static final long serialVersionUID = 1L;
	private long id;
	
	//	CONSTRUCTORS --
	
    public ReplyObject() {}

    public ReplyObject(long id) {
		this.id = id;
	}

    //	OVERRIDDEN METHODS --
    
	public String toString() {
        StringBuffer ret=new StringBuffer();
        ret.append("[ " +  "]");
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
