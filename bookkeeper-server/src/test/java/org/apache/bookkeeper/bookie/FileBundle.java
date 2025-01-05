package org.apache.bookkeeper.bookie;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

public class FileBundle {
    public final File file;
    public final RandomAccessFile randomAccessFile;
    public FileChannel fileChannel;

    public FileBundle(File file) throws IOException {
        // Create a temp file the filesystem
        if (file == null) {
            this.file = getTempFile();
        } else {
            this.file = file;
            this.file.deleteOnExit();
        }
        // Create a RandomAccessFile object for the newly created file
        this.randomAccessFile = new RandomAccessFile(this.file, "rw");
        // Get the file channel
        this.fileChannel = this.randomAccessFile.getChannel();
    }

    public ByteBuffer fillFileChannel(byte fillByte, int size, boolean resetPosition) throws IOException {
        long prevPos = fileChannel.position();
        ByteBuffer tempFileBuffer = ByteBuffer.allocate(size);
        for (int i = 0; i < size; i++) {
            tempFileBuffer.put(fillByte);
        }
        tempFileBuffer.flip();
        fileChannel.position(0);
        int writtenBytes = fileChannel.write(tempFileBuffer);
        fileChannel.force(true);
        assert writtenBytes == size;
        if (resetPosition){
            fileChannel.position(prevPos);
        }
        return tempFileBuffer;
    }

    private File getTempFile() throws IOException {
        File result = File.createTempFile("TMP_FILE_BUNDLE", null);
        result.deleteOnExit();
        return result;
    }

    public FileBundle copyBundle() throws IOException {
        return new FileBundle(this.file);
    }

    public void close() throws IOException {
        this.fileChannel.close();
        this.randomAccessFile.close();
    }
}