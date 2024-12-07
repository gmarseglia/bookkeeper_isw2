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
    private static final int SEGMENT_SIZE = 256;
    private static final int SEGMENT_NUMBER = 8;

    private static void logTest(String testID, ReadCacheStatus status, String inputString, String expectedString) {
        logger.info(String.format("#%s: <%s, %s, %s>",
                testID,
                status.keyStatus.toString(),
                inputString == null ? "null" : String.format("\"%s\"", inputString),
                expectedString == null ? "null" : String.format("\"%s\"", expectedString)
        ));
    }

    private static String buildStringOfLength(int n, char c) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < n; i++) {
            builder.append(c);
        }
        return builder.toString();
    }

    private static Stream<Arguments> putAndGetArguments() {
        String empty = "";
        String oldString = buildStringOfLength(SEGMENT_SIZE, 'b');
        String smallerThanSS = buildStringOfLength(SEGMENT_SIZE, 'a');
        String biggerThanSS = buildStringOfLength(SEGMENT_SIZE + 1, 'a');

        String edgeOldString = buildStringOfLength(4, 'b');
        String edgeNewString = buildStringOfLength(4, 'a');

        ReadCacheStatus cleanStatus = new ReadCacheStatus(KeyStatus.CLEAN, null);
        ReadCacheStatus dirtyStatus = new ReadCacheStatus(KeyStatus.DIRTY, oldString);
        ReadCacheStatus edgeStatus = new ReadCacheStatus(KeyStatus.DIRTY, edgeOldString, (SEGMENT_SIZE * (SEGMENT_NUMBER + 1)) / 4);

        // String testID, ReadCacheStatus status, String inputString, String expectedString
        return Stream.of(
                Arguments.of("1", cleanStatus, empty, empty),
                Arguments.of("2.1", cleanStatus, smallerThanSS, smallerThanSS),
                Arguments.of("2.2", cleanStatus, biggerThanSS, null),
                Arguments.of("3", dirtyStatus, empty, empty),
                Arguments.of("4.1", dirtyStatus, smallerThanSS, smallerThanSS),
                Arguments.of("4.2", dirtyStatus, biggerThanSS, oldString),
                Arguments.of("5.1", dirtyStatus, null, oldString),
                Arguments.of("5.2", edgeStatus, edgeNewString, edgeNewString),
                Arguments.of("5.3", edgeStatus, null, null)
        );
    }

    @ParameterizedTest
    @MethodSource("putAndGetArguments")
    void testPut(String testID, ReadCacheStatus status, String inputString, String expectedString) {

        logTest(testID, status, inputString, expectedString);

        try (ReadCache sut = new ReadCache(UnpooledByteBufAllocator.DEFAULT, SEGMENT_SIZE * 8, SEGMENT_SIZE)) {

            if (status.keyStatus == KeyStatus.DIRTY) {
                ByteBuf old = Unpooled.wrappedBuffer(status.oldString.getBytes());
                for (int i = 1; i <= status.repetition; i++) {
                    sut.put(1, i, old);
                    assert Objects.equals(old, sut.get(1, i));
                }
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
        CLEAN,
        DIRTY
    }

    public static class ReadCacheStatus {
        public KeyStatus keyStatus;
        public String oldString;
        public int repetition;

        public ReadCacheStatus(KeyStatus keyStatus, String oldString) {
            this.keyStatus = keyStatus;
            this.oldString = oldString;
            this.repetition = 1;
        }

        public ReadCacheStatus(KeyStatus keyStatus, String oldString, int repetition) {
            this.keyStatus = keyStatus;
            this.oldString = oldString;
            this.repetition = repetition;
        }
    }
}
