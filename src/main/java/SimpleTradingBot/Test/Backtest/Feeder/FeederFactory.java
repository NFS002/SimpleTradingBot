package SimpleTradingBot.Test.Backtest.Feeder;

public class FeederFactory {


    public static Feeder getBinanceFeeder() {
        return new BinanceFeeder();
    }
}
