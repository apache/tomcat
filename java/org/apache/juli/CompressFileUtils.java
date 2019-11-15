package org.apache.juli;

import java.io.*;
import java.util.zip.GZIPOutputStream;

/**
 * A set of utility class used to compress files into different formats.
 */
public final class CompressFileUtils {

    /**
     * The extension GZIP compressed files.
     */
    private static final String GZIP_COMPRESSED_FILE_EXTENSION = ".gz";

    /**
     *  Compresses the given instance of {@link File} <code>file</code> into a GZIP format.
     *  <p>
     *  The compressed file will be in the same directory as the one pointed to by the given
     *  <code>file</code>.
     *
     *  @param file An instance of {@link File} to be compressed into GZIP format.
     *  @return An instance of {@link File} that is GZIP compressed.
     *  @throws IOException If an error occured while reading/writing to the GZIP compressed file.
     */
    public static File gzipCompress(File file) throws IOException {

        // try to get handle of the file (if it exists).
        FileInputStream fileInputStream = new FileInputStream(file);

        // Create a handle to store the compressed rotated log file.
        // TODO: probably trim extension (in future) is needed.
        String compressedFileAbsolutePath = file.getAbsolutePath() + GZIP_COMPRESSED_FILE_EXTENSION;
        File compressedFile = new File(compressedFileAbsolutePath);

        // Perform compression.
        GZIPOutputStream compressedFileOutputStream = new GZIPOutputStream(new FileOutputStream(compressedFile));
        byte[] buffer = new byte[1024];
        int length;
        while((length = fileInputStream.read(buffer)) != -1) {
            compressedFileOutputStream.write(buffer, 0, length);
        }
        fileInputStream.close();
        compressedFileOutputStream.finish();
        compressedFileOutputStream.close();

        // the input file has been compressed successfully.
        return compressedFile;
    }
}
