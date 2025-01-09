package org.apache.bookkeeper.bookie;

import io.netty.buffer.ByteBuf;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

public class MyBufferedChannelTest {

    private static final Logger logger = LoggerFactory.getLogger(MyBufferedChannelTest.class);


    private static Stream<Arguments> readTestArguments() {
        Configuration allEmpty = new Configuration(BufferState.EMPTY, BufferState.EMPTY, BufferState.EMPTY);
        Configuration fromFile = new Configuration(BufferState.EMPTY, BufferState.EMPTY, BufferState.NON_EMPTY);
        Configuration fromRead = new Configuration(BufferState.EMPTY, BufferState.NON_EMPTY, BufferState.NON_EMPTY);
        Configuration fromWrite = new Configuration(BufferState.NON_EMPTY, BufferState.EMPTY, BufferState.EMPTY);

        String TS_01_desc, TS_02_desc, TS_03_desc, TS_04_desc, TS_05_desc,
                TS_06_desc, TS_07_desc, TS_08_desc, TS_09_desc, TS_10_desc, TS_11_desc;
        TestState TS_01, TS_02, TS_03, TS_04, TS_05, TS_06, TS_07, TS_08, TS_09, TS_10, TS_11;

        /* Read from empty file */
        TS_01_desc = "#1: Read no available";
        TS_01 = new TestState(
                allEmpty,
                DestState.GREATER_EQUAL_THAN_LENGTH, PosState.LESS_EQUAL_THAN_AVAILABLE, LengthState.LESS_EQUAL_THAN_READABLE,
                ExpectedState.EOF);

        /* Read from file */
        TS_02_desc = "#2: Read from file";
        TS_02 = new TestState(
                fromFile,
                DestState.GREATER_EQUAL_THAN_LENGTH, PosState.LESS_EQUAL_THAN_AVAILABLE, LengthState.LESS_EQUAL_THAN_READABLE,
                ExpectedState.FILE_TIMES_LENGTH);

        /* Read from read buffer */
        TS_03_desc = "#3: Read from read buffer";
        TS_03 = new TestState(
                fromRead,
                DestState.GREATER_EQUAL_THAN_LENGTH, PosState.LESS_EQUAL_THAN_AVAILABLE, LengthState.LESS_EQUAL_THAN_READABLE,
                ExpectedState.READ_TIMES_LENGTH);

        /* Read from write buffer */
        TS_04_desc = "#4: Read from write buffer";
        TS_04 = new TestState(
                fromWrite,
                DestState.GREATER_EQUAL_THAN_LENGTH, PosState.LESS_EQUAL_THAN_AVAILABLE, LengthState.LESS_EQUAL_THAN_READABLE,
                ExpectedState.WRITE_TIMES_LENGTH
        );

        /* Read from negative position */
        TS_05_desc = "#5: Read from negative position";
        TS_05 = new TestState(
                fromFile,
                DestState.GREATER_EQUAL_THAN_LENGTH, PosState.LESS_THAN_ZERO, LengthState.LESS_EQUAL_THAN_READABLE,
                ExpectedState.ILLEGAL_ARGUMENT
        );

        /* Read after end */
        TS_06_desc = "#6: Read after end";
        TS_06 = new TestState(
                fromFile,
                DestState.GREATER_EQUAL_THAN_LENGTH, PosState.GREATER_THAN_AVAILABLE, LengthState.LESS_EQUAL_THAN_READABLE,
                ExpectedState.EOF
        );

        /* Read of negative length */
        TS_07_desc = "#7: Read of negative length";
        TS_07 = new TestState(
                fromFile,
                DestState.GREATER_EQUAL_THAN_LENGTH, PosState.LESS_EQUAL_THAN_AVAILABLE, LengthState.LESS_THAN_ZERO,
                ExpectedState.ILLEGAL_ARGUMENT
        );

        /* Read of 0 length */
        TS_08_desc = "#8: Read of 0 length";
        TS_08 = new TestState(
                fromFile,
                DestState.GREATER_EQUAL_THAN_LENGTH, PosState.LESS_EQUAL_THAN_AVAILABLE, LengthState.EQUAL_AS_ZERO,
                ExpectedState.EMPTY
        );

        /* Read more than available */
        TS_09_desc = "#9: Read more than available";
        TS_09 = new TestState(
                fromFile,
                DestState.GREATER_EQUAL_THAN_LENGTH, PosState.LESS_EQUAL_THAN_AVAILABLE, LengthState.GREATER_THAN_READABLE,
                ExpectedState.EOF
        );

        /* Read to null */
        TS_10_desc = "#10: Read to null dest";
        TS_10 = new TestState(
                fromFile,
                DestState.NULL, PosState.LESS_EQUAL_THAN_AVAILABLE, LengthState.LESS_EQUAL_THAN_READABLE,
                ExpectedState.NULL_POINTER
        );

        /* Read to desc with less capacity than length */
        TS_11_desc = "#11: Read to smaller dest";
        TS_11 = new TestState(
                fromFile,
                DestState.LESS_THAN_LENGTH, PosState.LESS_EQUAL_THAN_AVAILABLE, LengthState.LESS_EQUAL_THAN_READABLE,
                ExpectedState.INDEX_OUT_OF_BOUNDS
        );


        return Stream.of(
                Arguments.of(TS_01_desc, TS_01),
                Arguments.of(TS_02_desc, TS_02),
                Arguments.of(TS_03_desc, TS_03),
                Arguments.of(TS_04_desc, TS_04),
                Arguments.of(TS_05_desc, TS_05),
                Arguments.of(TS_06_desc, TS_06),
                Arguments.of(TS_07_desc, TS_07),
                Arguments.of(TS_08_desc, TS_08),
                Arguments.of(TS_09_desc, TS_09),
                Arguments.of(TS_10_desc, TS_10),
                Arguments.of(TS_11_desc, TS_11)
        );
    }

