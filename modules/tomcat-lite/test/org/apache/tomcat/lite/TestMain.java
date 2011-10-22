/*
 */
package org.apache.tomcat.lite;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.tomcat.lite.http.BaseMapper;
import org.apache.tomcat.lite.http.HttpClient;
import org.apache.tomcat.lite.http.Dispatcher;
import org.apache.tomcat.lite.http.HttpConnector;
import org.apache.tomcat.lite.http.HttpRequest;
import org.apache.tomcat.lite.http.HttpResponse;
import org.apache.tomcat.lite.http.HttpServer;
import org.apache.tomcat.lite.http.HttpChannel.HttpService;
import org.apache.tomcat.lite.http.services.EchoCallback;
import org.apache.tomcat.lite.http.services.SleepCallback;
import org.apache.tomcat.lite.io.BBuffer;
import org.apache.tomcat.lite.io.jsse.JsseSslProvider;
import org.apache.tomcat.lite.proxy.HttpProxyService;
import org.apache.tomcat.lite.proxy.StaticContentService;
import org.apache.tomcat.lite.service.IOStatus;

/**
 * Laucher for tomcat-lite standalone, configured with test handlers.
 *
 * Used in tests - one is running for the entire suite.
 *
 * @author Costin Manolache
 */
public class TestMain {

    static {
        JsseSslProvider.testModeURLConnection();
    }

    static TestMain defaultServer;

    private boolean init = false;

    HttpConnector testClient;
    HttpConnector testServer;
    HttpConnector testProxy;
    HttpConnector sslServer;
    HttpProxyService proxy;

    public TestMain() {
        init();
    }

    protected void init() {
        testClient = HttpClient.newClient();
    }

    /**
     * A single instance used for all tests.
     */
    public static TestMain shared() {
        if (defaultServer == null) {
            defaultServer = new TestMain();
            defaultServer.run();
        }
        return defaultServer;
    }

    public static HttpConnector getTestServer() {
        return shared().testServer;
    }

    public HttpConnector getClient() {
        return shared().testClient;
    }

    public static BaseMapper.Context initTestContext(Dispatcher d) throws IOException {
        BaseMapper.Context mCtx = d.addContext(null, "", null, null, null, null);

        mCtx.addWrapper("/", new StaticContentService()
            .setContentType("text/html")
            .setData("<a href='/proc/cpool/client'>Client pool</a><br/>" +
                    "<a href='/proc/cpool/server'>Server pool</a><br/>" +
                    "<a href='/proc/cpool/proxy'>Proxy pool</a><br/>" +
                    ""));

        mCtx.addWrapper("/favicon.ico",
                new StaticContentService().setStatus(404).setData("Not found"));

        mCtx.addWrapper("/hello", new StaticContentService().setData("Hello world"));
        mCtx.addWrapper("/2nd", new StaticContentService().setData("Hello world2"));
        mCtx.addWrapper("/echo/*", new EchoCallback());

        mCtx.addWrapper("/sleep/1", new SleepCallback().setData("sleep 1"));
        mCtx.addWrapper("/sleep/10", new SleepCallback().sleep(10000).setData(
                "sleep 1"));

        mCtx.addWrapper("/chunked/*", new StaticContentService().setData("AAAA")
                .chunked());
        mCtx.addWrapper("/helloClose", new HttpService() {
            @Override
            public void service(HttpRequest httpReq, HttpResponse httpRes)
                    throws IOException {
                httpRes.setHeader("Connection", "close");
                httpRes.getBodyWriter().write("Hello");
            }
        });
        return mCtx;
    }

    public void initTestCallback(Dispatcher d) throws IOException {
        BaseMapper.Context mCtx = initTestContext(d);
        mCtx.addWrapper("/proc/cpool/client", new IOStatus(testClient.cpool));
        mCtx.addWrapper("/proc/cpool/proxy", new IOStatus(testProxy.cpool));
        mCtx.addWrapper("/proc/cpool/server", new IOStatus(testServer.cpool));
    }

