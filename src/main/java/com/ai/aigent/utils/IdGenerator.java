package com.ai.aigent.utils;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * ID生成工具类
 * 提供多种ID生成策略，包含数组ID和UUID生成
 */
public class IdGenerator {

    // 字符集
    private static final String ALPHANUMERIC = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final String NUMERIC = "0123456789";
    private static final String HEX_CHARS = "0123456789abcdef";

    // 默认配置
    private final String prefix;
    private final String suffix;
    private final String separator;
    private final boolean includeTimestamp;
    private final int randomLength;

    // 序列号生成器（按命名空间存储）
    private final Map<String, AtomicLong> sequences = new HashMap<>();

    // 雪花算法相关
    private final long workerId;
    private final long datacenterId;
    private final long epoch = 1609459200000L; // 2021-01-01 00:00:00
    private long lastTimestamp = -1L;
    private long sequence = 0L;

    // 随机数生成器
    private static final SecureRandom secureRandom = new SecureRandom();

    /**
     * 私有构造方法
     */
    private IdGenerator(Builder builder) {
        this.prefix = builder.prefix;
        this.suffix = builder.suffix;
        this.separator = builder.separator;
        this.includeTimestamp = builder.includeTimestamp;
        this.randomLength = builder.randomLength;
        this.workerId = builder.workerId;
        this.datacenterId = builder.datacenterId;
    }

    /**
     * 构建器模式
     */
    public static class Builder {
        private String prefix = "";
        private String suffix = "";
        private String separator = "_";
        private boolean includeTimestamp = false;
        private int randomLength = 8;
        private long workerId = 0L;
        private long datacenterId = 0L;

        public Builder prefix(String prefix) {
            this.prefix = prefix;
            return this;
        }

        public Builder suffix(String suffix) {
            this.suffix = suffix;
            return this;
        }

        public Builder separator(String separator) {
            this.separator = separator;
            return this;
        }

        public Builder includeTimestamp(boolean includeTimestamp) {
            this.includeTimestamp = includeTimestamp;
            return this;
        }

        public Builder randomLength(int randomLength) {
            this.randomLength = randomLength;
            return this;
        }

        public Builder workerId(long workerId) {
            this.workerId = workerId;
            return this;
        }

        public Builder datacenterId(long datacenterId) {
            this.datacenterId = datacenterId;
            return this;
        }

        public IdGenerator build() {
            return new IdGenerator(this);
        }
    }

    /**
     * 生成标准UUID (版本4)
     * @return UUID字符串
     */
    public static String uuid() {
        return UUID.randomUUID().toString();
    }

    /**
     * 生成不带连字符的UUID
     * @return 32位UUID字符串
     */
    public static String uuidWithoutHyphens() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 生成UUID数组
     * @param count 生成数量
     * @return UUID数组
     */
    public static String[] uuidArray(int count) {
        if (count <= 0) {
            return new String[0];
        }

        String[] uuids = new String[count];
        for (int i = 0; i < count; i++) {
            uuids[i] = uuid();
        }
        return uuids;
    }

