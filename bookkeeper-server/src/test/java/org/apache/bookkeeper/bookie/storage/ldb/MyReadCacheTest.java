package org.apache.bookkeeper.bookie.storage.ldb;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.buffer.UnpooledByteBufAllocator;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.stream.Stream;

@RunWith(Parameterized.class)
class MyReadCacheTest {

    private static final Logger logger = LoggerFactory.getLogger(MyReadCacheTest.class);

    private static Stream<Arguments> putAndGetArguments() {
        return Stream.of(
                Arguments.of(KeyStatus.NON_PRESENT, "", null),
                Arguments.of(KeyStatus.NON_PRESENT, "test", null),
                Arguments.of(KeyStatus.PRESENT, "", ""),
                Arguments.of(KeyStatus.PRESENT, "test", "test")
        );
    }

    @ParameterizedTest
    @MethodSource("putAndGetArguments")
    void putAndGet(KeyStatus keyStatus, String inputString, String expectedString) {
        logger.info(String.format("<%s, %s, %s>",
                keyStatus.toString(),
                inputString == null ? "null" : String.format("\"%s\"", inputString),
                expectedString == null ? "null" : String.format("\"%s\"", expectedString)
        ));

        try (ReadCache sut = new ReadCache(UnpooledByteBufAllocator.DEFAULT, 10 * 1024)) {

            ByteBuf input = Unpooled.wrappedBuffer(new byte[1024]);
            if (inputString != null)
                input.setBytes(0, inputString.getBytes());

            if (keyStatus == KeyStatus.PRESENT) {
                sut.put(1, 1, input);
            }

            ByteBuf expected = null;
            if (expectedString != null) {
                expected = Unpooled.wrappedBuffer(new byte[1024]);
                expected.setBytes(0, expectedString.getBytes());
            }

            Assertions.assertEquals(expected, sut.get(1, 1));
        }
    }

    @Test
    public void putAndGetMultipleSegments() {
        try (ReadCache sut = new ReadCache(UnpooledByteBufAllocator.DEFAULT, 10 * 1024, 10)) {
            ByteBuf input = Unpooled.wrappedBuffer("01234".getBytes());
            sut.put(1, 1, input);
            sut.put(1, 2, input);
            sut.put(1, 3, input);
            Assertions.assertEquals(input, sut.get(1, 3));
        }
    }

    public enum KeyStatus {
        NON_PRESENT,
        PRESENT
    }
}
