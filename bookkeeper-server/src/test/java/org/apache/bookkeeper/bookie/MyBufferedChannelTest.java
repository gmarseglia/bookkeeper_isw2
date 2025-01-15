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
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class MyBufferedChannelTest {

    private static final Logger logger = LoggerFactory.getLogger(MyBufferedChannelTest.class);
    private static final String envFlag = System.getenv("flag");


    private static Stream<Arguments> readTestArguments() {
        logger.info(String.format("env: %s", envFlag));
        List<TestState> activeTestState = new ArrayList<>();
        List<Arguments> activeArguments = new ArrayList<>();

        Configuration allEmpty = new Configuration(BufferState.EMPTY, BufferState.EMPTY, BufferState.EMPTY);
        Configuration fromFile = new Configuration(BufferState.EMPTY, BufferState.EMPTY, BufferState.NON_EMPTY);
        Configuration fromRead = new Configuration(BufferState.NULL, BufferState.NON_EMPTY, BufferState.NON_EMPTY);
        Configuration fromWrite = new Configuration(BufferState.NON_EMPTY, BufferState.EMPTY, BufferState.EMPTY);
        Configuration nullWrite = new Configuration(BufferState.NULL, BufferState.EMPTY, BufferState.EMPTY);
        Configuration truncatedFile = new Configuration(BufferState.EMPTY, BufferState.EMPTY, BufferState.TRUNCATED);
        Configuration smallRead = new Configuration(BufferState.EMPTY, BufferState.SECOND_HALF, BufferState.NON_EMPTY);

        /* Read from empty file */
        activeTestState.add(new TestState(
                "#1: Read no available",
                allEmpty,
                DestState.EQUAL_THAN_LENGTH, PosState.EQUAL_AS_ZERO, LengthState.LESS_EQUAL_THAN_READABLE,
                ExpectedState.EOF, true
        ));

        /* Read from file */
        activeTestState.add(new TestState(
                "#2: Read from file",
                fromFile,
                DestState.EQUAL_THAN_LENGTH, PosState.EQUAL_AS_ZERO, LengthState.LESS_EQUAL_THAN_READABLE,
                ExpectedState.FILE_TIMES_LENGTH, true
        ));

        /* Read from read buffer */
        activeTestState.add(new TestState(
                "#3: Read from read buffer",
                fromRead,
                DestState.EQUAL_THAN_LENGTH, PosState.LESS_EQUAL_THAN_AVAILABLE, LengthState.LESS_EQUAL_THAN_READABLE,
                ExpectedState.READ_TIMES_LENGTH, true
        ));

        /* Read from write buffer */
        activeTestState.add(new TestState(
                "#4: Read from write buffer",
                fromWrite,
                DestState.GREATER_THAN_LENGTH, PosState.LESS_EQUAL_THAN_AVAILABLE, LengthState.LESS_EQUAL_THAN_READABLE,
                ExpectedState.WRITE_TIMES_LENGTH, true
        ));

        /* Read from negative position */
        activeTestState.add(new TestState(
                "#5: Read from negative position",
                fromFile,
                DestState.EQUAL_THAN_LENGTH, PosState.LESS_THAN_ZERO, LengthState.LESS_EQUAL_THAN_READABLE,
                ExpectedState.ILLEGAL_ARGUMENT, true
        ));

        /* Read after end */
        activeTestState.add(new TestState(
                "#6: Read after end",
                fromFile,
                DestState.EQUAL_THAN_LENGTH, PosState.GREATER_THAN_AVAILABLE, LengthState.LESS_EQUAL_THAN_READABLE,
                ExpectedState.EOF, false
        ));

        /* Read of negative length */
        activeTestState.add(new TestState(
                "#7: Read of negative length",
                fromFile,
                DestState.EQUAL_THAN_LENGTH, PosState.LESS_EQUAL_THAN_AVAILABLE, LengthState.LESS_THAN_ZERO,
                ExpectedState.ILLEGAL_ARGUMENT, false
        ));

        /* Read of 0 length */
        activeTestState.add(new TestState(
                "#8: Read of 0 length",
                fromFile,
                DestState.EQUAL_THAN_LENGTH, PosState.LESS_EQUAL_THAN_AVAILABLE, LengthState.EQUAL_AS_ZERO,
                ExpectedState.EMPTY, true
        ));

        /* Read more than available */
        activeTestState.add(new TestState(
                "#9: Read more than available",
                fromFile,
                DestState.EQUAL_THAN_LENGTH, PosState.LESS_EQUAL_THAN_AVAILABLE, LengthState.GREATER_THAN_READABLE,
                ExpectedState.EOF, true
        ));

        /* Read to null */
        activeTestState.add(new TestState(
                "#10: Read to null dest",
                fromFile,
                DestState.NULL, PosState.LESS_EQUAL_THAN_AVAILABLE, LengthState.LESS_EQUAL_THAN_READABLE,
                ExpectedState.NULL_POINTER, true
        ));

        /* Read to desc with less capacity than length */
        activeTestState.add(new TestState(
                "#11: Read to smaller dest",
                fromFile,
                DestState.LESS_THAN_LENGTH, PosState.LESS_EQUAL_THAN_AVAILABLE, LengthState.LESS_EQUAL_THAN_READABLE,
                ExpectedState.INDEX_OUT_OF_BOUNDS, false
        ));

        activeTestState.add(new TestState(
                "#12: null write buffer",
                nullWrite,
                DestState.EQUAL_THAN_LENGTH, PosState.LESS_EQUAL_THAN_AVAILABLE, LengthState.LESS_EQUAL_THAN_READABLE,
                ExpectedState.EOF, false
        ));

        activeTestState.add(new TestState(
                "13: read on file truncated after SUT creation",
                truncatedFile,
                DestState.EQUAL_THAN_LENGTH, PosState.LESS_EQUAL_THAN_AVAILABLE, LengthState.LESS_EQUAL_THAN_READABLE,
                ExpectedState.IO_EXCEPTION, true
        ));

        activeTestState.add(new TestState(
                "#14: read on smaller read buffer",
                smallRead,
                DestState.EQUAL_THAN_LENGTH, PosState.LESS_EQUAL_THAN_FIRST_HALF, LengthState.LESS_EQUAL_THAN_READABLE,
                ExpectedState.FILE_TIMES_LENGTH, true
        ));

        for (TestState state : activeTestState) {
            if (!state.successful)
                if (("pitest".equals(envFlag) || "onlySuccess".equals(envFlag)))
                    continue;
            activeArguments.add(Arguments.of(state));
        }

        return activeArguments.stream();
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
                int expectedBytesRead, actualBytesRead;
                expectedBytesRead = testState.expectedBuffer.writerIndex();
                actualBytesRead = SUT.read(testState.dest, testState.pos, testState.length);
                expected = testState.expectedBuffer;
                actual = testState.dest;
                Assertions.assertEquals(expected.writerIndex(), actual.writerIndex());
                for (int i = 0; i < expected.writerIndex(); i++) {
                    Assertions.assertEquals(expected.getByte(i), actual.getByte(i), String.format("Byte: %d", i));
                }
                Assertions.assertEquals(expectedBytesRead, actualBytesRead);
                break;
            default:
                throw new IllegalStateException("Unexpected value: " + testState.expectedState);
        }
    }

    public enum BufferState {
        EMPTY, NON_EMPTY, NULL, TRUNCATED, SECOND_HALF
    }

    public enum DestState {
        NULL, LESS_THAN_LENGTH, EQUAL_THAN_LENGTH, GREATER_THAN_LENGTH
    }

    public enum PosState {
        LESS_THAN_ZERO, EQUAL_AS_ZERO, LESS_EQUAL_THAN_AVAILABLE, GREATER_THAN_AVAILABLE, LESS_EQUAL_THAN_FIRST_HALF
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
        boolean successful;

        BufferedChannel sut;
        FileBundle fileBundle;
        ByteBuf dest;
        long pos;
        int length;
        ByteBuf expectedBuffer;

        public TestState(String description, Configuration configuration, DestState destState, PosState posState, LengthState lengthState, ExpectedState expectedState, boolean successful) {
            this.description = description;
            this.configuration = configuration;
            this.destState = destState;
            this.posState = posState;
            this.lengthState = lengthState;
            this.expectedState = expectedState;
            this.successful = successful;
        }
    }

}
