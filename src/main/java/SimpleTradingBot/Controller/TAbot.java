package SimpleTradingBot.Controller;

import SimpleTradingBot.Config.Config;
import SimpleTradingBot.Strategy.IStrategy;
import org.ta4j.core.Rule;
import org.ta4j.core.TimeSeries;

import java.util.logging.Logger;


public class TAbot {

    private final String symbol;

    private Logger log;

    private String next;

    public String getNext() {
        String row = this.next;
        this.next = "";
        return row;
    }

    public TAbot( String symbol ) {
        String loggerName = this.getClass().getSimpleName();
        this.symbol = symbol;
        this.log = Logger.getLogger( "root." + symbol + "." + loggerName );
        this.next = getHeader();
    }

    private String getHeader() {
        StringBuilder builder = new StringBuilder( );
        int len = Config.TA_STRATEGIES.length;
        for (int i = 0; i < len; i++ ) {
            IStrategy rule = Config.TA_STRATEGIES[i];
            builder.append( rule.getNext() );
            if (i < len - 1 ) {
                builder.append(",");
            }
        }
        return builder.toString();
    }



    public boolean isSatisfied( TimeSeries series  )  {

        boolean allSatisfied = true;
        int index = series.getEndIndex();
        StringBuilder builder = new StringBuilder();
        int len = Config.TA_STRATEGIES.length;
        for ( int i = 0; i < len; i++ ) {
            IStrategy r = Config.TA_STRATEGIES[i];
            Rule rule = r.apply( series, index );
            builder.append( r.getNext() );

            if (i < len - 1 ) {
                builder.append(",");
            }

            boolean ruleSatisfied = rule.isSatisfied( index );
            allSatisfied = allSatisfied && ruleSatisfied;
            this.log.info( "Rule " + r.getName() + " applied, satisfied: " + ruleSatisfied );
        }

        this.next = builder.toString();
        return allSatisfied;
    }
}
