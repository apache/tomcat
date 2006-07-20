/*
 * Copyright 1999-2002,2004-2005 The Apache Software Foundation.
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

package org.apache.catalina.ssi;

import java.io.ByteArrayOutputStream;
import javax.servlet.ServletOutputStream;


/**
 * Class that extends ServletOuputStream, used as a wrapper from within
 * <code>SsiInclude</code>
 *
 * @author Bip Thelin
 * @version $Revision: 304045 $, $Date: 2005-08-04 14:18:30 +0200 (jeu., 04 août 2005) $
 * @see ServletOutputStream and ByteArrayOutputStream
 */
public class ByteArrayServletOutputStream extends ServletOutputStream {
    /**
     * Our buffer to hold the stream.
     */
    protected ByteArrayOutputStream buf = null;


    /**
     * Construct a new ServletOutputStream.
     */
    public ByteArrayServletOutputStream() {
        buf = new ByteArrayOutputStream();
    }


    /**
     * @return the byte array.
     */
    public byte[] toByteArray() {
        return buf.toByteArray();
    }


    /**
     * Write to our buffer.
     *
     * @param b The parameter to write
     */
    public void write(int b) {
        buf.write(b);
    }
}
