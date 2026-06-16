package com.velocache.protocol;

import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class RespParserTest {

    private ByteBuffer toBuffer(String data) {
        return ByteBuffer.wrap(data.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void testPingInline() {
        ByteBuffer buf = toBuffer("PING\r\n");
        RespValue val = RespParser.parse(buf);
        assertNotNull(val);
        assertTrue(val instanceof RespValue.ArrayResp);
        List<RespValue> elements = ((RespValue.ArrayResp) val).elements();
        assertEquals(1, elements.size());
        assertEquals("PING", ((RespValue.BulkString) elements.get(0)).asString());
    }

    @Test
    void testSetKeyValueArray() {
        ByteBuffer buf = toBuffer("*3\r\n$3\r\nSET\r\n$5\r\nmykey\r\n$7\r\nmyvalue\r\n");
        RespValue val = RespParser.parse(buf);
        assertNotNull(val);
        assertTrue(val instanceof RespValue.ArrayResp);
        List<RespValue> elements = ((RespValue.ArrayResp) val).elements();
        assertEquals(3, elements.size());
        assertEquals("SET", ((RespValue.BulkString) elements.get(0)).asString());
        assertEquals("mykey", ((RespValue.BulkString) elements.get(1)).asString());
        assertEquals("myvalue", ((RespValue.BulkString) elements.get(2)).asString());
    }

    @Test
    void testGetArray() {
        ByteBuffer buf = toBuffer("*2\r\n$3\r\nGET\r\n$5\r\nmykey\r\n");
        RespValue val = RespParser.parse(buf);
        assertNotNull(val);
        assertTrue(val instanceof RespValue.ArrayResp);
        List<RespValue> elements = ((RespValue.ArrayResp) val).elements();
        assertEquals(2, elements.size());
        assertEquals("GET", ((RespValue.BulkString) elements.get(0)).asString());
        assertEquals("mykey", ((RespValue.BulkString) elements.get(1)).asString());
    }

    @Test
    void testIntegerResponse() {
        ByteBuffer buf = toBuffer(":1000\r\n");
        RespValue val = RespParser.parse(buf);
        assertNotNull(val);
        assertTrue(val instanceof RespValue.IntegerResp);
        assertEquals(1000L, ((RespValue.IntegerResp) val).value());
    }

    @Test
    void testBulkString() {
        ByteBuffer buf = toBuffer("$5\r\nhello\r\n");
        RespValue val = RespParser.parse(buf);
        assertNotNull(val);
        assertTrue(val instanceof RespValue.BulkString);
        assertEquals("hello", ((RespValue.BulkString) val).asString());
    }

    @Test
    void testNullBulk() {
        ByteBuffer buf = toBuffer("$-1\r\n");
        RespValue val = RespParser.parse(buf);
        assertNotNull(val);
        assertTrue(val instanceof RespValue.NullBulk);
    }

    @Test
    void testErrorResponse() {
        ByteBuffer buf = toBuffer("-ERR unknown command\r\n");
        RespValue val = RespParser.parse(buf);
        assertNotNull(val);
        assertTrue(val instanceof RespValue.ErrorResp);
        assertEquals("ERR unknown command", ((RespValue.ErrorResp) val).message());
    }

    @Test
    void testPartialBufferSimpleString() {
        ByteBuffer buf = toBuffer("+OK");
        int initialPos = buf.position();
        RespValue val = RespParser.parse(buf);
        assertNull(val);
        assertEquals(initialPos, buf.position()); // position should not advance on failure
    }

    @Test
    void testPartialBufferBulkString() {
        ByteBuffer buf = toBuffer("$5\r\nhel");
        int initialPos = buf.position();
        RespValue val = RespParser.parse(buf);
        assertNull(val);
        assertEquals(initialPos, buf.position());
    }

    @Test
    void testPartialBufferArray() {
        ByteBuffer buf = toBuffer("*3\r\n$3\r\nSET\r\n$5\r\nmykey\r\n");
        int initialPos = buf.position();
        RespValue val = RespParser.parse(buf);
        assertNull(val);
        assertEquals(initialPos, buf.position());
    }

    @Test
    void testArrayOfArrays() {
        ByteBuffer buf = toBuffer("*2\r\n*1\r\n:1\r\n*1\r\n:2\r\n");
        RespValue val = RespParser.parse(buf);
        assertNotNull(val);
        assertTrue(val instanceof RespValue.ArrayResp);
        List<RespValue> elements = ((RespValue.ArrayResp) val).elements();
        assertEquals(2, elements.size());
        
        assertTrue(elements.get(0) instanceof RespValue.ArrayResp);
        List<RespValue> sub1 = ((RespValue.ArrayResp) elements.get(0)).elements();
        assertEquals(1, sub1.size());
        assertEquals(1L, ((RespValue.IntegerResp) sub1.get(0)).value());

        assertTrue(elements.get(1) instanceof RespValue.ArrayResp);
        List<RespValue> sub2 = ((RespValue.ArrayResp) elements.get(1)).elements();
        assertEquals(1, sub2.size());
        assertEquals(2L, ((RespValue.IntegerResp) sub2.get(0)).value());
    }

    @Test
    void testEmptyArray() {
        ByteBuffer buf = toBuffer("*0\r\n");
        RespValue val = RespParser.parse(buf);
        assertNotNull(val);
        assertTrue(val instanceof RespValue.ArrayResp);
        assertEquals(0, ((RespValue.ArrayResp) val).elements().size());
    }

    @Test
    void testLargeInteger() {
        ByteBuffer buf = toBuffer(":-1234567890\r\n");
        RespValue val = RespParser.parse(buf);
        assertNotNull(val);
        assertTrue(val instanceof RespValue.IntegerResp);
        assertEquals(-1234567890L, ((RespValue.IntegerResp) val).value());
    }

    @Test
    void testEmptyBulkString() {
        ByteBuffer buf = toBuffer("$0\r\n\r\n");
        RespValue val = RespParser.parse(buf);
        assertNotNull(val);
        assertTrue(val instanceof RespValue.BulkString);
        assertEquals(0, ((RespValue.BulkString) val).value().length);
    }

    @Test
    void testConsecutiveParsing() {
        ByteBuffer buf = toBuffer("+OK\r\n+PONG\r\n");
        RespValue first = RespParser.parse(buf);
        assertNotNull(first);
        assertTrue(first instanceof RespValue.SimpleString);
        assertEquals("OK", ((RespValue.SimpleString) first).value());

        RespValue second = RespParser.parse(buf);
        assertNotNull(second);
        assertTrue(second instanceof RespValue.SimpleString);
        assertEquals("PONG", ((RespValue.SimpleString) second).value());
    }
}