    /**
     * 生成UUID列表
     * @param count 生成数量
     * @return UUID列表
     */
    public static List<String> uuidList(int count) {
        List<String> uuids = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            uuids.add(uuid());
        }
        return uuids;
    }

    /**
     * 生成短ID（字母数字组合）
     * @param length ID长度
     * @return 短ID
     */
    public String shortId(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            int index = secureRandom.nextInt(ALPHANUMERIC.length());
            sb.append(ALPHANUMERIC.charAt(index));
        }
        return formatId(sb.toString());
    }

    /**
     * 生成短ID（使用默认长度）
     * @return 短ID
     */
    public String shortId() {
        return shortId(randomLength);
    }

    /**
     * 生成数字ID
     * @param length 数字长度
     * @return 数字ID
     */
    public String numericId(int length) {
        StringBuilder sb = new StringBuilder(length);
        // 第一位不能是0
        sb.append(secureRandom.nextInt(9) + 1);

        for (int i = 1; i < length; i++) {
            int index = secureRandom.nextInt(NUMERIC.length());
            sb.append(NUMERIC.charAt(index));
        }
        return formatId(sb.toString());
    }

    /**
     * 生成时间戳ID
     * @return 时间戳ID
     */
    public String timestampId() {
        String timestamp = Long.toString(System.currentTimeMillis(), 36);
        String random = generateRandomString(randomLength);
        return formatId(timestamp + separator + random);
    }

    /**
     * 雪花算法生成分布式ID
     * @return 雪花算法ID
     */
    public synchronized long snowflakeId() {
        long timestamp = System.currentTimeMillis();

        if (timestamp < lastTimestamp) {
            throw new RuntimeException("Clock moved backwards. Refusing to generate id");
        }

        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) & 4095;
            if (sequence == 0) {
                timestamp = tilNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0;
        }

        lastTimestamp = timestamp;

        return ((timestamp - epoch) << 22) |
                (datacenterId << 17) |
                (workerId << 12) |
                sequence;
    }

    /**
     * 等待下一毫秒
     */
    private long tilNextMillis(long lastTimestamp) {
        long timestamp = System.currentTimeMillis();
        while (timestamp <= lastTimestamp) {
            timestamp = System.currentTimeMillis();
        }
        return timestamp;
    }

    /**
     * 生成带时间格式的ID
     * @param pattern 时间格式
     * @return 时间格式ID
     */
    public String timePatternId(String pattern) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
        String timeStr = LocalDateTime.now().format(formatter);
        String random = generateRandomString(randomLength);
        return formatId(timeStr + separator + random);
    }

    /**
     * 生成序列ID
     * @param namespace 命名空间
     * @return 序列ID
     */
    public long sequenceId(String namespace) {
        AtomicLong counter = sequences.computeIfAbsent(namespace, k -> new AtomicLong(0));
        return counter.incrementAndGet();
    }

    /**
     * 生成序列ID（带格式化）
     * @param namespace 命名空间
     * @param length 序列号长度（补零）
     * @return 格式化序列ID
     */
    public String formattedSequenceId(String namespace, int length) {
        long seq = sequenceId(namespace);
        String seqStr = String.format("%0" + length + "d", seq);
        return formatId(seqStr);
    }

    /**
     * 生成数组ID（批量生成同类型ID）
     * @param count 数量
     * @param type ID类型：short, numeric, timestamp, snowflake
     * @return ID数组
     */
    public String[] idArray(int count, String type) {
        if (count <= 0) {
            return new String[0];
        }

        Set<String> idSet = new LinkedHashSet<>(count);
        while (idSet.size() < count) {
            switch (type.toLowerCase()) {
                case "numeric":
                    idSet.add(numericId(randomLength));
                    break;
                case "timestamp":
                    idSet.add(timestampId());
                    break;
                case "snowflake":
                    idSet.add(String.valueOf(snowflakeId()));
                    break;
                case "short":
                default:
                    idSet.add(shortId(randomLength));
                    break;
            }
        }
        return idSet.toArray(new String[0]);
    }

    /**
     * 生成ID列表
     * @param count 数量
     * @param type ID类型
     * @return ID列表
     */
    public List<String> idList(int count, String type) {
        String[] array = idArray(count, type);
        return new ArrayList<>(Arrays.asList(array));
    }

    /**
     * 生成16进制ID
     * @param length 长度
     * @return 16进制ID
     */
    public String hexId(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            int index = secureRandom.nextInt(HEX_CHARS.length());
            sb.append(HEX_CHARS.charAt(index));
        }
        return formatId(sb.toString());
    }

    /**
     * 生成带校验位的ID
     * @param length 基础长度
     * @return 带校验位的ID
     */
    public String idWithChecksum(int length) {
        String baseId = numericId(length - 1);
        char checksum = calculateChecksum(baseId);
        return formatId(baseId + checksum);
    }

    /**
     * 计算校验位（简单实现）
     */
    private char calculateChecksum(String str) {
        int sum = 0;
        for (char c : str.toCharArray()) {
            sum += Character.getNumericValue(c);
        }
        return NUMERIC.charAt(sum % 10);
    }

    /**
     * 格式化ID（添加前缀、后缀、时间戳）
     */
    private String formatId(String id) {
        StringBuilder sb = new StringBuilder();

        if (includeTimestamp) {
            sb.append(Instant.now().toEpochMilli()).append(separator);
        }

        if (!prefix.isEmpty()) {
            sb.append(prefix).append(separator);
        }

        sb.append(id);

        if (!suffix.isEmpty()) {
            sb.append(separator).append(suffix);
        }

        return sb.toString();
    }

    /**
     * 生成随机字符串
     */
    private String generateRandomString(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            int index = secureRandom.nextInt(ALPHANUMERIC.length());
            sb.append(ALPHANUMERIC.charAt(index));
        }
        return sb.toString();
    }

    // ========== 静态工厂方法 ==========

    /**
     * 创建默认生成器
     */
    public static IdGenerator createDefault() {
        return new Builder().build();
    }

    /**
     * 创建带前缀的生成器
     */
    public static IdGenerator createWithPrefix(String prefix) {
        return new Builder().prefix(prefix).build();
    }

    /**
     * 创建带时间戳的生成器
     */
    public static IdGenerator createWithTimestamp() {
        return new Builder().includeTimestamp(true).build();
    }

    /**
     * 创建雪花算法生成器
     */
    public static IdGenerator createSnowflake(long workerId, long datacenterId) {
        return new Builder()
                .workerId(workerId)
                .datacenterId(datacenterId)
                .build();
    }
}