package io.github.sorbose.lbtrade.quote;


import com.longport.OpenApiException;
import com.longport.quote.AdjustType;
import com.longport.quote.Candlestick;
import com.longport.quote.Period;
import com.longport.quote.QuoteContext;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.github.sorbose.lbtrade.util.CSVSerializer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class CandlesticksFetcher {
    private static final Logger logger = LogManager.getLogger(CandlesticksFetcher.class);

    private Candlestick[] fetch(String symbol, LocalDate start, LocalDate end) {
        try (QuoteContext quoteContext = new QuoteContext()) {
            CompletableFuture<Candlestick[]> future = quoteContext.getHistoryCandlesticksByDate
                    (symbol, Period.Min_1, AdjustType.ForwardAdjust, start, end);
            return future.get(10, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            logger.warn("Fetching data timed out for symbol: {}", symbol, e);
            return new Candlestick[0]; // 返回空数据
        } catch (Exception e) {
            logger.error("Error fetching data for symbol: {}", symbol, e);
            throw new RuntimeException("Error fetching data for symbol: " + symbol, e);
        }
    }

    public void toConsole(String symbol, LocalDate start, LocalDate end) {
        Arrays.stream(fetch(symbol, start, end)).forEach(System.out::println);
    }

    public void toCsv(String symbol, LocalDate start, LocalDate end) {
        CSVSerializer.writeObjectsToCsv(Arrays.asList(fetch(symbol, start, end)),symbol+start+end);
    }

    public static void main(String[] args) {
        System.out.println(System.getProperty("os.arch"));
        CandlesticksFetcher fetcher = new CandlesticksFetcher();
        fetcher.toConsole("TSLA.US", LocalDate.of(2024, 11, 10), LocalDate.of(2024, 11, 17));
    }

}
