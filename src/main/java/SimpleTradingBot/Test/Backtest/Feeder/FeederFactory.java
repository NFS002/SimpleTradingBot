package SimpleTradingBot.Test.Backtest.Feeder;

public class FeederFactory {


    public static Feeder getQuandlFeeder( String symbol ) {
        return new QuandlFeeder( symbol );
    }

    public static Feeder getBinanceFeeder() {
        return new BinanceFeeder();
    }
}
