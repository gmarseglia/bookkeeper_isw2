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
import java.util.stream.Stream;

public class MyBufferedChannelTest {

    private static final Logger logger = LoggerFactory.getLogger(MyBufferedChannelTest.class);


    private static Stream<Arguments> readTestArguments() {
        Configuration allEmpty = new Configuration(BufferState.EMPTY, BufferState.EMPTY, BufferState.EMPTY);
        Configuration fromFile = new Configuration(BufferState.EMPTY, BufferState.EMPTY, BufferState.NON_EMPTY);
        Configuration fromRead = new Configuration(BufferState.NULL, BufferState.NON_EMPTY, BufferState.NON_EMPTY);
        Configuration fromWrite = new Configuration(BufferState.NON_EMPTY, BufferState.EMPTY, BufferState.NON_EMPTY);
        Configuration nullWrite = new Configuration(BufferState.NULL, BufferState.EMPTY, BufferState.EMPTY);
        Configuration truncatedFile = new Configuration(BufferState.EMPTY, BufferState.EMPTY, BufferState.TRUNCATED);
        Configuration smallRead = new Configuration(BufferState.EMPTY, BufferState.SECOND_HALF, BufferState.NON_EMPTY);

        /* Read from empty file */
        TestState TS_01 = new TestState(
                "#1: Read no available",
                allEmpty,
                DestState.GREATER_EQUAL_THAN_LENGTH, PosState.LESS_EQUAL_THAN_AVAILABLE, LengthState.LESS_EQUAL_THAN_READABLE,
                ExpectedState.EOF);

        /* Read from file */
        TestState TS_02 = new TestState(
                "#2: Read from file",
                fromFile,
                DestState.GREATER_EQUAL_THAN_LENGTH, PosState.LESS_EQUAL_THAN_AVAILABLE, LengthState.LESS_EQUAL_THAN_READABLE,
                ExpectedState.FILE_TIMES_LENGTH);

        /* Read from read buffer */
        TestState TS_03 = new TestState(
                "#3: Read from read buffer",
                fromRead,
                DestState.GREATER_EQUAL_THAN_LENGTH, PosState.LESS_EQUAL_THAN_AVAILABLE, LengthState.LESS_EQUAL_THAN_READABLE,
                ExpectedState.READ_TIMES_LENGTH);

        /* Read from write buffer */
        TestState TS_04 = new TestState(
                "#4: Read from write buffer",
                fromWrite,
                DestState.GREATER_EQUAL_THAN_LENGTH, PosState.GREATER_EQUAL_THAN_WRITE_BUFFER, LengthState.LESS_EQUAL_THAN_READABLE,
                ExpectedState.WRITE_TIMES_LENGTH
        );

        /* Read from negative position */
        TestState TS_05 = new TestState(
                "#5: Read from negative position",
                fromFile,
                DestState.GREATER_EQUAL_THAN_LENGTH, PosState.LESS_THAN_ZERO, LengthState.LESS_EQUAL_THAN_READABLE,
                ExpectedState.ILLEGAL_ARGUMENT
        );

        /* Read after end */
        TestState TS_06 = new TestState(
                "#6: Read after end",
                fromFile,
                DestState.GREATER_EQUAL_THAN_LENGTH, PosState.GREATER_THAN_AVAILABLE, LengthState.LESS_EQUAL_THAN_READABLE,
                ExpectedState.EOF
        );

        /* Read of negative length */
        TestState TS_07 = new TestState(
                "#7: Read of negative length",
                fromFile,
                DestState.GREATER_EQUAL_THAN_LENGTH, PosState.LESS_EQUAL_THAN_AVAILABLE, LengthState.LESS_THAN_ZERO,
                ExpectedState.ILLEGAL_ARGUMENT
        );

        /* Read of 0 length */
        TestState TS_08 = new TestState(
                "#8: Read of 0 length",
                fromFile,
                DestState.GREATER_EQUAL_THAN_LENGTH, PosState.LESS_EQUAL_THAN_AVAILABLE, LengthState.EQUAL_AS_ZERO,
                ExpectedState.EMPTY
        );

        /* Read more than available */
        TestState TS_09 = new TestState(
                "#9: Read more than available",
                fromFile,
                DestState.GREATER_EQUAL_THAN_LENGTH, PosState.LESS_EQUAL_THAN_AVAILABLE, LengthState.GREATER_THAN_READABLE,
                ExpectedState.EOF
        );

        /* Read to null */
        TestState TS_10 = new TestState(
                "#10: Read to null dest",
                fromFile,
                DestState.NULL, PosState.LESS_EQUAL_THAN_AVAILABLE, LengthState.LESS_EQUAL_THAN_READABLE,
                ExpectedState.NULL_POINTER
        );

        /* Read to desc with less capacity than length */
        TestState TS_11 = new TestState(
                "#11: Read to smaller dest",
                fromFile,
                DestState.LESS_THAN_LENGTH, PosState.LESS_EQUAL_THAN_AVAILABLE, LengthState.LESS_EQUAL_THAN_READABLE,
                ExpectedState.INDEX_OUT_OF_BOUNDS
        );

        TestState TS_12 = new TestState(
                "#12: null write buffer",
                nullWrite,
                DestState.GREATER_EQUAL_THAN_LENGTH, PosState.LESS_EQUAL_THAN_AVAILABLE, LengthState.LESS_EQUAL_THAN_READABLE,
                ExpectedState.EOF
        );

        TestState TS_13 = new TestState(
                "13: read on file truncated after SUT creation",
                truncatedFile,
                DestState.GREATER_EQUAL_THAN_LENGTH, PosState.LESS_EQUAL_THAN_AVAILABLE, LengthState.LESS_EQUAL_THAN_READABLE,
                ExpectedState.IO_EXCEPTION
        );

        TestState TS_14 = new TestState(
                "#14: read on smaller read buffer",
                smallRead,
                DestState.GREATER_EQUAL_THAN_LENGTH, PosState.LESS_EQUAL_THAN_FIRST_HALF, LengthState.LESS_EQUAL_THAN_READABLE,
                ExpectedState.FILE_TIMES_LENGTH
        );

        return Stream.of(
                Arguments.of(TS_01),
                Arguments.of(TS_02),
                Arguments.of(TS_03),
                Arguments.of(TS_04),
                Arguments.of(TS_05),
                Arguments.of(TS_06),
                Arguments.of(TS_07),
                Arguments.of(TS_08),
                Arguments.of(TS_09),
                Arguments.of(TS_10),
                Arguments.of(TS_11),
                Arguments.of(TS_12),
                Arguments.of(TS_13),
                Arguments.of(TS_14)
        );
    }

