package org.apache.bookkeeper.bookie.storage.ldb;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.buffer.UnpooledByteBufAllocator;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.stream.Stream;

@RunWith(Parameterized.class)
class MyReadCacheTest {

    private static final Logger logger = LoggerFactory.getLogger(MyReadCacheTest.class);
    private static final int SEGMENT_SIZE = 64;

    private static void logTest(String testID, KeyStatus keyStatus, String inputString, String expectedString) {
        logger.info(String.format("#%s: <%s, %s, %s>",
                testID,
                keyStatus.toString(),
                inputString == null ? "null" : String.format("\"%s\"", inputString),
                expectedString == null ? "null" : String.format("\"%s\"", expectedString)
        ));
    }

    private static String buildStringOfLength(int n, char c) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < n + 1; i++) {
            builder.append(c);
        }
        return builder.toString();
    }

    private static Stream<Arguments> putAndGetArguments() {
        String empty = "";
        String smallerThanSS = buildStringOfLength(SEGMENT_SIZE - 1, 'a');

        // testID, inputString, keyStatus, expectedString
        return Stream.of(
                Arguments.of("1", KeyStatus.WRITE, empty, empty),
                Arguments.of("2", KeyStatus.WRITE, smallerThanSS, smallerThanSS),
                Arguments.of("3", KeyStatus.OVERWRITE, empty, empty),
                Arguments.of("4", KeyStatus.OVERWRITE, "test", "test")
        );
    }

    @ParameterizedTest
    @MethodSource("putAndGetArguments")
    void testPut(String testID, KeyStatus keyStatus, String inputString, String expectedString) {

        logTest(testID, keyStatus, inputString, expectedString);

        try (ReadCache sut = new ReadCache(UnpooledByteBufAllocator.DEFAULT, 10 * 1024, SEGMENT_SIZE)) {

            if (keyStatus == KeyStatus.OVERWRITE) {
                String oldString = buildStringOfLength(SEGMENT_SIZE - 1, 'b');
                ByteBuf old = Unpooled.wrappedBuffer(oldString.getBytes());
                sut.put(1, 1, old);

                assert Objects.equals(old, sut.get(1, 1));
            }

            if (inputString != null) {
                ByteBuf input = Unpooled.wrappedBuffer(inputString.getBytes());
                sut.put(1, 1, input);
            }

            ByteBuf expected = null;
            if (expectedString != null) {
                expected = Unpooled.wrappedBuffer(expectedString.getBytes());
            }

            ByteBuf actual = sut.get(1, 1);

            Assertions.assertEquals(expected, actual);
        }
    }

    public enum KeyStatus {
        WRITE,
        OVERWRITE
    }
}
