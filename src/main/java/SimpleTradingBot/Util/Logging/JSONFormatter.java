package SimpleTradingBot.Util.Logging;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.time.Instant;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static SimpleTradingBot.Util.Logging.Constants.*;

/**
 * A {@code java.util.logging.Formatter} for java.util.logging that logs
 * messages as JSON
 */
public class JSONFormatter extends Formatter {
	/**
	 * JSON converter
	 */
	private static final JsonConverter CONVERTER = createJsonConverter();

	private static final ThreadMXBean THREAD_MX_BEAN = ManagementFactory.getThreadMXBean();

	/**
	 * Cache of thread names
	 */
	private static final Map<Integer, String> THREAD_NAME_CACHE = new LinkedHashMap<Integer, String>() {

		private static final long serialVersionUID = 1L;

		@Override
		protected boolean removeEldestEntry(Entry<Integer, String> eldest) {
			return (size() > THREAD_NAME_CACHE_SIZE);
		}
	};

	private final boolean useSlf4jLevelNames;
	private final String timestampKey;
	private final String loggerNameKey;
	private final String logLevelKey;
	private final String threadIdKey;
	private final String loggerClassKey;
	private final String loggerMethodKey;
	private final String messageKey;
	private final String exceptionKey;

	public JSONFormatter() {
		LogManager manager = LogManager.getLogManager();
		String cname = getClass().getName();

		String value = manager.getProperty(cname + ".use_slf4j_level_names");
		useSlf4jLevelNames = Boolean.valueOf(value);

		value = manager.getProperty(cname + ".key_timestamp");
		timestampKey = (value != null) ? value: KEY_TIMESTAMP;

		value = manager.getProperty(cname + ".key_logger_name");
		loggerNameKey = (value != null) ? value: KEY_LOGGER_NAME;

		value = manager.getProperty(cname + ".key_log_level");
		logLevelKey = (value != null) ? value: KEY_LOG_LEVEL;

		value = manager.getProperty(cname + ".key_thread_id");
		threadIdKey = (value != null) ? value: KEY_THREAD_ID;

		value = manager.getProperty(cname + ".key_logger_class");
		loggerClassKey = (value != null) ? value: KEY_LOGGER_CLASS;
		
		value = manager.getProperty(cname + ".key_logger_method");
		loggerMethodKey = (value != null) ? value: KEY_LOGGER_METHOD;
		
		value = manager.getProperty(cname + ".key_message");
		messageKey = (value != null) ? value: KEY_MESSAGE;
		
		value = manager.getProperty(cname + ".key_exception");
		exceptionKey = (value != null) ? value: KEY_EXCEPTION;
	}

	@Override
	public String format(LogRecord record) {
		Map<String, Object> object = new LinkedHashMap<>();
		object.put(timestampKey, Constants.ISO_8601_FORMAT.format(Instant.ofEpochMilli(record.getMillis())));
		object.put(loggerNameKey, record.getLoggerName());
		if (useSlf4jLevelNames) {
			object.put(logLevelKey, renameLogLevel(record.getLevel().getName()));
		} else {
			object.put(logLevelKey, record.getLevel().getName());
		}
		object.put(threadIdKey, record.getThreadID());

		if (null != record.getSourceClassName()) {
			object.put(loggerClassKey, record.getSourceClassName());
		}

		if (null != record.getSourceMethodName()) {
			object.put(loggerMethodKey, record.getSourceMethodName());
		}
		object.put(messageKey, formatMessage(record));

		// Used an enum map for lighter memory consumption
		if (null != record.getThrown()) {
			Map<Constants.ExceptionKeys, Object> exceptionInfo = new EnumMap<>(Constants.ExceptionKeys.class);
			exceptionInfo.put(Constants.ExceptionKeys.exception_class, record.getThrown().getClass().getName());

			if (record.getThrown().getMessage() != null) {
				exceptionInfo.put(Constants.ExceptionKeys.exception_message, record.getThrown().getMessage());
			}

			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			record.getThrown().printStackTrace(pw);
			pw.close();
			exceptionInfo.put(Constants.ExceptionKeys.stack_trace, sw.toString());
			object.put(exceptionKey, exceptionInfo);
		}

		return CONVERTER.convertToJson(object);
	}

	/**
	 * Gets the thread name from the threadId present in the logRecord.
	 * 
	 * @param logRecordThreadId
	 * @return thread name
	 */
	private static String getThreadName(int logRecordThreadId) {
		String result = THREAD_NAME_CACHE.get(logRecordThreadId);

		if (result != null) {
			return result;
		}

		if (logRecordThreadId > Integer.MAX_VALUE / 2) {
			result = String.valueOf(logRecordThreadId);
		} else {
			ThreadInfo threadInfo = THREAD_MX_BEAN.getThreadInfo(logRecordThreadId);
			if (threadInfo == null) {
				return String.valueOf(logRecordThreadId);
			}
			result = threadInfo.getThreadName();
		}

		synchronized (THREAD_NAME_CACHE) {
			THREAD_NAME_CACHE.put(logRecordThreadId, result);
		}

		return result;
	}

	private static JsonConverter createJsonConverter() {
		JsonConverter jsonConverter;

		try {
			Class.forName("com.fasterxml.jackson.databind.ObjectMapper");
			jsonConverter = new JacksonJsonConverter();
		} catch (ClassNotFoundException e) {
			try {
				Class.forName("com.google.gson.Gson");
				jsonConverter = new GsonJsonConverter();
			} catch (ClassNotFoundException e1) {
				try {
					Class.forName("org.json.simple.JSONObject");
					jsonConverter = new SimpleJsonConverter();
				} catch (ClassNotFoundException e2) {
					Logger.getAnonymousLogger().log(Level.WARNING,
							"None of GSON/Jackson/json-simple found in classpath");
					jsonConverter = new CustomJsonConverter();
				}
			}
		}

		return jsonConverter;
	}

	/**
	 * Rename log levels to
	 * http://www.slf4j.org/apidocs/org/slf4j/bridge/SLF4JBridgeHandler.html
	 * 
	 * <pre>
	 * FINEST  -&gt; TRACE
	 * FINER   -&gt; DEBUG
	 * FINE    -&gt; DEBUG
	 * INFO    -&gt; INFO
	 * CONFIG  -&gt; CONFIG
	 * WARNING -&gt; WARN
	 * SEVERE  -&gt; ERROR
	 * </pre>
	 * 
	 * @param logLevel
	 * @return
	 */
	private String renameLogLevel(String logLevel) {

		switch (logLevel) {
		case "FINEST":
			return "TRACE";

		case "FINER":
		case "FINE":
			return "DEBUG";

		case "INFO":
		case "CONFIG":
			return "INFO";

		case "WARNING":
			return "WARN";

		case "SEVERE":
			return "ERROR";

		default:
			return logLevel;
		}
	}
}
