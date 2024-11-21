package io.github.sorbose.lbtrade;

import com.longport.Config;
import com.longport.OpenApiException;
import com.longport.quote.QuoteContext;
import com.longport.trade.TradeContext;
import org.slf4j.LoggerFactory;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.concurrent.*;
import java.util.logging.Logger;

public class TradeMain {
    public static final Config config;
    public static final QuoteContext quoteContext;
    public static final TradeContext tradeContext;
    public static final Logger logger = Logger.getLogger(TradeMain.class.getName());
    private static final org.slf4j.Logger log = LoggerFactory.getLogger(TradeMain.class);


    static {
        Config configTmp=null;
        QuoteContext quoteContextTmp=null;
        TradeContext tradeContextTmp=null;
        for(int i=1;i<=5;i++){
            try {
                System.out.println(ZonedDateTime.now(ZoneOffset.UTC));
                configTmp = Config.fromEnv();
                quoteContextTmp = QuoteContext.create(configTmp).get();
                tradeContextTmp = TradeContext.create(configTmp).get();
                break;
            } catch (OpenApiException | ExecutionException | InterruptedException e) {
                logger.severe("Error initializing TradeMain");
                logger.severe(Arrays.toString(e.getStackTrace()));
                e.printStackTrace();
                try {
                    long waitTime = 1000 * i*i;
                    logger.info("Waiting for " + waitTime + "ms Retry " + i);
                    Thread.sleep(waitTime);
                } catch (InterruptedException interruptedException) {
                    logger.info("Interrupted");
                }
            }
        }
        config = configTmp;
        quoteContext = quoteContextTmp;
        tradeContext = tradeContextTmp;
        if(config==null || quoteContext==null || tradeContext==null){
            logger.severe("Failed to initialize TradeMain");
            System.exit(1);
        }
    }
}
