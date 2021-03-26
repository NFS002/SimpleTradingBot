package SimpleTradingBot.Controller;

import SimpleTradingBot.Exception.STBException;
import SimpleTradingBot.Test.FakeOrderResponses;
import SimpleTradingBot.Test.TestLevel;
import SimpleTradingBot.Util.Handler;
import SimpleTradingBot.Util.Static;
import org.ta4j.core.BaseTimeSeries;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.num.PrecisionNum;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.XMLFormatter;

import static SimpleTradingBot.Config.Config.*;

public abstract class BaseController {
    protected TAbot taBot;

    protected Handler handler;

    protected TimeSeries timeSeries;

    protected Logger log;

    protected PrintWriter tsWriter;

    protected String symbol;

    protected int coolOff;

    public BaseController( String symbol ) throws IOException {
        this.taBot = new TAbot( symbol );

        this.handler = new Handler( symbol );

        this.symbol = symbol;

        this.coolOff = 0;

        File baseDir = initBaseDir( symbol );
        BaseTimeSeries.SeriesBuilder builder = new BaseTimeSeries.SeriesBuilder();
        builder.withNumTypeOf( PrecisionNum::valueOf )
                .withMaxBarCount( MAX_BAR_COUNT )
                .withName( symbol );
        this.timeSeries = builder.build();

        if ( LOG_TS_AT != -1 )
            initTsWriter( baseDir );

        initLogger( baseDir );

        if ( TEST_LEVEL == TestLevel.MOCK)
            FakeOrderResponses.register( this.symbol );
    }

    private File initBaseDir( String symbol ) throws STBException {
        File dir = new File(Static.ROOT_OUT + symbol );
        if (!dir.exists() && !dir.mkdirs())
            throw new STBException( 60 );
        return dir;
    }

    private void initTsWriter( File baseDir ) throws IOException {
        this.tsWriter = new PrintWriter( baseDir + "/ts.csv" );
        String taHeader = this.taBot.getNext();
        String myHeader = "TIME,CLOSE,PCHANGE,STOP,POS,";
        int n = myHeader.length();
        int l = taHeader.length();
        if ( l > 1 )
            taHeader = taHeader.substring(0, l - 1);
        else
            myHeader = myHeader.substring( 0, n - 1 );
        String header = myHeader + taHeader;
        this.tsWriter.append( header ).append("\n").flush();
    }


    private void initLogger( File baseDir ) throws IOException {
        this.log = Logger.getLogger("root." + this.symbol );
        this.log.setLevel( Level.ALL );
        FileHandler handler = new FileHandler( baseDir + "/debug.log" );
        handler.setFormatter( new XMLFormatter() );
        this.log.addHandler( handler );
        this.log.setUseParentHandlers( true );
    }
}
