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
    private static final int UNPERSISTED_LIMIT = 512;

    private static final Logger logger = LoggerFactory.getLogger(MyBufferedChannelTest.class);

    private static String buildStringOfLength(int n, char c) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < n; i++) {
            builder.append(c);
        }
        return builder.toString();
    }

    private static Stream<Arguments> writeReadTestArguments() {
        String ltUnpString = buildStringOfLength(UNPERSISTED_LIMIT - 1, 'a');
        String ltCapString = buildStringOfLength(MAX_CAPACITY - 1, 'a');
        String geCapString = buildStringOfLength(MAX_CAPACITY, 'a');
        String doubleCapString = buildStringOfLength(MAX_CAPACITY * 2, 'a');
        return Stream.of(
                Arguments.of(new WRTArgument(0, null, false, "")),
                Arguments.of(new WRTArgument(0, "", false, "")),
                Arguments.of(new WRTArgument(0, ltCapString, false, "")),
                Arguments.of(new WRTArgument(0, ltCapString, true, ltCapString)),
                Arguments.of(new WRTArgument(0, geCapString, false, geCapString)),
                Arguments.of(new WRTArgument(0, geCapString, true, geCapString)),
                Arguments.of(new WRTArgument(0, doubleCapString, false, doubleCapString)),
                Arguments.of(new WRTArgument(0, doubleCapString, true, doubleCapString)),
                Arguments.of(new WRTArgument(UNPERSISTED_LIMIT, ltUnpString, false, "")),
                Arguments.of(new WRTArgument(UNPERSISTED_LIMIT, ltUnpString, true, ltUnpString)),
                Arguments.of(new WRTArgument(UNPERSISTED_LIMIT, ltCapString, false, ltCapString)),
                Arguments.of(new WRTArgument(UNPERSISTED_LIMIT, ltCapString, true, ltCapString))
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
    void writeReadTest(WRTArgument args) throws IOException {
        // Open the File, RandomAccessFile and FileChannel
        FileBundle fileBundle = new FileBundle(null);

        // Create the BufferedChannel
        BufferedChannel bufferedChannel = new BufferedChannel(
                ByteBufAllocator.DEFAULT,
                fileBundle.fileChannel,
                MAX_CAPACITY,
                args.unpersistedBytes);

        // Write into the BufferedChannel
        ByteBuf writeBuffer = null;
        if (args.valid) {
            writeBuffer = getByteBufFromString(args.input);
        }
        try {
            bufferedChannel.write(writeBuffer);
        } catch (NullPointerException e) {
            // Check that if a NullPointerException was thrown, it's due to invalid args
            Assertions.assertFalse(args.valid);
            return;
        }

        // Assert that the position on the file channel has been correctly updated
        Assertions.assertEquals(writeBuffer.array().length, bufferedChannel.position());

        // Read from the BufferedChannel
        ByteBuf readBuffer = Unpooled.buffer(args.input.length());
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


    protected static class WRTArgument {
        protected final boolean valid;
        protected final String input;
        protected final String expected;
        protected final boolean forceFlush;
        private final String expectedOnFile;
        private final int unpersistedBytes;

        public WRTArgument(int unpersistedBytes, String input, boolean forceFlush, String expectedOnFile) {
            this.unpersistedBytes = unpersistedBytes;
            this.valid = (input != null);
            this.input = input;
            this.expected = input;
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

    protected static class FileBundle {
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
