package io.github.sorbose.lbtrade.trade;

import com.longport.Config;
import com.longport.OpenApiException;
import com.longport.trade.*;
import io.github.sorbose.lbtrade.TradeMain;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public class AssetManager {
    private static final Config config= TradeMain.config;
    private static final TradeContext context= TradeMain.tradeContext;
    private AccountBalance balanceUsd;
    private StockPosition[] positions;

    private AccountBalance pullBalance(String currency) {
        try{
            AccountBalance[] accountBalances = context.getAccountBalance(currency).get(30, TimeUnit.SECONDS);
            return accountBalances[0];
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public AccountBalance getBalanceUSD() {
        balanceUsd = pullBalance("USD");
        return balanceUsd;
    }


    public CompletableFuture<StockPositionsResponse> pullStockPositions(String... symbols) throws OpenApiException {
            return context.getStockPositions(new GetStockPositionsOptions().setSymbols(symbols));
    }

    private StockPosition[] getPositions(String... symbols) throws OpenApiException, ExecutionException, InterruptedException {
        return pullStockPositions(symbols).get().getChannels()[0].getPositions();
    }


    public static void main(String[] args) throws OpenApiException, ExecutionException, InterruptedException {
        System.out.println(LocalDateTime.now());
        AssetManager assetManager = new AssetManager();
        System.out.println(assetManager.getBalanceUSD());
        StockPosition[] res= assetManager.getPositions("YINN.US", "YANG.US");
        CompletableFuture<Void> positionsFuture = assetManager.pullStockPositions("YINN.US").thenAcceptAsync(
                positions -> {
                    System.out.println("positions = " + positions);
                });
        for(long i=0;i<1e10;i++);
    }
}
