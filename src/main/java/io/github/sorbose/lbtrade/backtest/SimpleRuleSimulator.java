package io.github.sorbose.lbtrade.backtest;

import com.longport.Market;
import com.longport.quote.Candlestick;
import io.github.sorbose.lbtrade.strategy.SimpleRule;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class SimpleRuleSimulator extends AbstractSimulator {
    private SimpleRule strategy;
    int[] observationMinute;
    BigDecimal[] percentage;
    int[] highThanExpected;
    int conditionNum;
    BigDecimal gapPrice;
    BigDecimal winPercentage;
    BigDecimal losePercentage;
    boolean stopLoss;
    private static final BigDecimal fixedBuyFee = new BigDecimal("1.5");
    private static final BigDecimal fixedSellFee = new BigDecimal("1.5");
    private static final Logger logger = LogManager.getLogger(SimpleRuleSimulator.class);
    private int tradeCount = 0;

    public SimpleRuleSimulator(String[] symbols, String currency, BigDecimal initCash, LocalDateTime beginTime, LocalDateTime endTime, HashMap<String, List<Candlestick>> candlesticksMap,
                               int[] observationMinute, BigDecimal[] percentage, int[] highThanExpected, int conditionNum, BigDecimal gapPrice, BigDecimal winPercentage, BigDecimal losePercentage, boolean stopLoss) {
        super(symbols, currency, initCash, beginTime, endTime, candlesticksMap);
        this.observationMinute = observationMinute;
        this.percentage = percentage;
        this.highThanExpected = highThanExpected;
        this.conditionNum = conditionNum;
        this.gapPrice = gapPrice;
        this.winPercentage = winPercentage;
        this.losePercentage = losePercentage;
        this.stopLoss = stopLoss;
    }

    @Override
    protected BigDecimal getBuyFee(Market market, BigDecimal estTotalPrice) {
        // 模拟时按照当前分钟的最高价买入，最低价卖出，默认均可成交，不再计算下单价和最新价之间的价格差
        return fixedBuyFee.add(estTotalPrice.multiply(new BigDecimal("0")));
    }

    @Override
    protected BigDecimal getSellFee(Market market, BigDecimal totalPrice) {
        return fixedSellFee.add(totalPrice.multiply(new BigDecimal("0")));
    }

    @Override
    public void run() {
        List<Candlestick> candlesticks = candlesticksMap.get(symbols[0]);
        strategy = new SimpleRule(candlesticksMap, observationMinute, percentage, highThanExpected, conditionNum, gapPrice, winPercentage, losePercentage);
        for (Candlestick candlestick : candlesticks) {
            LocalDateTime now = candlestick.getTimestamp().toLocalDateTime();
            if (now.isBefore(beginTime)) {
                continue;
            }
            // 一分钟内买入或卖出只能操作一次
            if (getTotalStocksAmount() > 0) {
                // 还有持仓股票，优先考虑卖出
                BigDecimal lastPrice = candlestick.getHigh();
                BigDecimal buyingPrice = stockPositions.get(symbols[0]).costPrice;
                LocalDateTime buyTime = getStockRecentBuyTime(symbols[0]);
                boolean shouldSell = strategy.shouldSell(symbols[0], buyingPrice, buyTime, lastPrice, now, stopLoss);
                if (shouldSell) {
                    BigDecimal quantity = stockPositions.get(symbols[0]).quantity;
                    // TODO: 模拟时卖出价格欠考虑，不应该用最高价
                    sellAndPrint(quantity, candlestick.getLow(),
                             now, buyingPrice);
                    continue;
                }
            }
            BigDecimal lastPrice = candlestick.getLow();
            boolean shouldBuy = strategy.shouldBuy(symbols[0], lastPrice, now);
            if (shouldBuy) {
                BigDecimal quantity = cash.subtract(getBuyFee(Market.US, cash)).
                        divide(lastPrice, 0, RoundingMode.DOWN);
                boolean haveBought = buy(symbols[0], quantity,
                        candlestick.getHigh(), now);
            }
        }
        sellAndPrint(stockPositions.get(symbols[0]).quantity, candlesticks.get(candlesticks.size() - 1).getClose(), candlesticks.get(candlesticks.size() - 1).getTimestamp().toLocalDateTime(), stockPositions.get(symbols[0]).costPrice);
    }

    private void sellAndPrint(BigDecimal quantity, BigDecimal lastPrice, LocalDateTime now, BigDecimal buyingPrice) {
        sell(symbols[0], quantity, lastPrice, now, buyingPrice);
    }

    private boolean buy(String symbol, BigDecimal quantity, BigDecimal lastPrice, LocalDateTime now) {
        if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        MyStockPosition stockPosition = stockPositions.get(symbol);
        BigDecimal estCost = quantity.multiply(lastPrice);
        BigDecimal buyFee = getBuyFee(Market.US, estCost);
        BigDecimal realQuantity = cash.subtract(buyFee).divide(lastPrice, 0, RoundingMode.DOWN);
        BigDecimal cost = realQuantity.multiply(lastPrice);
        cash = cash.subtract(cost).subtract(buyFee);
        stockPosition.costPrice = stockPosition.costPrice.multiply(stockPosition.quantity)
                .add(lastPrice.multiply(realQuantity)).divide(stockPosition.quantity.add(realQuantity), 3, RoundingMode.HALF_UP);
        stockPosition.quantity = stockPosition.quantity.add(realQuantity);
        tradeRecords.add(new TradeRecord(now, lastPrice, realQuantity, TradeRecord.Direction.BUY, symbol));
        logger.info("\n Buy " + symbol + " at " + lastPrice + " quantity "+ realQuantity + " at " + now);
        logger.info("Balance After Buying: " + cash);
        tradeCount++;
        return true;
    }

    private boolean sell(String symbol, BigDecimal quantity, BigDecimal lastPrice,
                         LocalDateTime localDateTime, BigDecimal buyingPrice) {
        MyStockPosition stockPosition = stockPositions.get(symbol);
        if (quantity.compareTo(BigDecimal.ZERO) <= 0 ||
                stockPosition.quantity.compareTo(quantity) < 0) {
            return false;
        }
        if (stockPosition.quantity.compareTo(quantity) == 0) {
            stockPosition.costPrice = BigDecimal.ZERO;
        } else {
            stockPosition.costPrice = stockPosition.costPrice.multiply(stockPosition.quantity)
                    .subtract(lastPrice.multiply(quantity)).divide(stockPosition.quantity.subtract(quantity), 3, RoundingMode.HALF_UP);
        }

        stockPosition.quantity = stockPosition.quantity.subtract(quantity);
        BigDecimal sellAmount = quantity.multiply(lastPrice);
        BigDecimal sellFee = getSellFee(Market.US, sellAmount);
        cash = cash.add(sellAmount).subtract(sellFee);
        tradeRecords.add(new TradeRecord(localDateTime, lastPrice, quantity, TradeRecord.Direction.SELL, symbol));

        logger.info("\n Sell " + symbols[0] + " at " + lastPrice + " at " + localDateTime +
                " with profit " + (lastPrice.subtract(buyingPrice)).multiply(quantity));
        logger.info("Balance: " + cash);
        tradeCount++;
        return true;
    }

    public static void main(String[] args) {
        String[] symbols = new String[]{"MSTX"};
        String[] inPaths = Arrays.stream(symbols).map(s -> CandlestickLoader.symbolToPath.get(s)).toArray(String[]::new);
        CandlestickLoader candlestickLoader = new CandlestickLoader();
        HashMap<String, List<Candlestick>> candlesticksMap =
                IntStream.range(0, symbols.length).boxed().collect(Collectors.toMap(
                        i -> symbols[i],
                        i -> {
                            System.out.println("Loading data for symbol: " + symbols[i]);
                            List<Candlestick> candlesticks = candlestickLoader.fromCsv(
                                    CandlestickLoader.symbolToPath.get(symbols[i]),
                                    LocalDateTime.of(2024, 10, 30, 0, 0),
                                    LocalDateTime.now()
                            );
                            System.out.println("Loaded " + candlesticks.size() + " candlesticks for symbol: " + symbols[i]);
                            return candlesticks;
                        },
                        (existing, replacement) -> existing,  // 处理重复键时选择现有的值
                        HashMap::new  // 使用 HashMap
                ));
        BigDecimal initCash = new BigDecimal("2000");
        LocalDateTime beginTime = LocalDateTime.of(2024, 11, 18, 0, 0);
        LocalDateTime endTime = LocalDateTime.now();
        int[] observationMinute=new int[]{3,1};
        BigDecimal[] percentage=Arrays.stream(new String[]{"98","99"}).map(BigDecimal::new).toArray(BigDecimal[]::new);
        int[] higherThanExpected=new int[]{-1,-1};
        int conditionNum=2;
        BigDecimal simpleRuleProfitGapPrice=new BigDecimal("0.1");
        BigDecimal simpleRuleWinPercentage=new BigDecimal("102");
        BigDecimal simpleRuleLosePercentage=new BigDecimal("102");
        boolean stopLoss=false;
        SimpleRuleSimulator simulator = new SimpleRuleSimulator(
                symbols, "USD", initCash, beginTime, endTime, candlesticksMap,
                observationMinute, percentage, higherThanExpected, conditionNum,
                simpleRuleProfitGapPrice, simpleRuleWinPercentage, simpleRuleLosePercentage, stopLoss
        );
        String currentDir = Paths.get("").toAbsolutePath().toString();
        System.out.println("Current working directory: " + currentDir);
        simulator.run();
        System.out.println(symbols[0]+" Trade count: " + simulator.tradeCount);
    }
}
