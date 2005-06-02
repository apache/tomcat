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

    /**
     * Set Virtual host id. Usually host:port combination.
     * @param ctx Context to use.
     * @param id  String that uniquely identifies this context.
     */
    public static native void setVhostId(long ctx, String id);

    /**
     * Asssociate BIOCallback for input or output data capture.
     * <br />
     * First word in the output string will contain error
     * level in the form:
     * <PRE>
     * [ERROR]  -- Critical error messages
     * [WARN]   -- Varning messages
     * [INFO]   -- Informational messages
     * [DEBUG]  -- Debugging messaged
     * </PRE>
     * Callback can use that word to determine application logging level
     * by intercepting <b>write</b> call.
     * If the <b>bio</b> is set to 0 no error messages will be displayed.
     * Default is to use the stderr output stream.
     * @param ctx Server or Client context to use.
     * @param bio BIO handle to use, created with SSL.newBIO
     * @param dir BIO direction (1 for input 0 for output).
     */
    public static native void setBIO(long ctx, long bio, int dir);

    /**
     * Set OpenSSL Option.
     * @param ctx Server or Client context to use.
     * @param options  See SSL.SSL_OP_* for option flags.
     */
    public static native void setOptions(long ctx, int options);

    /**
     * Sets the "quiet shutdown" flag for <b>ctx</b> to be
     * <b>mode</b>. SSL objects created from <b>ctx</b> inherit the
     * <b>mode</b> valid at the time and may be 0 or 1.
     * <br />
     * Normally when a SSL connection is finished, the parties must send out
     * "close notify" alert messages using L<SSL_shutdown(3)|SSL_shutdown(3)>
     * for a clean shutdown.
     * <br />
     * When setting the "quiet shutdown" flag to 1, <b>SSL.shutdown</b>
     * will set the internal flags to SSL_SENT_SHUTDOWN|SSL_RECEIVED_SHUTDOWN.
     * (<b>SSL_shutdown</b> then behaves like called with
     * SSL_SENT_SHUTDOWN|SSL_RECEIVED_SHUTDOWN.)
     * The session is thus considered to be shutdown, but no "close notify" alert
     * is sent to the peer. This behaviour violates the TLS standard.
     * The default is normal shutdown behaviour as described by the TLS standard.
     * @param ctx Server or Client context to use.
     * @param mode True to set the quiet shutdown.
     */
    public static native void setQuietShutdown(long ctx, boolean mode);

    /**
     * Cipher Suite available for negotiation in SSL handshake.
     * <br />
     * This complex directive uses a colon-separated cipher-spec string consisting
     * of OpenSSL cipher specifications to configure the Cipher Suite the client
     * is permitted to negotiate in the SSL handshake phase. Notice that this
     * directive can be used both in per-server and per-directory context.
     * In per-server context it applies to the standard SSL handshake when a
     * connection is established. In per-directory context it forces a SSL
     * renegotation with the reconfigured Cipher Suite after the HTTP request
     * was read but before the HTTP response is sent.
     * @param ctx Server or Client context to use.
     * @param ciphers An SSL cipher specification.
     */
    public static native boolean setCipherSuite(long ctx, String ciphers);

    /**
     * Set Directory of PEM-encoded CA Certificates for Client Auth
     * <br />
     * This directive sets the directory where you keep the Certificates of
     * Certification Authorities (CAs) whose clients you deal with. These are
     * used to verify the client certificate on Client Authentication.
     * <br />
     * The files in this directory have to be PEM-encoded and are accessed through
     * hash filenames. So usually you can't just place the Certificate files there:
     * you also have to create symbolic links named hash-value.N. And you should
     * always make sure this directory contains the appropriate symbolic links.
     * Use the Makefile which comes with mod_ssl to accomplish this task.
     * @param ctx Server or Client context to use.
     * @param path Directory of PEM-encoded CA Certificates for Client Auth.
     */
    public static native boolean setCARevocationPath(long ctx, String path);

    /**
     * Set File of concatenated PEM-encoded CA CRLs for Client Auth
     * <br />
     * This directive sets the all-in-one file where you can assemble the
     * Certificate Revocation Lists (CRL) of Certification Authorities (CA)
     * whose clients you deal with. These are used for Client Authentication.
     * Such a file is simply the concatenation of the various PEM-encoded CRL
     * files, in order of preference. This can be used alternatively and/or
     * additionally to <code>setCARevocationPath</code>.
     * @param ctx Server or Client context to use.
     * @param file File of concatenated PEM-encoded CA CRLs for Client Auth.
     */
    public static native boolean setCARevocationFile(long ctx, String file);

    /**
     * Set File of PEM-encoded Server CA Certificates
     * <br />
     * This directive sets the optional all-in-one file where you can assemble the
     * certificates of Certification Authorities (CA) which form the certificate
     * chain of the server certificate. This starts with the issuing CA certificate
     * of of the server certificate and can range up to the root CA certificate.
     * Such a file is simply the concatenation of the various PEM-encoded CA
     * Certificate files, usually in certificate chain order.
     * <br />
     * But be careful: Providing the certificate chain works only if you are using
     * a single (either RSA or DSA) based server certificate. If you are using a
     * coupled RSA+DSA certificate pair, this will work only if actually both
     * certificates use the same certificate chain. Else the browsers will be
     * confused in this situation.
     * @param ctx Server or Client context to use.
     * @param file File of PEM-encoded Server CA Certificates.
     */
    public static native boolean setCertificateChainFile(long ctx, String file);

    /**
     * Set Server Certificate
     * <br />
     * Point setCertificateFile at a PEM encoded certificate.  If
     * the certificate is encrypted, then you will be prompted for a
     * pass phrase.  Note that a kill -HUP will prompt again. A test
     * certificate can be generated with `make certificate' under
     * built time. Keep in mind that if you've both a RSA and a DSA
     * certificate you can configure both in parallel (to also allow
     * the use of DSA ciphers, etc.)
     * @param ctx Server or Client context to use.
     * @param file Certificate file.
     */
    public static native boolean setCertificateFile(long ctx, String file);

    /**
     * Set Server Private Key
     * <br />
     * If the key is not combined with the certificate, use this
     * directive to point at the key file.  Keep in mind that if
     * you've both a RSA and a DSA private key you can configure
     * both in parallel (to also allow the use of DSA ciphers, etc.)
     * @param ctx Server or Client context to use.
     * @param file Server Private Key file.
     */
    public static native boolean setCertificateKeyFile(long ctx, String file);

    /**
     * Set File of concatenated PEM-encoded CA Certificates for Client Auth
     * <br />
     * This directive sets the all-in-one file where you can assemble the
     * Certificates of Certification Authorities (CA) whose clients you deal with.
     * These are used for Client Authentication. Such a file is simply the
     * concatenation of the various PEM-encoded Certificate files, in order of
     * preference. This can be used alternatively and/or additionally to
     * <code>setCACertificatePath</code>.
     * @param ctx Server or Client context to use.
     * @param file File of concatenated PEM-encoded CA Certificates for
     *             Client Auth.
     */
    public static native boolean setCACertificateFile(long ctx, String file);

    /**
     * Set Directory of PEM-encoded CA Certificates for Client Auth
     * <br />
     * This directive sets the directory where you keep the Certificates of
     * Certification Authorities (CAs) whose clients you deal with. These are used
     * to verify the client certificate on Client Authentication.
     * <br />
     * The files in this directory have to be PEM-encoded and are accessed through
     * hash filenames. So usually you can't just place the Certificate files there:
     * you also have to create symbolic links named hash-value.N. And you should
     * always make sure this directory contains the appropriate symbolic links.
     * Use the Makefile which comes with mod_ssl to accomplish this task.
     * @param ctx Server or Client context to use.
     * @param path Directory of PEM-encoded CA Certificates for Client Auth.
     */
    public static native boolean setCACertificatePath(long ctx, String path);

    /**
     * Set File of concatenated PEM-encoded CA Certificates for defining
     * acceptable CA names
     * <br />
     * When a client certificate is requested by mod_ssl, a list of acceptable
     * Certificate Authority names is sent to the client in the SSL handshake.
     * These CA names can be used by the client to select an appropriate client
     * certificate out of those it has available.
     * <br />
     * If neither of the directives <code>setCADNRequestPath</code> or
     * <code>setCADNRequestFile</code> are given, then the set of acceptable
     * CA names sent to the client is the names of all the CA certificates given
     * by the <code>setCACertificateFile</code> and
     * <code>setCACertificatePath</code> directives; in other words, the names
     * of the CAs which will actually be used to verify the client certificate.
     * <br />
     * In some circumstances, it is useful to be able to send a set of acceptable
     * CA names which differs from the actual CAs used to verify the client
     * certificate - for example, if the client certificates are signed by
     * intermediate CAs. In such cases, CADNRequestPath and/or CADNRequestFile
     * can be used; the acceptable CA names are then taken from the complete
     * set of certificates in the directory and/or file specified by
     * this pair of directives.
     * <br />
     * setCADNRequestFile must specify an all-in-one file containing a
     * concatenation of PEM-encoded CA certificates.
     * @param ctx Server or Client context to use.
     * @param file File of concatenated PEM-encoded CA Certificates for defining
     *             acceptable CA names.
     */
    public static native boolean setCADNRequestFile(long ctx, String file);

    /**
     * Set Directory of PEM-encoded CA Certificates for defining acceptable
     * CA names
     * <br />
     * This optional directive can be used to specify the set of acceptable
     * CA names which will be sent to the client when a client certificate is
     * requested. See the <code>setCADNRequestFile</code> directive for more details.
     * <br />
     * The files in this directory have to be PEM-encoded and are accessed through
     * hash filenames. So usually you can't just place the Certificate files there:
     * you also have to create symbolic links named hash-value.N. And you should
     * always make sure this directory contains the appropriate symbolic links.
     * Use the Makefile which comes with mod_ssl to accomplish this task.
     * @param ctx Server or Client context to use.
     * @param path Directory of PEM-encoded CA Certificates for defining
     *             acceptable CA names.
     */
    public static native boolean setCADNRequestPath(long ctx, String path);

}
