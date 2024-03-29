package SimpleTradingBot.Exception;
import org.apache.http.StatusLine;

public class STBException extends RuntimeException {

    private final int statusCode;

    public STBException( int statusCode, Throwable cause ) {
        super( getMessage(statusCode), cause);
        this.statusCode = statusCode;
    }

    public STBException( int statusCode ) {
        super( getMessage(statusCode) );
        this.statusCode = statusCode;
    }


    private static String getMessage( int statusCode )  {
        switch ( statusCode ) {

            case 0:     return "SHUTDOWN";

            case 30:    return "CONF_CREATE";

            case 40:    return "NO_LOT_FILTER";

            case 50:    return "ROOT_DIR_CREATE";

            case 55:    return "DATA_DIR_CREATE";

            case 60:    return "LOG_DIR_CREATE";

            case 70:    return "MAX_ORDER_RETRY";

            case 90:    return "MAX_ORDER_UPDATES";

            case 100:   return "NO_ORDER_UPDATES";

            case 110:   return "SERVER_TIME_DIFFERENCE";

            case 120:   return "MAX_OSS_ERR";

            case 130:   return "MAX_TIME_SYNC";

            case 140:   return "INCORRECT_INTERVAL";

            case 150:   return "MAX_DDIFF";

            case 160:   return "RECV_WINDOW";

            case 200:   return "UNKNOWN_STATE";

            case 210:   return "UNKNOWN_STATUS";

            default:    throw new IllegalArgumentException("UNKNOWN_STATUS_CODE: " + statusCode);
        }
    }

    public int getStatusCode() {
        return statusCode;
    }

}
