package org.apache.catalina.webresources;

import java.net.URLStreamHandler;
import java.net.spi.URLStreamHandlerProvider;

import org.apache.catalina.webresources.war.Handler;

@SuppressWarnings("deprecation")
public class TomcatURLStreamHandlerProvider extends URLStreamHandlerProvider {

    private static final String WAR_PROTOCOL = "war";
    private static final String CLASSPATH_PROTOCOL = "classpath";

    static {
        // Create an instance without calling URL.setURLStreamHandlerFactory
        TomcatURLStreamHandlerFactory.disable();
    }


    @Override
    public URLStreamHandler createURLStreamHandler(String protocol) {
        if (WAR_PROTOCOL.equals(protocol)) {
            return new Handler();
        } else if (CLASSPATH_PROTOCOL.equals(protocol)) {
            return new ClasspathURLStreamHandler();
        }

        // Possible user handler defined via Tomcat's custom API
        return TomcatURLStreamHandlerFactory.getInstance().createURLStreamHandler(protocol);
    }
}
