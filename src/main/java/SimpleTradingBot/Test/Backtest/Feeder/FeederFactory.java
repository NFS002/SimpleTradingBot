package SimpleTradingBot.Test.Backtest.Feeder;

public class FeederFactory {

    public static Feeder getFeeder( String datasetId ) {
        char type = datasetId.charAt( 0 );
        switch ( type ) {
            case 'Q': return new QuandlFeeder( "<SYMBOL>" );
            case 'Y': return new YahooFeeder();
            default: throw new IllegalArgumentException( "Unknown datasetId: " + datasetId );
        }
    }

    public static Feeder getQuandlFeeder( String symbol ) {
        return new QuandlFeeder( symbol );
    }
}
