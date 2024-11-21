package io.github.sorbose.lbtrade.strategy;

import com.longport.quote.Candlestick;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public interface RuleStrategy {
    @Deprecated
    boolean shouldBuy(String symbol, BigDecimal lastPrice, LocalDateTime now);
    @Deprecated
    boolean shouldSell(String symbol, BigDecimal buyingPrice, LocalDateTime buyingTime, BigDecimal lastPrice, LocalDateTime now);

    boolean shouldBuy(Candlestick[] candlesticks, String symbol, BigDecimal lastPrice);
    boolean shouldSell(Candlestick[] candlesticks, BigDecimal buyingPrice, LocalDateTime buyingTime, String symbol, BigDecimal lastPrice);

}
