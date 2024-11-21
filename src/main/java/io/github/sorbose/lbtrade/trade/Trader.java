package io.github.sorbose.lbtrade.trade;

import com.longport.Config;
import com.longport.OpenApiException;
import com.longport.trade.*;
import io.github.sorbose.lbtrade.TradeMain;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.concurrent.*;

public class Trader {
    private static final Config config= TradeMain.config;
    private static final TradeContext context= TradeMain.tradeContext;
    private final ScheduledExecutorService scheduler = new ScheduledThreadPoolExecutor(1);
    private static final Logger logger = LogManager.getLogger(Trader.class);
    public final int expireSecond;
    public final int timeoutSecond;
    public final BigDecimal cashBuyAvailableRatio;
    public final BigDecimal marginBuyAvailableRatio;
    public Trader(int expireSecond, int timeoutSecond, BigDecimal cashBuyAvailableRatio, BigDecimal marginBuyAvailableRatio) {
        this.expireSecond = expireSecond;
        this.timeoutSecond = timeoutSecond;
        this.cashBuyAvailableRatio = cashBuyAvailableRatio;
        this.marginBuyAvailableRatio = marginBuyAvailableRatio;

    }
    public static void handleException(Exception e, String msg){
        if (e instanceof OpenApiException){
            OpenApiException oe = (OpenApiException) e;
            logger.error("An error occurred: {} {}", msg, oe.getMessage());
        }else {
            logger.error("An error occurred: {} {}", msg, e.getMessage());
        }
    }

    private String submitOrderLOReally(String symbol, BigDecimal quantity, BigDecimal price, OrderSide side) throws Exception {
        SubmitOrderOptions orderOption = new SubmitOrderOptions(symbol, OrderType.LO, side, quantity, TimeInForceType.GoodTilCanceled);
        orderOption.setSubmittedPrice(price);
        orderOption.setOutsideRth(OutsideRTH.AnyTime);
        SubmitOrderResponse response = context.submitOrder(orderOption).get(timeoutSecond, TimeUnit.SECONDS);
        scheduleOrderCancellation(response.getOrderId());
        return response.getOrderId();
    }
    public String submitOrderLO(String symbol, int quantity, BigDecimal price, OrderSide side) throws Exception {
        price=price.setScale(2, RoundingMode.HALF_EVEN);
        return submitOrderLOReally(symbol, BigDecimal.valueOf(quantity), price, side);
    }

    public CompletableFuture<String> submitOrderLOFuture(String symbol, int quantity, BigDecimal price, OrderSide side) {
        BigDecimal submittedPrice=price.setScale(2, RoundingMode.HALF_EVEN);
        SubmitOrderOptions orderOption = new SubmitOrderOptions(symbol, OrderType.LO, side, BigDecimal.valueOf(quantity), TimeInForceType.Day);
        orderOption.setSubmittedPrice(submittedPrice);
        orderOption.setOutsideRth(OutsideRTH.AnyTime);
        try {
            return context.submitOrder(orderOption).thenApplyAsync(response -> {
                try {
                    logger.info("Order " + response.getOrderId() + "symbol: " + symbol + " quantity: " + quantity + " price: " + submittedPrice + " side: " + side + " was submitted.");
                    return response.getOrderId();
                } catch (Exception e) {
                    handleException(e, "Failed to schedule order cancellation.");
                    return null;
                }
            });
        } catch (OpenApiException e) {
            logger.error("submitOrder error : {}, symbol {}, quantity {}, price {}, side {}", e.getMessage(), symbol, quantity, price, side);
            throw new RuntimeException(e);
        }
    }

    public OrderDetail pullOrderDetail(String orderId) throws Exception {
        OrderDetail response = context.getOrderDetail(orderId).get(timeoutSecond, TimeUnit.SECONDS);
        return response;
    }
    public CompletableFuture<Void> cancelOrder(String orderId) throws Exception{
        return context.cancelOrder(orderId);
    }


    private ScheduledFuture<Boolean> scheduleOrderCancellation(String orderId) {
        return scheduler.schedule(() -> {
                try {
                    OrderStatus status = pullOrderDetail(orderId).getStatus();
                    if (status != OrderStatus.Filled) {
                        cancelOrder(orderId);
                        logger.info("Order " + orderId + " was cancelled after "+expireSecond+" seconds.");
                        return true;
                    }
                }catch (Exception e){
                    handleException(e, "Failed to cancel order " + orderId);
                }
                return false;
            }, expireSecond, TimeUnit.SECONDS);
    }

    private EstimateMaxPurchaseQuantityResponse pullEstMaxOrderQty(String symbol) throws Exception {
        return context.getEstimateMaxPurchaseQuantity(
                new EstimateMaxPurchaseQuantityOptions(symbol, OrderType.LO, OrderSide.Buy))
                .get(timeoutSecond, TimeUnit.SECONDS);
    }

    public CompletableFuture<EstimateMaxPurchaseQuantityResponse> pullEstMaxOrderQtyFuture(String symbol) throws Exception {
        return context.getEstimateMaxPurchaseQuantity(
                new EstimateMaxPurchaseQuantityOptions(symbol, OrderType.LO, OrderSide.Buy));
    }

    // TODO: 换成Asset来完成
    @Deprecated
    public BigDecimal pullSellMaxQuantity(String symbol) throws Exception {
        return context.getStockPositions(new GetStockPositionsOptions().setSymbols(new String[]{symbol}))
                .get(timeoutSecond, TimeUnit.SECONDS).getChannels()[0].getPositions()[0].getAvailableQuantity();
    }

    public CompletableFuture<BigDecimal> pullSellMaxQuantityFuture(String symbol) throws Exception {
        return context.getStockPositions(new GetStockPositionsOptions().setSymbols(new String[]{symbol}))
                .thenApplyAsync(response -> response.getChannels()[0].getPositions()[0].getAvailableQuantity());
    }

    public BigDecimal pullEstMarginMaxOrderQty(String symbol) throws Exception {
        return pullEstMaxOrderQty(symbol).getMarginMaxQty().multiply(marginBuyAvailableRatio).setScale(0, RoundingMode.FLOOR);
    }

    public static void main(String[] args) {
        Trader trader = new Trader(70,30, new BigDecimal("0.015"), new BigDecimal("0.015"));
        try {
            EstimateMaxPurchaseQuantityResponse res= trader.pullEstMaxOrderQty("TSLA.US");
            System.out.println(res);
            context.setOnOrderChange(System.out::println);
            context.subscribe(new TopicType[]{TopicType.Private});
        } catch (Exception e) {
            handleException(e, "Failed.");
        }

    }
}
