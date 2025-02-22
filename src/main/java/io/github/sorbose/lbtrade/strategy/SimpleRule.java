package io.github.sorbose.lbtrade.strategy;

import com.longport.quote.Candlestick;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

public class SimpleRule implements RuleStrategy {
    /**实时1分钟K线，使用candlestick.high作为这一分钟的价格，
     *             至少覆盖observationMinute中最久远的时间点*/
    HashMap<String, List<Candlestick>> candlesticksMap;
    /**观测点*/
    int[] observationMinute;
    /** 与观测点对应，97表示是原价的97%，价格差3%*/
    BigDecimal[] percentage;
    /**为1表示，实际价格比预期（observationMinute[i]）更高，才算符合条件*/
    int[] higherThanExpected;
    /**至少应该有 `conditionNum`个观测点达到预期标准*/
    int conditionNum;
    /**价格差，可正可负，只有当每股现价大于buyingPrice+gapPrice时才被视为盈利*/
    BigDecimal gapPrice;
    /**97表示在盈利状态下允许有3%的回撤*/
    BigDecimal winPercentage;
    /**98表示在亏损状态下允许有2%的回撤*/
    BigDecimal losePercentage;

    public SimpleRule(HashMap<String, List<Candlestick>> candlesticksMap, int[] observationMinute, BigDecimal[] percentage, int[] higherThanExpected,
                      int conditionNum, BigDecimal gapPrice, BigDecimal winPercentage, BigDecimal losePercentage) {
        this.candlesticksMap = candlesticksMap;
        this.observationMinute = observationMinute;
        this.percentage = percentage;
        this.conditionNum = conditionNum;
        this.gapPrice = gapPrice;
        this.winPercentage = winPercentage;
        this.losePercentage = losePercentage;
        this.higherThanExpected = higherThanExpected;
        for(List<Candlestick> list : candlesticksMap.values()) {
            list.sort((a, b)->a.getTimestamp().compareTo(b.getTimestamp()));
        }
    }

    /**
     * 使用简单的浮动止盈止损策略决定是否应该买入股票。希望股票现在的价格，
     * 和observationMinute[i]分钟之前相比，下降了dropPercentage[i]
     * @param symbol 股票标识
     * @param lastPrice 股票实时现价
     * @param now 与lastPrice相对应的时间
     * @return true表示现在应该买入股票
     */
    @Override
    public boolean shouldBuy(String symbol, BigDecimal lastPrice, LocalDateTime now) {
//        return Math.random()<0.005;
        List<Candlestick> candlesticks = candlesticksMap.get(symbol);
        int nowIndex = Collections.binarySearch(candlesticks, null, (a, b) -> a.getTimestamp().toLocalDateTime().compareTo(now));
        if (nowIndex < 0) {
            nowIndex = -nowIndex - 2;
        }
        int realConditionNum = 0;
        for(int i=0;i<observationMinute.length;i++){
            if(lastPrice.compareTo(getBuyBoundPrice(candlesticks, nowIndex, i))*higherThanExpected[i] > 0){
                realConditionNum++;
            }
        }
        return realConditionNum >= conditionNum;
    }

    public BigDecimal getBuyBoundPrice(List<Candlestick> candlesticks, int nowIndex, int i) {
        Candlestick nowCandlestick = candlesticks.get(nowIndex - observationMinute[i]);
        BigDecimal avgPrice = nowCandlestick.getHigh().add(nowCandlestick.getLow()).add(nowCandlestick.getClose()).add(nowCandlestick.getOpen()).divide(BigDecimal.valueOf(4), 3, RoundingMode.HALF_UP);
        return avgPrice.multiply(percentage[i]).divide(BigDecimal.valueOf(100), 3, RoundingMode.HALF_UP);
    }

    /**
     * @param candlesticks 时间升序（从旧到新）的最新K线数据，请传入尽量少的数据，以减少计算量
     * @
     */
    public boolean shouldBuy(Candlestick[] candlesticks, String symbol, BigDecimal lastPrice) {
        int realConditionNum = 0;
        int length = candlesticks.length;
        for(int i=0;i<observationMinute.length;i++){
            if(lastPrice.compareTo(candlesticks[length-observationMinute[i]].getHigh().
                    multiply(percentage[i]).divide(BigDecimal.valueOf(100),3,RoundingMode.HALF_UP))*higherThanExpected[i] > 0){
                realConditionNum++;
            }
        }
        return realConditionNum >= conditionNum;
    }