    @ParameterizedTest
    @MethodSource("readTestArguments")
    @Timeout(value = 5, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void readTest(String description, TestState testState) throws IOException {
        logger.info(description);

        MyBufferedChannelTestConfigurer configurer = new MyBufferedChannelTestConfigurer();
        configurer.setup(testState);

        String loggerMsg = String.format(
                "destSize: %d, pos: %d, length: %d, expectedLen: %s, expectedFill: %s",
                testState.dest == null ? -1 : testState.dest.capacity(),
                testState.pos,
                testState.length,
                testState.expectedBuffer == null ? "null" : testState.expectedBuffer.toString(StandardCharsets.UTF_8).length(),
                testState.expectedBuffer == null ? "null" :
                        testState.expectedBuffer.capacity() > 0 ?
                                testState.expectedBuffer.toString(StandardCharsets.UTF_8).substring(0, 1) : ""
        );
        logger.info(loggerMsg);

        BufferedChannel SUT = testState.sut;
        ByteBuf expected;
        ByteBuf actual;

        /* Compare the result */
        switch (testState.expectedState) {
            case EOF:
                IOException e;
                e = Assertions.assertThrows(IOException.class, () -> SUT.read(testState.dest, testState.pos, testState.length));
                Assertions.assertEquals("Read past EOF", e.getMessage());
                break;
            case ILLEGAL_ARGUMENT:
                Assertions.assertThrows(IllegalArgumentException.class, () -> SUT.read(testState.dest, testState.pos, testState.length));
                break;
            case NULL_POINTER:
                Assertions.assertThrows(NullPointerException.class, () -> SUT.read(testState.dest, testState.pos, testState.length));
                break;
            case INDEX_OUT_OF_BOUNDS:
                Assertions.assertThrows(IndexOutOfBoundsException.class, () -> SUT.read(testState.dest, testState.pos, testState.length));
                break;
            case EMPTY:
            case FILE_TIMES_LENGTH:
            case READ_TIMES_LENGTH:
            case WRITE_TIMES_LENGTH:
            case FILE_TIMES_READABLE:
                SUT.read(testState.dest, testState.pos, testState.length);
                expected = testState.expectedBuffer;
                actual = testState.dest;
                Assertions.assertEquals(expected.capacity(), actual.capacity());
                for (int i = 0; i < expected.capacity(); i++) {
                    Assertions.assertEquals(expected.getByte(i), actual.getByte(i), String.format("Byte: %d", i));
                }
                break;
        }
    }

    public enum BufferState {
        EMPTY, NON_EMPTY
    }

    public enum DestState {
        NULL, LESS_THAN_LENGTH, GREATER_EQUAL_THAN_LENGTH
    }

    public enum PosState {
        LESS_THAN_ZERO, LESS_EQUAL_THAN_AVAILABLE, GREATER_THAN_AVAILABLE
    }

    public enum LengthState {
        LESS_THAN_ZERO, EQUAL_AS_ZERO, LESS_EQUAL_THAN_READABLE, GREATER_THAN_READABLE;
    }

    public enum ExpectedState {
        EMPTY, FILE_TIMES_LENGTH, READ_TIMES_LENGTH, WRITE_TIMES_LENGTH, FILE_TIMES_READABLE,
        EOF, ILLEGAL_ARGUMENT, NULL_POINTER, INDEX_OUT_OF_BOUNDS;
    }

    public static class Configuration {
        protected BufferState writeBufferState, readBufferState, fileChannelState;

        public Configuration(BufferState writeBufferState, BufferState readBufferState, BufferState fileChannelState) {
            this.writeBufferState = writeBufferState;
            this.readBufferState = readBufferState;
            this.fileChannelState = fileChannelState;
        }
    }

    public static class TestState {
        Configuration configuration;
        DestState destState;
        PosState posState;
        LengthState lengthState;
        ExpectedState expectedState;

        BufferedChannel sut;
        FileBundle fileBundle;
        ByteBuf dest;
        long pos;
        int length;
        ByteBuf expectedBuffer;

        public TestState(Configuration configuration, DestState destState, PosState posState, LengthState lengthState, ExpectedState expectedState) {
            this.configuration = configuration;
            this.destState = destState;
            this.posState = posState;
            this.lengthState = lengthState;
            this.expectedState = expectedState;
        }
    }

}
