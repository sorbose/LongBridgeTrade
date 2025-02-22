package io.github.sorbose.lbtrade.strategy;

import com.longport.quote.Candlestick;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;

public class LineStrategy implements RuleStrategy {

    @Override
    public boolean shouldBuy(String symbol, BigDecimal lastPrice, LocalDateTime now) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public boolean shouldSell(String symbol, BigDecimal buyingPrice, LocalDateTime buyingTime, BigDecimal lastPrice, LocalDateTime now, boolean stopLoss) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    /** 如果观测点K线的最高值全部位于现价与观测点连成的斜线（/）段的下方，返回true  */
    public boolean shouldBuy(Candlestick[] candlesticks, String symbol, BigDecimal lastPrice) {
        return false;
    }

    @Override
    public boolean shouldSell(Candlestick[] candlesticks, BigDecimal buyingPrice, OffsetDateTime buyingTime, String symbol, BigDecimal lastPrice, boolean stopLoss) {
        return false;
    }
}
