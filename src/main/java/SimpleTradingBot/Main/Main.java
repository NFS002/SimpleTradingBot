package SimpleTradingBot.Main;
import SimpleTradingBot.Config.Config;
import SimpleTradingBot.Server.Server;
import SimpleTradingBot.Util.Static;


public class Main {

    public static void main(String[] args) throws Exception {
        Static.init();
        Server server = new Server();
        server.serve( Config.DEFAULT_PORT );
    }

}

