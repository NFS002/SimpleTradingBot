package SimpleTradingBot.Util;
import SimpleTradingBot.Config.Config;
import com.binance.api.client.BinanceApiClientFactory;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.File;
import java.text.DecimalFormat;
import java.time.format.DateTimeFormatter;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Static {


    private static Logger log = Logger.getLogger("root");

    public static BinanceApiClientFactory factory = BinanceApiClientFactory.newInstance(Config.binanceAPIkey, Config.binanceSecretKey);

    public static DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("hh:mm");

    private static PrintWriter rtWriter = null;

    public static DecimalFormat df = new DecimalFormat("#.##");


    public static BinanceApiClientFactory getFactory() {
        return factory;
    }


    public static void setLoggers() throws Exception{
        log.setUseParentHandlers(false);
        File dir = new File(Config.outDir + "root");
        if (!dir.exists()) dir.mkdirs();
        FileHandler fileHandler = new FileHandler( dir + "/err.log");
        fileHandler.setLevel(Level.WARNING);
        log.addHandler(fileHandler);
        FileHandler fileHandler2 = new FileHandler( dir + "/all.log");
        log.addHandler(fileHandler2);
    }


    public static synchronized void appendRt(String msg) {
        try {
            if (rtWriter == null) {
                File rtFile = new File(Config.outDir + "rt.csv");
                rtFile.createNewFile();
                rtWriter = new PrintWriter(new FileWriter(rtFile,true));
                rtWriter.append("SYMBOL,TIME,OPEN,CLOSE,GAIN\n");
                rtWriter.flush();
            }
            rtWriter.append(msg);
            rtWriter.flush();
        }
        catch (IOException e) {
            log.log(Level.SEVERE, "Error logging rt file: ", e);
        }
    }
}
