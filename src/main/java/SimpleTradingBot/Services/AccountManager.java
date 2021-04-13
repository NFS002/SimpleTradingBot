package SimpleTradingBot.Services;


import SimpleTradingBot.Models.QueueMessage;
import SimpleTradingBot.Util.Static;
import com.binance.api.client.BinanceApiCallback;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.BinanceApiWebSocketClient;
import com.binance.api.client.domain.account.Account;
import com.binance.api.client.domain.account.AssetBalance;
import com.binance.api.client.domain.event.AccountUpdateEvent;
import com.binance.api.client.domain.event.UserDataUpdateEvent;

import java.io.Closeable;
import java.math.BigDecimal;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import static SimpleTradingBot.Config.Config.*;
import static SimpleTradingBot.Util.Static.requestExit;

import static  com.binance.api.client.domain.event.UserDataUpdateEvent.UserDataUpdateEventType.ACCOUNT_UPDATE;

public class AccountManager implements BinanceApiCallback<UserDataUpdateEvent> {

    private BinanceApiRestClient restClient;

    private String listenKey;

    private Closeable closeable;

    private final Logger log;

    private BigDecimal nextQty;

    /* Singleton */
    private static AccountManager instance;


    private AccountManager( ) {
        this.restClient = Static.getFactory().newRestClient();
        this.log = Logger.getLogger( "root.am" );
        this.nextQty = null;
        this.closeable = null;
        init();
    }

    public static AccountManager getInstance() {
        if ( instance == null )
            instance = new AccountManager();
        return instance;
    }

    private void init() {
        long timeStamp = System.currentTimeMillis();
        this.listenKey = this.restClient.startUserDataStream();
        BinanceApiWebSocketClient webSocketClient = Static.getFactory().newWebSocketClient();
        Account account = this.restClient.getAccount( RECV_WINDOW, timeStamp );
        AssetBalance balance = account.getAssetBalance( QUOTE_ASSET );
        BigDecimal freeBalance = new BigDecimal("40");
        if (freeBalance.compareTo(QUOTE_PER_TRADE) < 0) {
            this.log.severe(String.format("Initial free balance of %s is below configured trade default of %s, exiting now", freeBalance, QUOTE_PER_TRADE));
            this.close();
        }
        else {
            setNextTradeValue( freeBalance );
            this.closeable = webSocketClient.onUserDataUpdateEvent(this.listenKey, this);
        }
    }

    public BigDecimal getNextTradeValue() {
        switch ( TEST_LEVEL ) {
            case REAL:
                return this.nextQty;
            case MOCK:
            default:
                return QUOTE_PER_TRADE;
        }
    }

    private void setNextTradeValue(BigDecimal remainingBalance ) {
        this.log.entering( this.getClass().getSimpleName(), "setBalances" );
        log.info(String.format("Setting balances with remaining balance of: %s %s", remainingBalance, QUOTE_ASSET));

        remainingBalance = remainingBalance.compareTo( BigDecimal.ZERO ) < 1 ? BigDecimal.ZERO : remainingBalance;

        this.nextQty = remainingBalance.compareTo( QUOTE_PER_TRADE ) > 0 ? QUOTE_PER_TRADE : remainingBalance;;

        this.log.exiting( this.getClass().getSimpleName(), "setBalances" );

    }

    @Override
    public void onResponse(UserDataUpdateEvent event) {
        this.log.entering( this.getClass().getSimpleName(), "onResponse");
        this.log.info( "Received event: " + event );
        if ( event.getEventType() == ACCOUNT_UPDATE ) {
            AccountUpdateEvent accountUpdateEvent = event.getAccountUpdateEvent();
            List<AssetBalance> balances = accountUpdateEvent.getBalances();
            Optional<AssetBalance> optAssetBalance = balances.stream().filter(b -> b.getAsset().equals( QUOTE_ASSET )).findAny();
            if ( optAssetBalance.isPresent() ) {
                AssetBalance assetBalance = optAssetBalance.get();
                BigDecimal remainingFreeBalance = new BigDecimal( assetBalance.getFree() );
                log.info( "Setting next quantity with remaining balance of: " + remainingFreeBalance );
                setNextTradeValue( remainingFreeBalance );
            }
            else
                log.warning( "Account update event did not contain quote asset balance" );
        }

        this.log.exiting( this.getClass().getSimpleName(), "onResponse");
    }



    private void update() {
        this.log.entering( this.getClass().getSimpleName(), "update");
        this.log.info("Updating stream with listen key: " + this.listenKey);
        this.restClient.keepAliveUserDataStream(this.listenKey);
        this.log.exiting(this.getClass().getSimpleName(), "update");
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
