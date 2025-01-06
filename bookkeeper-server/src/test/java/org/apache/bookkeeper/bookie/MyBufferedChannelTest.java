package org.apache.bookkeeper.bookie;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

public class MyBufferedChannelTest {

    private static final Logger logger = LoggerFactory.getLogger(MyBufferedChannelTest.class);


    private static Stream<Arguments> readTestArguments() {
        Configuration allEmpty = new Configuration(BufferState.EMPTY, BufferState.EMPTY, BufferState.EMPTY);
        Configuration onlyFile = new Configuration(BufferState.EMPTY, BufferState.EMPTY, BufferState.NON_EMPTY);
        Configuration onlyRead = new Configuration(BufferState.EMPTY, BufferState.NON_EMPTY, BufferState.EMPTY);
        Configuration onlyWrite = new Configuration(BufferState.NON_EMPTY, BufferState.EMPTY, BufferState.EMPTY);

        TestState TS_00, TS_01, TS_02, TS_03, TS_04, TS_05, TS_06, TS_07, TS_08, TS_09, TS_10;

        /* Simple read */
        TS_00 = new TestState(onlyFile, DestState.EQUAL_AS_FILE, PosState.AT_BEGIN, LengthState.MIN_OF_CS, ExpectedState.TOTAL_FILE);

        /* Empty read */
        TS_01 = new TestState(allEmpty, DestState.GREATER_THAN_FILE, PosState.AT_BEGIN, LengthState.BW_0_AND_MIN_OF_CS, ExpectedState.EOF_EXCEPTION);

        /* Dest smaller than file */
        TS_02 = new TestState(onlyFile, DestState.LESS_THAN_FILE, PosState.AT_BEGIN, LengthState.MAX_OF_CS, ExpectedState.TOTAL_FILE);

        /* Read from negative position */
        TS_03 = new TestState(onlyFile, DestState.EQUAL_AS_FILE, PosState.BEFORE_BEGIN, LengthState.MIN_OF_CS, ExpectedState.ILLEGAL_ARG_EXCEPTION);

        /* Read at the end */
        TS_04 = new TestState(onlyFile, DestState.EQUAL_AS_FILE, PosState.AT_END, LengthState.MIN_OF_CS, ExpectedState.EOF_EXCEPTION);

        /* Read after the end */
        TS_05 = new TestState(onlyFile, DestState.EQUAL_AS_FILE, PosState.AFTER_END, LengthState.MIN_OF_CS, ExpectedState.ILLEGAL_ARG_EXCEPTION);

        /* Read of negative length */
        TS_06 = new TestState(onlyFile, DestState.EQUAL_AS_FILE, PosState.AT_BEGIN, LengthState.LESS_THAN_ZERO, ExpectedState.NO_CHANGE);

        /* Read of zero length */
        TS_07 = new TestState(onlyFile, DestState.EQUAL_AS_FILE, PosState.AT_BEGIN, LengthState.ZERO, ExpectedState.NO_CHANGE);

        /* Read from read buffer */
        TS_08 = new TestState(onlyRead, DestState.EQUAL_AS_FILE, PosState.BW_BEGIN_AND_END, LengthState.MIN_OF_CS, ExpectedState.PARTIAL_READ);

        /* Read from write buffer */
        TS_09 = new TestState(onlyWrite, DestState.EQUAL_AS_FILE, PosState.AT_BEGIN, LengthState.BW_MIN_AND_MAX_OF_CS, ExpectedState.TOTAL_WRITE);

        /* Read too long */
        TS_10 = new TestState(onlyFile, DestState.GREATER_THAN_FILE, PosState.AT_BEGIN, LengthState.MORE_THAN_MAX_OF_CS, ExpectedState.EOF_EXCEPTION);

        return Stream.of(
                // Arguments.of("TS_00", TS_00),
                // Arguments.of("TS_01", TS_01),
                Arguments.of("TS_02", TS_02),
                // Arguments.of("TS_03", TS_03),
                // Arguments.of("TS_04", TS_04),
                // Arguments.of("TS_05", TS_05),
                // Arguments.of("TS_06", TS_06),
                // Arguments.of("TS_07", TS_07),
                Arguments.of("TS_08", TS_08)
                // Arguments.of("TS_09", TS_09),
                // Arguments.of("TS_10", TS_10)
        );
    }

