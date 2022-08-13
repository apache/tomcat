package org.apache.catalina.valves;

import java.io.CharArrayWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.collections.SynchronizedStack;

public class JsonAccessLogValve extends AbstractAccessLogValve {

	private static final Log log = LogFactory.getLog(JsonAccessLogValve.class);

	/**
	 * Buffered logging.
	 */
	private boolean buffered = true;

	/**
	 * The PrintWriter to which we are currently logging, if any.
	 */
	protected PrintWriter writer = new PrintWriter(System.err);

	// TODO: is the usage pattern in this class threadsafe?
	private SynchronizedStack<CharArrayWriter> charArrayValueWriters =
			new SynchronizedStack<>();

	// TODO: check sizing, i made up 16, test with some real workloads
	/**
	 * Value buffers are usually recycled and re-used. To prevent
	 * excessive memory usage, if a buffer grows beyond this size it will be
	 * discarded. The default is 16 characters. This should be set to larger
	 * than the typical access log message size.
	 */
	private int maxValueMessageBufferSize = 16;

	private CharArrayWriter borrowWriter() {
		CharArrayWriter result = charArrayValueWriters.pop();
		if (result == null) {
			result = new JsonCharArrayWriter(8); // TODO: sizing, I came up with 8, test with some real workloads
		}
		return result;
	}

	public void releaseWriter(CharArrayWriter valueWriter) {
		if (valueWriter.size() <= maxValueMessageBufferSize) {
			valueWriter.reset();
			charArrayValueWriters.push(valueWriter);
		}
	}

	/**
	 * JSON string escaping writer
	 */
	private static class JsonCharArrayWriter extends CharArrayWriter {

		public JsonCharArrayWriter(int i) {
			super(i);
		}

		@Override
		public void write(int c) {
			if(JsonStringUtil.needsEscaping(c)) {
				try {
					super.write(JsonStringUtil.escape(c));
				} catch (IOException e) {
					// ignore
				}
			} else {
				super.write(c);
			}
		}

		@Override
		public void write(char[] c, int off, int len) {
			if(JsonStringUtil.needsEscaping(c, off ,len)) {
				try {
					super.write(JsonStringUtil.escape(c, off, len));
				} catch (IOException e) {
					// ignore
				}
			} else {
				super.write(c, off, len);
			}
		}

		@Override
		public void write(String str, int off, int len) {
			if(JsonStringUtil.needsEscaping(str, off, len)) {
				CharSequence escaped = JsonStringUtil.escape(str, off, len);
				super.write(escaped.toString(), 0, escaped.length());
			} else {
				super.write(str, off, len);
			}
		}
	}

	private class JsonWrappedElement implements AccessLogElement, CachedElement {

		private CharSequence attributeName;
		private boolean quoteValue;
		private AccessLogElement delegate;

		private CharSequence escapeJsonString(CharSequence nonEscaped) {
			if(JsonStringUtil.needsEscaping(nonEscaped, 0, nonEscaped.length())) {
				return JsonStringUtil.escape(nonEscaped, 0, nonEscaped.length());
			} else {
				return nonEscaped;
			}
		}

		public JsonWrappedElement(String attributeName, boolean quoteValue, AccessLogElement delegate) {
			this.attributeName = escapeJsonString(attributeName);
			this.quoteValue = quoteValue;
			this.delegate = delegate;
		}

		@Override
		public void addElement(CharArrayWriter buf, Date date, Request request, Response response, long time) {
			buf.append('"').append(attributeName).append('"').append(':');
			if(quoteValue) {
				buf.append('"');
			}
			CharArrayWriter valueWriter = borrowWriter(); // TODO: check threadsafety
			try {
				delegate.addElement(valueWriter, date, request, response, time);
				valueWriter.writeTo(buf);
			} catch (IOException e) {
				// ignore
			} finally {
				releaseWriter(valueWriter);
			}
			if(quoteValue) {
				buf.append('"');
			}
		}

		@Override
		public void cache(Request request) {
			if(delegate instanceof CachedElement) {
				((CachedElement) delegate).cache(request);;
			}
		}
	}

	@Override
	protected AccessLogElement[] createLogElements() {
		List<AccessLogElement> logElements = new ArrayList<>(Arrays.asList(super.createLogElements()));
		ListIterator<AccessLogElement> lit = logElements.listIterator();
		lit.add((buf, date, req, resp, time) -> buf.write('{'));
		while(lit.hasNext()) {
			AccessLogElement logElement = lit.next();
			// remove all other elements, like StringElements
			if(!(logElement instanceof JsonWrappedElement)) {
				lit.remove();
				continue;
			}
			lit.add((buf, date, req, resp, time) -> buf.write(','));
		}
		// remove last comma again
		lit.previous();
		lit.remove();
		lit.add((buf, date, req, resp, time) -> buf.write('}'));
		return logElements.toArray(new AccessLogElement[logElements.size()]);
	}

