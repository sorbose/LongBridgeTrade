package io.github.sorbose.lbtrade.quote;


import com.longport.Config;
import com.longport.quote.AdjustType;
import com.longport.quote.Candlestick;
import com.longport.quote.Period;
import com.longport.quote.QuoteContext;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.github.sorbose.lbtrade.util.CSVSerializer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class CandlesticksFetcher {
    private static final Logger logger = LogManager.getLogger(CandlesticksFetcher.class);

    private List<Candlestick> fetch1Min(String symbol, LocalDateTime start) {
        LocalDateTime current = start;
        List<Candlestick> list = new ArrayList<>();
        try (Config config = Config.fromEnv(); QuoteContext quoteContext =QuoteContext.create(config).get()) {
            int count=1000;
            while(count>0){
                CompletableFuture<Candlestick[]> future = quoteContext.getHistoryCandlesticksByOffset(
                        symbol, Period.Min_1, AdjustType.ForwardAdjust, true, current, 1000
                );
                Candlestick[] result = future.get(100, TimeUnit.SECONDS);
                count=result==null?0:result.length;
                if (result != null) {
                    if(result[result.length-1].getTimestamp().toLocalDateTime().isBefore(current)){
                        break;
                    }
                    list.addAll(Arrays.asList(result));
                }
                current=list.get(list.size()-1).getTimestamp().toLocalDateTime().plusMinutes(1);
                System.out.println(current);
                System.out.println(count);
            }
        } catch (TimeoutException e) {
            logger.warn("Fetching data timed out for symbol: {}", symbol, e);
        } catch (Exception e) {
            logger.error("Error fetching data for symbol: {}", symbol, e);
            throw new RuntimeException("Error fetching data for symbol: " + symbol, e);
        }
        return list;
    }

    public void toConsole(String symbol, LocalDateTime start) {
        fetch1Min(symbol, start).forEach(System.out::println);
    }

    public void toCsv(String symbol, LocalDateTime start) {
        CSVSerializer.writeObjectsToCsv(fetch1Min(symbol, start),
                ("data/"+symbol+start).replaceAll(":", ""));
    }

    public static void main(String[] args) {
        logger.debug(System.getProperty("os.arch"));
        logger.info("start");
        CandlesticksFetcher fetcher = new CandlesticksFetcher();
        long startTime = System.currentTimeMillis();
        fetcher.toCsv("YANG.US",
                LocalDate.of(2023, 12, 4).atStartOfDay());
        long endTime = System.currentTimeMillis();
        logger.info("end");
        logger.info("Time taken (s): " + (endTime - startTime)/1000f);
    }

}
