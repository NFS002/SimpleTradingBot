package SimpleTradingBot.Plugins;

import SimpleTradingBot.Config.Config;
import SimpleTradingBot.Util.Static;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Scanner;

public class CoinDesk {

    private static final String URL = "https://api.coindesk.com/v1/bpi/currentprice.json";

    public static BigDecimal getQuotePerTrade() throws Exception {
        CloseableHttpClient client = HttpClients.createDefault();
        HttpGet get = new HttpGet( URL );
        CloseableHttpResponse response = client.execute(get);
        Scanner s = new Scanner( response.getEntity().getContent() ).useDelimiter("\\A");
        String content =  s.hasNext() ? s.next() : "";
        JsonObject jsonObject = new JsonParser().parse( content ).getAsJsonObject();
        JsonObject gbp = jsonObject.get("bpi").getAsJsonObject().get("GBP").getAsJsonObject();
        BigDecimal price = gbp.get("rate_float").getAsBigDecimal();
        BigDecimal budget = BigDecimal.valueOf( Config.GBP_PER_TRADE );
        int precision = 10;
        price = price.setScale( precision, RoundingMode.HALF_UP );
        budget = budget.setScale( precision, RoundingMode.HALF_UP );
        return budget.divide( price, RoundingMode.HALF_UP );
    }
}
