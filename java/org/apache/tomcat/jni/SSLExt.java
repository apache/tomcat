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
package org.apache.tomcat.jni;

/**
 * Support TLS extensions and extra methods.
 *
 * The methods are separated to make it easier for java code to
 * support existing native library - it can check if this class can
 * be loaded in order to use the extensions.
 *
 * @author Costin Manolache
 */
public final class SSLExt {


    /**
     * Set advertised NPN protocol.
     * This is only available for recent or patched openssl.
     *
     * Example: "\x06spdy/2"
     *
     * Works with TLS1, doesn't with SSL2/SSL3
     *
     * Servers sends list in ServerHelo, client selects it and
     * sends it back after ChangeChipher
     *
     * Not supported in 1.0.0, seems to be in 1.0.1 and after
     */
    public static native int setNPN(long ctx, byte[] proto, int len);

    /**
     * Get other side's advertised protocols.
     * Only works after handshake.
     */
    public static native int getNPN(long tcsock, byte[] proto);

    /**
     * Enabling dump/debugging on the socket. Both raw and decrypted
     * packets will be logged.
     */
    public static native int debug(long tcsock);

    /**
     * Server: Extract the session data associated with the socket.
     * Must be saved, keyed by session ID.
     */
    public static native byte[] getSessionData(long tcsock);

    /**
     * Server: Set the session data for a socket.
     */
    public static native int setSessionData(long tcsock, byte[] data, int len);


    /**
     * Client: get the ticket received from server, if tickets are supported.
     */
    public static native int getTicket(long tcsock, byte[] resBuf);

    /**
     * Client: set the previously received ticket.
     */
    public static native int setTicket(long tcsock, byte[] data, int len);

    /**
     * Set the key used by server to generate tickets.
     * Key must be 48 bytes.
     */
    public static native int setTicketKeys(long ctx, byte[] data, int len);

    /**
     * For client side calls. Data should be a \0 terminated string
     */
    public static native int setSNI(long tcsock, byte[] data, int len);

    /**
     * Return the last openssl error
     */
    public static native String sslErrReasonErrorString();

    public static native long sslCtxSetMode(long ctx, long mode);

    /* Allow SSL_write(..., n) to return r with 0 < r < n (i.e. report success
     * when just a single record has been written): */
    public static final int SSL_MODE_ENABLE_PARTIAL_WRITE = 0x1;

    /* Make it possible to retry SSL_write() with changed buffer location
     * (buffer contents must stay the same!); this is not the default to avoid
     * the misconception that non-blocking SSL_write() behaves like
     * non-blocking write(): */
    public static final int SSL_MODE_ACCEPT_MOVING_WRITE_BUFFER = 0x2;

    /* Don't attempt to automatically build certificate chain */
    static final int SSL_MODE_NO_AUTO_CHAIN = 0x8;

    /* Save RAM by releasing read and write buffers when they're empty. (SSL3 and
     * TLS only.)  "Released" buffers are put onto a free-list in the context
     * or just freed (depending on the context's setting for freelist_max_len). */
    public static final int SSL_MODE_RELEASE_BUFFERS = 0x10;

    // 1.1
    //static final int SSL_MODE_HANDSHAKE_CUTTHROUGH = ..;

    /**
     * SSL_set_mode
     */
    public static native long sslSetMode(long tcsock, long mode);

    public static int setNPN(long ctx, byte[] spdyNPN) {
        try {
            return SSLExt.setNPN(ctx, spdyNPN, spdyNPN.length);
        } catch (Throwable t) {
            t.printStackTrace();
            return -1;
        }
    }

    /**
     * Higher level method, checking if the specified protocol has been
     * negotiated.
     */
    public static boolean checkNPN(long tcsocket, byte[] expected) {
        byte[] npn = new byte[expected.length + 1];
        int npnLen = 0;
        try {
            npnLen = SSLExt.getNPN(tcsocket, npn);
            if (npnLen != expected.length) {
                return false;
            }
        } catch (Throwable t) {
            // ignore
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if (expected[i] != npn[i]) {
                return false;
            }
        }
        return true;
    }
}
