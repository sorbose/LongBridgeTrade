package io.github.sorbose.lbtrade.util;

import java.util.List;

public class CSVSerializer {
    public static <T> void writeObjectsToCsv(List<T> objects, String outPath){
        if (objects == null || objects.isEmpty()){
            System.err.println("No objects to write to");
        }
    }
}
