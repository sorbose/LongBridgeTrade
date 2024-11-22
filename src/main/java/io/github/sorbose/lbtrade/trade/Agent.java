package io.github.sorbose.lbtrade.trade;

import com.longport.OpenApiException;
import com.longport.quote.Candlestick;
import com.longport.quote.PushQuote;
import com.longport.quote.SecurityQuote;
import com.longport.trade.EstimateMaxPurchaseQuantityResponse;
import com.longport.trade.OrderSide;
import com.longport.trade.StockPosition;
import com.longport.trade.StockPositionsResponse;
import com.sun.xml.internal.ws.api.ha.StickyFeature;
import io.github.sorbose.lbtrade.quote.Quoter;
import io.github.sorbose.lbtrade.strategy.RuleStrategy;
import io.github.sorbose.lbtrade.strategy.SimpleRule;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.sound.midi.Sequence;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.Arrays;
import java.util.HashMap;
import java.util.concurrent.*;

public class Agent {
    Quoter quoter;
    Trader trader;
    RuleStrategy strategy;
    AssetManager assetManager;
    BigDecimal buyGapRatio;
    BigDecimal sellGapRatio;
    BigDecimal minBuyQuantity;
    ThreadPoolExecutor executor = new ThreadPoolExecutor(
            1,                // 核心线程数
            2,               // 最大线程数
            100,              // 线程空闲最大存活时间
            TimeUnit.SECONDS, // 时间单位
            new LinkedBlockingQueue<>(10),  // 队列容量
            new ThreadPoolExecutor.DiscardOldestPolicy() // 拒绝策略
    );
    private static final Logger logger = LogManager.getLogger(Agent.class);

    private Quoter initQuoter(String[] symbols, int expireSecond, int timeoutSecond) {
        return new Quoter(symbols, expireSecond, timeoutSecond);
    }

    private Trader initTrader(int expireSecond, int timeoutSecond,
                              BigDecimal cashBuyAvailableRatio, BigDecimal marginBuyAvailableRatio,
                              BigDecimal minRemainFinanceAmount) {
        return new Trader(expireSecond, timeoutSecond, cashBuyAvailableRatio, marginBuyAvailableRatio, minRemainFinanceAmount);
    }

    private RuleStrategy initSimpleRule(int[] observationMinute, BigDecimal[] percentage, int conditionNum, BigDecimal gapPrice, BigDecimal winPercentage, BigDecimal losePercentage) {
        return new SimpleRule(new HashMap<>(),
                observationMinute, percentage, conditionNum, gapPrice, winPercentage, losePercentage);
    }

    public Agent(String[] symbols, BigDecimal cashBuyAvailableRatio, BigDecimal marginBuyAvailableRatio,
                 BigDecimal minRemainFinanceAmount,
                 int[] observationMinute, BigDecimal[] percentage, int conditionNum,
                 BigDecimal simpleRuleProfitGapPrice, BigDecimal simpleRuleWinPercentage, BigDecimal simpleRuleLosePercentage,
                 BigDecimal buyOrderPriceGapTenThousandPercent, BigDecimal sellOrderPriceGapTenThousandPercent,
                 BigDecimal minBuyQuantity,
                 int submittedOrderExpireTimeSec, int getByNetworkTimeoutSecond) {
        this.strategy = initSimpleRule(observationMinute, percentage, conditionNum, simpleRuleProfitGapPrice, simpleRuleWinPercentage, simpleRuleLosePercentage);
        this.quoter = initQuoter(symbols, submittedOrderExpireTimeSec, getByNetworkTimeoutSecond);
        this.trader = initTrader(submittedOrderExpireTimeSec, getByNetworkTimeoutSecond, cashBuyAvailableRatio, marginBuyAvailableRatio, minRemainFinanceAmount);
        this.buyGapRatio = buyOrderPriceGapTenThousandPercent.divide(BigDecimal.valueOf(10000), 6, RoundingMode.HALF_UP);
        this.sellGapRatio = sellOrderPriceGapTenThousandPercent.divide(BigDecimal.valueOf(10000), 6, RoundingMode.HALF_UP);
        this.minBuyQuantity = minBuyQuantity;
        this.assetManager = new AssetManager();
    }

    // TODO: 丢弃来不及处理的数据
    private boolean discardOld(String symbol, PushQuote pushQuote) {
        return false;
    }

