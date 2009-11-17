/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.apache.tomcat.servlets.file;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Reader;

import javax.servlet.ServletOutputStream;

import org.apache.tomcat.servlets.util.Range;


/** Like CopyUtils, but with File as source.
 *  
 *  This has the potential to be optimized with JNI
 */
public class FileCopyUtils {
    protected static int input = 2048;

    public static void copy(File f, OutputStream ostream)
            throws IOException {
        CopyUtils.copy(new FileInputStream(f), ostream);
    }


    public static void copy(File cacheEntry, 
                            PrintWriter writer,
                            String fileEncoding)
            throws IOException {
        CopyUtils.copy(new FileInputStream(cacheEntry), writer, fileEncoding);
    }

    /**
     * Copy the contents of the specified input stream to the specified
     * output stream, and ensure that both streams are closed before returning
     * (even in the face of an exception).
     *
     * @param resourceInfo The ResourceInfo object
     * @param ostream The output stream to write to
     * @param range Range the client wanted to retrieve
     * @exception IOException if an input/output error occurs
     */
    public static void copy(File cacheEntry, ServletOutputStream ostream,
                      Range range)
        throws IOException {

        IOException exception = null;

        InputStream resourceInputStream = new FileInputStream(cacheEntry);
        InputStream istream =
            new BufferedInputStream(resourceInputStream, input);
        exception = CopyUtils.copyRange(istream, ostream, range.start, range.end);

        // Clean up the input stream
        try {
            istream.close();
        } catch (Throwable t) {
            ;
        }

        // Rethrow any exception that has occurred
        if (exception != null)
            throw exception;

    }

    /**
     * Copy the contents of the specified input stream to the specified
     * output stream, and ensure that both streams are closed before returning
     * (even in the face of an exception).
     *
     * @param resourceInfo The ResourceInfo object
     * @param writer The writer to write to
     * @param range Range the client wanted to retrieve
     * @exception IOException if an input/output error occurs
     */
    public static void copy(File cacheEntry, PrintWriter writer,
                            Range range, String fileEncoding)
        throws IOException {

        IOException exception = null;

        InputStream resourceInputStream = new FileInputStream(cacheEntry);

        Reader reader;
        if (fileEncoding == null) {
            reader = new InputStreamReader(resourceInputStream);
        } else {
            reader = new InputStreamReader(resourceInputStream,
                                           fileEncoding);
        }

        exception = CopyUtils.copyRange(reader, writer, range.start, range.end);

        // Clean up the input stream
        try {
            reader.close();
        } catch (Throwable t) {
            ;
        }

        // Rethrow any exception that has occurred
        if (exception != null)
            throw exception;

    }
}
