package com.jeremy.lsm.codec;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Objects;
import java.util.zip.CRC32;


public final class RecordCodec {

    public static final int HEADER_SIZE = 4 + 8 + 1 + 4 + 4; // 21 bytes
    public static final byte TOMBSTONE_FALSE = 0x00;
    public static final byte TOMBSTONE_TRUE  = 0x01;

    private RecordCodec() {}

    
    public static ByteBuffer encode(Record record) {
        Objects.requireNonNull(record, "record cannot be null");
        int totalSize = HEADER_SIZE + record.key().length + record.value().length;
        ByteBuffer buf = ByteBuffer.allocate(totalSize).order(ByteOrder.BIG_ENDIAN);
        buf.position(4); // Jump past 0-3 (put the CRC32 there last)
        buf.putLong(record.timestamp());
        buf.put(record.tombstone() ? TOMBSTONE_TRUE : TOMBSTONE_FALSE);
        buf.putInt(record.key().length);
        buf.putInt(record.value().length);
        buf.put(record.key());
        buf.put(record.value());
        // Compute CRC32 over bytes [4 .. totalLength) and write to index 0
        buf.position(4);
        CRC32 crc32 = new CRC32();
        crc32.update(buf);
        buf.putInt(0, (int) crc32.getValue());
        buf.rewind();
        return buf;
    }

    
    public static Record decode(ByteBuffer buf) {
        Objects.requireNonNull(buf, "buf cannot be null");
        if (buf.remaining() < HEADER_SIZE) {
            throw new CorruptRecordException("Buffer too small to contain a valid record");
        }
        buf.order(ByteOrder.BIG_ENDIAN);
        int startPosition = buf.position();
        int storedCrc32 = buf.getInt();
        long timestamp = buf.getLong();
        byte tombstone = buf.get();
        int keyLength = buf.getInt();
        int valLength = buf.getInt();
        if (keyLength < 0 || valLength < 0 || (long) keyLength + valLength > buf.remaining()) {
            throw new CorruptRecordException("Invalid key/value length or truncated record");
        }
        int payloadPosition = (HEADER_SIZE - 4) + keyLength + valLength; // position after CRC32
        ByteBuffer payloadSlice = buf.slice(startPosition + 4, payloadPosition); // slice for CRC32 calculation
        CRC32 crc32 = new CRC32();
        crc32.update(payloadSlice);
        if (crc32.getValue() != Integer.toUnsignedLong(storedCrc32)) {
            throw new CorruptRecordException("CRC32 checksum mismatch");
        }
        byte[] key = new byte[keyLength];
        buf.get(key);
        byte[] value = new byte[valLength];
        buf.get(value);
        if (tombstone != TOMBSTONE_FALSE && tombstone != TOMBSTONE_TRUE) {
            throw new CorruptRecordException("Invalid tombstone byte");
        }
        return new Record(timestamp, tombstone == TOMBSTONE_TRUE, key, value);
    }
}