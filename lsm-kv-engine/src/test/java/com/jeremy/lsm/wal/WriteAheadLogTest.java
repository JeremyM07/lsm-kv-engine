package com.jeremy.lsm.wal;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.jeremy.lsm.codec.Record;

class WriteAheadLogTest {

    @TempDir
    Path tempDir;

    @Test
    void appendAndRecoverAcrossReopen() throws IOException {
        Path walPath = tempDir.resolve("engine.wal");
        try (WriteAheadLog wal = new WriteAheadLog(walPath)) {
            wal.append(new Record(System.currentTimeMillis(), false, "key1".getBytes(StandardCharsets.UTF_8), "value1".getBytes(StandardCharsets.UTF_8)));
            wal.append(Record.delete("key2".getBytes(StandardCharsets.UTF_8), System.currentTimeMillis()));
            wal.append(new Record(System.currentTimeMillis(), false, "key3".getBytes(StandardCharsets.UTF_8), "value3".getBytes(StandardCharsets.UTF_8)));
        }
        try (WriteAheadLog wal2 = new WriteAheadLog(walPath)) {
        List<Record> recovered = wal2.recover();
        assertEquals(3, recovered.size());
        assertEquals("key1", new String(recovered.get(0).key(), StandardCharsets.UTF_8));
        assertEquals("value1", new String(recovered.get(0).value(), StandardCharsets.UTF_8));
        assertEquals("key2", new String(recovered.get(1).key(), StandardCharsets.UTF_8));
        assertEquals(0, recovered.get(1).value().length);
        assertEquals("key3", new String(recovered.get(2).key(), StandardCharsets.UTF_8));
        assertEquals("value3", new String(recovered.get(2).value(), StandardCharsets.UTF_8));

        assertFalse(recovered.get(0).tombstone());
        assertTrue(recovered.get(1).tombstone());
        assertFalse(recovered.get(2).tombstone());
        }

        // 1. Open WriteAheadLog, append 3 records
        // 2. Open a WriteAheadLog instance pointing to walPath
        // 3. Call recover() and verify all 3 records match.
    }

    @Test
    void recoversValidPrefixAndTruncatesTornTail() throws IOException {
        Path walPath = tempDir.resolve("crash.wal");
        try (WriteAheadLog wal = new WriteAheadLog(walPath)) {
            wal.append(new Record(System.currentTimeMillis(), false, "key1".getBytes(StandardCharsets.UTF_8), "value1".getBytes(StandardCharsets.UTF_8)));
            wal.append(Record.delete("key2".getBytes(StandardCharsets.UTF_8), System.currentTimeMillis()));
        }
        long validSize = Files.size(walPath);
        Files.write(walPath, new byte[]{1,2,3,4,5,6,7}, StandardOpenOption.APPEND);

        try(WriteAheadLog wal2 = new WriteAheadLog(walPath)){
            
            List<Record> recovered = wal2.recover();
            assertEquals(2, recovered.size());
            assertEquals("key1", new String(recovered.get(0).key(), StandardCharsets.UTF_8));
            assertEquals("value1", new String(recovered.get(0).value(), StandardCharsets.UTF_8));
            assertEquals("key2", new String(recovered.get(1).key(), StandardCharsets.UTF_8));
            assertEquals(0, recovered.get(1).value().length);
            assertEquals(validSize, Files.size(walPath));

            wal2.append(new Record(System.currentTimeMillis(), false, "key3".getBytes(StandardCharsets.UTF_8), "value3".getBytes(StandardCharsets.UTF_8)));
            List<Record> recovered2 = wal2.recover();
            assertEquals(3, recovered2.size());
        }

        // 1. Open WAL append 2 valid records
        // 2. Record Size
        // 3. Simulate a mid-write power loss by adding garbage bytes
        // 4. Reopen WAL
        // 5. Assert that only the 2 valid records are returned and that the file size is truncated back to validSize
    }

    @Test
    void clearResetsLogToEmpty() throws IOException {
        Path walPath = tempDir.resolve("clear.wal");
        try (WriteAheadLog wal = new WriteAheadLog(walPath)) {
            wal.append(new Record(System.currentTimeMillis(), false, "key1".getBytes(StandardCharsets.UTF_8), "value1".getBytes(StandardCharsets.UTF_8)));
            wal.append(Record.delete("key2".getBytes(StandardCharsets.UTF_8), System.currentTimeMillis()));
            wal.clear();
            assertEquals(0, Files.size(walPath));
            List<Record> recovered = wal.recover();
            assertTrue(recovered.isEmpty());
        }

        // 1. Open WAL, append 2 records
        // 2. Call clear()
        // 3. Assert Files.size(walPath) == 0
        // 4. Assert recover() is empty
    }
}