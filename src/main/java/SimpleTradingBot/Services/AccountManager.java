package SimpleTradingBot.Services;


import SimpleTradingBot.Config.Config;
import SimpleTradingBot.Exception.STBException;
import SimpleTradingBot.Models.QueueMessage;
import SimpleTradingBot.Util.Handler;
import SimpleTradingBot.Util.Static;
import com.binance.api.client.BinanceApiCallback;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.BinanceApiWebSocketClient;
import com.binance.api.client.domain.account.Account;
import com.binance.api.client.domain.account.AssetBalance;
import com.binance.api.client.domain.event.AccountUpdateEvent;
import com.binance.api.client.domain.event.UserDataUpdateEvent;

import java.io.Closeable;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ScheduledExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;

import static SimpleTradingBot.Util.Static.requestExit;

import static  com.binance.api.client.domain.event.UserDataUpdateEvent.UserDataUpdateEventType.ACCOUNT_UPDATE;

public class AccountManager implements BinanceApiCallback<UserDataUpdateEvent> {

    private BinanceApiRestClient restClient;

    private String listenKey;

    private Closeable closeable;

    private final Logger log;

    private boolean updated;

    private BigDecimal nextQty;

    /* Singleton */
    private static AccountManager instance;


    private AccountManager(  ) {
        this.restClient = Static.getFactory().newRestClient();
        this.log = Logger.getLogger( "root.am" );
        nextQty = null;
        this.updated = false;
    }

    public static AccountManager getInstance() {
        if ( instance == null )
            instance = new AccountManager();
        return instance;
    }

    void init() {
        long timeStamp = System.currentTimeMillis();
        this.listenKey = restClient.startUserDataStream();
        BinanceApiWebSocketClient webSocketClient = Static.getFactory().newWebSocketClient();
        Account account = restClient.getAccount( Config.RECV_WINDOW, timeStamp );
        String quote = Config.QUOTE_ASSET;
        AssetBalance b = account.getAssetBalance( quote );
        setBalances( b.getFree() );
        this.closeable = webSocketClient.onUserDataUpdateEvent( this.listenKey, this );

    }

    public BigDecimal getNextQty() {
        return nextQty;
    }

    private void setBalances(String remainingStr) {
        this.log.entering( this.getClass().getSimpleName(), "setBalances" );
        log.info( "Setting balances with remaining balance of: " + remainingStr + " " + Config.QUOTE_ASSET);
        BigDecimal remaining = new BigDecimal( remainingStr );

        remaining = remaining.compareTo( BigDecimal.ZERO ) < 1 ? BigDecimal.ZERO : remaining;

        remaining = remaining.compareTo( Static.QUOTE_PER_TRADE ) > 0 ? Static.QUOTE_PER_TRADE : remaining;

        switch ( Config.TEST_LEVEL ) {
            case REAL:
                nextQty = remaining;
                break;
            case FAKEORDER:
                nextQty = Static.QUOTE_PER_TRADE;
                break;
        }

        this.updated = true;
        this.log.exiting( this.getClass().getSimpleName(), "setBalances" );

    }

    @Override
    public void onResponse(UserDataUpdateEvent event ) {
        this.log.entering( this.getClass().getSimpleName(), "onResponse");
        this.log.info( "Received event" );
        if ( event.getEventType() == ACCOUNT_UPDATE ) {
            AccountUpdateEvent accountUpdateEvent = event.getAccountUpdateEvent();
            List<AssetBalance> balances = accountUpdateEvent.getBalances();
            String asset = Config.QUOTE_ASSET;
            Optional<AssetBalance> optAssetBalance = balances.stream().filter(b -> b.getAsset().equals(asset)).findFirst();
            if ( optAssetBalance.isPresent() ) {
                AssetBalance assetBalance = optAssetBalance.get();
                log.info( assetBalance.toString() );
                setBalances( assetBalance.getFree() );
            }
        }

        this.log.exiting( this.getClass().getSimpleName(), "onResponse");
    }



    private void update() {
        this.log.entering( this.getClass().getSimpleName(), "update");
        if ( this.closeable == null ) {
            this.log.info("Initialising user data stream ");
            init();
        }
        else {
            this.log.info("Updating stream with listen key: " + this.listenKey);
            this.restClient.keepAliveUserDataStream(this.listenKey);
            this.log.exiting(this.getClass().getSimpleName(), "update");
        }
    }

    private void close() {
        this.log.entering(this.getClass().getSimpleName(), "close");
        this.log.severe("Closing data stream and exiting" );

        if ( this.restClient != null && this.listenKey != null ) {
            try {
                this.restClient.closeUserDataStream(this.listenKey);
            } catch (Throwable e) {
                this.log.log(Level.SEVERE, "Error closing user data stream", e);
            }
        }

        if ( this.closeable != null ) {
            try {
                this.closeable.close();
            } catch (Throwable e) {
                this.log.log(Level.SEVERE, "Error closing web socket stream", e);
            }
        }

        this.shutdown();
        this.log.exiting(this.getClass().getSimpleName(), "close");
    }


    public void maintain() {
        try {
            this.log.entering(this.getClass().getSimpleName(), "maintenance");
            this.log.info("Performing maintenance");
            Optional<QueueMessage> opMessage = Static.checkForExit( "*");
            if (Static.constraints.isEmpty()) {
                this.log.warning("Constraints are empty, shutting down");
                close();
            }

            else if (opMessage.isPresent()) {
                this.log.warning("Received message from exit queue, shutting down");
                close();
            }

            else update();
        }


        catch ( Throwable e )  {
            this.log.log( Level.SEVERE, e.getMessage(), e );
            this.log.severe("Sending shutdown message" );
            requestExit( "*");
            close();
        }

        finally {
            this.log.exiting( this.getClass().getSimpleName(), "maintain" );
        }
    }

    public boolean isUpdated() {
        return this.updated;
    }

    public void setOutdated( ) {
        this.updated = false;
    }

    private void shutdown() {
        try {
            Thread.sleep(3000 );
        }
        catch ( InterruptedException e ) {
            log.log( Level.SEVERE, "Shutdown failed", e);
        }
        System.exit(0);
    }
}
