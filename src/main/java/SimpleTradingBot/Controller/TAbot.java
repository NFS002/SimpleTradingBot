package SimpleTradingBot.Controller;

import SimpleTradingBot.Config.Config;
import SimpleTradingBot.Rules.IRule;
import SimpleTradingBot.Util.Static;
import com.binance.api.client.domain.market.TickerStatistics;
import org.ta4j.core.Rule;
import org.ta4j.core.TimeSeries;

import java.io.PrintWriter;
import java.util.logging.Logger;


public class TAbot {

    private final String symbol;

    private Logger log;

    private int nFields;

    private String next;

    public String getNext() {
        String row = this.next;
        this.next = "";
        return row;
    }

    public int getnFields() {
        return nFields;
    }

    public TAbot( String symbol ) {
        String loggerName = this.getClass().getSimpleName();
        this.symbol = symbol;
        this.log = Logger.getLogger( "root." + symbol + "." + loggerName );
        setHeader();
    }

    private void setHeader() {
        StringBuilder builder = new StringBuilder( );
        for ( IRule rule: Config.TA_RULES )
            builder.append( rule.getNext() );
        this.next = builder.toString();
        if ( this.next.trim().isEmpty() )
            this.nFields = 0;
        else
            this.nFields = this.next.split( "," ).length;

    }



    public boolean isSatisfied( TimeSeries series  )  {

        boolean satisfied = true;
        int index = series.getEndIndex();
        StringBuilder builder = new StringBuilder();


        for ( IRule r: Config.TA_RULES ) {
            Rule rule = r.apply( series, index );
            builder.append( r.getNext() );
            satisfied = rule.isSatisfied( index );
            this.log.info( "Rule " + r.getName() + " applied. Satisfied: " + satisfied );

            if ( !satisfied )
                break;
        }

        this.next = builder.toString();
        return satisfied;
    }
}
