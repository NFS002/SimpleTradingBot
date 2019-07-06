package SimpleTradingBot.Controller;

import SimpleTradingBot.Rules.IRule;
import SimpleTradingBot.Util.Static;
import com.binance.api.client.domain.market.TickerStatistics;
import org.ta4j.core.Rule;
import org.ta4j.core.TimeSeries;

import java.io.PrintWriter;
import java.util.logging.Logger;


public class TAbot {

    private TickerStatistics statistics;

    private Logger log;

    private PrintWriter writer;

    public TAbot( TickerStatistics statistics, PrintWriter writer ) {
        String loggerName = this.getClass().getSimpleName();
        this.statistics = statistics;
        this.log = Logger.getLogger( "root." + statistics.getSymbol() + "." + loggerName );
        this.writer = writer;

    }

    public TAbot( TickerStatistics statistics ) {
        this ( statistics, null );

    }

    public void setWriter(PrintWriter writer) {
        this.writer = writer;
    }

    public void append( String message ) {
        if ( this.writer != null )
            this.writer.append( message ).flush();
    }

    public void close() {
        if ( this.writer != null )
            this.writer.close();
    }

    public boolean isSatisfied(TimeSeries series, IRule...rules) {
        boolean satisfied = true;
        int index = series.getEndIndex();
        long currentTime = System.currentTimeMillis();
        String dateTime = Static.toReadableDate( currentTime );

        if ( this.writer != null)
            this.writer.append( dateTime ).append(":\n").flush();

        for (IRule r: rules) {
            StringBuilder builder = new StringBuilder();
            Rule rule = r.apply( series, index, builder );
            satisfied = rule.isSatisfied( index );

            if ( this.writer != null ) {
                writer.append(r.getName()).append("\n");
                writer.append(builder.toString());
                writer.append("\n");
                if ( satisfied )
                    this.writer.append( "SATISFIED\n" );
                this.writer.append("\n").flush();
            }

            this.log.info( "Rule " + r.getName() + " applied. Satisfied: " + satisfied );

            if ( ! satisfied )
                break;
        }
        return satisfied;
    }

}
