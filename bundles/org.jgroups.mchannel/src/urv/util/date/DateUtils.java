package urv.util.date;

import java.util.Calendar;
import java.util.GregorianCalendar;

/**
 * Class used to retrieve as a String the current date in
 * a easy format.
 * 
 * @author Marcel Arrufat
 */
public class DateUtils {

	//	PUBLIC METHODS --
	
	/**
	 * This method returns the current date as a String
	 */
	public static String getTimeFormatString(){
		GregorianCalendar gregDate = new GregorianCalendar();
		int day = gregDate.get(Calendar.DAY_OF_MONTH);
		int month = gregDate.get(Calendar.MONTH)+1;
		int year = gregDate.get(Calendar.YEAR);
		int hour = gregDate.get(Calendar.HOUR);
		int minute = gregDate.get(Calendar.MINUTE);
		int second = gregDate.get(Calendar.SECOND);
		String dateStr = year +"-"+month+"-"+day+" "+hour+"."+minute+"."+second;
		return dateStr;
	}
}