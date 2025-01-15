package org.apache.bookkeeper.bookie;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MyBufferedChannelTestConfigurer {

    private static final byte FILE_BYTE = (byte) 'F';
    private static final int FILE_SIZE = 1024;
    private static final byte READ_BYTE = (byte) 'R';
    private static final byte WRITE_BYTE = (byte) 'W';
    private static final Logger log = LoggerFactory.getLogger(MyBufferedChannelTestConfigurer.class);

    public MyBufferedChannelTestConfigurer() {
    }

    public void setup(MyBufferedChannelTest.TestState testState) throws IOException {
        /* Create the file, the RandomAccess and the channel */
        testState.fileBundle = new FileBundle(null);

        /* Configure the environment */
        this.configure(testState);

        /* Compute available bytes */
        int available = (int) testState.sut.position();

        /* Set up the position */
        int pos;
        switch (testState.posState) {
            case LESS_THAN_ZERO:
                pos = -1;
                break;
            case EQUAL_AS_ZERO:
                pos = 0;
                break;
            case LESS_EQUAL_THAN_AVAILABLE:
                pos = 1;
                break;
            case LESS_EQUAL_THAN_FIRST_HALF:
                pos = available / 4;
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
            case EQUAL_THAN_LENGTH:
                destSize = Math.max(length, 0);
                destBuf = ByteBufAllocator.DEFAULT.buffer(destSize);
                break;
            case GREATER_THAN_LENGTH:
                destSize = Math.max(length + 1, 0);
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
            case IO_EXCEPTION:
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

        int writeCapacity, readCapacity;
        writeCapacity = FILE_SIZE + 1;

        if (testState.configuration.readBufferState == MyBufferedChannelTest.BufferState.SECOND_HALF) {
            readCapacity = FILE_SIZE / 2 + 1;
        } else {
            readCapacity = FILE_SIZE + 1;
        }

        /* Create the BufferedChannel class */
        testState.sut = new BufferedChannel(
                allocator, testState.fileBundle.fileChannel,
                writeCapacity, readCapacity,
                FILE_SIZE + 1);
    }

    public void configure(MyBufferedChannelTest.TestState testState) throws IOException {
        FileChannel fileChannel = testState.fileBundle.fileChannel;
        ByteBuffer readFileBuffer = null;

        /* Set up the file for read buffer */
        boolean readBufferPresent = (testState.configuration.readBufferState == MyBufferedChannelTest.BufferState.NON_EMPTY ||
                testState.configuration.readBufferState == MyBufferedChannelTest.BufferState.SECOND_HALF);
        boolean filePresent = (testState.configuration.fileChannelState == MyBufferedChannelTest.BufferState.NON_EMPTY
                || testState.configuration.fileChannelState == MyBufferedChannelTest.BufferState.TRUNCATED);

        if (readBufferPresent || filePresent) {
            /* Write into fileChannel */
            readFileBuffer = testState.fileBundle.fillFileChannel(READ_BYTE, FILE_SIZE, false);

            /* Read from fileChannel */
            ByteBuffer actual = ByteBuffer.allocate(FILE_SIZE);
            readFromFileChannel(fileChannel, actual);

            /* Assert that the file has been written correctly */
            assertEqualsByteBuffer(readFileBuffer, actual);
        }

        /* Create the SUT */
        this.createSUT(testState);
        BufferedChannel sut = testState.sut;

        /* Set up the SUT read buffer */
        if (readBufferPresent) {
            int fileSize;
            int filePos;
            switch (testState.configuration.readBufferState) {
                case NON_EMPTY:
                    filePos = 0;
                    fileSize = FILE_SIZE;
                    break;
                case SECOND_HALF:
                    filePos = FILE_SIZE / 2;
                    fileSize = FILE_SIZE / 2;
                    break;
                default:
                    throw new IllegalStateException("Unexpected value: " + testState.configuration.readBufferState);
            }
            ByteBuf actual = Unpooled.buffer(fileSize);
            sut.read(actual, filePos, fileSize);

            // Assert that SUT has read what has been written to file
            assertEqualsByteBuffer(readFileBuffer, actual.nioBuffer(), fileSize);
            actual.release();
        }

        int byteToWrite = -1;
        switch (testState.configuration.fileChannelState) {
            case EMPTY:
                // Empty the file
                fileChannel.truncate(0);
                break;
            case NON_EMPTY:
                byteToWrite = FILE_SIZE;
                break;
            case TRUNCATED:
                // Empty the file
                fileChannel.truncate(0);
                byteToWrite = FILE_SIZE / 2;
                break;
            default:
                throw new IllegalStateException("Unexpected value: " + testState.configuration.fileChannelState);
        }
        if (byteToWrite >= 0) {
            /* Write into fileChannel */
            ByteBuffer expected = testState.fileBundle.fillFileChannel(FILE_BYTE, byteToWrite, true);
            expected.flip();

            /* Read from file channel */
            ByteBuffer actual = ByteBuffer.allocate(byteToWrite);
            readFromFileChannel(fileChannel, actual);

            /* Assert that what was read was what was read */
            assertEqualsByteBuffer(expected, actual);
        }

        /* Set up the SUT write buffer */
        if (testState.configuration.writeBufferState == MyBufferedChannelTest.BufferState.NON_EMPTY) {
            /* Read from fileChannel before and after to ensure that the file has not been changed */
            ByteBuffer expectedFile, actualFile;
            ByteBuf expectedWrite;
            expectedFile = ByteBuffer.allocate(FILE_SIZE);
            actualFile = ByteBuffer.allocate(FILE_SIZE);

            /* Read from fileChannel */
            int byteRead = readFromFileChannel(fileChannel, expectedFile);

            /* Fill the expectedWrite buffer */
            expectedWrite = Unpooled.buffer(FILE_SIZE);
            for (int i = 0; i < FILE_SIZE; i++) {
                expectedWrite.writeByte(WRITE_BYTE);
            }

            /* Write into SUT */
            assert sut.position() == 0;
            sut.write(expectedWrite);

            /* Read from file channel */
            readFromFileChannel(fileChannel, actualFile);

            /* Assert file has not changes */
            assertEqualsByteBuffer(expectedFile, actualFile, byteRead);

            /* Assert something has been read */
            assert FILE_SIZE == sut.getNumOfBytesInWriteBuffer();

            /* Assert that what the content of sut.writeBuffer is correct */
            for (int i = 0; i < FILE_SIZE; i++) {
                assert expectedWrite.getByte(1) == sut.writeBuffer.getByte(i);
            }
            expectedWrite.release();
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

    private void assertEqualsByteBuffer(ByteBuffer expected, ByteBuffer actual) {
        assert expected.capacity() == actual.capacity();
        for (int i = 0; i < expected.capacity(); i++) {
            assert expected.get(i) == actual.get(i);
        }
    }

    private void assertEqualsByteBuffer(ByteBuffer expected, ByteBuffer actual, int byteRead) {
        for (int i = 0; i < byteRead; i++) {
            assert expected.get(i) == actual.get(i);
        }
    }

}
