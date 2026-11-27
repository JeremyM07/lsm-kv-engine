package com.jeremy.lsm.wal;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.jeremy.lsm.codec.CorruptRecordException;
import com.jeremy.lsm.codec.Record;
import com.jeremy.lsm.codec.RecordCodec;

public final class WriteAheadLog implements Closeable {

    private final Path path;
    private final FileChannel channel;

    public WriteAheadLog(Path path) throws IOException {
        //Validate path, open FileChannel, seek to end of file
        this.path = Objects.requireNonNull(path);
        this.channel = FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE);
        try {
            this.channel.position(this.channel.size());
        } catch (IOException e) {
            this.channel.close();
            throw new IOException("Failed to open WAL file: " + path, e);
        }
    }

    /**
     * Encodes the record and appends all bytes to the WAL file, then forces durability.
     * Steps:
     * 1. Encode record with RecordCodec.encode(record).
     * 2. Loop `while (buf.hasRemaining()) { channel.write(buf); }` (a single write() call
     *    on a FileChannel is not guaranteed to write every byte in one OS syscall).
     * 3. Call channel.force(true) to flush data + metadata to disk.
     */
    public synchronized void append(Record record) throws IOException {
        ByteBuffer buf = RecordCodec.encode(record);
        while (buf.hasRemaining()) {
            channel.write(buf);
        }
        // Force write to disk
        channel.force(true);
    }

    /**
     * Reads all valid records sequentially from offset 0 of the WAL file.
     * Steps:
     * 1. Read the entire file into a ByteBuffer (e.g., via Files.readAllBytes(path) wrapped
     *    with ByteBuffer.wrap(...)) and track `int lastValidOffset = 0`.
     * 2. Loop while `buf.hasRemaining()`:
     *    - Try calling RecordCodec.decode(buf).
     *    - If it succeeds, add the record to your list and update `lastValidOffset = buf.position()`.
     *    - If it throws CorruptRecordException (a torn tail write from a crash):
     *      truncate the file back to `lastValidOffset` via `channel.truncate(lastValidOffset)`,
     *      set `channel.position(lastValidOffset)`, and break out of the loop.
     * 3. Return the recovered list of valid records.
     */
    public synchronized List<Record> recover() throws IOException {
        // sequential replay + torn tail truncation
        int lastValidOffset = 0;
        ByteBuffer buf = ByteBuffer.wrap(Files.readAllBytes(this.path));
        List<Record> records = new ArrayList<>();
        while (buf.hasRemaining()) {
            try {
                Record record = RecordCodec.decode(buf);
                records.add(record);
                lastValidOffset = buf.position();
            } catch (CorruptRecordException e) {
                // Truncate the file back to last valid offset
                channel.truncate(lastValidOffset);
                channel.position(lastValidOffset);
                channel.force(true);
                break;
            }
        }
        return records;
    }

    /**
     * Wipes the WAL contents (used later once a MemTable has safely flushed to an SSTable).
     * Hint: channel.truncate(0), channel.position(0), channel.force(true).
     */
    public synchronized void clear() throws IOException {
        //log reset
        channel.truncate(0);
        channel.position(0);
        channel.force(true);
    }

    public Path getPath() {
        return path;
    }

    @Override
    public synchronized void close() throws IOException {
        channel.close();
    }
}