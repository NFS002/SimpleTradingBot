package SimpleTradingBot.Util;
import SimpleTradingBot.Config.ApiKeys;
import SimpleTradingBot.Config.Config;
import SimpleTradingBot.Exception.STBException;
import SimpleTradingBot.Models.FilterConstraints;
import SimpleTradingBot.Models.QueueMessage;
import com.binance.api.client.BinanceApiClientFactory;
import com.binance.api.client.domain.general.ExchangeInfo;
import com.binance.api.client.domain.general.SymbolInfo;
import org.ta4j.core.num.Num;

import java.io.File;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.HashMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.logging.*;

public class Static {


    public static String OUT_DIR;

    public static BinanceApiClientFactory factory = BinanceApiClientFactory.newInstance( ApiKeys.API_KEY, ApiKeys.SECRET_KEY);

    public static DateTimeFormatter timeFormatter = DateTimeFormatter.ofLocalizedTime( FormatStyle.SHORT );

    public static HashMap<String, FilterConstraints> constraints;

    public static DecimalFormat df = new DecimalFormat("0.#####E0");

    public static ExchangeInfo exchangeInfo;

    public static BigDecimal QUOTE_PER_TRADE;

    public final static BlockingQueue<QueueMessage> DR_QUEUE = new LinkedBlockingQueue<>();

    public final static PriorityBlockingQueue<QueueMessage> EXIT_QUEUE = new PriorityBlockingQueue<>();

    public static BinanceApiClientFactory getFactory() {
        return factory;
    }

    public static boolean checkRootDir( int n ) {
        OUT_DIR = "out-" + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE) + "-(" + n + ")/";
        File f = new File( OUT_DIR );
        return f.exists();
    }


    public static void initRootLoggers() throws Exception {
        for  ( int n = 1; checkRootDir( n ); n++ );
        Logger log = Logger.getLogger( "root" );
        log.setUseParentHandlers( false );
        File dir = new File( OUT_DIR );
        if ( !dir.mkdirs() )
            throw new STBException( 50 );
        XMLFormatter formatter = new XMLFormatter();
        FileHandler fileHandler = new FileHandler( dir + "/debug.log");
        fileHandler.setLevel( Level.ALL );
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

    public static String getAssetFromSymbol( String symbol ) {
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
}