    private void handleLatestPriceSubscribe(String symbol, PushQuote pushQuote) {
        try {
            System.out.println(symbol + " " + pushQuote.getLastDone()+" "+pushQuote.getTimestamp());
            quoter.pullRealtimeCalcIndex().thenAccept(securityCalcIndices -> {
                System.out.println("securityCalcIndices: "+ Arrays.toString(securityCalcIndices));
            });

            CompletableFuture<StockPositionsResponse> positionsFuture = assetManager.pullStockPositions(symbol);

            // TODO: 检查夜盘交易时段的K线是否最新
            CompletableFuture<Candlestick[]> candlestickFuture = quoter.pullRealtime1MinCandlestickFuture(symbol, 40);
            CompletableFuture<BigDecimal> estMaxOrderQtyFuture = trader.pullEstMarginMaxOrderQtyFuture(symbol);
            CompletableFuture<Void> allOf = CompletableFuture.allOf(positionsFuture, candlestickFuture, estMaxOrderQtyFuture);
            allOf.thenApply(v -> {
                Candlestick[] candlesticks=candlestickFuture.join();
                StockPositionsResponse positions=positionsFuture.join();
                System.out.println(symbol+ " v "+v);
                return "q";
            });
            // Candlestick[] candlesticks = quoter.pullRealtime1MinCandlestickFuture(symbol, 40).get();
            Candlestick[] candlesticks =null;
            // TODO: 简单起见，这里没有根据盈亏情况判断是否卖出，全部按盈利处理
            // TODO: 简单起见，这里没有考虑买入时间
//            if (buyOrSell(symbol, pushQuote, candlesticks)) return;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    private String buyIfNeeded(String symbol, BigDecimal lastDone, Candlestick[] candlesticks,
                               BigDecimal estMarginMaxOrderQty, BigDecimal remainingFinanceAmount) throws OpenApiException, ExecutionException, InterruptedException, TimeoutException {
        logger.debug("estMarginMaxOrderQty: {}", estMarginMaxOrderQty);
        if (estMarginMaxOrderQty.compareTo(minBuyQuantity) >= 0
                && remainingFinanceAmount.compareTo(trader.minRemainFinanceAmount) >= 0
                && strategy.shouldBuy(candlesticks, symbol, lastDone)) {
            logger.debug("buyIfNeeded: {} {}", symbol, lastDone);
            return trader.submitOrderLO(symbol, estMarginMaxOrderQty.intValue(),
                    lastDone.multiply(BigDecimal.ONE.add(buyGapRatio)), OrderSide.Buy);
        }
        return null;
    }

    private String sellIfNeeded(String symbol, BigDecimal lastDone, Candlestick[] candlesticks,
                                 StockPositionsResponse res) throws OpenApiException, ExecutionException, InterruptedException, TimeoutException {
        StockPosition[] positions=res.getChannels()[0].getPositions();
        if(positions.length==0){
            return null;
        }
        StockPosition position=positions[0];
        logger.debug("position = {}", position);
        BigDecimal buyingPrice = position.getCostPrice();
        BigDecimal quantity = position.getAvailableQuantity();
        if(quantity.compareTo(BigDecimal.ZERO)>0){
            OffsetDateTime buyingTimeUtc =
                    trader.pullLatestFilledBuyOrderTime(symbol).get(trader.timeoutSecond, TimeUnit.SECONDS);
            if(strategy.shouldSell(candlesticks, buyingPrice, buyingTimeUtc, symbol, lastDone)){
                logger.debug("sellIfNeeded: {} {}  {}", symbol, lastDone, buyingTimeUtc);
                return trader.submitOrderLO(symbol, quantity.intValue(),
                        lastDone.multiply(BigDecimal.ONE.subtract(sellGapRatio)), OrderSide.Sell);
            }
        }
        return null;
    }

    private String buyOrSellSync(String symbol, BigDecimal lastDone, Candlestick[] candlesticks){
        String orderId=null;
        try {
            StockPositionsResponse stockPositionsResponse= assetManager.pullStockPositions(symbol).get(trader.timeoutSecond, TimeUnit.SECONDS);
            orderId = sellIfNeeded(symbol, lastDone, candlesticks, stockPositionsResponse);
            if(orderId==null){
                BigDecimal estMarginMaxOrderQty=trader.pullEstMarginMaxOrderQtyFuture(symbol).get(trader.timeoutSecond, TimeUnit.SECONDS);
                BigDecimal remainingFinanceAmount=assetManager.getBalanceUSD().getRemainingFinanceAmount();
                orderId = buyIfNeeded(symbol, lastDone, candlesticks, estMarginMaxOrderQty, remainingFinanceAmount);
            }
            return orderId;
        } catch (ExecutionException | InterruptedException | OpenApiException | TimeoutException e) {
            logger.error("An error occurred {}", e.getMessage());
            logger.error(Arrays.toString(e.getStackTrace()));
            e.printStackTrace();
        }
        return orderId;
    }

    public void runByAsking() {
        try {
            SecurityQuote[] securityQuotes= quoter.pullRealtimeQuote().get(trader.timeoutSecond, TimeUnit.SECONDS);
            logger.debug("securityQuotes: {}", Arrays.toString(securityQuotes));
            for(SecurityQuote quote: securityQuotes){
                String symbol=quote.getSymbol();
                BigDecimal lastDone=quote.getLastDone();
                Candlestick[] candlesticks= quoter.pullRealtime1MinCandlestickFuture(symbol, 1000).get(trader.timeoutSecond, TimeUnit.SECONDS);
                logger.debug("candlesticks length: {}", candlesticks.length);
                OffsetDateTime latestCandlestickTime=candlesticks[candlesticks.length-1].getTimestamp();
                Duration duration= Duration.between(latestCandlestickTime, OffsetDateTime.now(ZoneId.of("UTC")));
                if(duration.toMinutes()>3){
                    logger.warn("Delayed candlesticks time (UTC+0) {}, duration {} min", latestCandlestickTime, duration.toMinutes());
                }
                String orderId= buyOrSellSync(symbol, lastDone, candlesticks);
                if(orderId!=null){
                    logger.info("orderId: {}", orderId);
                }
            }
        } catch (OpenApiException | ExecutionException | InterruptedException | TimeoutException e) {
            throw new RuntimeException(e);
        }
    }

    // TODO: 简单的草稿，还有许多需要检查完善的地方
    public void runBySubscribe() {
        try {
            quoter.subscribe(this::handleLatestPriceSubscribe);
        } catch (Exception e) {
            logger.error("An error occurred: {}", e.getMessage());
        }
    }

    // IMPORTANT: 直接进行交易，真实环境请小心使用
    public static void main(String[] args) throws InterruptedException {
        String[] symbols = new String[]{"TSLL.US","TSLQ.US"};
        BigDecimal cashBuyAvailableRatio = new BigDecimal("0.015");
        BigDecimal marginBuyAvailableRatio = new BigDecimal("0.005");
        BigDecimal minRemainFinanceAmount = new BigDecimal("408000");
        int[] observationMinute=new int[]{30};
        BigDecimal[] percentage=new BigDecimal[]{new BigDecimal("98.5")};
        int conditionNum=1;
        BigDecimal simpleRuleProfitGapPrice=new BigDecimal("0.1");
        BigDecimal simpleRuleWinPercentage=new BigDecimal("98.5");
        BigDecimal simpleRuleLosePercentage=new BigDecimal("99.5");
        BigDecimal buyOrderPriceGapTenThousandPercent=new BigDecimal("7");
        BigDecimal sellOrderPriceGapTenThousandPercent=new BigDecimal("7");
        BigDecimal minBuyQuantity=new BigDecimal("3");
        int submittedOrderExpireTimeSec=70;
        int getByNetworkTimeoutSecond=30;
        Agent agent=new Agent(symbols, cashBuyAvailableRatio, marginBuyAvailableRatio,
                minRemainFinanceAmount,observationMinute, percentage, conditionNum,
                simpleRuleProfitGapPrice, simpleRuleWinPercentage, simpleRuleLosePercentage,
                buyOrderPriceGapTenThousandPercent, sellOrderPriceGapTenThousandPercent,
                minBuyQuantity,
                submittedOrderExpireTimeSec, getByNetworkTimeoutSecond);
        // agent.runBySubscribe();
        LocalDateTime endTime = LocalDateTime.of(2024, 11, 22, 5, 0);
        System.out.println(LocalDateTime.now().isBefore(endTime));

        while (LocalDateTime.now().isBefore(endTime)) {
            agent.executor.submit(agent::runByAsking);
            Thread.sleep(1000);
        }

        // Thread.sleep(15000);
        agent.executor.shutdown();
    }


}
