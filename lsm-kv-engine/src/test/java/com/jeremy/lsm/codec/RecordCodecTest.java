package com.jeremy.lsm.codec;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class RecordCodecTest {

    @Test
    void roundTripPutRecord() {
        // 1. Create a PUT Record with UTF-8 key and value bytes + a known timestamp
        // 2. Encode it into a ByteBuffer via RecordCodec.encode(...)
        // 3. Decode it back via RecordCodec.decode(...)
        // 4. Assert timestamp, tombstone == false, assertArrayEquals on key and value,
        //    and assert buf.remaining() == 0
        byte[] key = "user007".getBytes(StandardCharsets.UTF_8);
        byte[] value = "tom".getBytes(StandardCharsets.UTF_8);
        long timestamp = 1_700_000_000L;
        Record originalRecord = Record.put(key, value, timestamp);
        ByteBuffer encodedBuffer = RecordCodec.encode(originalRecord);
        Record decodedRecord = RecordCodec.decode(encodedBuffer);
        assertEquals(timestamp, decodedRecord.timestamp());
        assertFalse(decodedRecord.tombstone());
        assertArrayEquals(key, decodedRecord.key());
        assertArrayEquals(value, decodedRecord.value());
        assertEquals(0, encodedBuffer.remaining());
    }

    @Test
    void roundTripDeleteTombstone() {
        // 1. Create a DELETE Record via Record.delete(...)
        // 2. Encode and decode
        // 3. Assert tombstone == true, value.length == 0, and key matches
        byte[] key = "user9221027".getBytes(StandardCharsets.UTF_8);
        long timestamp = 1_700_000_000L;
        Record originalRecord = Record.delete(key, timestamp);
        ByteBuffer encodedBuffer = RecordCodec.encode(originalRecord);
        Record decodedRecord = RecordCodec.decode(encodedBuffer);
        assertEquals(timestamp, decodedRecord.timestamp());
        assertTrue(decodedRecord.tombstone());
        assertArrayEquals(key, decodedRecord.key());
        assertEquals(0, decodedRecord.value().length);
        assertEquals(0, encodedBuffer.remaining());
    }

    @Test
    void throwsOnBitFlipCorruption() {
        // 1. Encode a valid PUT Record
        // 2. Flip 1 bit in the payload (e.g. buf.put(buf.limit() - 1, (byte) (buf.get(buf.limit() - 1) ^ 0xFF)))
        // 3. Assert RecordCodec.decode(buf) throws CorruptRecordException
        byte[] key = "character0".getBytes(StandardCharsets.UTF_8);
        byte[] value = "ada".getBytes(StandardCharsets.UTF_8);
        long timestamp = 1_700_000_000L;
        Record originalRecord = Record.put(key, value, timestamp);
        ByteBuffer encodedBuffer = RecordCodec.encode(originalRecord);
        // Flip a bit in the payload
        encodedBuffer.put(encodedBuffer.limit() - 1, (byte) (encodedBuffer.get(encodedBuffer.limit() - 1) ^ 0xFF));
        CorruptRecordException exception = assertThrows(
            CorruptRecordException.class,
            () -> RecordCodec.decode(encodedBuffer));
        
    }

    @Test
    void throwsOnTruncatedHeaderAndPayload() {
        // 1. Encode a valid PUT Record
        // 2. Create two short slices: one < 21 bytes (truncated header), one missing the last 2 bytes (truncated payload)
        // 3. Assert RecordCodec.decode(...) throws CorruptRecordException for both
        byte[] key = "character0".getBytes(StandardCharsets.UTF_8);
        byte[] value = "ada".getBytes(StandardCharsets.UTF_8);
        long timestamp = 1_700_000_000L;
        Record originalRecord = Record.put(key, value, timestamp);
        ByteBuffer encodedBuffer = RecordCodec.encode(originalRecord);
        CorruptRecordException exception1 = assertThrows(
            CorruptRecordException.class,
            () -> RecordCodec.decode(encodedBuffer.slice(0, 9))); // truncated header
        CorruptRecordException exception2 = assertThrows(
            CorruptRecordException.class,
            () -> RecordCodec.decode(encodedBuffer.slice(0, encodedBuffer.limit() - 2))); // truncated payload
        
    }

    @Test
    void decodesMultipleSequentialRecords() {
        // 1. Encode 3 different Records (e.g., PUT, DELETE, PUT) into individual ByteBuffers
        // 2. Combine them back-to-back into a single ByteBuffer and flip it for reading
        // 3. Call RecordCodec.decode(combined) 3 times in sequence and verify each decoded Record
        // 4. Assert combined.remaining() == 0 at the end
        byte[] key1 = "user83746".getBytes(StandardCharsets.UTF_8);
        byte[] value1 = "ada".getBytes(StandardCharsets.UTF_8);
        long timestamp1 = 1_700_000_000L;
        Record record1 = Record.put(key1, value1, timestamp1);

        byte[] key2 = "user3203948".getBytes(StandardCharsets.UTF_8);
        long timestamp2 = 1_700_000_002L;
        Record record2 = Record.delete(key2, timestamp2);

        byte[] key3 = "user452039".getBytes(StandardCharsets.UTF_8);
        byte[] value3 = "bob".getBytes(StandardCharsets.UTF_8);
        long timestamp3 = 1_700_000_001L;
        Record record3 = Record.put(key3, value3, timestamp3);



        ByteBuffer buf1 = RecordCodec.encode(record1);
        ByteBuffer buf2 = RecordCodec.encode(record2);
        ByteBuffer buf3 = RecordCodec.encode(record3);

        ByteBuffer combined = ByteBuffer.allocate(buf1.remaining() + buf2.remaining() + buf3.remaining());
        combined.put(buf1).put(buf2).put(buf3);
        combined.flip();
        Record decoded1 = RecordCodec.decode(combined);
        Record decoded2 = RecordCodec.decode(combined);
        Record decoded3 = RecordCodec.decode(combined);

        assertEquals(record1.timestamp(), decoded1.timestamp());
        assertEquals(record2.timestamp(), decoded2.timestamp());
        assertEquals(record3.timestamp(), decoded3.timestamp());

        assertArrayEquals(record1.key(), decoded1.key());
        assertArrayEquals(record2.key(), decoded2.key());
        assertArrayEquals(record3.key(), decoded3.key());

        assertArrayEquals(record1.value(), decoded1.value());
        assertArrayEquals(record2.value(), decoded2.value());
        assertArrayEquals(record3.value(), decoded3.value());

        assertEquals(0, combined.remaining());

        assertFalse(decoded1.tombstone());
        assertTrue(decoded2.tombstone());
        assertFalse(decoded3.tombstone());
    }
}