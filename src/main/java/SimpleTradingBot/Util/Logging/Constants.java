package SimpleTradingBot.Util.Logging;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Holds constants used across multiple classes
 * 
 * @author Devaprasadh Xavier
 *
 */
public class Constants {
	/**
	 * Pattern for the logged time which is in ISO 8601 format
	 */
	public static final DateTimeFormatter ISO_8601_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
			.withZone(ZoneId.systemDefault());

	/**
	 * Maximum number of thread names to cache
	 */
	public static final int THREAD_NAME_CACHE_SIZE = 10000;

	/**
	 * JSON key for log time
	 */
	public static final String KEY_TIMESTAMP = "@timestamp";

	/**
	 * JSON key for logger name
	 */
	public static final String KEY_LOGGER_NAME = "logger";

	/**
	 * JSON key for log level
	 */
	public static final String KEY_LOG_LEVEL = "level";

	/**
	 * JSON key for thread name that issued the log statement
	 */
	public static final String KEY_THREAD_ID = "thread";

	/**
	 * JSON key for class name that issued the log statement
	 */
	public static final String KEY_LOGGER_CLASS = "class";

	/**
	 * JSON key for method name that issued the log statement
	 */
	public static final String KEY_LOGGER_METHOD = "method";

	/**
	 * JSON key for the message being logged
	 */
	public static final String KEY_MESSAGE = "message";

	/**
	 * JSON keys for the exception and cause being logged
	 */

	public static final String KEY_EXCEPTION = "exception";

	public static final String KEY_EXCEPTION_CLASS = "exception_class";

	public static final String KEY_EXCEPTION_MESSAGE = "exception_message";

	public static final String KEY_EXCEPTION_TRACE = "exception_trace";

	public static final String KEY_EXCEPTION_CAUSE_CLASS = "cause_class";

	public static final String KEY_EXCEPTION_CAUSE_MESSAGE = "cause_message";

	public static final String KEY_EXCEPTION_CAUSE_TRACE = "cause_trace";

}
