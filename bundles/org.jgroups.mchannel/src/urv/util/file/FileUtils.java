package urv.util.file;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import urv.app.messages.FileMessage;

/**
 * Class that encapsulates useful methods to manage files
 * 
 * @author Raúl Gracia
 */

public class FileUtils {
	
	//	PUBLIC METHODS --
	
	/**
	 * Creates a FileMessage object filled with the amount of data specified in the
	 * size (kb) parameter
	 * 
	 * @param fileName
	 * @param size
	 * @return FileMessage object
	 */
	public static synchronized FileMessage createFileOfCorrectSize(String fileName, int size) {
		FileMessage fileMessage = null;
		fileMessage = new FileMessage(fileName, new byte[size*1000]);
		return fileMessage;	
	}	
	/**
	 * This method creates a file into the "receivedFile" folder when an
	 * instance of FileMessage is received
	 * 
	 * @param fileMessage
	 */
	public static synchronized void generateReceivedFile(FileMessage fileMessage){
		String dir = FileMessage.filesReceivedDirectory;
		File baseDir = new File(dir);
		//Create directory for the received files
		if (!baseDir.exists()) baseDir.mkdir();
		
		File file = new File(dir+File.separator+fileMessage.getFileName());
		FileOutputStream out;
		try {
			out = new FileOutputStream(file);		
			ByteArrayInputStream in = new ByteArrayInputStream(fileMessage.getContent());
			//Read the content of the fileMessage object
			byte [] content = new byte[1024];
			int readResult = 0;
			while ((readResult = in.read(content))>=0){
				out.write(content, 0, readResult);
			}
			//create the file
			out.flush();
			//close channels
			out.close();
			in.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();	
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}