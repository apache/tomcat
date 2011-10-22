/*
 */
package org.apache.tomcat.lite.service;

import java.io.IOException;
import java.util.List;

import org.apache.tomcat.lite.http.HttpConnectionPool;
import org.apache.tomcat.lite.http.HttpRequest;
import org.apache.tomcat.lite.http.HttpResponse;
import org.apache.tomcat.lite.http.HttpWriter;
import org.apache.tomcat.lite.http.HttpChannel.HttpService;
import org.apache.tomcat.lite.http.HttpConnectionPool.RemoteServer;
import org.apache.tomcat.lite.http.HttpConnector.HttpConnection;
import org.apache.tomcat.lite.io.IOChannel;

/**
 * Dump status of a connection pool.
 */
public class IOStatus implements HttpService {

    private HttpConnectionPool pool;

    public IOStatus(HttpConnectionPool pool) {
        this.pool = pool;
    }

    @Override
    public void service(HttpRequest httpReq, HttpResponse httpRes)
            throws IOException {
        HttpConnectionPool sc = pool;
        HttpWriter out = httpRes.getBodyWriter();

        httpRes.setContentType("text/plain");
        // TODO: use JMX/DynamicObject to get all public info
        out.println("hosts=" + sc.getTargetCount());
        out.println("waiting=" + sc.getSocketCount());
        out.println("closed=" + sc.getClosedSockets());
        out.println();

        for (RemoteServer remote: sc.getServers()) {
            out.append(remote.target);
            out.append("=");
            List<HttpConnection> connections = remote.getConnections();
            out.println(Integer.toString(connections.size()));

            for (IOChannel ch: connections) {
                out.println(ch.getId() +
                        " " + ch.toString());
            }
            out.println();
        }

    }

}
