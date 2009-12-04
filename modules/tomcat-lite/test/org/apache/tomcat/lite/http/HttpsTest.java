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

package org.apache.tomcat.lite.http;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import junit.framework.TestCase;

import org.apache.commons.codec.binary.Base64;
import org.apache.tomcat.lite.TestMain;
import org.apache.tomcat.lite.http.HttpChannel.HttpService;
import org.apache.tomcat.lite.io.BBuffer;
import org.apache.tomcat.lite.io.SslConnector;
import org.apache.tomcat.util.buf.ByteChunk;

public class HttpsTest extends TestCase {
    
    static int port = 8443;
    HttpConnector con;
    HttpConnector httpClient;
    
    public void setUp() {
        Logger.getLogger("SSL").setLevel(Level.FINEST);
    }

    public void tearDown() {
        if (con != null) {
            con.stop();
        }
        if (httpClient != null) {
            httpClient.stop();
        }
    }
    
    static HttpConnector initServer(int port) throws IOException {
        SslConnector sslCon = new SslConnector()
            .setKeysResource("org/apache/tomcat/lite/http/test.keystore", "changeit");
        
        HttpConnector con = new HttpConnector(sslCon);
        con.setPort(port);

        addService(con);
        return con;
    }
    
    private static void addService(HttpConnector con) throws IOException {
        con.setMaxHttpPoolSize(0);
//        con.setDebug(true);
//        con.setDebugHttp(true);
        
        con.getDispatcher().setDefaultService(new HttpService() {
            @Override
            public void service(HttpRequest httpReq, HttpResponse httpRes)
                    throws IOException {
                httpRes.setHeader("Connection", "close");
                httpRes.getBodyWriter().write("Hello");
            }
        });
        con.start();
    }

    public void testSimpleClient() throws Exception {
        SslConnector sslCon = new SslConnector();
        httpClient = new HttpConnector(sslCon);
//        httpClient.setDebug(true);
//        httpClient.setDebugHttp(true);
        httpClient.setMaxHttpPoolSize(0);
        con = initServer(++port);
        checkResponse(httpClient);
    }
    
    
    public void testSimpleServer() throws Exception {
        con = initServer(++port);
        ByteChunk res = TestMain.getUrl("https://localhost:" + port +
            "/examples/servlets/servlet/HelloWorldExample");
        assertTrue(res.toString().indexOf("Hello") >= 0);
    }       

    
    private void checkResponse(HttpConnector httpCon) throws Exception {
        HttpRequest ch = httpCon.request("localhost", port);
        ch.setRequestURI("/hello");
        ch.setProtocol("HTTP/1.0");
        ch.send();
        BBuffer res = ch.readAll();
        
        assertTrue(res.toString().indexOf("Hello") >= 0);
    }    
    
    public void testSimpleClient20() throws Exception {
        SslConnector sslCon = new SslConnector();
        httpClient = new HttpConnector(sslCon);
//        httpClient.setDebug(true);
//        httpClient.setDebugHttp(true);

        con = initServer(++port);
        for (int i = 0; i < 20; i++) {
            checkResponse(httpClient);
        }
    }
    
    public void testSimpleRequestGoogle() throws Exception {
        SslConnector sslCon = new SslConnector();
        httpClient = new HttpConnector(sslCon);
        HttpRequest client = httpClient.request("www.google.com", 443);
        client.getHttpChannel().setIOTimeout(2000);
        client.setRequestURI("/accounts/ServiceLogin");
        client.send();
        
        BBuffer res = client.readAll();
        assertTrue(res.toString().indexOf("<title>Google Accounts</title>") > 0);
    }
        

