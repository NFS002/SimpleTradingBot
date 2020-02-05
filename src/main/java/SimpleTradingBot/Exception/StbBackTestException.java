package SimpleTradingBot.Exception;

import org.apache.http.StatusLine;

public class StbBackTestException extends RuntimeException {


    public StbBackTestException(String message) {
        super( message );
    }

    public StbBackTestException( Throwable cause ) {
        super( cause );
    }

}
