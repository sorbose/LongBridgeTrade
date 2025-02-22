package io.github.sorbose.lbtrade.backtest;

import com.longport.Market;
import com.longport.quote.Candlestick;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public abstract class AbstractSimulator {
    protected BigDecimal initCash;
    protected BigDecimal cash;
    protected BigDecimal assetAmount;
    protected String currency;
    protected String[] symbols;

    protected List<TradeRecord> tradeRecords;
    protected LocalDateTime beginTime;
    protected LocalDateTime endTime;
    protected HashMap<String, MyStockPosition> stockPositions;
    HashMap<String, List<Candlestick>> candlesticksMap;

    public AbstractSimulator(String[] symbols, String currency, BigDecimal initCash, LocalDateTime beginTime, LocalDateTime endTime, HashMap<String, List<Candlestick>> candlesticksMap) {
        this.endTime = endTime;
        this.beginTime = beginTime;
        this.symbols = symbols;
        this.currency = currency;
        this.initCash = initCash;
        cash = assetAmount = initCash;
        tradeRecords = new ArrayList<>();
        stockPositions = new HashMap<>();
        this.candlesticksMap = candlesticksMap;
        for(String symbol : symbols) {
            stockPositions.put(symbol,
                    new MyStockPosition(symbol, BigDecimal.ZERO, currency, BigDecimal.ZERO));
        }
    }
    public static class TradeRecord{
        LocalDateTime time;
        BigDecimal price;
        BigDecimal quantity;
        public enum Direction{BUY, SELL}
        Direction direction;
        String symbol;
        public TradeRecord(LocalDateTime time, BigDecimal price, BigDecimal quantity, Direction direction, String symbol) {
            this.time = time;
            this.price = price;
            this.quantity = quantity;
            this.direction = direction;
            this.symbol = symbol;
        }
    }
    public static class MyStockPosition{
        final String symbol;
        BigDecimal quantity;
        final String currency;
        /**平均买入成本价格，不算手续费*/
        BigDecimal costPrice;
        public MyStockPosition(String symbol, BigDecimal quantity, String currency, BigDecimal costPrice) {
            this.symbol = symbol;
            this.quantity = quantity;
            this.currency = currency;
            this.costPrice = costPrice;
        }
    }

    abstract protected BigDecimal getBuyFee(Market market, BigDecimal estTotalPrice);
    abstract protected BigDecimal getSellFee(Market market, BigDecimal estTotalPrice);
    abstract public void run();
    // abstract protected void sell(String symbol, BigDecimal lastPrice, LocalDateTime localDateTime);
    protected HashMap<String, MyStockPosition> getstockPositions() {
        return stockPositions;
    }
    protected int getTotalStocksAmount(){
        return stockPositions.values().stream().map(msp->msp.quantity).reduce(BigDecimal.ZERO, BigDecimal::add).intValue();
    }
    protected List<TradeRecord> getTradeRecords() {
        return tradeRecords;
    }
    protected LocalDateTime getStockRecentBuyTime(String symbol){
        for(int i=tradeRecords.size()-1;i>=0;i--){
            if(tradeRecords.get(i).symbol.equals(symbol) && tradeRecords.get(i).direction== TradeRecord.Direction.BUY){
                return tradeRecords.get(i).time;
            }
        }
        return null;
    }
}
