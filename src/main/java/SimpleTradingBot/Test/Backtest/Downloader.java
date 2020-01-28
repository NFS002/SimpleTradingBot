package SimpleTradingBot.Test.Backtest;

import SimpleTradingBot.Config.Config;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jimmoores.quandl.MetaDataRequest;
import com.jimmoores.quandl.MetaDataResult;
import com.jimmoores.quandl.SessionOptions;
import com.jimmoores.quandl.classic.ClassicQuandlSession;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Scanner;

public class Downloader {

    private static final String URL = "https://www.quandl.com/api/v3/datasets/EOD/GS.csv?api_key=dkKNTwZkqss1AWx_Kqh7";

    public static void main( String... args)  {
        SessionOptions sessionOptions = SessionOptions.Builder.withAuthToken("dkKNTwZkqss1AWx_Kqh7").build();
        ClassicQuandlSession session = ClassicQuandlSession.create( sessionOptions );
        MetaDataResult metaData = session.getMetaData(MetaDataRequest.of("EOD/metadata"));
        System.out.println(metaData.toPrettyPrintedString());
    }


    public static void allWords( int len ) {
        char[] chars = "abcdefghijklmnopqrstuvwxyz".toCharArray();
        iterate(chars, len, new char[len], 0);
    }

    public static void iterate(char[] chars, int len, char[] build, int pos) {
        if (pos == len) {
            String word = new String(build);
            System.out.println( word );
            return;
        }

        for (int i = 0; i < chars.length; i++) {
            build[pos] = chars[i];
            iterate(chars, len, build, pos + 1);
        }
    }
}
