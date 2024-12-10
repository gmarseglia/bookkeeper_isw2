package org.apache.bookkeeper.bookie;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

public class MyBufferedChannelTest {

    private static final int MAX_CAPACITY = 1024;

    private static final Logger logger = LoggerFactory.getLogger(MyBufferedChannelTest.class);

    private static Stream<Arguments> writeReadTestArguments() {
        return Stream.of(
                // Arguments.of(""),
                Arguments.of("INPUT_STRING")
        );
    }

    private File getTempFile() throws IOException {
        File result = File.createTempFile("TMP", "TMP_FILE");
        result.deleteOnExit();
        return result;
    }

    private ByteBuf getByteBufFromString(String input) {
        return Unpooled.wrappedBuffer(input.getBytes());
    }

    private String getStringFromByteBuf(ByteBuf byteBuf) {
        return byteBuf.toString(StandardCharsets.UTF_8);
    }

    @ParameterizedTest
    @MethodSource("writeReadTestArguments")
    void writeReadTest(String input) throws IOException {
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
