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
    private static final int SEGMENT_SIZE = 64;

    private static String buildStringOfLength(int n) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < n + 1; i++) {
            builder.append("A");
        }
        return builder.toString();
    }

    private static Stream<Arguments> putAndGetArguments() {
        String empty = "";
        String smallerThanSS = buildStringOfLength(10);
        String biggerThanSS = buildStringOfLength(SEGMENT_SIZE + 1);

        // testID, keyStatus, inputString, expectedString
        return Stream.of(
                Arguments.of("1", empty, KeyStatus.NON_PRESENT, null),
                Arguments.of("2", smallerThanSS, KeyStatus.NON_PRESENT, null),
                Arguments.of("3", empty, KeyStatus.PRESENT, empty),
                Arguments.of("4", "test", KeyStatus.PRESENT, "test")
        );
    }

    @ParameterizedTest
    @MethodSource("putAndGetArguments")
    void putAndGet(String testID, String inputString, KeyStatus keyStatus, String expectedString) {
        logger.info(String.format("#%s: <%s, %s, %s>",
                testID,
                inputString == null ? "null" : String.format("\"%s\"", inputString),
                keyStatus.toString(),
                expectedString == null ? "null" : String.format("\"%s\"", expectedString)
        ));

        try (ReadCache sut = new ReadCache(UnpooledByteBufAllocator.DEFAULT, 10 * 1024, SEGMENT_SIZE)) {

            ByteBuf input = null;
            if (inputString != null) {
                input = Unpooled.wrappedBuffer(new byte[inputString.length()]);
                input.setBytes(0, inputString.getBytes());
            }

            if (input != null && keyStatus == KeyStatus.PRESENT) {
                sut.put(1, 1, input);
            }

            ByteBuf expected = null;
            if (expectedString != null) {
                expected = Unpooled.wrappedBuffer(new byte[expectedString.length()]);
                expected.setBytes(0, expectedString.getBytes());
            }

            ByteBuf actual = sut.get(1,1);

            Assertions.assertEquals(expected, actual);
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
