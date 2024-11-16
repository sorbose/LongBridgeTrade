package io.github.sorbose.lbtrade.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.List;

public class CSVSerializer {
    public static final Logger logger = LogManager.getLogger(CSVSerializer.class);

    public static <T> void writeObjectsToCsv(List<T> objects, String outPath) {
        if (objects == null || objects.isEmpty()) {
            logger.warn("No objects to write to");
            return;
        }
        Class<?> clazz = objects.get(0).getClass();
        Field[] fields = clazz.getDeclaredFields();
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outPath))) {
            // 写入 CSV 标题行
            writeHeader(fields, writer);
            // 写入对象数据行
            for (T obj : objects) {
                writeObjectData(obj, fields, writer);
            }
            logger.info("Data written to CSV file: {}", outPath);

        } catch (IOException | IllegalAccessException e) {
            logger.error("Error writing to CSV file: {}", e.getMessage());
        }
    }

    private static void writeHeader(Field[] fields, BufferedWriter writer) throws IOException {
        StringBuilder header = new StringBuilder();
        for (int i = 0; i < fields.length; i++) {
            header.append(fields[i].getName());
            if (i < fields.length - 1) {
                header.append(",");
            }
        }
        writer.write(header.toString());
        writer.newLine();
    }
    // 写入对象数据行
    private static <T> void writeObjectData(T obj, Field[] fields, BufferedWriter writer)
            throws IOException, IllegalAccessException {
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < fields.length; i++) {
            fields[i].setAccessible(true); // 访问私有字段
            Object value = fields[i].get(obj);
            line.append(value != null ? value.toString() : "");
            if (i < fields.length - 1) {
                line.append(",");
            }
        }
        writer.write(line.toString());
        writer.newLine();
    }
}
