package SimpleTradingBot.Util;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class SupportedCoins {

    public static List<String> supportedCoins;

    public static void loadSupportedCoins() throws IOException {
        String path = "resources/supported_coins_list";
        BufferedReader reader = null;
        reader = new BufferedReader(new FileReader(path));
        String line;
        supportedCoins = new ArrayList<>();
        while ((line = reader.readLine()) != null) {
            line = line.trim();
            supportedCoins.add(line);
        }
    }

    public static boolean isSupported(String symbol) {
        return supportedCoins != null && supportedCoins.contains(symbol);
    }
}
