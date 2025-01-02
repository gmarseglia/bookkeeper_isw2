package org.apache.bookkeeper.bookie;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

public class MyBufferedChannelTest {

    private static final Logger logger = LoggerFactory.getLogger(MyBufferedChannelTest.class);
    private static final char FILE_CHAR = 'F';
    private static final int FILE_SIZE = 1024;
    private static final char READ_CHAR = 'R';
    private static final char WRITE_CHAR = 'W';

    private static Stream<Arguments> readTestArguments() {
        Configuration allEmpty = new Configuration(BufferState.EMPTY, BufferState.EMPTY, BufferState.EMPTY);
        Configuration onlyFile = new Configuration(BufferState.EMPTY, BufferState.EMPTY, BufferState.NON_EMPTY);
        Configuration onlyRead = new Configuration(BufferState.EMPTY, BufferState.NON_EMPTY, BufferState.EMPTY);
        Configuration onlyWrite = new Configuration(BufferState.NON_EMPTY, BufferState.EMPTY, BufferState.EMPTY);

        TestState TS_01, TS_02, TS_03, TS_04, TS_05, TS_06, TS_07, TS_08, TS_09, TS_10;

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
                Arguments.of("TS_01", TS_01),
                Arguments.of("TS_02", TS_02),
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

        void configure(TestState testState) throws IOException;
    }

    protected static abstract class BaseConfigurer implements Configurer {
        public void setup(TestState testState) throws IOException {
            /* Create the file, the RandomAccess and the channel */
            testState.fileBundle = new FileBundle(null);

            /* Create the BufferedChannel class */
            testState.sut = new BufferedChannel(ByteBufAllocator.DEFAULT, testState.fileBundle.fileChannel, FILE_SIZE + 1, FILE_SIZE + 1);

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

            this.configure(testState);
        }
    }

    protected static class BlackBoxConfigurer extends BaseConfigurer {

        @Override
        public void configure(TestState testState) throws IOException {
            BufferedChannel sut = testState.sut;
            FileChannel fileChannel = testState.fileBundle.fileChannel;

            /* Set up the SUT write buffer */
            if (testState.configuration.writeBufferState == BufferState.NON_EMPTY) {
                ByteBuf writeBuffer = Unpooled.buffer(FILE_SIZE);
                for (int i = 0; i < FILE_SIZE; i++) {
                    writeBuffer.writeByte((byte) WRITE_CHAR);
                }
                sut.write(writeBuffer);

                assert FILE_SIZE == sut.getNumOfBytesInWriteBuffer();
                assert fileChannel.size() == 0;
                writeBuffer.release();
            }

            /* Set up the SUT read buffer */
            if (testState.configuration.readBufferState == BufferState.NON_EMPTY) {
                ByteBuffer tempFileBuffer = ByteBuffer.allocate(FILE_SIZE);
                for (int i = 0; i < FILE_SIZE; i++) {
                    tempFileBuffer.put((byte) READ_CHAR);
                }
                tempFileBuffer.flip();
                int writtenBytes = fileChannel.write(tempFileBuffer);
                fileChannel.force(true);
                assert writtenBytes == FILE_SIZE;
                // fileChannel.position(0);

                sut = new BufferedChannel(ByteBufAllocator.DEFAULT, testState.fileBundle.fileChannel, FILE_SIZE + 1, FILE_SIZE + 1);

                ByteBuf tempReadBuffer = Unpooled.buffer(FILE_SIZE);
                sut.read(tempReadBuffer, 0, FILE_SIZE);

                // Assert that SUT has read what has been written to file
                tempFileBuffer.position(0);
                assert tempFileBuffer.compareTo(tempReadBuffer.nioBuffer()) == 0;
                tempReadBuffer.release();

                // Empty the file
                Path filePath = testState.fileBundle.file.toPath();
                Files.newBufferedWriter(filePath).close();
            }

            /* Set up the file */
            if (testState.configuration.fileChannelState == BufferState.NON_EMPTY) {
                ByteBuffer tempFileBuffer = ByteBuffer.allocate(FILE_SIZE);
                for (int i = 0; i < FILE_SIZE; i++) {
                    tempFileBuffer.put((byte) FILE_CHAR);
                }
                tempFileBuffer.flip();
                int writtenBytes = fileChannel.write(tempFileBuffer);
                fileChannel.force(true);
                assert writtenBytes == FILE_SIZE;
                fileChannel.position(0);

                ByteBuffer tempReadBuffer = ByteBuffer.allocate(FILE_SIZE);
                fileChannel.read(tempReadBuffer);
                fileChannel.position(0);
                tempReadBuffer.flip();

                tempFileBuffer.position(0);
                assert tempFileBuffer.compareTo(tempReadBuffer) == 0;
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

        FileBundle fileBundle;
        ByteBuf dest;
        BufferedChannel sut;

        public TestState(Configuration configuration, DestState destState, PosState posState, LengthState lengthState, ExpectedState expectedState) {
            this.configuration = configuration;
            this.destState = destState;
            this.posState = posState;
            this.lengthState = lengthState;
            this.expectedState = expectedState;
        }
    }

}
