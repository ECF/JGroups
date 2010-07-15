package urv.log;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Hashtable;

import javax.swing.JTextPane;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.SimpleAttributeSet;

import org.apache.log4j.Layout;
import org.apache.log4j.Level;
import org.apache.log4j.WriterAppender;
import org.apache.log4j.helpers.LogLog;
import org.apache.log4j.spi.LoggingEvent;

/**
 */
public class TextPaneAppender extends WriterAppender {
	private TextPaneOutStream textPaneOutStream = null;

	public TextPaneAppender() {
		super();
		setWriter(createWriter(getTextPaneOutStream()));
		super.activateOptions();
	}

	public TextPaneAppender(Layout layout) {
		super();
		setLayout(layout);
		setWriter(createWriter(getTextPaneOutStream()));
		super.activateOptions();
	}

	public synchronized void doAppend(LoggingEvent event) {
		if (closed) {
			LogLog.error("Attempted to append to closed appender named [" + name + "].");
			return;
		}
		this.appendByLevel(event);
	}

	public TextPaneOutStream getTextPaneOutStream() {
		if (textPaneOutStream == null)
			textPaneOutStream = new TextPaneOutStream();
		return textPaneOutStream;
	}

	protected void appendByLevel(LoggingEvent event) {
		this.getTextPaneOutStream().writeLeveled(this.layout.format(event), event.getLevel());
		this.getTextPaneOutStream().flush();
	}

	/**
	 * @author ADolgarev OutputStream that writes messages to the document of
	 *         JTextPane
	 */
	public static class TextPaneOutStream extends OutputStream {
		private final Hashtable<Integer, StringBuffer> buffers = new Hashtable<Integer, StringBuffer>();
		private final Hashtable<Integer, JTextPane> panes = new Hashtable<Integer, JTextPane>();
		private final Hashtable<StringBuffer, Boolean> flushables = new Hashtable<StringBuffer, Boolean>();
		
		public TextPaneOutStream() {
			// ALL level is always active
			StringBuffer sb = new StringBuffer();
			buffers.put(Level.ALL.toInt(),sb);
			flushables.put(sb, true);
		}

		public void addTextPane(JTextPane textPane, Level l) {
			synchronized (panes) {
				panes.put(l.toInt(), textPane);
				synchronized (buffers) {
					if (!buffers.containsKey(l)) {
						StringBuffer sb = new StringBuffer();
						buffers.put(l.toInt(), sb);
						flushables.put(sb, true);
					}
				}
			}
		}

		public void close() {
		}

		public void flush() {
			for (int level : buffers.keySet()) {
				JTextPane textPane = panes.get(level);
				StringBuffer buff = buffers.get(level);
				synchronized (buff) {
					if (buff.length() > 1 && textPane != null && isBufferFlushable(buff)) {
						try {
							Document document = textPane.getDocument();
							synchronized(document){
								document.insertString(document.getLength(), buff
										.toString(), new SimpleAttributeSet());
								buff.setLength(0);
								textPane.setCaretPosition(document.getLength());
							}
						} catch (BadLocationException e) {
							LogLog.warn(e.getMessage());
						}
					}
				}
			}

		}

		public StringBuffer getTextBuffer(int level) {
			return buffers.get(level);
		}

		public JTextPane getTextPane(int level) {
			return panes.get(level);
		}

		public boolean isBufferFlushable(StringBuffer sb) {
			boolean ret = false;
			synchronized (sb){
				ret = flushables.get(sb);
			}
			return ret;
		}

		public void setBufferFlushable(StringBuffer sb, boolean b) {
			synchronized (sb){
				flushables.put(sb, b);
			}
		}

		public void write(int b) throws IOException {
			LogLog.error("bad write call!");
		}

		public void writeLeveled(String msg, Level level) {
			StringBuffer buff = this.buffers.get(level.toInt());
			if (buff != null){
				synchronized (buff) {
					buff.append(msg);
				}
			}
			// add it to all as well
			buff = this.buffers.get(Level.ALL.toInt());
			synchronized (buff) {
				buff.append(msg);
			}
		}
	}
}
