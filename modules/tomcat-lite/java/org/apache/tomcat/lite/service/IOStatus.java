/*
 */
package org.apache.tomcat.lite.service;

import java.io.IOException;
import java.util.Map;

import org.apache.tomcat.lite.http.HttpRequest;
import org.apache.tomcat.lite.http.HttpResponse;
import org.apache.tomcat.lite.http.HttpWriter;
import org.apache.tomcat.lite.http.HttpChannel.HttpService;
import org.apache.tomcat.lite.http.HttpConnector.ConnectionPool;
import org.apache.tomcat.lite.http.HttpConnector.RemoteServer;
import org.apache.tomcat.lite.io.IOChannel;

/**
 * Dump status of a connection pool.
 */
public class IOStatus implements HttpService {

    private ConnectionPool pool;

    public IOStatus(ConnectionPool pool) {
        this.pool = pool;
    }
    
    @Override
    public void service(HttpRequest httpReq, HttpResponse httpRes)
            throws IOException {
        ConnectionPool sc = pool;
        HttpWriter out = httpRes.getBodyWriter();
        
        httpRes.setContentType("text/plain");
        out.println("hosts=" + sc.getTargetCount());
        out.println("waiting=" + sc.getSocketCount());
        out.println("closed=" + sc.getClosedSockets());
        out.println();

        for (Map.Entry<CharSequence, RemoteServer> e: sc.hosts.entrySet()) {
            out.append(e.getKey());
            out.append("=");
            out.println(Integer.toString(e.getValue().connections.size()));

            for (IOChannel ch: e.getValue().connections) {
                out.println(ch.getId() + 
                        " " + ch.toString());
            }
            out.println();
        }

    }

}
