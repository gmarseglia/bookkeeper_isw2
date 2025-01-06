package org.apache.bookkeeper.bookie;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;

public class MyBufferedChannelTestConfigurer {

    private static final byte FILE_BYTE = (byte) 'F';
    private static final int FILE_SIZE = 1024;
    private static final byte READ_BYTE = (byte) 'R';
    private static final byte WRITE_BYTE = (byte) 'W';

    public MyBufferedChannelTestConfigurer() {
    }

    public void setup(MyBufferedChannelTest.TestState testState) throws IOException {
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

    public void createSUT(MyBufferedChannelTest.TestState testState) throws IOException {
        /* Create the BufferedChannel class */
        testState.sut = new BufferedChannel(ByteBufAllocator.DEFAULT, testState.fileBundle.fileChannel, FILE_SIZE + 1, FILE_SIZE + 1);
    }

    public void configure(MyBufferedChannelTest.TestState testState) throws IOException {
        FileChannel fileChannel = testState.fileBundle.fileChannel;
        ByteBuffer readFileBuffer = null;

        /* Set up the file for read buffer */
        if (testState.configuration.readBufferState == MyBufferedChannelTest.BufferState.NON_EMPTY ||
                testState.configuration.fileChannelState == MyBufferedChannelTest.BufferState.NON_EMPTY) {
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
        if (testState.configuration.readBufferState == MyBufferedChannelTest.BufferState.NON_EMPTY) {
            ByteBuf tempReadBuffer = Unpooled.buffer(FILE_SIZE);
            sut.read(tempReadBuffer, 0, FILE_SIZE);

            // Assert that SUT has read what has been written to file
            assert readFileBuffer != null;
            readFileBuffer.position(0);
            assert readFileBuffer.compareTo(tempReadBuffer.nioBuffer()) == 0;
            tempReadBuffer.release();
        }

        if (testState.configuration.fileChannelState == MyBufferedChannelTest.BufferState.EMPTY) {
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
        if (testState.configuration.writeBufferState == MyBufferedChannelTest.BufferState.NON_EMPTY) {
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
