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
package org.apache.tomcat.test.watchdog;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Vector;


public class WatchdogHttpClient {
    private static final String CRLF         = "\r\n";
    private static final int LINE_FEED       = 10;

    static int debug = 0;

    public static void dispatch(WatchdogTestImpl client) throws Exception {
        HashMap requestHeaders = client.requestHeaders;
        String host = client.host;
        int port = client.port;
        String content = client.content;
        String request = client.request;

        // XXX headers are ignored
        Socket socket;
        try {
            socket = new Socket( host, port );
        } catch (IOException ex) {
            System.out.println( " Socket Exception: " + ex );
            return;
        }
        socket.setSoTimeout(10000);

        //socket obtained, rebuild the request.
        rebuildRequest(client, client.request, socket);

        InputStream in = new CRBufferedInputStream( socket.getInputStream() );

        // Write the request
        socket.setSoLinger( true, 1000 );

        OutputStream out = new BufferedOutputStream(
                               socket.getOutputStream() );
        StringBuffer reqbuf = new StringBuffer( 128 );

        // set the Host header
        client.setHeaderDetails( "Host:" + host + ":" + port, requestHeaders, true );

        // set the Content-Length header
        if ( content != null ) {
            client.setHeaderDetails( "Content-Length:" + content.length(),
                              requestHeaders, true );
        }

        // set the Cookie header
        if ( client.testSession != null ) {
            client.cookieController = ( CookieController ) client.sessionHash.get( client.testSession );

            if ( client.cookieController != null ) {

                String releventCookieString = client.cookieController.applyRelevantCookies( client.requestURL );

                if ( ( releventCookieString != null ) && ( !releventCookieString.trim().equals( "" ) ) ) {
                    client.setHeaderDetails( "Cookie:" + releventCookieString, requestHeaders, true );
                }
            }
        }

        if ( debug > 0 ) {
            System.out.println( " REQUEST: " + request );
        }
        reqbuf.append( client.request ).append( CRLF );

        // append all request headers
        if ( !requestHeaders.isEmpty() ) {
            Iterator iter = requestHeaders.keySet().iterator();

            while ( iter.hasNext() ) {
                StringBuffer tmpBuf = new StringBuffer(32);
                String headerKey = ( String ) iter.next();
                        ArrayList values = (ArrayList) requestHeaders.get( headerKey );
                        String[] value = (String[]) values.toArray( new String[ values.size() ] );
                tmpBuf.append( headerKey ).append(": ");
                        for ( int i = 0; i < value.length; i++ ) {
                    if ((i + 1) == value.length) {
                                    tmpBuf.append( value[ i ] );
                    } else {
                        tmpBuf.append( value[ i ] ).append(", ");
                    }
                        }
                            if ( debug > 0 ) {
                                System.out.println( " REQUEST HEADER: " + tmpBuf.toString());
                            }
                tmpBuf.append( CRLF );
                reqbuf.append(tmpBuf.toString());
            }
        }

        /*

        if ( ( testSession != null ) && ( sessionHash.get( testSession ) != null ) ) {
            System.out.println("Sending Session Id : " + (String)sessionHash.get( testSession ) );
            pw.println("JSESSIONID:" + (String)sessionHash.get( testSession) );
        }

        */

        if ( request.indexOf( "HTTP/1." ) > -1 ) {
            reqbuf.append( "" ).append( CRLF );
        }

        // append request content
        if ( content != null ) {
            reqbuf.append( content );
            // XXX no CRLF at the end -see HTTP specs!
        }

        byte[] reqbytes = reqbuf.toString().getBytes();

        try {
            // write the request
            out.write( reqbytes, 0, reqbytes.length );
            out.flush();
        } catch ( Exception ex1 ) {
            System.out.println( " Error writing request " + ex1 );
                if ( debug > 0 ) {
                        System.out.println( "Message: " + ex1.getMessage() );
                        ex1.printStackTrace();
                }
        }

        // read the response
        try {

                client.responseLine = read( in );

                if ( debug > 0 ) {
                        System.out.println( " RESPONSE STATUS-LINE: " + client.responseLine );
                }

                client.headers = parseHeaders( client, in );

            byte[] result = readBody( in );

            if ( result != null ) {
                client.responseBody = result;
                        if ( debug > 0 ) {
                            System.out.println( " RESPONSE BODY:\n" + new String( client.responseBody ) );
                        }
                }

        } catch ( SocketException ex ) {
            System.out.println( " Socket Exception: " + ex );
        } finally {
                if ( debug > 0 ) {
                        System.out.println( " closing socket" );
                }
                socket.close();
                socket = null;
            }

    }

    /**
     * <code>readBody</code> reads the body of the response
     * from the InputStream.
     *
     * @param input an <code>InputStream</code>
     * @return a <code>byte[]</code> representation of the response
     */
    private static byte[] readBody( InputStream input ) {
        StringBuffer sb = new StringBuffer( 255 );
        while ( true ) {
            try {
                int ch = input.read();

                if ( ch < 0 ) {
                    if ( sb.length() == 0 ) {
                        return ( null );
                    } else {
                        break;
                    }
                }
                sb.append( ( char ) ch );

            } catch ( IOException ex ) {
                return null;
            }
        }
        return sb.toString().getBytes();
    }


