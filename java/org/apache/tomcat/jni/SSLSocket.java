/*
 *  Copyright 1999-2004 The Apache Software Foundation
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.apache.tomcat.jni;

/* Import needed classes */
import java.nio.ByteBuffer;

/** SSL Socket
 *
 * @author Mladen Turk
 * @version $Revision$, $Date$
 */

public class SSLSocket {

    /**
     * Attach APR socket on a SSL connection.
     * @param ctx SSLContext to use.
     * @param sock APR Socket that already did physical connect.
     * @param pool The pool to use
     * @param pool The pool to use
     * @return The new socket that has been set up.
     */
    public static native long attach(long ctx, long sock, long pool)
        throws Exception;

    /**
     * Do a SSL handshake.
     * @param thesocket The socket to use
     */
    public static native int handshake(long thesocket);

    /**
     * Retrun SSL Info parameter as byte array.
     *
     * @param sock The socket to read the data from.
     * @param id Parameter id.
     * @return Byte array containing info id value.
     */
    public static native byte[] getInfoB(long sock, int id)
        throws Exception;

    /**
     * Retrun SSL Info parameter as String.
     *
     * @param sock The socket to read the data from.
     * @param id Parameter id.
     * @return String containing info id value.
     */
    public static native String getInfoS(long sock, int id)
        throws Exception;

    /**
     * Retrun SSL Info parameter as integer.
     *
     * @param sock The socket to read the data from.
     * @param id Parameter id.
     * @return Integer containing info id value or -1 on error.
     */
    public static native int getInfoI(long sock, int id)
        throws Exception;

}
