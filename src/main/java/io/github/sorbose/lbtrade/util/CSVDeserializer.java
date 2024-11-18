package io.github.sorbose.lbtrade.util;

import com.longport.quote.Candlestick;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public class CSVDeserializer {

    public static final Logger logger = LogManager.getLogger(CSVDeserializer.class);

    /**
     * 从CSV文件中读取数据并反序列化成对象列表，支持通过过滤器过滤数据
     * @param clazz 对象类型
     * @param inPath 输入CSV文件路径
     * @param filter 过滤器，过滤不需要的对象
     * @param <T> 对象类型
     * @return 反序列化后的对象列表
     */
    public static <T> List<T> readObjectsFromCsv(Class<T> clazz, String inPath, Predicate<T> filter) {
        List<T> objects = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(inPath))) {
            // 读取标题行
            String headerLine = reader.readLine();
            if (headerLine == null) {
                logger.warn("CSV file is empty.");
                return objects;
            }
            // 获取字段信息
            Field[] fields = clazz.getDeclaredFields();
            String[] headers = headerLine.split(",");

            // 逐行读取数据并映射到对象
            String line;
            while ((line = reader.readLine()) != null) {
                T obj = clazz.getDeclaredConstructor().newInstance(); // 创建对象实例
                String[] values = line.split(",");

                // 为每个字段赋值
                for (int i = 0; i < headers.length; i++) {
                    String fieldName = headers[i].trim();
                    for (Field field : fields) {
                        if (field.getName().equalsIgnoreCase(fieldName)) {
                            field.setAccessible(true); // 访问私有字段
                            String value = values[i].trim();
                            // 将字符串值转换为字段的类型
                            setFieldValue(field, obj, value);
                            break;
                        }
                    }
                }

                // 如果过滤器条件通过，则添加到结果列表
                if (filter == null || filter.test(obj)) {
                    objects.add(obj);
                }
            }
            logger.info("Data read from CSV file: {}", inPath);

        } catch (Exception e) {
            logger.error("Error reading from CSV file: {}", e.getMessage());
            throw new RuntimeException(e);
        }
        return objects;
    }

    /**
     * 设置字段的值，根据字段的类型进行类型转换
     * @param field 字段
     * @param obj 对象实例
     * @param value 字段值
     */
    private static void setFieldValue(Field field, Object obj, String value) throws IllegalAccessException {
        Class<?> fieldType = field.getType();
        if (fieldType == String.class) {
            field.set(obj, value);
        } else if (fieldType == int.class || fieldType == Integer.class) {
            field.set(obj, Integer.parseInt(value));
        } else if (fieldType == double.class || fieldType == Double.class) {
            field.set(obj, Double.parseDouble(value));
        } else if (fieldType == boolean.class || fieldType == Boolean.class) {
            field.set(obj, Boolean.parseBoolean(value));
        } else if (fieldType == long.class || fieldType == Long.class) {
            field.set(obj, Long.parseLong(value));
        } else if (fieldType == BigDecimal.class) {
            field.set(obj, new BigDecimal(value));
        } else if (fieldType == OffsetDateTime.class) {
            field.set(obj, OffsetDateTime.parse(value));
        }
        else {
            // 处理其他类型或抛出异常
            throw new IllegalArgumentException("Unsupported field type: " + fieldType);
        }
    }
}