    @ParameterizedTest
    @MethodSource("readTestArguments")
    void readTest(String testID, TestState testState) throws IOException {
        logger.info(testID);

        MyBufferedChannelTestConfigurer configurer = new MyBufferedChannelTestConfigurer();
        configurer.setup(testState);

        String loggerMsg = String.format(
                "destSize: %d, pos: %d, length: %d, expectedLen: %s, expectedFill: %s",
                testState.dest.capacity(),
                testState.pos,
                testState.length,
                testState.expectedBuffer == null ? "null" : testState.expectedBuffer.toString(StandardCharsets.UTF_8).length(),
                testState.expectedBuffer == null ? "null" : testState.expectedBuffer.toString(StandardCharsets.UTF_8).substring(0, 1)
        );
        logger.info(loggerMsg);

        BufferedChannel SUT = testState.sut;
        ByteBuf expected;
        ByteBuf actual;

        /* Compare the result */
        switch (testState.expectedState) {
            case EOF_EXCEPTION:
                Assertions.assertThrows(IOException.class, () -> SUT.read(testState.dest, testState.pos, testState.length));
                break;
            case ILLEGAL_ARG_EXCEPTION:
                Assertions.assertThrows(IllegalArgumentException.class, () -> SUT.read(testState.dest, testState.pos, testState.length));
                break;
            case NO_CHANGE:
                expected = testState.dest.copy();
                SUT.read(testState.dest, testState.pos, testState.length);
                actual = testState.dest;
                Assertions.assertEquals(expected.capacity(), actual.capacity());
                for (int i = 0; i < expected.capacity(); i++) {
                    Assertions.assertEquals(expected.getByte(i), actual.getByte(i), String.format("Byte: %d", i));
                }
            case PARTIAL_READ:
                IOException ioException = Assertions.assertThrows(IOException.class, () -> SUT.read(testState.dest, testState.pos, testState.length));
                Assertions.assertEquals("Read past EOF", ioException.getMessage());
                logger.info(String.format("IOException received, .getMessage: %s", ioException.getMessage()));
                expected = testState.expectedBuffer;
                actual = testState.dest;
                for (int i = 0; i < expected.capacity(); i++) {
                    Assertions.assertEquals(expected.getByte(i), actual.getByte(i), String.format("Byte: %d", i));
                }
                break;
            default:
                SUT.read(testState.dest, testState.pos, testState.length);
                expected = testState.expectedBuffer;
                actual = testState.dest;
                Assertions.assertEquals(expected.capacity(), actual.capacity());
                for (int i = 0; i < expected.capacity(); i++) {
                    Assertions.assertEquals(expected.getByte(i), actual.getByte(i), String.format("Byte: %d", i));
                }
        }
    }

    public enum BufferState {
        EMPTY, NON_EMPTY
    }

    public enum DestState {
        LESS_THAN_FILE, EQUAL_AS_FILE, GREATER_THAN_FILE
    }

    public enum PosState {
        BEFORE_BEGIN, AT_BEGIN, BW_BEGIN_AND_END, AT_END, AFTER_END
    }

    public enum LengthState {
        LESS_THAN_ZERO, ZERO, BW_0_AND_MIN_OF_CS, MIN_OF_CS, BW_MIN_AND_MAX_OF_CS, MAX_OF_CS, MORE_THAN_MAX_OF_CS
    }

    public enum ExpectedState {
        NO_CHANGE, TOTAL_FILE, PARTIAL_FILE, EOF_EXCEPTION, ILLEGAL_ARG_EXCEPTION, PARTIAL_READ, TOTAL_WRITE
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
