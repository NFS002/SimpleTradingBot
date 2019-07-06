# Simple Trading Bot

A fully automated trading integrated with the Binance Cryptocurrency exchange API,
and using quantative modelling for technical analysis.

## Dependencies
```xml
<dependencies>
        <dependency>
            <!-- Binance API java client -->
            <groupId>com.github.binance-exchange</groupId>
            <artifactId>binance-java-api</artifactId>
            <version>master-SNAPSHOT</version>
        </dependency>
        <dependency>
            TA_RULES
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
