package SimpleTradingBot.Services;


import SimpleTradingBot.Config.Config;
import SimpleTradingBot.Models.FilterConstraints;
import SimpleTradingBot.Models.QueueMessage;
import SimpleTradingBot.Util.Static;
import com.binance.api.client.BinanceApiCallback;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.BinanceApiWebSocketClient;
import com.binance.api.client.domain.account.Account;
import com.binance.api.client.domain.account.AssetBalance;
import com.binance.api.client.domain.event.AccountUpdateEvent;
import com.binance.api.client.domain.event.UserDataUpdateEvent;

import java.math.BigDecimal;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import static  com.binance.api.client.domain.event.UserDataUpdateEvent.UserDataUpdateEventType.ACCOUNT_UPDATE;
import static java.lang.Enum.valueOf;

public class AccountManager implements BinanceApiCallback<UserDataUpdateEvent> {

    private BinanceApiRestClient restClient;

    private String listenKey;

    private final long lastUpdated;

    private final long updateFrequency;

    private final Logger log;

    private static AccountManager instance;


    public static AccountManager getInstance() {
        if ( instance == null )
            instance = new AccountManager();
        return instance;
    }


    public AccountManager() {
        long timeStamp = System.currentTimeMillis();
        this.restClient = Static.getFactory().newRestClient();
        this.listenKey = restClient.startUserDataStream();
        this.lastUpdated = System.currentTimeMillis();
        this.updateFrequency = ( 60 * 30 * 1000 );
        this.log = Logger.getLogger( "root.asm" );

        BinanceApiWebSocketClient webSocketClient = Static.getFactory().newWebSocketClient();
        Account account = restClient.getAccount( Config.RECV_WINDOW, timeStamp );
        String base = Config.BASE_ASSET;
        AssetBalance b = account.getAssetBalance( base );
        webSocketClient.onUserDataUpdateEvent( this.listenKey, this );

    }


    private void setRemainingQty( String remainingStr ) {
        this.log.entering( this.getClass().getSimpleName(), "setRemainingQty" );
        BigDecimal available = new BigDecimal( remainingStr );
        BigDecimal max = BigDecimal.valueOf( Config.MAX_BUDGET_PER_TRADE );
        BigDecimal actual = available.compareTo( max ) > 0 ? available : max;
        FilterConstraints.setRemainingQty( actual );
        this.log.info( "Setting remaining balance of: " + actual + " " + Config.BASE_ASSET);
        this.log.exiting( this.getClass().getSimpleName(), "setRemainingQty" );

    }

    @Override
    public void onResponse(UserDataUpdateEvent event ) {
        this.log.entering( this.getClass().getSimpleName(), "onResponse");
        this.log.info( "Received event" );
        if ( event.getEventType() == ACCOUNT_UPDATE ) {
            AccountUpdateEvent accountUpdateEvent = event.getAccountUpdateEvent();
            List<AssetBalance> balances = accountUpdateEvent.getBalances();
            String asset = Config.BASE_ASSET;
            Optional<AssetBalance> optAssetBalance = balances.stream().filter(b -> b.getAsset().equals(asset)).findFirst();
            if ( optAssetBalance.isPresent() ) {
                AssetBalance assetBalance = optAssetBalance.get();
                log.info( assetBalance.toString() );
                setRemainingQty( assetBalance.getFree() );
            }
        }

        this.log.exiting( this.getClass().getSimpleName(), "onResponse");
    }

    private boolean shouldUpdate() {
        long now = System.currentTimeMillis();
        long diff = now - this.lastUpdated;
        return diff >= this.updateFrequency;
    }



    private void update() {
        this.log.entering( this.getClass().getSimpleName(), "update");
        this.log.info( "Updating stream with listen key: " + this.listenKey);
        this.restClient.keepAliveUserDataStream( this.listenKey );
        this.log.exiting( this.getClass().getSimpleName(), "update");
    }

    private void close() {
        this.log.entering(this.getClass().getSimpleName(), "close");
        this.log.info("Closing down data stream and thread" );
        this.restClient.closeUserDataStream( this.listenKey );
        Thread thread = Thread.currentThread();
        if ( !thread.isInterrupted() )
            thread.interrupt();
        System.exit(0 );
        this.log.exiting(this.getClass().getSimpleName(), "close");
    }


    public void maintain() {
        this.log.entering( this.getClass().getSimpleName(), "maintenance");
        while ( true ) {
            try {
                this.log.info( "Performing maintenance" );

                if ( Static.constraints.isEmpty() )
                    close();

                else if ( shouldUpdate() )
                    this.update();
                Thread.sleep( this.updateFrequency );
                QueueMessage message = Static.checkExit( "*" );
                if ( message != null ) {
                    this.log.info( "Received message from exit queue ");
                    close();
                }
            }

            catch (InterruptedException e) {
                this.log.log( Level.SEVERE, e.getMessage(), e);
                this.log.severe( "Sending message to exit queue");
                QueueMessage message = new QueueMessage(QueueMessage.Type.INTERRUPT, "*");
                Static.getExitQueue().offer( message );
                close();
                break;
            }
            this.log.exiting( this.getClass().getSimpleName(), "maintenance");
        }
    }
}
