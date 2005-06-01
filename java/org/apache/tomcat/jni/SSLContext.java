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

/** SSL Context
 *
 * @author Mladen Turk
 * @version $Revision$, $Date$
 */

public final class SSLContext {


    /**
     * Initialize new Server context
     * @param pool The pool to use.
     * @param protocol The SSL protocol to use. It can be one of:
     * <PRE>
     * SSL_PROTOCOL_SSLV2
     * SSL_PROTOCOL_SSLV3
     * SSL_PROTOCOL_SSLV2 | SSL_PROTOCOL_SSLV3
     * SSL_PROTOCOL_TLSV1
     * SSL_PROTOCOL_ALL
     * </PRE>
     */
    public static native long initS(long pool, int protocol)
        throws Exception;

    /**
     * Initialize new Client context
     * @param pool The pool to use.
     * @param protocol The SSL protocol to use. It can be one of:
     * <PRE>
     * SSL_PROTOCOL_SSLV2
     * SSL_PROTOCOL_SSLV3
     * SSL_PROTOCOL_SSLV2 | SSL_PROTOCOL_SSLV3
     * SSL_PROTOCOL_TLSV1
     * SSL_PROTOCOL_ALL
     * </PRE>
     */
    public static native long initC(long pool, int protocol)
        throws Exception;

    /**
     * Free the resources used by the Context
     * @param ctx Server or Client context to free.
     * @return APR Status code.
     */
    public static native int free(long ctx);


}
