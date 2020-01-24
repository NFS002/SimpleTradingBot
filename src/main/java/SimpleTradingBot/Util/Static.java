package SimpleTradingBot.Util;
import SimpleTradingBot.Config.ApiKeys;
import SimpleTradingBot.Config.Config;
import SimpleTradingBot.Exception.STBException;
import SimpleTradingBot.Models.Cycle;
import SimpleTradingBot.Models.FilterConstraints;
import SimpleTradingBot.Models.QueueMessage;
import com.binance.api.client.BinanceApiClientFactory;
import com.binance.api.client.domain.OrderStatus;
import com.binance.api.client.domain.account.NewOrder;
import com.binance.api.client.domain.account.Order;
import com.binance.api.client.domain.account.request.CancelOrderResponse;
import com.binance.api.client.domain.general.ExchangeInfo;
import com.binance.api.client.domain.general.SymbolInfo;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.math.MathContext;
import java.text.DecimalFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.HashMap;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.logging.*;

public class Static {

    public static String OUT_DIR;

    public static BinanceApiClientFactory factory = BinanceApiClientFactory.newInstance( ApiKeys.BINANCE_API_KEY, ApiKeys.BINANCE_SECRET_KEY);

    public static DateTimeFormatter timeFormatter = DateTimeFormatter.ofLocalizedTime( FormatStyle.SHORT );

    public static HashMap<String, FilterConstraints> constraints;

    public static DecimalFormat df = new DecimalFormat("0.#####E0");

    public static ExchangeInfo exchangeInfo;

    private static Logger log;

    private static BigDecimal netGain = BigDecimal.ZERO;

    private static PrintWriter rtWriter;

    public static BigDecimal QUOTE_PER_TRADE;

    private final static BlockingQueue<QueueMessage> DR_QUEUE = new LinkedBlockingQueue<>();

    private final static PriorityBlockingQueue<QueueMessage> EXIT_QUEUE = new PriorityBlockingQueue<>();

    public static BinanceApiClientFactory getFactory() {
        return factory;
    }

    static {
        initRootLoggers();
        initRtWriter();
        Config.print();
    }

    private static void initRtWriter() {
        try {
            rtWriter = new PrintWriter(OUT_DIR + "rt.csv");
            rtWriter.append( Cycle.CSV_HEADER ).flush();
        }
        catch ( IOException e ) {
            log.warning( "Cant create necessary rt files. Skipping rt logging" );
        }
    }


    private static boolean checkRootDir( int n ) {
        OUT_DIR = "out-" + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE) + "-(" + n + ")/";
        File f = new File( OUT_DIR );
        return f.exists();
    }


    private static void initRootLoggers()  {
        for  ( int n = 1; checkRootDir( n ); n++ );

        log = Logger.getLogger( "root" );
        log.setUseParentHandlers( false );
        File dir = new File( OUT_DIR );
        if ( !dir.mkdirs() )
            throw new STBException( 50 );

        XMLFormatter formatter = new XMLFormatter();

        try {
            FileHandler fileHandler = new FileHandler(dir + "/debug.log");
            fileHandler.setLevel( Level.ALL );
            fileHandler.setFormatter( formatter );
            log.addHandler(fileHandler);
        }
        catch (IOException e) {
            throw new STBException( 50 );
        }
    }

    public static void removeConstraint( String symbol ) {
        constraints.remove( symbol );
    }

    public static synchronized String toReadableTime(long millis ) {
        if ( millis < 0)
            return String.valueOf( millis );
        Instant instant = Instant.ofEpochMilli( millis );
        ZonedDateTime zonedDateTime = ZonedDateTime.ofInstant( instant, Config.ZONE_ID);
        return timeFormatter.format( zonedDateTime );
    }

    public static synchronized String toReadableDuration( long millis ) {
        Duration duration = Duration.ofMillis( millis );
        return String.format("%d:%02d:%02d", duration.toHours(), duration.toMinutesPart(), duration.toSecondsPart());
    }

    public static synchronized String safeDecimal( BigDecimal bigDecimal, int maxLength ) {

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

    public static synchronized String safeDecimal( BigDecimal bigDecimal ) {
        return safeDecimal( bigDecimal, 5 );
    }


    public static synchronized String getQuoteFromSymbol( String symbol ) {
        SymbolInfo symbolInfo = exchangeInfo.getSymbolInfo( symbol );
        return symbolInfo.getQuoteAsset();
    }

    public static synchronized void requestExit( String symbol ) {
        QueueMessage m = new QueueMessage( symbol );
        EXIT_QUEUE.put( m );
    }

    public static synchronized Optional<QueueMessage> checkForExit( String symbol ) {
        Optional<QueueMessage> optional = EXIT_QUEUE.stream().filter(
                m -> m.getSymbol().equals( symbol ) || m.getSymbol().equals("*")).findFirst();
        EXIT_QUEUE.removeIf( m -> !m.getSymbol().equals("*") && m.getSymbol().equals( symbol ));
        return optional;
    }

    public static synchronized boolean requestDeregister( String symbol )  {
        QueueMessage m = new QueueMessage( symbol );
        return DR_QUEUE.offer( m );
    }

    public static synchronized QueueMessage checkForDeregister( ) {
        return DR_QUEUE.poll( );
    }

    public static synchronized void logRt(Cycle cycle ) {
        if ( !cycle.isFinalised() ) {
            cycle.finalise();
            netGain = netGain.add(cycle.getGain(), MathContext.DECIMAL64);
            if (rtWriter != null) {
                log.info("Logging rt for symbol: " + cycle.getSymbol());
                rtWriter.append(cycle.toCsv()).append(",")
                        .append(safeDecimal(netGain)).append("\n")
                        .flush();
            }

        }
    }

}
