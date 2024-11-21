package io.github.sorbose.lbtrade.quote;

import com.longport.Config;
import com.longport.OpenApiException;
import com.longport.quote.*;
import io.github.sorbose.lbtrade.TradeMain;
import io.github.sorbose.lbtrade.trade.Trader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class Quoter {
    private static final Config config= TradeMain.config;
    private static final QuoteContext context= TradeMain.quoteContext;
    private static final Logger logger = LogManager.getLogger(Trader.class);
    public final int expireSecond;
    public final int timeoutSecond;
    public String[] symbols;
    public CalcIndex[] calcIndices;

    public Quoter(String[] symbols, int expireSecond, int timeoutSecond, CalcIndex[] calcIndices) {
        this.symbols=symbols;
        this.expireSecond = expireSecond;
        this.timeoutSecond = timeoutSecond;
        this.calcIndices = calcIndices;
    }
    public Quoter(String[] symbols, int expireSecond, int timeoutSecond) {
        this(symbols, expireSecond, timeoutSecond, null);
    }

    public CompletableFuture<SecurityQuote[]> pullRealtimeQuote() throws OpenApiException, ExecutionException, InterruptedException, TimeoutException {
        return context.getQuote(symbols);
    }

    public CompletableFuture<SecurityCalcIndex[]> pullRealtimeCalcIndex() throws OpenApiException, ExecutionException, InterruptedException, TimeoutException {
        return context.getCalcIndexes(symbols, calcIndices);
    }

    // TODO: 此方法获取不到夜盘数据！
    public Candlestick[] pullRealtime1MinCandlestick(String symbol, int count) throws OpenApiException, ExecutionException, InterruptedException, TimeoutException {
        return context.getCandlesticks(symbol, Period.Min_1, count, AdjustType.ForwardAdjust).get(timeoutSecond, TimeUnit.SECONDS);
    }

    public CompletableFuture<Candlestick[]> pullRealtime1MinCandlestickFuture(String symbol, int count) throws OpenApiException {
        return context.getCandlesticks(symbol, Period.Min_1, count, AdjustType.ForwardAdjust);
    }


    public void pullRealtime1MinCandlestickAccept(String symbol, int count) throws OpenApiException, ExecutionException, InterruptedException, TimeoutException {
        context.getCandlesticks(symbol, Period.Min_1, count, AdjustType.ForwardAdjust).thenAccept(candlesticks -> {
            System.out.println("thenAccept: "+ Arrays.toString(candlesticks));
        });
    }

    public Candlestick[] pullRealtime1MinCandlestickApply(String symbol, int count) throws OpenApiException, ExecutionException, InterruptedException, TimeoutException {
        CompletableFuture<Candlestick[]> future = context.getCandlesticks(symbol, Period.Min_1, count, AdjustType.ForwardAdjust).thenApply(candlesticks -> {
            System.out.println("thenApply: "+ Arrays.toString(candlesticks));
            return candlesticks;
        });
        return  future.join();
    }


    public void subscribe(QuoteHandler handler) throws OpenApiException, ExecutionException, InterruptedException, TimeoutException {
        context.setOnQuote(handler);
        context.subscribe(symbols, 1, true);
    }

    public static void handleException(Exception e, String msg){
        if (e instanceof OpenApiException){
            OpenApiException oe = (OpenApiException) e;
            logger.error("An error occurred: {} {}", msg, oe.getMessage());
        }else {
            logger.error("An error occurred: {} {}", msg, e.getMessage());
        }
    }

    public static void main(String[] args) {
        Quoter quoter = new Quoter(new String[]{"YINN.US","TSLL.US", "TSLQ.US", "TSLA.US","1810.HK","9988.HK"},
                70, 30
                );
        try {
            System.out.println(System.getenv("LONGPORT_ENABLE_OVERNIGHT"));
            quoter.pullRealtimeQuote().thenAccept(securityQuotes -> {
                System.out.println("securityQuotes: "+ Arrays.toString(securityQuotes));
            });
            quoter.subscribe(((symbol, event) -> {
                System.out.println(symbol + " " + event);
            }));
            Thread.sleep(1000);
            Candlestick[] candlesticks = quoter.pullRealtime1MinCandlestick("TSLQ.US", 5);
            System.out.println(Arrays.toString(candlesticks));
            Thread.sleep(50000);
            long sum=0;
            long startTime = System.currentTimeMillis();
            System.out.println("开始计算");
            for(long i=1;i<1e10;i++){sum+=i*i;}
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;
            System.out.println("执行时间：" + duration + " 毫秒");
        } catch (OpenApiException | ExecutionException | InterruptedException | TimeoutException e) {
            handleException(e, "pulling realtime quote");
        }
    }
}