    @ParameterizedTest
    @MethodSource("readTestArguments")
    @Timeout(value = 5, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void readTest(TestState testState) throws IOException {
        String description = testState.description;
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
        IOException e;
        switch (testState.expectedState) {
            case EOF:
                e = Assertions.assertThrows(IOException.class, () -> SUT.read(testState.dest, testState.pos, testState.length));
                Assertions.assertEquals("Read past EOF", e.getMessage());
                break;
            case IO_EXCEPTION:
                e = Assertions.assertThrows(IOException.class, () -> SUT.read(testState.dest, testState.pos, testState.length));
                Assertions.assertNotEquals("Read past EOF", e.getMessage());
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
                int actualBytesRead;
                actualBytesRead = SUT.read(testState.dest, testState.pos, testState.length);
                expected = testState.expectedBuffer;
                actual = testState.dest;
                Assertions.assertEquals(expected.writerIndex(), actual.writerIndex());
                for (int i = 0; i < expected.capacity(); i++) {
                    Assertions.assertEquals(expected.getByte(i), actual.getByte(i), String.format("Byte: %d", i));
                }
                Assertions.assertEquals(testState.length, actualBytesRead);
                break;
            default:
                throw new IllegalStateException("Unexpected value: " + testState.expectedState);
        }
    }

    public enum BufferState {
        EMPTY, NON_EMPTY, NULL, TRUNCATED, SECOND_HALF
    }

    public enum DestState {
        NULL, LESS_THAN_LENGTH, GREATER_EQUAL_THAN_LENGTH
    }

    public enum PosState {
        LESS_THAN_ZERO, LESS_EQUAL_THAN_AVAILABLE, GREATER_THAN_AVAILABLE, LESS_EQUAL_THAN_FIRST_HALF, GREATER_EQUAL_THAN_WRITE_BUFFER
    }

    public enum LengthState {
        LESS_THAN_ZERO, EQUAL_AS_ZERO, LESS_EQUAL_THAN_READABLE, GREATER_THAN_READABLE;
    }

    public enum ExpectedState {
        EMPTY, FILE_TIMES_LENGTH, READ_TIMES_LENGTH, WRITE_TIMES_LENGTH, FILE_TIMES_READABLE,
        EOF, ILLEGAL_ARGUMENT, NULL_POINTER, INDEX_OUT_OF_BOUNDS, IO_EXCEPTION;
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
        String description;
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

        public TestState(String description, Configuration configuration, DestState destState, PosState posState, LengthState lengthState, ExpectedState expectedState) {
            this.description = description;
            this.configuration = configuration;
            this.destState = destState;
            this.posState = posState;
            this.lengthState = lengthState;
            this.expectedState = expectedState;
        }
    }

}
