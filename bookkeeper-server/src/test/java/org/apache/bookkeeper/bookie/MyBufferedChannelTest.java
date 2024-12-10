package org.apache.bookkeeper.bookie;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;

public class MyBufferedChannelTest {

    private static final int MAX_CAPACITY = 1024;
    private static final boolean RESET = true;
    private static final boolean NON_RESET = false;

    private static final Logger logger = LoggerFactory.getLogger(MyBufferedChannelTest.class);

    private File getTempFile() throws IOException {
        File result = File.createTempFile("TMP", "TMP_FILE");
        result.deleteOnExit();
        return result;
    }

    private int writeStringToChannel(String input, FileChannel fileChannel, boolean resetPosition) throws IOException {
        ByteBuffer writeBuffer = ByteBuffer.allocate(1024);
        writeBuffer.put(input.getBytes());
        writeBuffer.flip();
        int byteWritten = fileChannel.write(writeBuffer);
        if (resetPosition) fileChannel.position(0);
        return byteWritten;
    }

    private String readFromFileChannel(FileChannel fileChannel) throws IOException {
        ByteBuffer readBuffer = ByteBuffer.allocate(MAX_CAPACITY);
        fileChannel.read(readBuffer);
        readBuffer.flip();
        return StandardCharsets.UTF_8.decode(readBuffer).toString();
    }

    private ByteBuf getByteBufFromString(String input) {
        return Unpooled.wrappedBuffer(input.getBytes());
    }

    private String getStringFromByteBuf(ByteBuf byteBuf){
        return byteBuf.toString(StandardCharsets.UTF_8);
    }

    @Test
    public void writeReadTest() throws IOException {
        String input = "TEST_STRING";

        // Open the File, RandomAccessFile and FileChannel
        FileBoundle fileBoundle = new FileBoundle();

        // Create the BufferedChannel
        BufferedChannel bufferedChannel = new BufferedChannel(ByteBufAllocator.DEFAULT, fileBoundle.fileChannel, MAX_CAPACITY);

        // Write into the BufferedChannel
        bufferedChannel.write(getByteBufFromString(input));

        // Read from the BufferedChannel
        ByteBuf readBuffer = Unpooled.buffer(MAX_CAPACITY);
        bufferedChannel.read(readBuffer, 0, input.length());
        logger.info(String.format("readBuffer: %s", getStringFromByteBuf(readBuffer)));

        // Assert that what was written is also read
        Assertions.assertEquals(input, getStringFromByteBuf(readBuffer));

        // Close the opened resources
        fileBoundle.close();
    }

    public void simpleWriteReadToFileChannel() throws IOException {
        String dataToWrite = "TEST";

        FileBoundle fileBoundle = new FileBoundle();
        FileChannel fileChannel = fileBoundle.fileChannel;

        // Write data to the file
        int byteWritten;
        byteWritten = writeStringToChannel(dataToWrite, fileChannel, RESET);
        System.out.printf("byteWritten: %d b%n", byteWritten);

        // Read data back from the file
        int byteRead;
        String dataRead = readFromFileChannel(fileChannel);
        byteRead = dataRead.getBytes().length;
        System.out.printf("byteRead: %d b%n", byteRead);

        // Print the data
        System.out.println("Data read from file: " + dataRead);

        // Close the channel and file
        fileBoundle.close();

        Assertions.assertEquals(dataToWrite, dataRead);
    }

    protected class FileBoundle {
        private final File file;
        private final RandomAccessFile randomAccessFile;
        private final FileChannel fileChannel;

        protected FileBoundle() throws IOException {
            // Create a temp file the filesystem
            this.file = getTempFile();
            this.file.deleteOnExit();
            // Create a RandomAccessFile object for the newly created file
            this.randomAccessFile = new RandomAccessFile(this.file, "rw");
            // Get the file channel
            this.fileChannel = this.randomAccessFile.getChannel();
        }

        protected void close() throws IOException {
            this.fileChannel.close();
            this.randomAccessFile.close();
        }
    }
}
