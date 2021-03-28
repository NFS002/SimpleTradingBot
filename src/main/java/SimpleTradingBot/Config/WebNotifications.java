package SimpleTradingBot.Config;

import SimpleTradingBot.Config.Config;
import SimpleTradingBot.Models.Cycle;
import com.rollbar.notifier.Rollbar;

import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import static SimpleTradingBot.Config.Config.WEB_NOTIFICATIONS;
import static com.rollbar.notifier.config.ConfigBuilder.withAccessToken;
import static SimpleTradingBot.Config.Config.WEB_NOTIFICATION_UPDATES;

public class WebNotifications {

    private static Rollbar rollbar;

    static {
        initWebNotifications();
    }

    private static void initWebNotifications() {
        if (Config.WEB_NOTIFICATIONS) {

            if (Config.ROLLBAR_API_KEY == null) {
                throw new IllegalArgumentException("Web notifications are enabled, but ROLLBAR_API_KEY is null");
            }

            com.rollbar.notifier.config.Config config
                    = withAccessToken(Config.ROLLBAR_API_KEY)
                    .environment("production")
                    .codeVersion("1.0.0")
                    .build();
            rollbar = Rollbar.init(config);
        }
    }

    public static void error(Throwable throwable) {
        if (rollbar != null) {
            rollbar.error(throwable);
        }
    }

    public static void info(String message) {
        if (rollbar != null) {
            rollbar.info(message);
        }
    }

    public static void controllerExit(String symbol) {
        if (rollbar != null) {
            rollbar.critical(symbol + ": Preparing to exit controller and interrupt thread. ");
        }
    }

    public static void cycleCompleted(Cycle cycle) {
        if (rollbar != null) {
            HashMap<String, Object> params = cycle.toMap();
            String symbol = params.get("symbol").toString();
            rollbar.info("RT complete for symbol: " + symbol, params);
        }
    }

    public static void controllerUpdate(String symbol, int ticks) {
        if (rollbar != null) {
            if (WEB_NOTIFICATION_UPDATES > 0 && ticks % WEB_NOTIFICATION_UPDATES == 0) {
                rollbar.info(symbol + ": Controller has processed" + ticks + " events");
            }
        }
    }

    public static void controllerStream(String symbol) {
        if (rollbar != null) {
            rollbar.info("Beginning stream: "
                    + symbol
                    + ", with regular updates set to "
                    + WEB_NOTIFICATION_UPDATES);
        }
    }

    public static void heartbeatFailure(String symbol, long duration) {
        if (rollbar != null) {
            rollbar.critical("Hearbeat failed for symbol: " + symbol + ", with idle duration of " + (duration/1000) + " (s)");
        }
    }

    public static void heartbeatExit(boolean close) {
        if (rollbar != null) {
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