    public void run() {
        try {
            startAll();
            // TODO(costin): clean up
            // Hook in JMX and debug properties
            try {
                Class c = Class.forName("org.apache.tomcat.lite.TomcatLiteJmx");
                Constructor constructor = c.getConstructor(TestMain.class);
                constructor.newInstance(this);
            } catch (Throwable t) {
                // ignore
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    public static String findDir(String dir) {
        String path = ".";
        for (int i = 0; i < 5; i++) {
            File f = new File(path + dir);
            if (f.exists()) {
                try {
                    return f.getCanonicalPath();
                } catch (IOException e) {
                    return f.getAbsolutePath();
                }
            }
            path = path + "/..";
        }
        return null;
    }

    public int getServerPort() {
        return 8802;
    }

    public int getProxyPort() {
        return 8903;
    }

    public int getSslServerPort() {
        return 8443;
    }

    static String PRIVATE_KEY =
    "MIICdgIBADANBgkqhkiG9w0BAQEFAASCAmAwggJcAgEAAoGBALsz2milZGHliWte61TfMTSwpAdq" +
"9uJkMTqgpSVtwxxOe8kT84QtIzhdAsQYjRz9ZtQn9DYWhJQs/cs/R3wWsjWwgiFHLzGalvsmMYJ3" +
"vBO8VMj762fAWu7GjUApIXcxMJoK4sQUpZKbqTuXpwzVUeeqBcspsIDgOLCo233G7/fBAgMBAAEC" +
"gYAWEaDX4VeaKuMuYzw+/yjf20sbDMMaIVGkZbfSV8Q+nAn/xHhaxq92P5DJ6VMJbd4neKZTkggD" +
"J+KriUQ2Hr7XXd/nM+sllaDWGmUnMYFI4txaNkikMA3ZyE/Xa79eDpTnSst8Nm11vrX9oF/hDNo4" +
"dhbU1krjAwVl/WijzSk4gQJBANvSmsmdjPlzvGNE11Aq3Ffb9/SqAOdE8NevMFeVKtBEKHIe1WlO" +
"ThRyWv3I8bUKTQMNULruSFVghTh6Hkt/CBkCQQDaAuxaXjv2voYozkOviXMpt0X5LZJMQu2gFc2x" +
"6UgBqYP2pNGDdRVWpbxF65PpXcLNKllCss2WB8i8kdeixYHpAkEAnIrzfia7sR2RiCQLLWUIe20D" +
"vHGgqRG4bfCtfYGV9rDDGNoKYq7H/dmeIOML9kA6rbS6zBRK4LoWxSx6DIuPaQJAL2c3USbwTuR6" +
"c2D2IrL2UXnCQz3/c4mR9Z8IDMk2mPXs9bI8xCKvMxnyaBmjHbj/ZHDy26fZP+gNY8MqagAcEQJA" +
"SidPwFV6cO8LCIA43wSVHlKZt4yU5wa9EWfzqVZxj7VSav7431kuxktW/YlwwxO4Pn8hgpPqD+W1" +
"E+Ssocxi8A==";
		
    static String CERTIFICATE = "-----BEGIN CERTIFICATE-----\n" +
"MIIC5DCCAk2gAwIBAgIJAMa8ioWQMpEZMA0GCSqGSIb3DQEBBQUAMFYxCzAJBgNV" +
"BAYTAlVTMQswCQYDVQQIEwJDQTESMBAGA1UEChMJbG9jYWxob3N0MRIwEAYDVQQL" +
"Ewlsb2NhbGhvc3QxEjAQBgNVBAMTCWxvY2FsaG9zdDAeFw0xMDAyMjYyMzIxNDBa" +
"Fw0xMTAyMjYyMzIxNDBaMFYxCzAJBgNVBAYTAlVTMQswCQYDVQQIEwJDQTESMBAG" +
"A1UEChMJbG9jYWxob3N0MRIwEAYDVQQLEwlsb2NhbGhvc3QxEjAQBgNVBAMTCWxv" +
"Y2FsaG9zdDCBnzANBgkqhkiG9w0BAQEFAAOBjQAwgYkCgYEAuzPaaKVkYeWJa17r" +
"VN8xNLCkB2r24mQxOqClJW3DHE57yRPzhC0jOF0CxBiNHP1m1Cf0NhaElCz9yz9H" +
"fBayNbCCIUcvMZqW+yYxgne8E7xUyPvrZ8Ba7saNQCkhdzEwmgrixBSlkpupO5en" +
"DNVR56oFyymwgOA4sKjbfcbv98ECAwEAAaOBuTCBtjAdBgNVHQ4EFgQUj3OnBK8R" +
"UN2CcmPvfQ1/IBeFwn8wgYYGA1UdIwR/MH2AFI9zpwSvEVDdgnJj730NfyAXhcJ/" +
"oVqkWDBWMQswCQYDVQQGEwJVUzELMAkGA1UECBMCQ0ExEjAQBgNVBAoTCWxvY2Fs" +
"aG9zdDESMBAGA1UECxMJbG9jYWxob3N0MRIwEAYDVQQDEwlsb2NhbGhvc3SCCQDG" +
"vIqFkDKRGTAMBgNVHRMEBTADAQH/MA0GCSqGSIb3DQEBBQUAA4GBAKcJWWZbHRuG" +
"77ir1ETltxNIsAFvuhDD6E68eBwpviWfKhFxiOdD1vmAGqWWDYpmgORBGxFMZxTq" +
"c82iSbM0LseFeHwxAfeNXosSShMFtQzKt2wKZLLQB/Oqrea32m4hU//NP8rNbTux" +
"dcAHeNQEDB5EUUSewAlh+fUE6HB6c8j0\n" +
"-----END CERTIFICATE-----\n\n";

    protected synchronized void startAll() throws IOException {
        if (init) {
            System.err.println("2x init ???");
        } else {
            init = true;
            boolean debug = false;
            if (debug) {
                System.setProperty("javax.net.debug", "ssl");
                System.setProperty("jsse", "conn_state,alert,engine,record,ssocket,socket,prf");
                Logger.getLogger("SSL").setLevel(Level.FINEST);
                testClient.setDebug(true);
                testClient.setDebugHttp(true);
            }

            proxy = new HttpProxyService()
                .withHttpClient(testClient);
            testProxy = HttpServer.newServer(getProxyPort());

            if (debug) {
                testProxy.setDebugHttp(true);
                testProxy.setDebug(true);
            }

            // dispatcher rejects 'http://'
            testProxy.setHttpService(proxy);
            try {
                testProxy.start();
            } catch (IOException e) {
                e.printStackTrace();
            }

            testServer = HttpServer.newServer(getServerPort());
            if (debug) {
                testServer.setDebugHttp(true);
                testServer.setDebug(true);
            }
            initTestCallback(testServer.getDispatcher());
            try {
                testServer.start();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }

//            Base64 b64 = new Base64();
//            byte[] keyBytes = b64.decode(PRIVATE_KEY);

            sslServer = HttpServer.newSslServer(getSslServerPort());

            if (debug) {
                sslServer.setDebug(true);
                sslServer.setDebugHttp(true);
            }
            JsseSslProvider sslCon = (JsseSslProvider) sslServer.getSslProvider();

            sslCon = sslCon
                .setKeyRes("org/apache/tomcat/lite/http/genrsa_512.cert",
                        "org/apache/tomcat/lite/http/genrsa_512.der");
            initTestCallback(sslServer.getDispatcher());
            sslServer.start();
        }

        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                System.err.println("Done");
            }
            public void start() {
                System.err.println("Done1");
            }
        });
    }

    /**
     * Blocking get, returns when the body has been read.
     */
    public static BBuffer get(String url) throws IOException {

        BBuffer out = BBuffer.allocate();

        HttpRequest aclient = HttpClient.newClient().request(url);
        aclient.send();
        aclient.readAll(out,
                //Long.MAX_VALUE);//
                2000000);
        aclient.release(); // return connection to pool
        return out;
    }

    public static BBuffer getUrl(String path) throws IOException {
        BBuffer out = BBuffer.allocate();
        getUrl(path, out);
        return out;
    }

    public static HttpURLConnection getUrl(String path,
                             BBuffer out) throws IOException {
        URL url = new URL(path);
        HttpURLConnection connection =
            (HttpURLConnection) url.openConnection();
        connection.setReadTimeout(10000);
        connection.connect();
        int rc = connection.getResponseCode();
        InputStream is = connection.getInputStream();
        BufferedInputStream bis = new BufferedInputStream(is);
        byte[] buf = new byte[2048];
        int rd = 0;
        while((rd = bis.read(buf)) > 0) {
            out.append(buf, 0, rd);
        }
        return connection;
    }


}
