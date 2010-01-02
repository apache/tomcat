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


package org.apache.tomcat.lite.servlet;

import java.io.IOException;

import javax.servlet.ServletInputStream;

import org.apache.tomcat.lite.io.IOInputStream;


/**
 * Wrapper around BufferInputStream.
 */
public final class ServletInputStreamImpl extends ServletInputStream {
    private IOInputStream ib;

    public ServletInputStreamImpl(IOInputStream ib) {
        this.ib = ib;
    }

    public long skip(long n)
        throws IOException {
        return ib.skip(n);
    }
    
    public void mark(int readAheadLimit)
    {
        ib.mark(readAheadLimit);
    }


    public void reset()
        throws IOException {
        ib.reset();
    }
    
    public int read()
        throws IOException {    
        return ib.read();
    }

    public int available() throws IOException {
        return ib.available();
    }

    public int read(final byte[] b) throws IOException {
        return ib.read(b, 0, b.length);
    }


    public int read(final byte[] b, final int off, final int len)
            throws IOException {
        return ib.read(b, off, len);
    }


    public int readLine(byte[] b, int off, int len) throws IOException {
        return ib.readLine(b, off, len);
    }

    public void close() throws IOException {
        // no call to super.close !
        ib.close();
    }

}