    /**
     * 使用简单的浮动止盈止损策略决定是否应该卖出股票，记basePrice为buyingPrice+gapPrice，作为盈亏分界线
     * 当股票的现价lastPrice大于basePrice时，记为盈利，否则为亏损
     * 在盈利状态下，如果lastPrice小于highestPrice乘winPercentage，或者在亏损状态下，lastPrice小于highestPrice乘losePercentage
     * 则应该卖出股票止损
     * @param buyingPrice 股票的成本价
     * @param symbol 股票标识
     * @param lastPrice 股票实时现价
     * @param buyingTime 股票最近一次购买时间
     * @param now 与lastPrice相对应的时间
     * @return true表示应该卖出股票
     */
    @Override
    public boolean shouldSell(String symbol, BigDecimal buyingPrice, LocalDateTime buyingTime, BigDecimal lastPrice, LocalDateTime now, boolean stopLoss) {
//        return Math.random()<0.02;
        boolean isProfit = lastPrice.compareTo(buyingPrice.add(gapPrice)) > 0;
        List<Candlestick> candlesticks = candlesticksMap.get(symbol);
        BigDecimal highestPrice =  candlesticks.stream().filter(c->{
            LocalDateTime t=c.getTimestamp().toLocalDateTime();
            return t.isBefore(now)&& (t.isAfter(buyingTime)||t.isEqual(buyingTime));
        }).max(Comparator.comparing(Candlestick::getHigh)).get().getHigh();

        int stopLossIndex = stopLoss?1:-1;  // 1表示止损，-1表示止盈
        BigDecimal standardPrice = stopLoss?highestPrice:buyingPrice; // 止损状态下，用最高价；止盈状态下，用买入价
        // 计算盈利状态下的止损界限
        BigDecimal profitThreshold = standardPrice.multiply(
                winPercentage.divide(new BigDecimal("100"), 3, RoundingMode.HALF_UP));
        // 计算亏损状态下的止损界限
        BigDecimal lossThreshold = standardPrice.multiply(
                losePercentage.divide(new BigDecimal("100"), 3, RoundingMode.HALF_UP));

        // 如果是盈利状态，检查是否满足止损条件
        if (isProfit) {
            return lastPrice.compareTo(profitThreshold)*stopLossIndex < 0;  // 如果现价低于盈利止损阈值，则卖出
        } else {
            // 如果是亏损状态，检查是否满足止损条件
            return lastPrice.compareTo(lossThreshold)*stopLossIndex < 0;
        }
    }

    /**
     * @param candlesticks 时间升序（从旧到新）的最新K线数据，请传入尽量少的数据，以减少计算量
     */
    public boolean shouldSell(Candlestick[] candlesticks, BigDecimal buyingPrice, OffsetDateTime buyingTimeUTC, String symbol, BigDecimal lastPrice, boolean stopLoss){
        boolean isProfit = lastPrice.compareTo(buyingPrice.add(gapPrice)) > 0;
        BigDecimal highestPrice = BigDecimal.ZERO;
        for(Candlestick c : candlesticks){
            if(c.getTimestamp().isAfter(buyingTimeUTC)
                    &&c.getHigh().compareTo(highestPrice)>0){
                highestPrice = c.getHigh();
            }
        }

        int stopLossIndex = stopLoss?1:-1;  // 1表示止损，-1表示止盈
        BigDecimal standardPrice = stopLoss?highestPrice:buyingPrice; // 止损状态下，用最高价；止盈状态下，用买入价
        // 计算盈利状态下的止损界限
        BigDecimal profitThreshold = standardPrice.multiply(
                winPercentage.divide(new BigDecimal("100"), 3, RoundingMode.HALF_UP));
        // 计算亏损状态下的止损界限
        BigDecimal lossThreshold = standardPrice.multiply(
                losePercentage.divide(new BigDecimal("100"), 3, RoundingMode.HALF_UP));
        // 如果是盈利状态，检查是否满足止损条件
        if (isProfit) {
            return lastPrice.compareTo(profitThreshold)*stopLossIndex < 0;  // 如果现价低于盈利止损阈值，则卖出
        } else {
            // 如果是亏损状态，检查是否满足止损条件
            return lastPrice.compareTo(lossThreshold)*stopLossIndex < 0;
        }
    }
}
