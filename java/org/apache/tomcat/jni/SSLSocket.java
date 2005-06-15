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
     * @param thesocket The socket to close
     */
    public static native int handshake(long thesocket);

    /**
     * Shutdown a socket.
     * <br />
     * This does not actually close the socket descriptor, it just
     *      controls which calls are still valid on the socket.
     * @param thesocket The socket to close
     * @param how How to shutdown the socket.  One of:
     * <PRE>
     * SSL_SHUTDOWN_TYPE_UNSET
     * SSL_SHUTDOWN_TYPE_STANDARD
     * SSL_SHUTDOWN_TYPE_UNCLEAN
     * SSL_SHUTDOWN_TYPE_ACCURATE
     * </PRE>
     * If SSL_SHUTDOWN_TYPE_UNSET is used the default context shutdown
     * type is used.
     */
    public static native int shutdown(long thesocket, int how);

    /**
     * Close a socket.
     * @param thesocket The socket to close
     */
    public static native int close(long thesocket);

    /**
     * Destroy the socket.
     * <br />
     * This function destroys the pool used for <code>attach</code> call.
     * The main usage for this function is to allow the SSLSocket to be
     * passed as client data to the Poll.
     * @param thesocket The socket to destroy
     */
    public static native int destroy(long thesocket);

    /**
     * Send data over a network.
     * <PRE>
     * This functions acts like a blocking write by default.  To change
     * this behavior, use apr_socket_timeout_set() or the APR_SO_NONBLOCK
     * socket option.
     *
     * It is possible for both bytes to be sent and an error to be returned.
     *
     * APR_EINTR is never returned.
     * </PRE>
     * @param sock The socket to send the data over.
     * @param buf The buffer which contains the data to be sent.
     * @param offset Offset in the byte buffer.
     * @param len The number of bytes to write; (-1) for full array.
     * @return The number of bytes send.
     *
     */
    public static native int send(long sock, byte[] buf, int offset, int len);

    /**
     * Send data over a network.
     * <PRE>
     * This functions acts like a blocking write by default.  To change
     * this behavior, use apr_socket_timeout_set() or the APR_SO_NONBLOCK
     * socket option.
     *
     * It is possible for both bytes to be sent and an error to be returned.
     *
     * APR_EINTR is never returned.
     * </PRE>
     * @param sock The socket to send the data over.
     * @param buf The Byte buffer which contains the data to be sent.
     * @param offset The offset within the buffer array of the first buffer from
     *               which bytes are to be retrieved; must be non-negative
     *               and no larger than buf.length
     * @param len The maximum number of buffers to be accessed; must be non-negative
     *            and no larger than buf.length - offset
     * @return The number of bytes send.
     *
     */
    public static native int sendb(long sock, ByteBuffer buf,
                                   int offset, int len);

    /**
     * Send multiple packets of data over a network.
     * <PRE>
     * This functions acts like a blocking write by default.  To change
     * this behavior, use apr_socket_timeout_set() or the APR_SO_NONBLOCK
     * socket option.
     * The number of bytes actually sent is stored in argument 3.
     *
     * It is possible for both bytes to be sent and an error to be returned.
     *
     * APR_EINTR is never returned.
     * </PRE>
     * @param sock The socket to send the data over.
     * @param vec The array from which to get the data to send.
     *
     */
    public static native int sendv(long sock, byte[][] vec);

    /**
     * Read data from a network.
     *
     * <PRE>
     * This functions acts like a blocking read by default.  To change
     * this behavior, use apr_socket_timeout_set() or the APR_SO_NONBLOCK
     * socket option.
     * The number of bytes actually received is stored in argument 3.
     *
     * It is possible for both bytes to be received and an APR_EOF or
     * other error to be returned.
     *
     * APR_EINTR is never returned.
     * </PRE>
     * @param sock The socket to read the data from.
     * @param buf The buffer to store the data in.
     * @param offset Offset in the byte buffer.
     * @param nbytes The number of bytes to read (-1) for full array.
     * @return the number of bytes received.
     */
    public static native int recv(long sock, byte[] buf, int offset, int nbytes);

    /**
     * Read data from a network with timeout.
     *
     * <PRE>
     * This functions acts like a blocking read by default.  To change
     * this behavior, use apr_socket_timeout_set() or the APR_SO_NONBLOCK
     * socket option.
     * The number of bytes actually received is stored in argument 3.
     *
     * It is possible for both bytes to be received and an APR_EOF or
     * other error to be returned.
     *
     * APR_EINTR is never returned.
     * </PRE>
     * @param sock The socket to read the data from.
     * @param buf The buffer to store the data in.
     * @param offset Offset in the byte buffer.
     * @param nbytes The number of bytes to read (-1) for full array.
     * @param timeout The socket timeout in microseconds.
     * @return the number of bytes received.
     */
    public static native int recvt(long sock, byte[] buf, int offset,
                                   int nbytes, long timeout);

    /**
     * Read data from a network.
     *
     * <PRE>
     * This functions acts like a blocking read by default.  To change
     * this behavior, use apr_socket_timeout_set() or the APR_SO_NONBLOCK
     * socket option.
     * The number of bytes actually received is stored in argument 3.
     *
     * It is possible for both bytes to be received and an APR_EOF or
     * other error to be returned.
     *
     * APR_EINTR is never returned.
     * </PRE>
     * @param sock The socket to read the data from.
     * @param buf The buffer to store the data in.
     * @param offset Offset in the byte buffer.
     * @param nbytes The number of bytes to read (-1) for full array.
     * @return the number of bytes received.
     */
    public static native int recvb(long sock, ByteBuffer buf,
                                   int offset, int nbytes);

    /**
     * Read data from a network with timeout.
     *
     * <PRE>
     * This functions acts like a blocking read by default.  To change
     * this behavior, use apr_socket_timeout_set() or the APR_SO_NONBLOCK
     * socket option.
     * The number of bytes actually received is stored in argument 3.
     *
     * It is possible for both bytes to be received and an APR_EOF or
     * other error to be returned.
     *
     * APR_EINTR is never returned.
     * </PRE>
     * @param sock The socket to read the data from.
     * @param buf The buffer to store the data in.
     * @param offset Offset in the byte buffer.
     * @param nbytes The number of bytes to read (-1) for full array.
     * @param timeout The socket timeout in microseconds.
     * @return the number of bytes received.
     */
    public static native int recvbt(long sock, ByteBuffer buf,
                                    int offset, int nbytes, long timeout);

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
