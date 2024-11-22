package io.github.sorbose.lbtrade.backtest;

import com.longport.Market;
import com.longport.quote.Candlestick;
import io.github.sorbose.lbtrade.strategy.SimpleRule;

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
    private static final BigDecimal fixedBuyFee = new BigDecimal("1.01");
    private static final BigDecimal fixedSellFee = new BigDecimal("1.01");

    public SimpleRuleSimulator(String[] symbols, String currency, BigDecimal initCash, LocalDateTime beginTime, LocalDateTime endTime, HashMap<String, List<Candlestick>> candlesticksMap,
                               int[] observationMinute, BigDecimal[] percentage, int[] highThanExpected, int conditionNum, BigDecimal gapPrice, BigDecimal winPercentage, BigDecimal losePercentage) {
        super(symbols, currency, initCash, beginTime, endTime, candlesticksMap);
        this.observationMinute = observationMinute;
        this.percentage = percentage;
        this.highThanExpected = highThanExpected;
        this.conditionNum = conditionNum;
        this.gapPrice = gapPrice;
        this.winPercentage = winPercentage;
        this.losePercentage = losePercentage;
    }

    @Override
    protected BigDecimal getBuyFee(Market market, BigDecimal quantity, BigDecimal unitCost) {
        return fixedBuyFee.add(quantity.multiply(unitCost).multiply(new BigDecimal("0.0012")));
    }

    @Override
    protected BigDecimal getSellFee(Market market, BigDecimal quantity, BigDecimal unitCost) {
        return fixedSellFee.add(quantity.multiply(unitCost).multiply(new BigDecimal("0.0012")));
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
                boolean shouldSell = strategy.shouldSell(symbols[0], buyingPrice, buyTime, lastPrice, now);
                if (shouldSell) {
                    BigDecimal quantity = stockPositions.get(symbols[0]).quantity;
                    // TODO: 模拟时卖出价格欠考虑，不应该用最高价
                    sellAndPrint(quantity, candlestick.getClose(),
                             now, buyingPrice);
                    continue;
                }
            }
            BigDecimal lastPrice = candlestick.getLow();
            boolean shouldBuy = strategy.shouldBuy(symbols[0], lastPrice, now);
            if (shouldBuy) {
                BigDecimal quantity = cash.divide(lastPrice, 0, RoundingMode.DOWN);
                boolean haveBought = buy(symbols[0], quantity,
                        candlestick.getClose(), now);
                if (haveBought) {
                    System.out.println("Buy " + symbols[0] + " at " + lastPrice + " with quantity " + quantity
                            + " at " + now);
                    System.out.println("Balance: " + cash);
                }
            }
        }
        sellAndPrint(stockPositions.get(symbols[0]).quantity, candlesticks.get(candlesticks.size() - 1).getClose(), candlesticks.get(candlesticks.size() - 1).getTimestamp().toLocalDateTime(), stockPositions.get(symbols[0]).costPrice);
    }

    private void sellAndPrint(BigDecimal quantity, BigDecimal lastPrice, LocalDateTime now, BigDecimal buyingPrice) {
        sell(symbols[0], quantity, lastPrice, now);
        System.out.println("Sell " + symbols[0] + " at " + lastPrice + " at " + now +
                " with profit " + (lastPrice.subtract(buyingPrice)).multiply(quantity));
        System.out.println("Balance: " + cash);
    }

    private boolean buy(String symbol, BigDecimal quantity, BigDecimal lastPrice, LocalDateTime now) {
        if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        MyStockPosition stockPosition = stockPositions.get(symbol);
        BigDecimal cost = quantity.multiply(lastPrice);
        BigDecimal buyFee = getBuyFee(Market.US, quantity, lastPrice);
        if (cash.compareTo(cost.add(buyFee)) < 0) {
            return false;
        }
        cash = cash.subtract(cost).subtract(buyFee);
        stockPosition.costPrice = stockPosition.costPrice.multiply(stockPosition.quantity)
                .add(lastPrice.multiply(quantity)).divide(stockPosition.quantity.add(quantity), 3, RoundingMode.HALF_UP);
        stockPosition.quantity = stockPosition.quantity.add(quantity);

        tradeRecords.add(new TradeRecord(now, lastPrice, quantity, TradeRecord.Direction.BUY, symbol));
        return true;
    }

    private boolean sell(String symbol, BigDecimal quantity, BigDecimal lastPrice, LocalDateTime localDateTime) {
        if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        MyStockPosition stockPosition = stockPositions.get(symbol);
        if (stockPosition.quantity.compareTo(quantity) < 0) {
            return false;
        }
        if (quantity.compareTo(stockPosition.quantity) == 0) {
            stockPosition.costPrice = BigDecimal.ZERO;
        } else {
            stockPosition.costPrice = stockPosition.costPrice.multiply(stockPosition.quantity)
                    .subtract(lastPrice.multiply(quantity)).divide(stockPosition.quantity.subtract(quantity), 3, RoundingMode.HALF_UP);
        }

        stockPosition.quantity = stockPosition.quantity.subtract(quantity);
        BigDecimal sellAmount = quantity.multiply(lastPrice);
        BigDecimal sellFee = getSellFee(Market.US, quantity, lastPrice);
        cash = cash.add(sellAmount).subtract(sellFee);
        tradeRecords.add(new TradeRecord(localDateTime, lastPrice, quantity, TradeRecord.Direction.SELL, symbol));
        return true;
    }

    public static void main(String[] args) {
        String[] symbols = new String[]{"TSLL"};
        String[] inPaths = Arrays.stream(symbols).map(s -> CandlestickLoader.symbolToPath.get(s)).toArray(String[]::new);
        CandlestickLoader candlestickLoader = new CandlestickLoader();
        HashMap<String, List<Candlestick>> candlesticksMap =
                IntStream.range(0, symbols.length).boxed().collect(Collectors.toMap(
                        i -> symbols[i],
                        i -> {
                            System.out.println("Loading data for symbol: " + symbols[i]);
                            List<Candlestick> candlesticks = candlestickLoader.fromCsv(
                                    CandlestickLoader.symbolToPath.get(symbols[i]),
                                    LocalDateTime.of(2024, 11, 1, 0, 0),
                                    LocalDateTime.now()
                            );
                            System.out.println("Loaded " + candlesticks.size() + " candlesticks for symbol: " + symbols[i]);
                            return candlesticks;
                        },
                        (existing, replacement) -> existing,  // 处理重复键时选择现有的值
                        HashMap::new  // 使用 HashMap
                ));
        SimpleRuleSimulator simulator = new SimpleRuleSimulator(
                symbols,
                "USD",
                new BigDecimal(2000),
                LocalDateTime.of(2024, 11, 15, 0, 0),
                LocalDateTime.now(),
                candlesticksMap,
                new int[]{25, 5},
                new BigDecimal[]{new BigDecimal("98.75"), new BigDecimal("99.25")},
                new int[]{-1, 1},
                2,
                new BigDecimal("0.1"),
                new BigDecimal("99.5"),
                new BigDecimal("99.5")
        );
        String currentDir = Paths.get("").toAbsolutePath().toString();
        System.out.println("Current working directory: " + currentDir);
        simulator.run();
    }
}
