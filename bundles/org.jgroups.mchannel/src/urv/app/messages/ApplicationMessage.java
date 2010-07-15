package urv.app.messages;

import java.io.Serializable;

/**
 * Class to provide a generic kind of messages for the
 * different applications made over the protocol
 * 
 * @author Raul Gracia
 *
 */
public class ApplicationMessage implements Serializable {
	
	//	CLASS FIELDS --

	private static final long serialVersionUID = 1L;
	private byte[] content;
	
	//	ACCESS METHODS --

	public byte[] getContent() {
		return content;
	}
	public void setContent(byte[] content) {
		this.content = content;
	}	
}