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


package org.apache.coyote.servlet;

import java.io.IOException;
import java.io.InputStream;

import javax.servlet.ServletInputStream;


/**
 * Wrapper around MessageReader
 * 
 * @author Remy Maucherat
 * @author Jean-Francois Arcand
 * @author Costin Manolache
 */
public class ServletInputStreamImpl extends ServletInputStream {


    // ----------------------------------------------------- Instance Variables


    protected InputStream ib;


    // ----------------------------------------------------------- Constructors


    public ServletInputStreamImpl(InputStream ib) {
        this.ib = ib;
    }

    // --------------------------------------------- ServletInputStream Methods

    public long skip(long n)
        throws IOException {
        return ib.skip(n);
    }
    
    public void mark(int readAheadLimit)
    {
        //try {
        ib.mark(readAheadLimit);
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
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
        return super.readLine(b, off, len);
    }

    /** 
     * Close the stream
     * Since we re-cycle, we can't allow the call to super.close()
     * which would permantely disable us.
     */
    public void close() throws IOException {
        ib.close();
    }

}
