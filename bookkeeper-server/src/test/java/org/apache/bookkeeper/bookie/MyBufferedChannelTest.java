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
    private static final byte FILE_BYTE = (byte) 'F';
    private static final int FILE_SIZE = 1024;
    private static final byte READ_BYTE = (byte) 'R';
    private static final byte WRITE_BYTE = (byte) 'W';

    private static Stream<Arguments> readTestArguments() {
        Configuration allEmpty = new Configuration(BufferState.EMPTY, BufferState.EMPTY, BufferState.EMPTY);
        Configuration onlyFile = new Configuration(BufferState.EMPTY, BufferState.EMPTY, BufferState.NON_EMPTY);
        Configuration onlyRead = new Configuration(BufferState.EMPTY, BufferState.NON_EMPTY, BufferState.EMPTY);
        Configuration onlyWrite = new Configuration(BufferState.NON_EMPTY, BufferState.EMPTY, BufferState.EMPTY);

        TestState TS_00, TS_01, TS_02, TS_03, TS_04, TS_05, TS_06, TS_07, TS_08, TS_09, TS_10;

        /* Simple read */
        TS_00 = new TestState(onlyFile, DestState.EQUAL_AS_FILE, PosState.AT_BEGIN, LengthState.MIN_OF_CS, ExpectedState.TOTAL_FILE);

        /* Empty read */
        TS_01 = new TestState(allEmpty, DestState.GREATER_THAN_FILE, PosState.AT_BEGIN, LengthState.BW_0_AND_MIN_OF_CS, ExpectedState.NO_CHANGE);

        /* Dest smaller than file */
        TS_02 = new TestState(onlyFile, DestState.LESS_THAN_FILE, PosState.AT_BEGIN, LengthState.MAX_OF_CS, ExpectedState.TOTAL_FILE);

        /* Read from negative position */
        TS_03 = new TestState(onlyFile, DestState.EQUAL_AS_FILE, PosState.BEFORE_BEGIN, LengthState.MIN_OF_CS, ExpectedState.PARTIAL_FILE);

        /* Read at the end */
        TS_04 = new TestState(onlyFile, DestState.EQUAL_AS_FILE, PosState.AT_END, LengthState.MIN_OF_CS, ExpectedState.NO_CHANGE);

        /* Read after the end */
        TS_05 = new TestState(onlyFile, DestState.EQUAL_AS_FILE, PosState.AFTER_END, LengthState.MIN_OF_CS, ExpectedState.EOF_EXCEPTION);

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
                Arguments.of("TS_00", TS_00),
                Arguments.of("TS_01", TS_01),
                // Arguments.of("TS_02", TS_02)
                Arguments.of("TS_03", TS_03),
                Arguments.of("TS_04", TS_04),
                Arguments.of("TS_05", TS_05),
                Arguments.of("TS_06", TS_06),
                Arguments.of("TS_07", TS_07),
                Arguments.of("TS_08", TS_08),
                Arguments.of("TS_09", TS_09),
                Arguments.of("TS_10", TS_10)
        );
    }

    @ParameterizedTest
    @MethodSource("readTestArguments")
    void readTest(String testID, TestState testState) throws IOException {
        logger.info(testID);

        Configurer configurer = new BlackBoxConfigurer();
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

        /* Compare the result */
        switch (testState.expectedState) {
            case NO_CHANGE:
                Assertions.assertThrows(IOException.class, () -> SUT.read(testState.dest, testState.pos, testState.length));
                break;
            default:
                SUT.read(testState.dest, testState.pos, testState.length);
                ByteBuf expected = testState.expectedBuffer;
                ByteBuf actual = testState.dest;
                Assertions.assertEquals(expected.capacity(), actual.capacity());
                for (int i = 0; i < expected.capacity(); i++) {
                    Assertions.assertEquals(expected.getByte(i), actual.getByte(i), String.format("Byte: %d", i));
                }
        }
    }

    protected enum BufferState {
        EMPTY, NON_EMPTY
    }

    protected enum DestState {
        LESS_THAN_FILE, EQUAL_AS_FILE, GREATER_THAN_FILE
    }

    protected enum PosState {
        BEFORE_BEGIN, AT_BEGIN, BW_BEGIN_AND_END, AT_END, AFTER_END
    }

    protected enum LengthState {
        LESS_THAN_ZERO, ZERO, BW_0_AND_MIN_OF_CS, MIN_OF_CS, BW_MIN_AND_MAX_OF_CS, MAX_OF_CS, MORE_THAN_MAX_OF_CS
    }

    protected enum ExpectedState {
        NO_CHANGE, TOTAL_FILE, PARTIAL_FILE, EOF_EXCEPTION, PARTIAL_READ, TOTAL_WRITE
    }

    protected interface Configurer {
        void setup(TestState testState) throws IOException;

        void createSUT(TestState testState) throws IOException;

        void configure(TestState testState) throws IOException;
    }

    protected static abstract class BaseConfigurer implements Configurer {
        public void setup(TestState testState) throws IOException {
            /* Create the file, the RandomAccess and the channel */
            testState.fileBundle = new FileBundle(null);

            /* Configure the environment */
            this.configure(testState);

            /* Initialize the dest buffer */
            int destSize;
            switch (testState.destState) {
                case EQUAL_AS_FILE:
                    destSize = FILE_SIZE;
                    break;
                case LESS_THAN_FILE:
                    destSize = FILE_SIZE - 1;
                    break;
                case GREATER_THAN_FILE:
                    destSize = FILE_SIZE + 1;
                    break;
                default:
                    throw new IllegalStateException("Unexpected value: " + testState.destState);
            }
            testState.dest = ByteBufAllocator.DEFAULT.buffer(destSize);

            /* Set up the position */
            switch (testState.posState) {
                case BEFORE_BEGIN:
                    testState.pos = -1;
                    break;
                case AT_BEGIN:
                    testState.pos = 0;
                    break;
                case BW_BEGIN_AND_END:
                    testState.pos = FILE_SIZE - 1;
                    break;
                case AT_END:
                    testState.pos = FILE_SIZE;
                    break;
                case AFTER_END:
                    testState.pos = FILE_SIZE + 1;
                    break;
            }

            /* Set up the length */
            switch (testState.lengthState) {
                case LESS_THAN_ZERO:
                    testState.length = -1;
                    break;
                case ZERO:
                    testState.length = 0;
                    break;
                case BW_0_AND_MIN_OF_CS:
                    testState.length = Math.min(destSize, FILE_SIZE) - 1;
                    break;
                case MIN_OF_CS:
                    testState.length = Math.min(destSize, FILE_SIZE);
                    break;
                case BW_MIN_AND_MAX_OF_CS:
                    testState.length = Math.max(destSize, FILE_SIZE) - 1;
                    break;
                case MAX_OF_CS:
                    testState.length = Math.max(destSize, FILE_SIZE);
                    break;
                case MORE_THAN_MAX_OF_CS:
                    testState.length = Math.max(destSize, FILE_SIZE) + 1;
                    break;
            }

            int expectedBufferSize;
            byte expectedBufferFill;
            ByteBuf expectedBuffer;
            int posSub = testState.pos < 0 ? (int) -testState.pos : (int) testState.pos;
            switch (testState.expectedState) {
                case TOTAL_FILE:
                    expectedBufferSize = destSize;
                    expectedBufferFill = FILE_BYTE;
                    break;
                case PARTIAL_FILE:
                    expectedBufferSize = Math.min(destSize, FILE_SIZE) - posSub;
                    expectedBufferFill = FILE_BYTE;
                    break;
                case PARTIAL_READ:
                    expectedBufferSize = Math.min(destSize, FILE_SIZE) - posSub;
                    expectedBufferFill = READ_BYTE;
                    break;
                case TOTAL_WRITE:
                    expectedBufferSize = destSize;
                    expectedBufferFill = WRITE_BYTE;
                    break;
                default:
                    expectedBufferSize = -1;
                    expectedBufferFill = -1;
            }
            if (expectedBufferSize != -1) {
                expectedBuffer = Unpooled.buffer(expectedBufferSize);
                for (int i = 0; i < expectedBufferSize; i++) {
                    expectedBuffer.writeByte(expectedBufferFill);
                }
                testState.expectedBuffer = expectedBuffer;
            } else {
                testState.expectedBuffer = null;
            }

        }

        public void createSUT(TestState testState) throws IOException {
            /* Create the BufferedChannel class */
            testState.sut = new BufferedChannel(ByteBufAllocator.DEFAULT, testState.fileBundle.fileChannel, FILE_SIZE + 1, FILE_SIZE + 1);
        }
    }

    protected static class BlackBoxConfigurer extends BaseConfigurer {

        @Override
        public void configure(TestState testState) throws IOException {
            FileChannel fileChannel = testState.fileBundle.fileChannel;
            ByteBuffer readFileBuffer = null;

            /* Set up the file for read buffer */
            if (testState.configuration.readBufferState == BufferState.NON_EMPTY ||
                    testState.configuration.fileChannelState == BufferState.NON_EMPTY) {
                readFileBuffer = testState.fileBundle.fillFileChannel(READ_BYTE, FILE_SIZE, false);

                /* Assert that the file has been written correctly */
                ByteBuffer tempReadBuffer = ByteBuffer.allocate(FILE_SIZE);
                long prevPos = fileChannel.position();
                fileChannel.position(0);
                fileChannel.read(tempReadBuffer);
                fileChannel.position(prevPos);
                tempReadBuffer.flip();

                assert readFileBuffer.capacity() == tempReadBuffer.capacity();
                for (int i = 0; i < readFileBuffer.capacity(); i++) {
                    assert readFileBuffer.get(i) == tempReadBuffer.get(i);
                }
            }

            /* Create the SUT */
            this.createSUT(testState);
            BufferedChannel sut = testState.sut;

            /* Set up the SUT read buffer */
            if (testState.configuration.readBufferState == BufferState.NON_EMPTY) {
                ByteBuf tempReadBuffer = Unpooled.buffer(FILE_SIZE);
                sut.read(tempReadBuffer, 0, FILE_SIZE);

                // Assert that SUT has read what has been written to file
                assert readFileBuffer != null;
                readFileBuffer.position(0);
                assert readFileBuffer.compareTo(tempReadBuffer.nioBuffer()) == 0;
                tempReadBuffer.release();
            }

            if (testState.configuration.fileChannelState == BufferState.EMPTY) {
                // Empty the file
                Path filePath = testState.fileBundle.file.toPath();
                Files.newBufferedWriter(filePath).close();
            } else {
                ByteBuffer tempFileBuffer = testState.fileBundle.fillFileChannel(FILE_BYTE, FILE_SIZE, true);

                /* Assert that the file has been written correctly */
                ByteBuffer tempReadBuffer = ByteBuffer.allocate(FILE_SIZE);
                long prevPos = fileChannel.position();
                fileChannel.position(0);
                fileChannel.read(tempReadBuffer);
                fileChannel.position(prevPos);
                tempReadBuffer.position(0);
                tempFileBuffer.flip();
                tempFileBuffer.position(0);
                assert tempFileBuffer.compareTo(tempReadBuffer) == 0;
            }

            /* Set up the SUT write buffer */
            if (testState.configuration.writeBufferState == BufferState.NON_EMPTY) {
                ByteBuf writeBuffer = Unpooled.buffer(FILE_SIZE);
                for (int i = 0; i < FILE_SIZE; i++) {
                    writeBuffer.writeByte(WRITE_BYTE);
                }
                sut.write(writeBuffer);

                assert FILE_SIZE == sut.getNumOfBytesInWriteBuffer();
                assert fileChannel.size() == 0;
                writeBuffer.release();
            }

        }
    }

    protected static class Configuration {
        protected BufferState writeBufferState, readBufferState, fileChannelState;

        public Configuration(BufferState writeBufferState, BufferState readBufferState, BufferState fileChannelState) {
            this.writeBufferState = writeBufferState;
            this.readBufferState = readBufferState;
            this.fileChannelState = fileChannelState;
        }
    }

    protected static class TestState {
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
