package SimpleTradingBot.Schedule;
import SimpleTradingBot.Exception.StbBackTestException;
import SimpleTradingBot.Test.Backtest.Feeder.Feeder;
import SimpleTradingBot.Test.Backtest.TestController;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import java.io.*;
import java.util.*;
import java.util.logging.*;

import static SimpleTradingBot.Test.Backtest.Feeder.FeederFactory.getQuandlFeeder;
import static SimpleTradingBot.Config.Config.QUANDL_BASE_URL;
import static SimpleTradingBot.Config.Config.QUANDL_API_KEY;
import static SimpleTradingBot.Config.Config.resetTa;

public class QuandlTestSchedule {


    public static void main(String... args) throws IOException {
        final String[] allSymbols = new String[] {
                "HD", "DIS", "MSFT", "BA", "MMM","PFE","NKE",
                "JNJ","MCD","INTC","XOM","GS","JPM","AXP","V",
                "IBM","UNH","PG","GE","KO","CSCO","CVX","CAT",
                "MRK","WMT","VZ","UTX","TRV","AAPL"
        };
        Logger log = Logger.getLogger("root");
        log.entering("Schedule","main");
        for (int k = 0; k < allSymbols.length; k++) {
            Thread parentThread = Thread.currentThread();
            parentThread.setName("Parent");
            String symbol = allSymbols[ k ];
            try {
                String url = buildQuandlUrl(symbol);
                List<String> csvData = getAllLines(url);
                Feeder feeder = getQuandlFeeder(symbol);
                TestController testController = new TestController(symbol);
                log.info("Starting backtest (" + k + "," + symbol + "). Read " + csvData.size() + " lines.");
                int r;
                for (r = 0; r < csvData.size(); r++) {
                    String line = csvData.get(r);
                    feeder.feed(line.trim(), testController);
                }
                testController.closeLogHandlers();
                log.info("Backtest complete (" + k + "," + symbol + "). Fed " + r + " lines");
            }
            catch (StbBackTestException e) {
                log.log(Level.SEVERE,"Backtest failed: ", e);
            }

            if ( k < allSymbols.length - 1 )
                resetTa();
        }
        log.info(allSymbols.length + " symbols complete. exiting schedule");

    }

    private static int getNextQuandlIndex() throws IOException {
        FileReader reader  = new FileReader(".quandl-symbol-index");
        Scanner scanner = new Scanner( reader );
        int i = scanner.nextInt();
        scanner.close();
        reader.close();
        return i;
    }

    private static void setNextQuandlIndex( int index ) throws IOException {
        PrintWriter writer = new PrintWriter( ".quandl-symbol-index");
        writer.print( index );
        writer.close();
    }

    private static String getSymbolAt( int lineNumber ) throws IOException {
        FileReader reader  = new FileReader("resources/EOD_metadata.csv");
        Scanner scanner = new Scanner( reader );
        for ( int i = 1; i < lineNumber; i++, scanner.nextLine() );
        String line = scanner.nextLine();
        String[] split = line.split(",");
        String symbol = split[0];
        scanner.close();
        reader.close();
        return symbol;
    }

    private static List<String> getAllLines(String url ) throws IOException {
        CloseableHttpClient client = HttpClients.createDefault();
        HttpGet get = new HttpGet( url );
        CloseableHttpResponse response = client.execute(get);
        StatusLine sLine = response.getStatusLine();
        if ( sLine.getStatusCode() != 200 )
            throw new StbBackTestException( sLine.toString() );
        InputStream is = response.getEntity().getContent();
        InputStreamReader isReader = new InputStreamReader( is );
        BufferedReader reader = new BufferedReader( isReader );
        List<String> allLines = new ArrayList<>();
        String line;
        while ( (line = reader.readLine() ) != null ) {
            String trimmedLine = line.trim();
            if ( !trimmedLine.isEmpty() )
                allLines.add(line);
        }
        is.close();
        isReader.close();
        reader.close();
        response.close();
        client.close();
        allLines.remove( 0 ); /* Remove heaader */
        Collections.reverse( allLines );
        return allLines;
    }

    private static String buildQuandlUrl( String symbol ) {
        return QUANDL_BASE_URL + symbol + ".csv?api_key=" + QUANDL_API_KEY;
    }

}