	private Map<Character, String> pattern2AttributeName = new HashMap<>();
	{
		// TODO: align with fluentd attribute naming, see https://github.com/fluent/fluentd/blob/master/lib/fluent/plugin/parser_apache2.rb#L72
		pattern2AttributeName.put('a', "remoteAddr");
		pattern2AttributeName.put('A', "localAddr");
		pattern2AttributeName.put('b', "byteSend");
		pattern2AttributeName.put('B', "byteSendNC");
		pattern2AttributeName.put('D', "elapsedTime");
		pattern2AttributeName.put('F', "firstByteTime");
		pattern2AttributeName.put('h', "host");
		pattern2AttributeName.put('H', "protocol");
		pattern2AttributeName.put('l', "logicalUserName");
		pattern2AttributeName.put('m', "method");
		pattern2AttributeName.put('p', "port");
		pattern2AttributeName.put('q', "query");
		pattern2AttributeName.put('r', "request");
		pattern2AttributeName.put('s', "statusCode");
		pattern2AttributeName.put('S', "sessionId");
		pattern2AttributeName.put('t', "dateTime");
		pattern2AttributeName.put('T', "elapsedTime");
		pattern2AttributeName.put('u', "user");
		pattern2AttributeName.put('U', "requestURI");
		pattern2AttributeName.put('v', "localServerName");
		pattern2AttributeName.put('I', "threadName");
		pattern2AttributeName.put('X', "connectionStatus");
	}

	@Override
	protected AccessLogElement createAccessLogElement(char pattern) {
		AccessLogElement ale = super.createAccessLogElement(pattern);
		String attributeName = pattern2AttributeName.get(pattern);
		if(attributeName == null) {
			attributeName = "unknownPattern-" + pattern;
		}
		return new JsonWrappedElement(attributeName, true, ale);
	}

	@Override
	protected void log(CharArrayWriter message) {
		// Log this message
		try {
			message.write(System.lineSeparator());
			synchronized (writer) {
				message.writeTo(writer);
				if (!buffered) {
					writer.flush();
				}
			}
		} catch (IOException ioe) {
			log.warn(sm.getString(
					"jsonAccessLogValve.writeFail", message.toString()), ioe);
		}
	}
}

/**
 * JSON string escaping utility
 * see RFC 8259 - chapter "7. Strings"
 */
class JsonStringUtil {
	public static int getPopularChar(int c) {
		switch(c) {
		case '"': case '\\': case '/':
			return c;
		case 0x8: return 'b';
		case 0xc: return 'f';
		case 0xa: return 'n';
		case 0xd: return 'r';
		case 0x9: return 't';
		default:
			return -1;
		}
	}

	/* TODO: i'm not so happy with the very similar implementations of escape()
	 * can those be replaced by just one?
	 */
	public static char[] escape(char[] ca, int off, int len) throws IOException {
		CharArrayWriter sb = new CharArrayWriter(len);
		for(int i = off, n = off + len; i < n; i++) {
			char c = ca[i];
			if(needsEscaping(c)) {
				sb.write(escape(c));
			} else {
				sb.append(c);
			}
		}
		return sb.toCharArray();
	}

	public static CharSequence escape(CharSequence nonEscaped, int off, int len) {
		StringBuilder sb = new StringBuilder();
		for(int i = off, n = off + len; i < n; i++) {
			char c = nonEscaped.charAt(i);
			if(needsEscaping(c)) {
				sb.append(escape(c));
			} else {
				sb.append(c);
			}
		}
		return sb.toString();
	}

	public static char[] escape(int c) {
		int popularChar = JsonStringUtil.getPopularChar(c);
		if(popularChar >= 0) {
			return new char[] { '\\', (char) popularChar };
		} else {
			char[] dst = new char[] { '\\', 'u', '0', '0', '0', '0' };
			String hexString = Integer.toHexString(c);
			hexString.getChars(0, hexString.length(), dst, dst.length - hexString.length());
			return dst;
		}
	}

	public static boolean needsEscaping(int c) {
		return c < 0x20 || c == '"' || c == '\\' || Character.isHighSurrogate((char) c) || Character.isLowSurrogate((char) c);
	}

	/*
	 * TODO: those needsEscaping() just exists because of this idea:
	 * the assumption is that traversing an existing array is cheaper than to create a new array for each string
	 * being processed for escaping, so we first check if the current string contains any char that needs escaping,
	 * this will be mostly not be the case, if we don't have any escaping chars, we just use the string as-is.
	 * else we create a new char array with the resp. escaped chars.
	 * not sure if above assumptions holds or if it's non-sense.
	 * another idea:
	 * all those LogElements we have, can they even produce strings that can contain chars that needs json string escaping?
	 * maybe this whole code can just get dropped?
	 */
	public static boolean needsEscaping(CharSequence cs, int off, int len) {
		for(int i = off, n = off + len; i < n; i++) {
			if(needsEscaping(cs.charAt(i))) {
				return true;
			}
		}
		return false;
	}

	public static boolean needsEscaping(char[] c, int off, int len) {
		for(int i = off, n = off + len; i < n; i++) {
			if(needsEscaping(c[i])) {
				return true;
			}
		}
		return false;
	}
}