/*
 */
package org.apache.tomcat.lite;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Map;

import javax.net.ssl.SSLContext;

import org.apache.tomcat.integration.simple.Main;
import org.apache.tomcat.integration.simple.SimpleObjectManager;
import org.apache.tomcat.lite.http.BaseMapper;
import org.apache.tomcat.lite.http.DefaultHttpConnector;
import org.apache.tomcat.lite.http.Dispatcher;
import org.apache.tomcat.lite.http.HttpChannel;
import org.apache.tomcat.lite.http.HttpConnector;
import org.apache.tomcat.lite.http.BaseMapper.ContextMapping;
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
    static TestMain defaultServer;
    
    SimpleObjectManager om;

    static SocketConnector serverCon = new SocketConnector();
    
    public static HttpConnector testClient = DefaultHttpConnector.get();
    public static HttpConnector testServer = new HttpConnector(serverCon);
    public static HttpConnector testProxy = new HttpConnector(serverCon);
    
    static Dispatcher mcb;
    static HttpProxyService proxy;

   
    public static HttpConnector getTestServer() {
        if (defaultServer == null) {
            defaultServer = new TestMain();
            defaultServer.run();
        }
        return defaultServer.testServer;
    }


    public static void initTestCallback(Dispatcher d) {
        BaseMapper.ContextMapping mCtx = d.addContext(null, "", null, null, null, null);
//      testServer.setDebugHttp(true);
//      testServer.setDebug(true);
        
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
    }

    static boolean RELEASE = true;
    /**
     * Blocking get, returns when the body has been read.
     */
    public static BBuffer get(String url) throws IOException {

        BBuffer out = BBuffer.allocate();

        HttpChannel aclient = DefaultHttpConnector.get().get(url);
        aclient.sendRequest();
        aclient.readAll(out, 
                //Long.MAX_VALUE);//
                2000000);
        if (RELEASE) {
            aclient.release(); // return connection to pool
        }
        return out;
    }

    public void run() {
        String cfgFile = "org/apache/tomcat/lite/test.properties";
        try {
            startAll(cfgFile, 8000);
        } catch (Throwable t) {
            t.printStackTrace();
        }
    } 
    
    protected void startAll(String cfgFile, int basePort) {
        if (om == null) {
            om = new SimpleObjectManager();
        }

        om.loadResource(cfgFile);

        //        // Override the port - don't want on 8080, Watchdog may run in same 
//        // process
//        om.getProperties().put("org.apache.tomcat.lite.Connector.port", 
//                Integer.toString(basePort + 800));
        
        // From Main:
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
        
//        ioConnector.setOnCreate(new IOConnector.ConnectedCallback() {
//            AtomicInteger ser = new AtomicInteger();
//            @Override
//            public void handleConnected(IOChannel data)
//                    throws IOException {
//                data.setId("IOChannel-" + data.getTarget() + "-" + 
//                        ser.incrementAndGet());
//                om.bind(data.getId(), data);
//            }
//        });
//        ioConnector.setOnClose(new IOConnector.ClosedCallback() {
//            @Override
//            public void handleClosed(IOChannel data)
//                    throws IOException {
//               System.err.println("UNBIND " + data.getId() + " " + data);
//               om.unbind(data.getId());
//            }
//        });
//        ioConnector.onNewWorker = new Callback<NioThread>() {
//            @Override
//            public void handle(NioThread data, Object extraData)
//                    throws IOException {
//                om.bind((String) extraData, data);
//            }
//        };
        
        int port = basePort + 903;
        if (proxy == null) {
            proxy = new HttpProxyService()
                .withHttpClient(testClient);
            testProxy.setPort(port);
            testProxy.setDebugHttp(true);
            testProxy.setDebug(true);

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
            
            port = basePort + 443;
            
        }   
        
        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                System.err.println("Done");
            }
            public void start() {
                System.err.println("Done1");
            }
        });

        
//        try {
//            ServletContextImpl ctx = (ServletContextImpl) tomcat.addServletContext(null, null, "/jmx");
//            // tomcat is already started, need to call init explicitely
//            ((ServletContextImpl) ctx).loadConfig();
//            
//            Servlet servlet = new JMXProxyServlet();
//            ServletRegistration.Dynamic jmxServlet = ctx.addServlet("jmx", 
//                    servlet);
//            jmxServlet.addMapping("/jmx");
//            // TODO: init servlet 
//            servlet.init(new ServletConfigImpl(ctx, null, null));
//
//            ctx.start();
//            
//        } catch (ServletException e) {
//            // TODO Auto-generated catch block
//            e.printStackTrace();
//        }

    }

    static {
        SslConnector.fixUrlConnection();
    }
    
    public static ByteChunk getUrl(String path) throws IOException {
        ByteChunk out = new ByteChunk();
        getUrl(path, out, null);
        return out;
    }

    public static int getUrl(String path, 
                             ByteChunk out, 
                             Map<String, List<String>> resHead) throws IOException {
        URL url = new URL(path);
        HttpURLConnection connection = 
            (HttpURLConnection) url.openConnection();
       // connection.setReadTimeout(100000);
        connection.connect();
        int rc = connection.getResponseCode();
        if (resHead != null) {
            Map<String, List<String>> head = connection.getHeaderFields();
            resHead.putAll(head);
        }
        InputStream is = connection.getInputStream();
        BufferedInputStream bis = new BufferedInputStream(is);
        byte[] buf = new byte[2048];
        int rd = 0;
        while((rd = bis.read(buf)) > 0) {
            out.append(buf, 0, rd);
        }
        return rc;
    }
    
    
    public static void main(String[] args) throws Exception, IOException {
        TestMain testMain = new TestMain();
        TestMain.defaultServer = testMain;
        testMain.om = new SimpleObjectManager(args);
        testMain.run();
        Main.waitStop();
    }

    
}
