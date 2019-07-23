package SimpleTradingBot.Util;
import SimpleTradingBot.Config.ApiKeys;
import SimpleTradingBot.Config.Config;
import SimpleTradingBot.Exception.STBException;
import SimpleTradingBot.Models.FilterConstraints;
import SimpleTradingBot.Models.QueueMessage;
import SimpleTradingBot.Server.Signal;
import com.binance.api.client.BinanceApiAsyncRestClient;
import com.binance.api.client.BinanceApiClientFactory;
import com.binance.api.client.domain.OrderType;
import com.binance.api.client.domain.general.ExchangeInfo;
import com.binance.api.client.domain.general.SymbolInfo;
import com.binance.api.client.exception.BinanceApiException;
import com.binance.api.client.impl.BinanceApiService;
import com.binance.api.client.impl.BinanceApiServiceGenerator;
import org.rapidoid.config.Conf;
import org.ta4j.core.num.Num;

import java.io.PrintWriter;
import java.io.File;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.logging.*;

public class Static {


    public static BinanceApiClientFactory factory = BinanceApiClientFactory.newInstance( ApiKeys.API_KEY, ApiKeys.SECRET_KEY);

    public static DateTimeFormatter timeFormatter = DateTimeFormatter.ofLocalizedTime( FormatStyle.SHORT );

    public static HashMap<String, FilterConstraints> constraints;

    public static DecimalFormat df = new DecimalFormat("0.#####E0");

    public static ExchangeInfo exchangeInfo;

    public final static BlockingQueue<QueueMessage> DR_QUEUE = new LinkedBlockingQueue<>();

    public final static BlockingQueue<OrderRequest> ORDER_QUEUE = new LinkedBlockingQueue<>();

    private final static PriorityBlockingQueue<QueueMessage> EXIT_QUEUE = new PriorityBlockingQueue<>();

    public static BinanceApiClientFactory getFactory() {
        return factory;
    }


    public static void init() throws Exception {
        Logger log = Logger.getLogger( "root" );
        log.setUseParentHandlers( false );
        File dir = new File(Config.OUT_DIR + "root");
        if (!dir.exists() && !dir.mkdirs())
            throw new STBException( 50 );
        XMLFormatter formatter = new XMLFormatter();
        FileHandler fileHandler = new FileHandler( dir + "/debug.log");
        fileHandler.setFormatter( formatter );
        log.addHandler(fileHandler);
    }

    public static void removeConstraint( String symbol ) {
        constraints.remove( symbol );
    }


    public static synchronized String toReadableDate( long millis ) {
        Instant instant = Instant.ofEpochMilli( millis );
        ZonedDateTime zonedDateTime = ZonedDateTime.ofInstant( instant, Config.ZONE_ID);
        return timeFormatter.format( zonedDateTime );
    }

    public static void requestExit( String symbol ) {
        Static.EXIT_QUEUE.offer( new QueueMessage( QueueMessage.Type.INTERRUPT, symbol ));
    }

    public static QueueMessage checkExit( String symbol ) {
        Optional<QueueMessage> message = Static.EXIT_QUEUE.stream().filter(m -> m.getSymbol().equals(symbol) ).findFirst();
        return ( message.isPresent() ) ? message.get() : null;
    }

    public static PriorityBlockingQueue<QueueMessage> getExitQueue() {
        return EXIT_QUEUE;
    }

    public static String getAssetFromSymbol(String symbol ) {
        SymbolInfo symbolInfo = exchangeInfo.getSymbolInfo( symbol );
        return symbolInfo.getQuoteAsset();
    }

    public static String safeDecimal( BigDecimal bigDecimal, int maxLength ) {

        String decimal = bigDecimal.toPlainString();
        int length = decimal.length();

        int index = decimal.indexOf(".");

        /* Integer */
        if ( index < 0 ) {
            if ( decimal.length() > maxLength )
                return decimal.substring(0, maxLength - 1 );
            else
                return decimal;
        }

        /* Decimal */
        else {

          String intPart = decimal.substring(0, index);
          String decimalPart = decimal.substring( index + 1, length );

          if ( intPart.length() > maxLength ) {
              intPart = intPart.substring(0, maxLength - 1);

              if ( decimalPart.length() > maxLength)
                  decimalPart = decimal.substring(0, maxLength - 1 );

              return intPart + "." + decimalPart;
          }
          else {

              if ( decimalPart.length() > maxLength)
                  decimalPart = decimalPart.substring(0, maxLength - 1 );

              return intPart + "." + decimalPart;
          }
        }
    }

   public static String formatNum( Num num ) {
       if ( !num.getName().equals("PrecisionNum") )
           throw new IllegalArgumentException("Argument is not PrecisionNum");
       BigDecimal decimal = (BigDecimal) num.getDelegate();
       return df.format( decimal );
    }

    public static String getBaseFromSymbol( String symbol ) {
        SymbolInfo symbolInfo = exchangeInfo.getSymbolInfo( symbol );
        return symbolInfo.getBaseAsset();
    }

    public static String getQuoteFromSymbol( String symbol ) {
        SymbolInfo symbolInfo = exchangeInfo.getSymbolInfo( symbol );
        return symbolInfo.getQuoteAsset();
    }

    public static boolean hasConstraint( String symbol ) {
        return Static.constraints.get( symbol ) == null;
    }

    public static boolean hasSource( String source ) {
        return Arrays.asList( Config.KNOWN_SOURCES ).contains( source );
    }

    public static OrderRequest toRequest( Signal signal ) {
        BigDecimal openPrice = new BigDecimal( signal.getOpenPrice() );
        OrderType type;

        try {
            type = OrderType.valueOf(signal.getType());
        }
        catch ( Exception e ) {
            type = Config.DEFAULT_ORDER_TYPE;
        }
        return new OrderRequest( signal.getSymbol(), openPrice, type, signal.getWeight() );

    }

    public static synchronized void placeOrder(  Signal signal )
        throws InterruptedException {
        OrderRequest orderRequest = toRequest( signal );
        boolean s = Static.ORDER_QUEUE.offer( orderRequest, 30, TimeUnit.SECONDS);
        if ( ! s )
            throw new InterruptedException("TIMEOUT");
    }
}
