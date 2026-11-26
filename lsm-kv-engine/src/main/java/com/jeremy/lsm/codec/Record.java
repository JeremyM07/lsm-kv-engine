package com.jeremy.lsm.codec;

import java.util.Objects;

public record Record(
    long timestamp,
    boolean tombstone,
    byte[] key,
    byte[] value
) {
    public Record {
        Objects.requireNonNull(key, "key cannot be null");
        Objects.requireNonNull(value, "value cannot be null");
        if (tombstone && value.length != 0) {
            throw new IllegalArgumentException("value must be empty for tombstone records");
        }
    }
    // Helper factory methods you can expose:

    // public static Record put(byte[] key, byte[] value, long timestamp)
    public static Record put(byte[] key, byte[] value, long timestamp) {
        Objects.requireNonNull(key, "key cannot be null");
        Objects.requireNonNull(value, "value cannot be null");
        if (value.length == 0) {
            throw new IllegalArgumentException("value cannot be empty for put records");
        }
        return new Record(timestamp, false, key, value);
    }

    // public static Record delete(byte[] key, long timestamp)
    public static Record delete(byte[] key, long timestamp) {
        Objects.requireNonNull(key, "key cannot be null");
        return new Record(timestamp, true, key, new byte[0]);
    }
}