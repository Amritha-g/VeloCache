package com.velocache.protocol;

import java.util.List;
import java.util.Arrays;

public sealed interface RespValue permits
    RespValue.SimpleString,
    RespValue.ErrorResp,
    RespValue.IntegerResp,
    RespValue.BulkString,
    RespValue.NullBulk,
    RespValue.ArrayResp {

    record SimpleString(String value) implements RespValue {}

    record ErrorResp(String message) implements RespValue {}

    record IntegerResp(long value) implements RespValue {}

    record BulkString(byte[] value) implements RespValue {
        public String asString() {
            return new String(value);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            BulkString that = (BulkString) o;
            return Arrays.equals(value, that.value);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(value);
        }

        @Override
        public String toString() {
            return "BulkString[" + new String(value) + "]";
        }
    }

    record NullBulk() implements RespValue {
        @Override
        public String toString() {
            return "NullBulk";
        }
    }

    record ArrayResp(List<RespValue> elements) implements RespValue {}
}
