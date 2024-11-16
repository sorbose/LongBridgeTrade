package io.github.sorbose.lbtrade.strategy;

import com.longport.quote.Candlestick;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

public class SimpleRule {
    /**
     * 使用简单的浮动止盈止损策略决定是否应该买入股票。希望股票现在的价格，
     * 和observationMinute[i]分钟之前相比，下降了dropPercentage[i]
     * @param data 实时1分钟K线，使用candlestick.high作为这一分钟的价格，
     *             至少覆盖observationMinute中最久远的时间点
     * @param lastPrice 股票实时现价
     * @param observationMinute 观测点
     * @param percentage 97表示是原价的97%，下降了3%
     * @param conditionNum 至少应该有 `conditionNum`个观测点达到预期下降标准
     * @return true表示现在应该买入股票
     */
    boolean shouldBuy(List<Candlestick> data, BigDecimal lastPrice,
                      int[] observationMinute, BigDecimal[] percentage, int conditionNum){
        data.sort((a,b)->b.getTimestamp().compareTo(a.getTimestamp()));
        int realConditionNum = 0;
        for(int i=0;i<observationMinute.length;i++){
            if(lastPrice.compareTo(data.get(observationMinute[i]).getHigh().multiply(percentage[i])) < 0){
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
     * @param highestPrice 最近一次买入股票后的最高价
     * @param lastPrice 股票的现价
     * @param gapPrice 价格差，可正可负，只有当股票现价大于buyingPrice+gapPrice时才被视为盈利
     * @param winPercentage 97表示在盈利状态下允许有3%的回撤
     * @param losePercentage 98表示在亏损状态下允许有2%的回撤
     * @return true表示应该卖出股票
     */
    boolean shouldSell(BigDecimal buyingPrice, BigDecimal highestPrice, BigDecimal lastPrice,
                       BigDecimal gapPrice, BigDecimal winPercentage, BigDecimal losePercentage){
        BigDecimal basePrice = buyingPrice.add(gapPrice);
        boolean isProfit = lastPrice.compareTo(basePrice) > 0;

        // 计算盈利状态下的止损界限
        BigDecimal profitThreshold = highestPrice.multiply(
                winPercentage.divide(new BigDecimal("100"), 3, RoundingMode.HALF_UP));
        // 计算亏损状态下的止损界限
        BigDecimal lossThreshold = highestPrice.multiply(
                losePercentage.divide(new BigDecimal("100"), 3, RoundingMode.HALF_UP));

        // 如果是盈利状态，检查是否满足止损条件
        if (isProfit) {
            return lastPrice.compareTo(profitThreshold) < 0;  // 如果现价低于盈利止损阈值，则卖出
        } else {
            // 如果是亏损状态，检查是否满足止损条件
            return lastPrice.compareTo(lossThreshold) < 0;
        }
    }
}
