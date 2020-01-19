package SimpleTradingBot.Util;

import com.google.gson.*;
import org.ta4j.core.Bar;
import org.ta4j.core.TimeSeries;

import java.lang.reflect.Type;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TimeSeriesSerializer implements JsonSerializer<TimeSeries> {

    @Override
    public JsonElement serialize(TimeSeries timeSeries, Type type, JsonSerializationContext jsonSerializationContext) {
        JsonArray array = new JsonArray();
        List<Bar> revSeries = new ArrayList<>(timeSeries.getBarData());
        Collections.reverse(revSeries);
        for ( Bar bar : revSeries ) {
            JsonObject object = new JsonObject();
            long millis = bar.getBeginTime().toInstant().toEpochMilli();
            object.addProperty("timestamp", millis);
            object.addProperty("open", bar.getOpenPrice().longValue());
            object.addProperty("high", bar.getMaxPrice() .longValue());
            object.addProperty("low", bar.getMinPrice().longValue());
            object.addProperty("close", bar.getClosePrice().longValue());
            object.addProperty("volume", bar.getVolume().longValue());
            array.add( object );
        }
        return array;
    }
}
