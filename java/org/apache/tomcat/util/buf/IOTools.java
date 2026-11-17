/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tomcat.util.buf;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class IOTools {

    private IOTools() {
        // Hide default constructor.
    }

    /**
     * Reads the {@link InputStream} to EOF and returns the bytes read in a byte array.
     *
     * @param is The stream to read
     *
     * @return An array containing all the bytes read from the stream
     *
     * @throws IOException if there is a problem reading the stream
     */
    public static byte[] readFully(InputStream is) throws IOException {
        byte[] buf = new byte[1024];
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int read;
        while ((read = is.read(buf)) >= 0) {
            out.write(buf, 0, read);
        }
        return out.toByteArray();
    }

}
