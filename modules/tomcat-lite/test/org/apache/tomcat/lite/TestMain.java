/*
 */
package org.apache.tomcat.lite;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import org.apache.tomcat.integration.simple.Main;
import org.apache.tomcat.integration.simple.SimpleObjectManager;
import org.apache.tomcat.lite.http.BaseMapper;
import org.apache.tomcat.lite.http.DefaultHttpConnector;
import org.apache.tomcat.lite.http.Dispatcher;
import org.apache.tomcat.lite.http.HttpChannel;
import org.apache.tomcat.lite.http.HttpConnector;
import org.apache.tomcat.lite.http.HttpRequest;
import org.apache.tomcat.lite.http.HttpResponse;
import org.apache.tomcat.lite.http.HttpChannel.HttpService;
import org.apache.tomcat.lite.http.HttpConnector.HttpChannelEvents;
import org.apache.tomcat.lite.http.services.EchoCallback;
import org.apache.tomcat.lite.http.services.SleepCallback;
import org.apache.tomcat.lite.io.BBuffer;
import org.apache.tomcat.lite.io.SocketConnector;
import org.apache.tomcat.lite.io.SslConnector;
import org.apache.tomcat.lite.proxy.HttpProxyService;
import org.apache.tomcat.lite.proxy.StaticContentService;
import org.apache.tomcat.lite.service.IOStatus;
import org.apache.tomcat.util.buf.ByteChunk;

/**
 * Server with lost of test servlets.
 * 
 * Used in tests - one is running for the entire suite.
 * 
 * @author Costin Manolache
 */
public class TestMain {

    static {
        SslConnector.fixUrlConnection();
    }

    static TestMain defaultServer;
    
    private SimpleObjectManager om;

    private SocketConnector serverCon = new SocketConnector();
    
    private HttpConnector testClient = DefaultHttpConnector.get();
    private HttpConnector testServer = new HttpConnector(serverCon);
    private HttpConnector testProxy = new HttpConnector(serverCon);

    private HttpProxyService proxy;
   
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

    public void initTestCallback(Dispatcher d) {
        BaseMapper.ContextMapping mCtx = d.addContext(null, "", null, null, null, null);
        
        d.addWrapper(mCtx, "/", new StaticContentService()
            .setContentType("text/html")
            .setData("<a href='/proc/cpool/client'>Client pool</a><br/>" +
                    "<a href='/proc/cpool/server'>Server pool</a><br/>" +
                    "<a href='/proc/cpool/proxy'>Proxy pool</a><br/>" +
                    ""));

        d.addWrapper(mCtx, "/hello", new StaticContentService().setData("Hello world"));
        d.addWrapper(mCtx, "/2nd", new StaticContentService().setData("Hello world2"));
        d.addWrapper(mCtx, "/echo/*", new EchoCallback());

        d.addWrapper(mCtx, "/sleep/1", new SleepCallback().setData("sleep 1"));
        d.addWrapper(mCtx, "/sleep/10", new SleepCallback().sleep(10000).setData(
                "sleep 1"));

        d.addWrapper(mCtx, "/chunked/*", new StaticContentService().setData("AAAA")
                .chunked());
        
        d.addWrapper(mCtx, "/proc/cpool/client", new IOStatus(testClient.cpool));
        d.addWrapper(mCtx, "/proc/cpool/proxy", new IOStatus(testProxy.cpool));
        d.addWrapper(mCtx, "/proc/cpool/server", new IOStatus(testServer.cpool));

        d.addWrapper(mCtx, "/helloClose", new HttpService() {
            @Override
            public void service(HttpRequest httpReq, HttpResponse httpRes)
                    throws IOException {
                httpRes.setHeader("Connection", "close");
                httpRes.getBodyWriter().write("Hello");
            }
        });

    }

    public void run() {
        try {
            startAll(8000);
        } catch (Throwable t) {
            t.printStackTrace();
        }
    } 
    
    protected void startAll(int basePort) throws IOException {
        int port = basePort + 903;
        if (proxy == null) {
            proxy = new HttpProxyService()
                .withHttpClient(testClient);
            testProxy.setPort(port);

            // dispatcher rejects 'http://'
            testProxy.setHttpService(proxy);
            try {
                testProxy.start();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            
            port = basePort + 802;
            initTestCallback(testServer.getDispatcher());
            testServer.setPort(port);
            try {
                testServer.start();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            
            SslConnector sslCon = new SslConnector()
                .setKeysResource("org/apache/tomcat/lite/http/test.keystore", 
                    "changeit");
            HttpConnector sslServer = new HttpConnector(sslCon);
            initTestCallback(sslServer.getDispatcher());            
            sslServer.setPort(basePort + 443);
            sslServer.start();
            
//          testProxy.setDebugHttp(true);
//          testProxy.setDebug(true);
//          testClient.setDebug(true);
//          testClient.setDebugHttp(true);
//            testServer.setDebugHttp(true);
//            testServer.setDebug(true);
//            sslServer.setDebug(true);
//            sslServer.setDebugHttp(true);
            
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

    private void initObjectManager(String cfgFile) {
        if (om == null) {
            om = new SimpleObjectManager();
        }

        om.loadResource(cfgFile);
        String run = (String) om.getProperty("RUN");
        String[] runNames = run == null ? new String[] {} : run.split(",");
        for (String name: runNames) {
            Object main = om.get(name);
            
            if (main instanceof Runnable) {
                ((Runnable) main).run();
            }
        }

        om.bind("HttpConnector-TestServer", testServer);
        om.bind("HttpConnector", testClient);
        om.bind("HttpConnector-Proxy", testProxy);
        
        testServer.setOnCreate(new HttpChannelEvents() {
            @Override
            public void onCreate(HttpChannel data, HttpConnector extraData)
                    throws IOException {
                //data.trace("BIND");
                om.bind("AsyncHttp-" + data.getId(), data);
            }
            @Override
            public void onDestroy(HttpChannel data, HttpConnector extraData)
                    throws IOException {
                //data.trace("UNBIND");
                om.unbind("AsyncHttp-" + data.getId());
            }
        });
    }
    

    /**
     * Blocking get, returns when the body has been read.
     */
    public static BBuffer get(String url) throws IOException {

        BBuffer out = BBuffer.allocate();

        HttpRequest aclient = DefaultHttpConnector.get().request(url);
        aclient.send();
        aclient.readAll(out, 
                //Long.MAX_VALUE);//
                2000000);
        aclient.release(); // return connection to pool
        return out;
    }
    
    public static ByteChunk getUrl(String path) throws IOException {
        ByteChunk out = new ByteChunk();
        getUrl(path, out);
        return out;
    }

    public static HttpURLConnection getUrl(String path, 
                             ByteChunk out) throws IOException {
        URL url = new URL(path);
        HttpURLConnection connection = 
            (HttpURLConnection) url.openConnection();
       // connection.setReadTimeout(100000);
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
    
    
    public static void main(String[] args) throws Exception, IOException {
        TestMain testMain = new TestMain();
        TestMain.defaultServer = testMain;
        testMain.om = new SimpleObjectManager(args);
        testMain.initObjectManager("org/apache/tomcat/lite/test.properties");
        testMain.run();
        Main.waitStop();
    }

    
}
