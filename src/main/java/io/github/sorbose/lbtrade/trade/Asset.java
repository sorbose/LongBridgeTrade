package io.github.sorbose.lbtrade.trade;

import com.longport.Config;
import com.longport.OpenApiException;
import com.longport.trade.AccountBalance;
import com.longport.trade.GetStockPositionsOptions;
import com.longport.trade.StockPositionsResponse;
import com.longport.trade.TradeContext;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class Asset {
    private AccountBalance balanceUSD;
    private AccountBalance balanceHKD;

    private AccountBalance pullBalance(String currency) {
        try (Config config = Config.fromEnv();
             TradeContext context = TradeContext.create(config).get()
        ) {
            AccountBalance[] accountBalances = context.getAccountBalance(currency).get(30, TimeUnit.SECONDS);
            return accountBalances[0];
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public AccountBalance getBalanceUSD() {
        balanceUSD = pullBalance("USD");
        return balanceUSD;
    }

    public AccountBalance getBalanceHKD() {
        balanceHKD = pullBalance("HKD");
        return balanceHKD;
    }

    public StockPositionsResponse pullStockPositions(String... symbols) {
        try (Config config = Config.fromEnv();
             TradeContext context = TradeContext.create(config).get();
        ) {
            StockPositionsResponse response =  context.getStockPositions(
                    new GetStockPositionsOptions().setSymbols(symbols)).get(30,TimeUnit.SECONDS);
            return response;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    public static void main(String[] args) {
        System.out.println(LocalDateTime.now());
        Asset asset = new Asset();
        asset.pullStockPositions("YINN.US", "YANG.US");
    }
}
