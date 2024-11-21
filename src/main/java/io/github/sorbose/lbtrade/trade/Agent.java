package io.github.sorbose.lbtrade.trade;

import com.longport.OpenApiException;
import com.longport.quote.Candlestick;
import com.longport.quote.PushQuote;
import com.longport.quote.SecurityQuote;
import com.longport.trade.EstimateMaxPurchaseQuantityResponse;
import com.longport.trade.OrderSide;
import com.longport.trade.StockPosition;
import com.longport.trade.StockPositionsResponse;
import io.github.sorbose.lbtrade.quote.Quoter;
import io.github.sorbose.lbtrade.strategy.RuleStrategy;
import io.github.sorbose.lbtrade.strategy.SimpleRule;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;

public class Agent {
    Quoter quoter;
    Trader trader;
    RuleStrategy strategy;
    AssetManager assetManager;
    BigDecimal buyGapRatio;
    BigDecimal sellGapRatio;
    BigDecimal minBuyQuantity;
    Logger logger = Logger.getLogger(Agent.class.getName());

    private Quoter initQuoter(String[] symbols, int expireSecond, int timeoutSecond) {
        return new Quoter(symbols, expireSecond, timeoutSecond);
    }

    private Trader initTrader(int expireSecond, int timeoutSecond, BigDecimal cashBuyAvailableRatio, BigDecimal marginBuyAvailableRatio) {
        return new Trader(expireSecond, timeoutSecond, cashBuyAvailableRatio, marginBuyAvailableRatio);
    }

    private RuleStrategy initSimpleRule(int[] observationMinute, BigDecimal[] percentage, int conditionNum, BigDecimal gapPrice, BigDecimal winPercentage, BigDecimal losePercentage) {
        return new SimpleRule(new HashMap<>(),
                observationMinute, percentage, conditionNum, gapPrice, winPercentage, losePercentage);
    }

