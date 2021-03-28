# Simple Trading Bot

A fully automated trading integrated with the Binance Cryptocurrency exchange API,
and using quantative modelling for technical analysis.


## OnlyBot branch

The OnlyBot branch is another version of the bot, which is essentially supposed to be only the bot 
(with a trailing stop loss) but does not make any decisions on when to open an order. Instead, it receives 
signals through a server which basically tell it what symbols to trade.
The TA and other Indicators are totally decoupled from the bot in this model and communicate through HTTP/2.
The idea behind this is that CPU heavy processes such as quantative analysis can be run by another program, and also that
we can respond to indicators/signal from multiple different sources, or choose
to aggregate them and choose a position weight accordingly. It is potentially more efficient and flexible than the master branch. 
This branch compiles and runs, but it is still in heavy development and testing stage. It has some more dependencies such as 
a rapidoid server and some spring framework components.

## Async branch
The same project but uses an asynchronous http client.






## (some of the) Dependencies
```xml
<dependencies>
    
        <dependency>
            <!-- Binance API java client -->
            <groupId>com.github.binance-exchange</groupId>
            <artifactId>binance-java-api</artifactId>
            <version>master-SNAPSHOT</version>
        </dependency>
    
        <dependency>
            <groupId>org.ta4j</groupId>
            <artifactId>ta4j-core</artifactId>
            <version>0.11</version>
        </dependency>
    
</dependencies>
```


### Running

Simply cd into the root directory, open terminal and type
```bash
mvn exec:java
```
Note: You must have java and maven installed (JDK 10)


### Project specifications

This bot is fully configured to make, place, and update orders 
programatically according to some customisable configuration.
Data is streamed lived and orders are made in realtime.
A trailing stop loss is also set up for each order to close accordingly, but this can 
be toggled on/off.
Currently, I just have it set up for a very simple strategy that
takes up positions based on moving averages. Theres a lot of 
other configuration options as well, mostly accesssible in Config.java
(Although TA can be configured in TAanalyser.java)
Everything (OLHC, TA, errors, websocket debug logs) is logged in the out/ directory also.
Theres a lot of other features, and I intend to write a blog post about it on
my github pages. Feel free to explore/clone

