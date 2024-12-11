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

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

class MyBufferedChannelTest {

    private static final int MAX_CAPACITY = 1024;

    private static final Logger logger = LoggerFactory.getLogger(MyBufferedChannelTest.class);

    private static Stream<Arguments> writeReadTestArguments() {
        return Stream.of(
                Arguments.of(new WriteReadTestArgument(null, "", false, "")),
                Arguments.of(new WriteReadTestArgument("", "", false, "")),
                Arguments.of(new WriteReadTestArgument("TEST_STRING", "TEST_STRING", false, "")),
                Arguments.of(new WriteReadTestArgument("TEST_STRING", "TEST_STRING", true, "TEST_STRING"))
        );
    }

    private ByteBuf getByteBufFromString(String input) {
        if (input == null)
            return null;
        return Unpooled.wrappedBuffer(input.getBytes());
    }

    private String getStringFromByteBuf(ByteBuf byteBuf) {
        if (byteBuf == null) {
            return "<NULL>";
        }
        return byteBuf.toString(StandardCharsets.UTF_8);
    }

    private ByteBuf readFromFileChannel(FileChannel fileChannel, long position, int count) throws IOException {
        ByteBuffer tempBuffer = ByteBuffer.allocate(count);
        fileChannel.position(position);
        fileChannel.read(tempBuffer);
        tempBuffer.flip();
        return Unpooled.wrappedBuffer(tempBuffer);
    }

    @ParameterizedTest
    @MethodSource("writeReadTestArguments")
    void writeReadTest(WriteReadTestArgument args) throws IOException {
        // Open the File, RandomAccessFile and FileChannel
        FileBundle fileBundle = new FileBundle(null);

        // Create the BufferedChannel
        BufferedChannel bufferedChannel = new BufferedChannel(ByteBufAllocator.DEFAULT, fileBundle.fileChannel, MAX_CAPACITY);

        // Write into the BufferedChannel
        try {
            ByteBuf writeBuffer = null;
            if (args.valid) {
                writeBuffer = getByteBufFromString(args.input);
            }
            bufferedChannel.write(writeBuffer);
        } catch (NullPointerException e) {
            // Check that if a NullPointerException was thrown, it's due to invalid args
            Assertions.assertFalse(args.valid);
            return;
        }

        // Read from the BufferedChannel
        ByteBuf readBuffer = Unpooled.buffer(MAX_CAPACITY);
        bufferedChannel.read(readBuffer, 0, args.input.length());
        logger.info(String.format("readBuffer: %s", getStringFromByteBuf(readBuffer)));

        // Assert that what was read from the buffered channel is what was written
        ByteBuf expectedBuffer = getByteBufFromString(args.expected);
        Assertions.assertEquals(expectedBuffer, readBuffer);

        // Force flush in according to argument
        if (args.forceFlush)
            bufferedChannel.flush();

        // Read directly from file channel
        ByteBuf backBuffer = readFromFileChannel(fileBundle.fileChannel, 0, args.input.length());
        logger.info(String.format("backBuffer: %s", getStringFromByteBuf(backBuffer)));

        // Assert that what was read from file is what is expected to have been flushed
        ByteBuf expectedOnFileBuffer = getByteBufFromString(args.expectedOnFile);
        Assertions.assertEquals(expectedOnFileBuffer, backBuffer);

        // Close the opened resources
        fileBundle.close();
    }

    protected static class WriteReadTestArgument {
        protected final boolean valid;
        protected final String input;
        protected final String expected;
        protected final boolean forceFlush;
        private final String expectedOnFile;

        public WriteReadTestArgument(String input, String expected, boolean forceFlush, String expectedOnFile) {
            this.valid = (input != null);
            this.input = input;
            this.expected = expected;
            this.forceFlush = forceFlush;
            this.expectedOnFile = expectedOnFile;
        }

        @Override
        public String toString() {
            return "{" +
                    "input='" + input + '\'' +
                    ", forceFlush='" + forceFlush + '\'' +
                    '}';
        }
    }

    protected class FileBundle {
        private final File file;
        private final RandomAccessFile randomAccessFile;
        private final FileChannel fileChannel;

        public FileBundle(File file) throws IOException {
            // Create a temp file the filesystem
            if (file == null) {
                this.file = getTempFile();
            } else {
                this.file = file;
            }
            this.file.deleteOnExit();
            // Create a RandomAccessFile object for the newly created file
            this.randomAccessFile = new RandomAccessFile(this.file, "rw");
            // Get the file channel
            this.fileChannel = this.randomAccessFile.getChannel();
        }

        private File getTempFile() throws IOException {
            File result = File.createTempFile("TMP", "TMP_FILE");
            result.deleteOnExit();
            return result;
        }

        protected FileBundle copyBundle() throws IOException {
            return new FileBundle(this.file);
        }

        protected void close() throws IOException {
            this.fileChannel.close();
            this.randomAccessFile.close();
        }
    }
}