    /**
     * Read a line from the specified servlet input stream, and strip off
     * the trailing carriage return and newline (if any).  Return the remaining
     * characters that were read as a string.7
     *
     * @returns The line that was read, or <code>null</code> if end of file
     *  was encountered
     *
     * @exception IOException if an input/output error occurred
     */
    private static String read( InputStream input ) throws IOException {
        // Read the next line from the input stream
        StringBuffer sb = new StringBuffer();

        while ( true ) {
            try {
                int ch = input.read();
                //              System.out.println("XXX " + (char)ch );
                if ( ch < 0 ) {
                    if ( sb.length() == 0 ) {
                        if ( debug > 0 )
                            System.out.println( " Error reading line " + ch + " " + sb.toString() );
                        return "";
                    } else {
                        break;
                    }
                } else if ( ch == LINE_FEED ) {
                    break;
                }

                sb.append( ( char ) ch );
            } catch ( IOException ex ) {
                System.out.println( " Error reading : " + ex );
                debug = 1;

                if ( debug > 0 ) {
                    System.out.println( "Partial read: " + sb.toString() );
                    ex.printStackTrace();
                }
                input.close();
                break;
            }
        }
        return  sb.toString();
    }


    // ==================== Code from JSERV !!! ====================
    /**
     * Parse the incoming HTTP request headers, and set the corresponding
     * request properties.
     *
     *
     * @exception IOException if an input/output error occurs
     */
    private static HashMap parseHeaders( WatchdogTestImpl client, InputStream is ) throws IOException {
        HashMap headers = new HashMap();
        client.cookieVector = new Vector();

        while ( true ) {
            // Read the next header line
            String line = read( is );

            if ( ( line == null ) || ( line.length() < 1 ) ) {
                break;
            }

            client.parseHeader( line, headers, false );

            if ( debug > 0 ) {
                System.out.println( " RESPONSE HEADER: " + line );
            }

        }

        if ( client.testSession != null ) {
            client.cookieController = ( CookieController ) client.sessionHash.get( client.testSession );

            if ( client.cookieController != null ) {
                client.cookieController.recordAnyCookies( client.cookieVector, client.requestURL );
            }
        }

        return headers;
    }


    /**
     * Private utility method to 'massage' a request string that
     * may or may not have replacement markers for the request parameters.
     *
     * @param req the request to manipulate
     * @param socket local socket.  Used to rebuild specified query strings.
     *
     * @exception Exception if an error occurs
     */
    private static void rebuildRequest(WatchdogTestImpl client, String req, Socket socket) throws Exception {
        client.request = client.replaceMarkers(req, socket );
        String addressString = client.request.substring( client.request.indexOf( "/" ), client.request.indexOf( "HTTP" ) ).trim();

        if ( addressString.indexOf( "?" ) > -1 ) {
            addressString = addressString.substring( 0, addressString.indexOf( "?" ) ) ;
        }

        client.requestURL = new URL( "http", client.host, client.port, addressString );
    }


    /**
     * <code>CRBufferedInputStream</code> is a modified version of
     * the java.io.BufferedInputStream class.  The fill code is
     * the same, but the read is modified in that if a carriage return
     * is found in the response stream from the target server,
     * it will skip that byte and return the next in the stream.
     */
    private static class CRBufferedInputStream extends BufferedInputStream {
        private static final int CARRIAGE_RETURN = 13;

        private static final int DEFAULT_BUFFER = 2048;

        /**
         * Creates a new <code>CRBufferedInputStream</code> instance.
         *
         * @param in an <code>InputStream</code> value
         */
        public CRBufferedInputStream( InputStream in ) {
            super( in, DEFAULT_BUFFER );
        }

        /**
         * <code>read</code> reads a single byte value per call.
         * If, the byte read, is a carriage return, the next byte
         * in the stream in returned instead.
         *
         * @return an <code>int</code> value
         * @exception IOException if an error occurs
         */
        public int read() throws IOException {
            if ( in == null ) {
                throw new IOException ( "Stream closed" );
            }
            if ( pos >= count ) {
                fill();
                if ( pos >= count ) {
                    return -1;
                }
            }
            int val = buf[pos++] & 0xff;
            if ( val == CARRIAGE_RETURN ) {
                if (pos >= count) {
                    fill();
                    if (pos >= count) {
                       return -1;
                    }
                }
                return buf[pos++] & 0xff;
            }
            return val;
        }

        /**
         * <code>fill</code> is used to fill the internal
         * buffer used by this BufferedInputStream class.
         *
         * @exception IOException if an error occurs
         */
        private void fill() throws IOException {
            if (markpos < 0) {
                pos = 0;        /* no mark: throw away the buffer */
            } else if (pos >= buf.length)  {/* no room left in buffer */
                if (markpos > 0) {  /* can throw away early part of the buffer */
                    int sz = pos - markpos;
                    System.arraycopy(buf, markpos, buf, 0, sz);
                    pos = sz;
                    markpos = 0;
                } else if (buf.length >= marklimit) {
                    markpos = -1;   /* buffer got too big, invalidate mark */
                    pos = 0;    /* drop buffer contents */
                } else {        /* grow buffer */
                    int nsz = pos * 2;
                    if (nsz > marklimit)
                        nsz = marklimit;
                    byte nbuf[] = new byte[nsz];
                    System.arraycopy(buf, 0, nbuf, 0, pos);
                    buf = nbuf;
                }
            }
            count = pos;
            int n = in.read(buf, pos, buf.length - pos);
            if (n > 0) {
                count = n + pos;
            }
        }
    }

}
