/*
 * Copyright 1999,2004 The Apache Software Foundation.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
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

import java.io.BufferedReader;
import java.io.IOException;



/**
 * Coyote implementation of the buffred reader.
 * 
 * @author Remy Maucherat
 */
public final class ServletReaderImpl extends BufferedReader {

    private BufferedReader ib;

    public ServletReaderImpl(BufferedReader ib) {
        super(ib, 1);
        this.ib = ib;
    }
    
    public void close()
        throws IOException {
        ib.close();
    }


    public int read()
        throws IOException {
        return ib.read();
    }


    public int read(char[] cbuf)
        throws IOException {
        return ib.read(cbuf, 0, cbuf.length);
    }


    public int read(char[] cbuf, int off, int len)
        throws IOException {
        return ib.read(cbuf, off, len);
    }


    public long skip(long n)
        throws IOException {
        return ib.skip(n);
    }


    public boolean ready()
        throws IOException {
        return ib.ready();
    }


    public boolean markSupported() {
        return true;
    }


    public void mark(int readAheadLimit)
        throws IOException {
        ib.mark(readAheadLimit);
    }


    public void reset()
        throws IOException {
        ib.reset();
    }


    // TODO: move the readLine functionality to base coyote IO
    public String readLine()
        throws IOException {
        return ib.readLine();

    }
}
