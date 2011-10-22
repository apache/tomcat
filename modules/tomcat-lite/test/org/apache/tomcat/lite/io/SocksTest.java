/*
 */
package org.apache.tomcat.lite.io;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

import org.apache.tomcat.lite.proxy.SocksServer;


import junit.framework.TestCase;

public class SocksTest extends TestCase {

    public void setUp() {
//        SocksServer socks = new SocksServer();
//        try {
//            socks.initServer();
//        } catch (IOException e1) {
//            // TODO Auto-generated catch block
//            e1.printStackTrace();
//        }
//
//        ProxySelector.setDefault(new ProxySelector() {
//
//            @Override
//            public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
//            }
//
//            @Override
//            public List<Proxy> select(URI uri) {
//
//                List<Proxy> res = new ArrayList<Proxy>();
//                try {
//                    res.add(new Proxy(Proxy.Type.SOCKS,
//                            new InetSocketAddress(InetAddress.getLocalHost(), 1080)));
//                } catch (UnknownHostException e) {
//                    // TODO Auto-generated catch block
//                    e.printStackTrace();
//                }
//                return res;
//            }
//
//        });
    }

    public void testSocks() {

    }
}
