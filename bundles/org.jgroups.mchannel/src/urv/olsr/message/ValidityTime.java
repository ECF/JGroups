package urv.olsr.message;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import org.jgroups.util.Streamable;

/**
 * This field indicates for how long time after reception a node MUST consider
 * the information contained in the message as valid, unless a more recent
 * update to the information is received. The validity time is represented by
 * its mantissa (four highest bits of Vtime field) and by its exponent (four
 * lowest bits of Vtime field). In other words:
 * 
 * validity time = C*(1+a/16)* 2^b [in seconds]
 * 
 * where a is the integer represented by the four highest bits of Vtime field
 * and b the integer represented by the four lowest bits of Vtime field. The
 * proposed value of the scaling factor C is specified in section 18.
 * 
 * @author Gerard Paris Aixala
 * 
 */
public class ValidityTime implements Streamable {

	// CLASS FIELDS --

	private static final double C = 0.0625; // C = 1/16 seconds
	private static final int MS4BITS_MASK = 15; // 00001111
	private double vTime; // in seconds

	// CONSTRUCTORS --

	public ValidityTime(double vTime) {
		this.vTime = vTime;
	}

	// STATIC METHODS --

	public static int signedByteToInt(byte b) {
		return (int) b & 0xFF;
	}

	// OVERRIDDEN METHODS --

	public void readFrom(DataInputStream in) throws IOException,
			IllegalAccessException, InstantiationException {
		int read = signedByteToInt(in.readByte());
		int a = read / 16;
		int b = read & MS4BITS_MASK;
		vTime = C * (1.0 + a / 16.0) * Math.pow(2.0, b);
	}
	public void writeTo(DataOutputStream out) throws IOException {
		int tmp = 0;
		int b = (int) Math.floor(Math.log10(vTime / C) / Math.log10(2));
		int a = (int) Math.ceil(16 * (vTime / (C * Math.pow(2, b)) - 1));
		if (a == 16) {
			b++;
			a = 0;
		}
		tmp = a * 16 + b;
		out.writeByte(tmp & 0xFF);
	}

	//	ACCESS METHODS --
	
	/**
	 * @return the vTime
	 */
	public double getVTime() {
		return vTime;
	}
}