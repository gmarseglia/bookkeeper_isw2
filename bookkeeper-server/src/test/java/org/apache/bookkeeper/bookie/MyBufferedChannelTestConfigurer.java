package org.apache.bookkeeper.bookie;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

        /* Compute available bytes */
        int available = FILE_SIZE;

        /* Set up the position */
        int pos;
        switch (testState.posState) {
            case LESS_THAN_ZERO:
                pos = -1;
                break;
            case LESS_EQUAL_THAN_AVAILABLE:
                pos = 0;
                break;
            case GREATER_THAN_AVAILABLE:
                pos = available + 1;
                break;
            default:
                throw new IllegalStateException("Unexpected value: " + testState.posState);
        }
        testState.pos = pos;

        /* Compute readable bytes */
        int readable = Math.max(available - pos, 0);

        /* Set up the length */
        int length;
        switch (testState.lengthState) {
            case LESS_THAN_ZERO:
                length = -1;
                break;
            case EQUAL_AS_ZERO:
                length = 0;
                break;
            case LESS_EQUAL_THAN_READABLE:
                length = Math.max(readable, 1);
                break;
            case GREATER_THAN_READABLE:
                length = Math.max(readable + 1, 1);
                break;
            default:
                throw new IllegalStateException("Unexpected value: " + testState.lengthState);
        }
        testState.length = length;

        /* Initialize the dest buffer */
        int destSize = 0;
        ByteBuf destBuf;
        switch (testState.destState) {
            case LESS_THAN_LENGTH:
                destSize = Math.max(length - 1, 0);
                destBuf = ByteBufAllocator.DEFAULT.buffer(destSize);
                break;
            case GREATER_EQUAL_THAN_LENGTH:
                destSize = Math.max(length, 0);
                destBuf = ByteBufAllocator.DEFAULT.buffer(destSize);
                break;
            case NULL:
                destBuf = null;
                break;
            default:
                throw new IllegalStateException("Unexpected value: " + testState.destState);
        }
        testState.dest = destBuf;

        /* Compute the expected buffer */

        int expectedSize;
        byte expectedFill;
        ByteBuf expectedBuffer = null;
        switch (testState.expectedState) {
            case FILE_TIMES_LENGTH:
                expectedFill = FILE_BYTE;
                expectedSize = length;
                expectedBuffer = createAndFillBuffer(expectedSize, expectedFill);
                break;
            case READ_TIMES_LENGTH:
                expectedFill = READ_BYTE;
                expectedSize = length;
                expectedBuffer = createAndFillBuffer(expectedSize, expectedFill);
                break;
            case WRITE_TIMES_LENGTH:
                expectedFill = WRITE_BYTE;
                expectedSize = length;
                expectedBuffer = createAndFillBuffer(expectedSize, expectedFill);
                break;
            case FILE_TIMES_READABLE:
                expectedFill = FILE_BYTE;
                expectedSize = readable;
                expectedBuffer = createAndFillBuffer(expectedSize, expectedFill);
                break;
            case EMPTY:
                expectedFill = 0;
                expectedSize = 0;
                expectedBuffer = createAndFillBuffer(expectedSize, expectedFill);
                break;
            case EOF:
            case ILLEGAL_ARGUMENT:
            case NULL_POINTER:
            case INDEX_OUT_OF_BOUNDS:
                break;
            default:
                throw new IllegalStateException("Unexpected value: " + testState.expectedState);
        }
        testState.expectedBuffer = expectedBuffer;
    }

    private ByteBuf createAndFillBuffer(int size, byte fill) {
        ByteBuf result = Unpooled.buffer(size);
        for (int i = 0; i < size; i++) {
            result.writeByte(fill);
        }
        return result;
    }

    public void createSUT(MyBufferedChannelTest.TestState testState) throws IOException {
        ByteBufAllocator allocator;

        if (testState.configuration.writeBufferState == MyBufferedChannelTest.BufferState.NULL) {
            allocator = mock(ByteBufAllocator.class);
            when(allocator.directBuffer(anyInt())).thenReturn(null);
        } else {
            allocator = ByteBufAllocator.DEFAULT;
        }

        /* Create the BufferedChannel class */
        testState.sut = new BufferedChannel(allocator, testState.fileBundle.fileChannel, FILE_SIZE + 1, FILE_SIZE + 1);
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
            /* Read from fileChannel before and after to ensure that the file has not been changed */
            ByteBuffer fileBeforeBuffer, fileAfterBuffer;
            fileBeforeBuffer = ByteBuffer.allocate(FILE_SIZE);
            fileAfterBuffer = ByteBuffer.allocate(FILE_SIZE);

            int byteRead = readFromFileChannel(fileChannel, fileBeforeBuffer);

            assert sut.position() == 0;
            ByteBuf writeBuffer = Unpooled.buffer(FILE_SIZE);
            for (int i = 0; i < FILE_SIZE; i++) {
                writeBuffer.writeByte(WRITE_BYTE);
            }
            sut.write(writeBuffer);

            readFromFileChannel(fileChannel, fileAfterBuffer);

            /* Assert file has not changes */
            for (int i = 0; i < byteRead; i++) {
                assert fileBeforeBuffer.get(i) == fileAfterBuffer.get(i);
            }
            /* Assert something has been read */
            assert FILE_SIZE == sut.getNumOfBytesInWriteBuffer();

            /* Assert that what has been read is correct */
            for (int i = 0; i < FILE_SIZE; i++) {
                assert sut.writeBuffer.getByte(i) == writeBuffer.getByte(1);
            }

            writeBuffer.release();
        }

    }

    private int readFromFileChannel(FileChannel fileChannel, ByteBuffer buffer) throws IOException {
        long prevPos = fileChannel.position();
        fileChannel.position(0);
        int read = fileChannel.read(buffer);
        fileChannel.position(prevPos);
        buffer.flip();
        return read;
    }

}