    /** 
     * Use byte[] for cert - avoids using the store file.
     * This may be nice for:
     * - tests without a file
     * - frameworks managing configs - no need to deal with files 
     * @throws Exception 
     * 
     */
    public void testSeverWithKeys() throws Exception {
        Base64 b64 = new Base64();

        
        byte[] keyBytes = b64.decode(PRIVATE_KEY);

        SslConnector sslCon = new SslConnector()
            .setKeys(CERTIFICATE, keyBytes);
            
        HttpConnector con = new HttpConnector(sslCon);
        con.setPort(++port);
        addService(con);
        
        ByteChunk res = TestMain.getUrl("https://localhost:" + port +
            "/examples/servlets/servlet/HelloWorldExample");
        assertTrue(res.toString().indexOf("Hello") >= 0);
        
    }
    
    
    //byte[] encoded = 
    //            SslConnector.getCertificateFromStore(
    //                    "test/org/apache/tomcat/lite/http/test.keystore", 
    //                "changeit");
    //byte[] encoded = 
    //            SslConnector.getPrivateKeyFromStore(
    //        "test/org/apache/tomcat/lite/http/test.keystore", 
    //        "changeit");
    //        
    //byte[] b64b = b64.encode(encoded);
    //System.out.println(new String(b64b));
    static String PRIVATE_KEY =
        "MIICdwIBADANBgkqhkiG9w0BAQEFAASCAmEwggJdAgEAAoGBALGOFqjC4Fefz0oOcfJeS8eLV8jY" +
        "zA3sHUnTKmASfgfhG8prWUgSEq7O/849MrBysiKpIvTN8R+ykV4QCAxauGURDsNI2ZtAv23YX2Mb" +
        "cfYfYqD0tgHEn355HKey0ICgmRuq3norlUWAH3hRv5qiQMc0UIhNrmdTs0jyvQ8E8AlZAgMBAAEC" +
        "gYBybr8P2Tk5gBfbBOBPcpKocpgLLB6nQmvF7sC61nA/p8d/eBw8pNlBrMuVIkAPFHzWdee/mxMy" +
        "eKXT18U4ISgBdIKLF9LwILhIgR8CwElLucmF2OdXqFe7baBIFI6OaqLvDgOwdHSIS6uZhAWOWIAZ" +
        "38DhJbHMzPpfeBv1bAIhAQJBAPwhjzWqSWZjAfcED4htKa/ZSbdqMa1iYtveoHdXIcLuj4Ck1DKQ" +
        "EFpzLnUe2gwul/TDcoW3ZVp85jn7jwnrNDECQQC0R5LgkGdGNMBih4kPU87tHFHUnggSMyIOBnCE" +
        "XuQEN6i68VOwbdm2F7Rg1XGHD8IIJmVeiTSgLtS/mJRht6WpAkEAqs9VhQbTaTDkEOPIXiWOW1q6" +
        "rS6dbxg7XzdowNDfx3706zM/qu2clpp3u9Ll5+DdA24xtNM1L+Nz2Y5KLm8Q0QJAQqpxEx/zQNAD" +
        "EKyEL6nTTHV7gT+LRoeoIT2aYCji8vhOKgtR4l1M8/xiFKj5mXNnUjI4rDPaxR1sSQm4XUZXOQJB" +
        "AJaCD0AhacU+KaOtk65tBJ7N2dKTbc5gs/CAz1uGgJtoD/jPjELMQwrxdp6AZP6+L6osqy6zDI3W" +
        "zNHXS+wWAd0=";
 
    static String CERTIFICATE = 
        "-----BEGIN CERTIFICATE-----\n" + 
        "MIICUzCCAbygAwIBAgIESviASzANBgkqhkiG9w0BAQUFADBuMRAwDgYDVQQGEwdVbmtub3duMRAw" + 
        "DgYDVQQIEwdVbmtub3duMRAwDgYDVQQHEwdVbmtub3duMRAwDgYDVQQKEwdVbmtub3duMRAwDgYD" +
        "VQQLEwdVbmtub3duMRIwEAYDVQQDEwlsb2NhbGhvc3QwHhcNMDkxMTA5MjA0OTE1WhcNMTAwMjA3" +
        "MjA0OTE1WjBuMRAwDgYDVQQGEwdVbmtub3duMRAwDgYDVQQIEwdVbmtub3duMRAwDgYDVQQHEwdV" +
        "bmtub3duMRAwDgYDVQQKEwdVbmtub3duMRAwDgYDVQQLEwdVbmtub3duMRIwEAYDVQQDEwlsb2Nh" +
        "bGhvc3QwgZ8wDQYJKoZIhvcNAQEBBQADgY0AMIGJAoGBALGOFqjC4Fefz0oOcfJeS8eLV8jYzA3s" +
        "HUnTKmASfgfhG8prWUgSEq7O/849MrBysiKpIvTN8R+ykV4QCAxauGURDsNI2ZtAv23YX2MbcfYf" +
        "YqD0tgHEn355HKey0ICgmRuq3norlUWAH3hRv5qiQMc0UIhNrmdTs0jyvQ8E8AlZAgMBAAEwDQYJ" +
        "KoZIhvcNAQEFBQADgYEAPHUr1BDENlV28yIQvJOWKYbcNWLd6Cp8xCltSI897xhPpKQ5tDvs+l0g" +
        "VfdBv5+jou0F5gbCkqgclBuUnUUWsU7r4HYBLVB8FiGSy9v5yuFJWyMMLJkWAfBgzxV1nHsCPhOn" +
        "rspSB+i6bwag0i3ENXstD/Fg1lN/7l9dRpurneI=\n" +
        "-----END CERTIFICATE-----\n\n";
}