    public Agent(String[] symbols, BigDecimal cashBuyAvailableRatio, BigDecimal marginBuyAvailableRatio,
                 int[] observationMinute, BigDecimal[] percentage, int conditionNum,
                 BigDecimal simpleRuleProfitGapPrice, BigDecimal simpleRuleWinPercentage, BigDecimal simpleRuleLosePercentage,
                 BigDecimal buyOrderPriceGapTenThousandPercent, BigDecimal sellOrderPriceGapTenThousandPercent,
                 BigDecimal minBuyQuantity,
                 int submittedOrderExpireTimeSec, int getByNetworkTimeoutSecond) {
        this.strategy = initSimpleRule(observationMinute, percentage, conditionNum, simpleRuleProfitGapPrice, simpleRuleWinPercentage, simpleRuleLosePercentage);
        this.quoter = initQuoter(symbols, submittedOrderExpireTimeSec, getByNetworkTimeoutSecond);
        this.trader = initTrader(submittedOrderExpireTimeSec, getByNetworkTimeoutSecond, cashBuyAvailableRatio, marginBuyAvailableRatio);
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
            CompletableFuture<EstimateMaxPurchaseQuantityResponse> estMaxOrderQtyFuture = trader.pullEstMaxOrderQtyFuture(symbol);
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

    private void buyOrSell(String symbol, BigDecimal lastDone, Candlestick[] candlesticks,
                               LocalDateTime buyingTime ) {
        try {
            CompletableFuture<Void> positionsFuture = assetManager.pullStockPositions(symbol).thenAcceptAsync(
                    res -> {
                        StockPosition position=res.getChannels()[0].getPositions()[0];
                        System.out.println(Thread.currentThread()+ " position "+position);
                        BigDecimal buyingPrice = position.getCostPrice();
                        BigDecimal quantity = position.getAvailableQuantity();
                        if(quantity.compareTo(BigDecimal.ZERO)>0 &&
                                strategy.shouldSell(candlesticks, buyingPrice, buyingTime, symbol, lastDone)){
                            trader.submitOrderLOFuture(symbol, quantity.intValue(),
                                    lastDone.multiply(BigDecimal.ONE.subtract(sellGapRatio)), OrderSide.Sell);
                        }
                    });

            CompletableFuture<EstimateMaxPurchaseQuantityResponse> estMaxOrderQtyFuture = trader.pullEstMaxOrderQtyFuture(symbol);
            estMaxOrderQtyFuture.thenAcceptAsync(rawQty -> {
                System.out.println(Thread.currentThread()+ " rawQty: "+rawQty);
                BigDecimal estMarginMaxOrderQty = rawQty.getMarginMaxQty().multiply(trader.marginBuyAvailableRatio).setScale(0, RoundingMode.FLOOR);
                if (estMarginMaxOrderQty.compareTo(minBuyQuantity) >= 0
                        && strategy.shouldBuy(candlesticks, symbol, lastDone)) {
                    trader.submitOrderLOFuture(symbol, estMarginMaxOrderQty.intValue(),
                            lastDone.multiply(BigDecimal.ONE.add(buyGapRatio)), OrderSide.Buy);
                }
            });
        }catch (Exception e){
            logger.warning("An error occurred: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void runByAsking() {
        try {
            quoter.pullRealtimeQuote().thenAcceptAsync(
                    securityQuotes -> {
                        System.out.println(Thread.currentThread()+ " securityQuotes: "+ Arrays.toString(securityQuotes));
                        for(SecurityQuote quote: securityQuotes){
                            String symbol=quote.getSymbol();
                            BigDecimal lastDone=quote.getLastDone();
                            try {
                                quoter.pullRealtime1MinCandlestickFuture(symbol, 40).thenAcceptAsync(
                                        candlesticks -> {
                                            System.out.println(Thread.currentThread()+" candlesticks: "+ Arrays.toString(candlesticks));
                                            buyOrSell(symbol, lastDone, candlesticks, LocalDateTime.MIN);
                                        });
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        }
                     }
            );
        } catch (OpenApiException | ExecutionException | InterruptedException | TimeoutException e) {
            throw new RuntimeException(e);
        }
    }

    // TODO: 简单的草稿，还有许多需要检查完善的地方
    public void runBySubscribe() {
        try {
            quoter.subscribe(this::handleLatestPriceSubscribe);
        } catch (Exception e) {
            logger.warning("An error occurred: " + e.getMessage());
        }
    }

    // IMPORTANT: 直接进行交易，真实环境请小心使用
    public static void main(String[] args) throws InterruptedException {
        String[] symbols = new String[]{"TSLQ.US","YINN.US"};
        BigDecimal cashBuyAvailableRatio = new BigDecimal("0.015");
        BigDecimal marginBuyAvailableRatio = new BigDecimal("0.005");
        int[] observationMinute=new int[]{30};
        BigDecimal[] percentage=new BigDecimal[]{new BigDecimal("98")};
        int conditionNum=1;
        BigDecimal simpleRuleProfitGapPrice=new BigDecimal("0.1");
        BigDecimal simpleRuleWinPercentage=new BigDecimal("98");
        BigDecimal simpleRuleLosePercentage=new BigDecimal("98");
        BigDecimal buyOrderPriceGapTenThousandPercent=new BigDecimal("7");
        BigDecimal sellOrderPriceGapTenThousandPercent=new BigDecimal("7");
        BigDecimal minBuyQuantity=new BigDecimal("3");
        int submittedOrderExpireTimeSec=70;
        int getByNetworkTimeoutSecond=30;
        Agent agent=new Agent(symbols, cashBuyAvailableRatio, marginBuyAvailableRatio,
                observationMinute, percentage, conditionNum,
                simpleRuleProfitGapPrice, simpleRuleWinPercentage, simpleRuleLosePercentage,
                buyOrderPriceGapTenThousandPercent, sellOrderPriceGapTenThousandPercent,
                minBuyQuantity,
                submittedOrderExpireTimeSec, getByNetworkTimeoutSecond);
        // agent.runBySubscribe();
        agent.runByAsking();
        Thread.sleep(15000);
    }


}
