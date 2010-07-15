package urv.app.messages;

import java.io.File;

/**
 * This class is used to encapsulate the files retransmitted by the
 * test applications.
 * 
 * @author Raul Gracia
 */
public class FileMessage extends ApplicationMessage{

	//	CLASS FIELDS --
	
	private static final long serialVersionUID = 1L;
	public static final String filesReceivedDirectory = System.getProperty("user.dir")+
		File.separator + "receivedFiles";
	private String fileName;
	
	//	CONSTRUCTORS --
	
	public FileMessage (String fileName, byte[] fileContent){
		this.fileName = fileName;
		setContent(fileContent);
	}
	
	//	ACCESS METHODS --
	
	public String getFileName() {
		return fileName;
	}
	public void setFileName(String fileName) {
		this.fileName = fileName;
	}
}