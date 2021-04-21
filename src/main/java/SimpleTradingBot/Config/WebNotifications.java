package SimpleTradingBot.Config;

import SimpleTradingBot.Models.Cycle;
import com.rollbar.notifier.Rollbar;

import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import static SimpleTradingBot.Config.Config.ROLLBAR_API_KEY;
import static com.rollbar.notifier.config.ConfigBuilder.withAccessToken;

public class WebNotifications {

    private static Rollbar rollbar;

    private static final boolean WN_ENABLED = false;

    private static final long UPDATES_AT = 10000;

    private static final HashMap<String, Boolean> WEB_NOTIFICATIONS = new HashMap<>() {{
        put("cycle_complete", true);
        put("controller_exit", true);
        put("controller_update", true);
        put("heartbeat_failure", true);
        put("heartbeat_exit", true);
        put("controller_stream", false);
        put("exception", true);
        put("info", true);
    }};

    static {
        initWebNotifications();
    }

    private static boolean isAnyEnabled() {
        return WN_ENABLED;
    }

    private static void initWebNotifications() {
        if ( isAnyEnabled() ) {

            if (ROLLBAR_API_KEY == null) {
                throw new IllegalArgumentException("Web notifications are enabled, but ROLLBAR_API_KEY is null");
            }

            com.rollbar.notifier.config.Config config
                    = withAccessToken(ROLLBAR_API_KEY)
                    .environment(Config.STB_ENV)
                    .codeVersion("1.0.0")
                    .build();
            rollbar = Rollbar.init(config);
        }
    }

    private static boolean isEnabled(String key) {
        return isAnyEnabled() && WEB_NOTIFICATIONS.getOrDefault(key, false);
    }

    public static void error(Throwable throwable) {
        if ( isEnabled("exception")) {
            rollbar.error(throwable);
        }
    }

    public static void info(String message) {
        if (isEnabled("info")) {
            rollbar.info(message);
        }
    }

    public static void controllerExit(String symbol) {
        if (isEnabled("controller_exit")) {
            rollbar.critical(symbol + ": Preparing to exit controller and interrupt thread. ");
        }
    }

    public static void cycleCompleted(Cycle cycle) {
        if ( isEnabled("cycle_complete")) {
            HashMap<String, Object> params = cycle.toMap();
            String symbol = params.get("symbol").toString();
            rollbar.info("RT complete for symbol: " + symbol, params);
        }
    }

    public static void controllerUpdate(String symbol, int ticks) {
        if ( isEnabled("controller_update") ) {
            if (UPDATES_AT > 0 && ticks % UPDATES_AT == 0) {
                rollbar.info(symbol + ": Controller has processed" + ticks + " events");
            }
        }
    }

    public static void controllerStream(String symbol) {
        if ( isEnabled("controller_stream") ) {
            rollbar.info("Beginning stream: "
                    + symbol
                    + ", with regular updates set to "
                    + UPDATES_AT);
        }
    }

    public static void heartbeatFailure(String symbol, long duration) {
        if ( isEnabled("heartbeat_failure") ) {
            rollbar.critical("Hearbeat failed for symbol: " + symbol + ", with idle duration of " + (duration/1000) + " (s)");
        }
    }

    public static void heartbeatExit(boolean close) {
        if ( isEnabled("hearbeat_exit") ) {
            rollbar.critical("Preparing to shutdown heartbeart and exit program");

            if (close)
                close();
        }
    }

    public static void close() {
        if (rollbar != null) {
            try {
                rollbar.close(true);
            }
            catch (Exception e) {
                Logger logger = Logger.getLogger("root.notifications");
                logger.log(Level.SEVERE, "Exception thrown whilst closing web notifications: ", e);
            }
        }
    }
}
