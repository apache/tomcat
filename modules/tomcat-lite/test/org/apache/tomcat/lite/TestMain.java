/*
 */
package org.apache.tomcat.lite;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.tomcat.integration.jmx.UJmxHandler;
import org.apache.tomcat.integration.jmx.UJmxObjectManagerSpi;
import org.apache.tomcat.integration.simple.Main;
import org.apache.tomcat.integration.simple.SimpleObjectManager;
import org.apache.tomcat.lite.http.BaseMapper;
import org.apache.tomcat.lite.http.DefaultHttpConnector;
import org.apache.tomcat.lite.http.Dispatcher;
import org.apache.tomcat.lite.http.HttpChannel;
import org.apache.tomcat.lite.http.HttpConnectionPool;
import org.apache.tomcat.lite.http.HttpConnector;
import org.apache.tomcat.lite.http.HttpRequest;
import org.apache.tomcat.lite.http.HttpResponse;
import org.apache.tomcat.lite.http.HttpChannel.HttpService;
import org.apache.tomcat.lite.http.HttpConnectionPool.RemoteServer;
import org.apache.tomcat.lite.http.HttpConnector.HttpChannelEvents;
import org.apache.tomcat.lite.http.HttpConnector.HttpConnection;
import org.apache.tomcat.lite.http.services.EchoCallback;
import org.apache.tomcat.lite.http.services.SleepCallback;
import org.apache.tomcat.lite.io.BBuffer;
import org.apache.tomcat.lite.io.IOConnector;
import org.apache.tomcat.lite.io.SocketConnector;
import org.apache.tomcat.lite.io.SslConnector;
import org.apache.tomcat.lite.proxy.HttpProxyService;
import org.apache.tomcat.lite.proxy.StaticContentService;
import org.apache.tomcat.lite.service.IOStatus;

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

    private boolean init = false;
    
    private SocketConnector serverCon = new SocketConnector();
    
    private HttpConnector testClient = DefaultHttpConnector.get();
    private HttpConnector testServer = new HttpConnector(serverCon);
    private HttpConnector testProxy = new HttpConnector(serverCon);
    private HttpConnector sslServer;
    
    private HttpProxyService proxy;

    UJmxObjectManagerSpi jmx = new UJmxObjectManagerSpi();

    private IOConnector sslCon;
        
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

    public void initTestCallback(Dispatcher d) throws IOException {
        BaseMapper.ContextMapping mCtx = d.addContext(null, "", null, null, null, null);
        
        d.addWrapper(mCtx, "/", new StaticContentService()
            .setContentType("text/html")
            .setData("<a href='/proc/cpool/client'>Client pool</a><br/>" +
                    "<a href='/proc/cpool/server'>Server pool</a><br/>" +
                    "<a href='/proc/cpool/proxy'>Proxy pool</a><br/>" +
                    ""));

        d.addWrapper(mCtx, "/favicon.ico", 
                new StaticContentService().setStatus(404).setData("Not found"));

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

        d.addWrapper(mCtx, "/ujmx", new UJmxHandler(jmx));
    }

    public void run() {
        try {
            startAll(8000);
        } catch (Throwable t) {
            t.printStackTrace();
        }
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
    
    protected synchronized void startAll(int basePort) throws IOException {
        int port = basePort + 903;
        if (!init) {
            init = true;
            
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
            
            sslCon = new SslConnector()
                .setKeysResource("org/apache/tomcat/lite/http/test.keystore", 
                    "changeit");
            sslServer = new HttpConnector(sslCon);
            initTestCallback(sslServer.getDispatcher());            
            sslServer.setPort(basePort + 443);
            sslServer.start();

//            System.setProperty("javax.net.debug", "ssl");
            
//            Logger.getLogger("SSL").setLevel(Level.FINEST);
//            testProxy.setDebugHttp(true);
//            testProxy.setDebug(true);
//            testClient.setDebug(true);
//            testClient.setDebugHttp(true);
//            testServer.setDebugHttp(true);
//            testServer.setDebug(true);
//            sslServer.setDebug(true);
//            sslServer.setDebugHttp(true);

            // Bind the objects, make them visible in JMX
            // additional settings from config
            initObjectManager("org/apache/tomcat/lite/test.properties");
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
    
    public void bindConnector(HttpConnector con, final String base) {
        om.bind("HttpConnector-" + base, con);
        om.bind("HttpConnectionPool-" + base, con.cpool);
        IOConnector io = con.getIOConnector();
        int ioLevel = 0;
        while (io != null) {
            om.bind("IOConnector-" + (ioLevel++) + "-" + base, io);
            if (io instanceof SocketConnector) {
                om.bind("NioThread-" + base, 
                        ((SocketConnector) io).getSelector());
                
            }
            io = io.getNet();
        }
        con.cpool.setEvents(new HttpConnectionPool.HttpConnectionPoolEvents() {

            @Override
            public void closedConnection(RemoteServer host, HttpConnection con) {
                om.unbind("HttpConnection-" + base + "-" + con.getId());
            }

            @Override
            public void newConnection(RemoteServer host, HttpConnection con) {
                om.bind("HttpConnection-" + base + "-" + con.getId(), con);
            }

            @Override
            public void newTarget(RemoteServer host) {
                om.bind("AsyncHttp-" + base + "-" + host.target, host);
            }

            @Override
            public void targetRemoved(RemoteServer host) {
                om.unbind("AsyncHttp-" + base + "-" + host.target);
            }
            
        });
        
        con.setOnCreate(new HttpChannelEvents() {
            @Override
            public void onCreate(HttpChannel data, HttpConnector extraData)
                    throws IOException {
                om.bind("AsyncHttp-" + base + "-" + data.getId(), data);
            }
            @Override
            public void onDestroy(HttpChannel data, HttpConnector extraData)
                    throws IOException {
                om.unbind("AsyncHttp-" + base + "-" + data.getId());
            }
        });
    
        
    }
    
    private void initObjectManager(String cfgFile) {
        if (om == null) {
            om = new SimpleObjectManager();
        }
        om.register(jmx);
        
        // Additional settings, via spring-like config file
        om.loadResource(cfgFile);
        
        // initialization - using runnables
        String run = (String) om.getProperty("RUN");
        String[] runNames = run == null ? new String[] {} : run.split(",");
        for (String name: runNames) {
            Object main = om.get(name);
            
            if (main instanceof Runnable) {
                ((Runnable) main).run();
            }
        }

        bindConnector(testServer, "TestServer");
        bindConnector(testClient, "Client");
        bindConnector(testProxy, "Proxy");
        bindConnector(sslServer, "Https");
        
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
    
    
    public static void main(String[] args) throws Exception, IOException {
        TestMain testMain = new TestMain();
        TestMain.defaultServer = testMain;
        testMain.om = new SimpleObjectManager(args);
        testMain.run();
        Main.waitStop();
    }

    
}
