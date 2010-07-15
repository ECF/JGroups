package urv.log;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;

import org.jgroups.Address;

import urv.util.date.DateUtils;
import urv.util.graph.HashMapSet;

/**
 *
 * @author Gerard Paris Aixala
 */
public class Log {

	//	CONSTANTS --
	
	public static final int DEBUG 	= 0;
	public static final int INFO 	= 1;
	public static final int WARN	= 2;
	public static final int ERROR 	= 3;
	public static final int FATAL 	= 4;
	public static final int LOG_PERIOD = 10;	// in seconds
	
	//	CLASS FIELDS --
	
	private int lostDataMessages = 0;
	private int successfulDeliveredMessages = 0;
	private HashMapSet<String,Loggable> loggables = new HashMapSet<String,Loggable>();
	private HashSet<String> dumpingClasses = new HashSet<String>();
	private int currentLevel = FATAL;
	private BufferedWriter out = null;

	//	CONSTRUCTORS --
	
	private Log(){
		// TODO Read log properties from file
		try {
			String dir = "runResults" + File.separator;
			File baseDir = new File(dir);			
			String dateTime = DateUtils.getTimeFormatString();			
			//Create log directory
			if (!baseDir.exists()) baseDir.mkdir();
			out = new BufferedWriter(new FileWriter(dir+dateTime+"_olsr.log"));
		} catch (IOException e) {
			e.printStackTrace();
		}
		new LogThread().start();
	}
	
	//	STATIC METHODS --
	
	public static Log getInstance(){
		return SingletonHolder.INSTANCE;
	}
	
	//	PUBLIC METHODS --
	
	public void debug(String string) {
		if (currentLevel<=DEBUG){
			_out("[DEBUG]"+string);
		}
	}
	public void fatal(String string) {
		if (currentLevel<=FATAL){
			_out("[FATAL]"+string);
		}
		System.exit(-1);
	}
	public void increaseLostDataMessage() {
		lostDataMessages++;
	}
	public void incresaseSuccessfulDeliveredMessages(){
		successfulDeliveredMessages ++;
	}
	public void info(String string) {
		if (currentLevel<=INFO){
			_out("[INFO]"+string);
		}
	}
	public String printLoggables(){
		StringBuffer strBuffer = new StringBuffer();
		Iterator<String> classNames = loggables.keySet().iterator();
		while (classNames.hasNext()){
			String className = classNames.next();
			if (dumpingClasses.contains(className)){
				Iterator<Loggable> setIterator = loggables.getSet(className).iterator();
				while (setIterator.hasNext()){
					String output = setIterator.next().toString();
					_out(output);
					strBuffer.append(output+"\n");
				}
			}
		}
		try {
			out.flush();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return strBuffer.toString();
	}
	public void printMessageReceived(Address dest, Address src, String str){
		_out("MSG RCVD: "+dest+" from "+src+" "+str);
	}
	public void printMessageStatistics(){
		_out("Data messages: LOST="+lostDataMessages+"  DELIVERED="+successfulDeliveredMessages);
	}
	/**
	 * The classes registered with this method will be dumped when
	 * the method <code>printLoggables()</code> is invoked.
	 * @param className
	 */
	public void registerDumpingClass(String className){
		dumpingClasses.add(className);
	}
	/**
	 * Registers a loggable for the specified classname
	 */
	public void registerLoggable(String className, Loggable l){
		loggables.addToSet(className, l);
	}
	public void warn(String string) {
		if (currentLevel<=WARN){
			_out("[WARN]"+string);
		}
	}

	//	ACCESS METHODS --
	
	/**
	 * @param currentLevel the currentLevel to set
	 */
	public void setCurrentLevel(int currentLevel) {
		this.currentLevel = currentLevel;
	}
	
	//	PRIVATE METHODS --
	
	private void _out(String str){
		String dateStr = DateFormat.getDateTimeInstance(
				DateFormat.MEDIUM, DateFormat.MEDIUM).format(new Date());
		// Write to file
		try {
			out.write(dateStr+"\n");
			out.write(str+"\n");
			//out.flush();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	//	PRIVATE CLASSES --
	
	private class LogThread extends Thread{
		
		//	CONSTRUCTORS --
		
		public LogThread(){}

		//	OVERRIDDEN METHODS --
		
		public void run(){
			while(true){
				try {
					Thread.sleep(LOG_PERIOD*1000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				Log.getInstance().printLoggables();
			}
		}
	}
	
	//	PRIVATE METHODS --
	
	private static class SingletonHolder {
		private final static Log INSTANCE = new Log();
	}
}