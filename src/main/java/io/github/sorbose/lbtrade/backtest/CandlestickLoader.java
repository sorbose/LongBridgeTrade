package io.github.sorbose.lbtrade.backtest;

import com.longport.quote.Candlestick;
import io.github.sorbose.lbtrade.util.CSVDeserializer;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.function.Predicate;

public class CandlestickLoader {
    private List<Candlestick> fromCsv(Class<Candlestick> clazz, String inPath, Predicate<Candlestick> filter) {
        return CSVDeserializer.readObjectsFromCsv(clazz, inPath, filter);
    }
    public List<Candlestick> fromCsv(String inPath, Predicate<Candlestick> filter) {
        return fromCsv(Candlestick.class, inPath, filter);
    }
    public List<Candlestick> fromCsv(String inPath) {
        return fromCsv(Candlestick.class, inPath, null);
    }
    public List<Candlestick> fromCsv(String inPath, LocalDateTime begin, LocalDateTime end) {
        return fromCsv(Candlestick.class, inPath,
                c-> c.getTimestamp().toLocalDateTime().isBefore(end)
                        && c.getTimestamp().toLocalDateTime().isAfter(begin));
    }
    public static HashMap<String, String> symbolToPath=new HashMap<String, String>(){
        {
            put("TSLL", "data/TSLL.US2023-12-04T0000");
            put("TSLQ", "data/TSLQ.US2023-12-04T0000");
            put("YANG", "data/YANG.US2023-12-04T0000");
            put("YINN", "data/YINN.US2023-12-04T0000");
        }
    };

}
