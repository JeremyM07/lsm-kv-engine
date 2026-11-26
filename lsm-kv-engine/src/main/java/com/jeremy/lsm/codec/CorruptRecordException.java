package com.jeremy.lsm.codec;

public class CorruptRecordException extends RuntimeException {

    public CorruptRecordException(String message) {
        super(message);
    }

    public CorruptRecordException(String message, Throwable cause) {
        super(message, cause);
    }
    
}
