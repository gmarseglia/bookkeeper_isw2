package org.apache.bookkeeper.bookie;

import org.junit.Test;
import org.junit.jupiter.api.Assertions;

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
        if (resetPosition)
            fileChannel.position(0);
        return byteWritten;
    }

    private String readFromFileChannel(FileChannel fileChannel) throws IOException {
        ByteBuffer readBuffer = ByteBuffer.allocate(MAX_CAPACITY);
        fileChannel.read(readBuffer);
        readBuffer.flip();
        return StandardCharsets.UTF_8.decode(readBuffer).toString();
    }

    @Test
    public void simpleWriteReadToFileChannel() throws IOException {
        String dataToWrite = "A";

        // Create a temp file the filesystem
        File provaFile = getTempFile();

        // Create a RandomAccessFile object for the newly created file
        RandomAccessFile file = new RandomAccessFile(provaFile, "rw");

        // Get the file channel
        FileChannel fileChannel = file.getChannel();

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
        fileChannel.close();
        file.close();

        Assertions.assertEquals(dataToWrite, dataRead);
    }
}
