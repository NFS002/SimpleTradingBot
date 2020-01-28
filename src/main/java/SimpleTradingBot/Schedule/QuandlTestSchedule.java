package SimpleTradingBot.Schedule;
import SimpleTradingBot.Config.Config;
import SimpleTradingBot.Test.Backtest.Feeder.Feeder;
import SimpleTradingBot.Test.Backtest.TestController;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;

import static SimpleTradingBot.Test.Backtest.Feeder.FeederFactory.getQuandlFeeder;
import static SimpleTradingBot.Config.Config.QUANDL_BASE_URL;
import static SimpleTradingBot.Config.Config.QUANDL_API_KEY;

public class QuandlTestSchedule {

    public static void main(String... args) {
        Logger log = Logger.getLogger( "root" );
        log.entering("Schedule","main");
        try {
            Thread parentThread = Thread.currentThread();
            parentThread.setName("Parent");
            int i = getNextQuandlIndex();
            String symbol = getSymbolAt( i );
            log.info("Starting backtest (" + i + "," + symbol + ").");
            Feeder feeder = getQuandlFeeder();
            TestController testController = new TestController(symbol);
            String url = buildQuandlUrl( symbol );
            Scanner csvData = getCsvScanner( url );
            int r = 0;
            String header = csvData.nextLine();
            feeder.readHeader( header );
            while ( csvData.hasNextLine() ) {
                String trimmedLine = csvData.nextLine().trim();
                if (!trimmedLine.isEmpty()) {
                    feeder.feed(trimmedLine, testController);
                    r++;
                }
            }
            setNextQuandlIndex( i + 1 );
            log.info("Backtest complete (" + i + "," + symbol + "). Read " + r + " lines");
        }
        catch ( Exception e ) {
            e.printStackTrace();
            log.log(Level.SEVERE, "Backtest failed", e);
        }
        finally {
            log.exiting("Schedule", "main");
        }
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

    private static Scanner getCsvScanner( String url ) throws Exception {
        CloseableHttpClient client = HttpClients.createDefault();
        HttpGet get = new HttpGet( url );
        CloseableHttpResponse response = client.execute(get);
        return new Scanner( response.getEntity().getContent() );
    }

    private static String buildQuandlUrl( String symbol ) {
        return QUANDL_BASE_URL + symbol + ".csv?api_key=" + QUANDL_API_KEY;
    }

}
