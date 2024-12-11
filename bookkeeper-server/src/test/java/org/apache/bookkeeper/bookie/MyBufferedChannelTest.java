package org.apache.bookkeeper.bookie;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import org.checkerframework.checker.units.qual.N;
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

class MyBufferedChannelTest {

    private static final int MAX_CAPACITY = 1024;

    private static final Logger logger = LoggerFactory.getLogger(MyBufferedChannelTest.class);

    private static Stream<Arguments> writeReadTestArguments() {
        return Stream.of(
                Arguments.of(new WriteReadTestArgument(null, "")),
                Arguments.of(new WriteReadTestArgument("", "")),
                Arguments.of(new WriteReadTestArgument("TEST_STRING", "TEST_STRING"))
        );
    }

    private File getTempFile() throws IOException {
        File result = File.createTempFile("TMP", "TMP_FILE");
        result.deleteOnExit();
        return result;
    }

    private ByteBuf getByteBufFromString(String input) {
        if (input == null)
            return null;
        return Unpooled.wrappedBuffer(input.getBytes());
    }

    private String getStringFromByteBuf(ByteBuf byteBuf) {
        return byteBuf.toString(StandardCharsets.UTF_8);
    }

    @ParameterizedTest
    @MethodSource("writeReadTestArguments")
    void writeReadTest(WriteReadTestArgument args) throws IOException {
        // Open the File, RandomAccessFile and FileChannel
        FileBundle fileBundle = new FileBundle();

        // Create the BufferedChannel
        BufferedChannel bufferedChannel = new BufferedChannel(ByteBufAllocator.DEFAULT, fileBundle.fileChannel, MAX_CAPACITY);

        // Write into the BufferedChannel
        try {
            ByteBuf writeBuffer = null;
            if (args.valid){
                writeBuffer = getByteBufFromString(args.input);
            }
            bufferedChannel.write(writeBuffer);
        } catch (NullPointerException e){
            Assertions.assertFalse(args.valid);
            return;
        }

        // Read from the BufferedChannel
        ByteBuf readBuffer = Unpooled.buffer(MAX_CAPACITY);
        bufferedChannel.read(readBuffer, 0, args.input.length());
        logger.info(String.format("readBuffer: %s", getStringFromByteBuf(readBuffer)));

        // Assert that what was written is also read
        String readString = getStringFromByteBuf(readBuffer);
        Assertions.assertEquals(args.expected, readString);

        // Close the opened resources
        fileBundle.close();
    }

    protected static class WriteReadTestArgument {
        protected final boolean valid;
        protected final String input;
        protected final String expected;

        public WriteReadTestArgument(String input, String expected) {
            this.valid = (input != null);
            this.input = input;
            this.expected = expected;
        }

        @Override
        public String toString() {
            return "{" +
                    "input='" + input + '\'' +
                    ", expected='" + expected + '\'' +
                    '}';
        }
    }

    protected class FileBundle {
        private final File file;
        private final RandomAccessFile randomAccessFile;
        private final FileChannel fileChannel;

        protected FileBundle() throws IOException {
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
