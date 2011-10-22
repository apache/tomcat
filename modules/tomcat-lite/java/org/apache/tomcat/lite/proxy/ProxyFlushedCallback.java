/*
 */
package org.apache.tomcat.lite.proxy;

import java.io.IOException;

import org.apache.tomcat.lite.io.IOChannel;
import org.apache.tomcat.lite.io.IOConnector;

public final class ProxyFlushedCallback implements IOConnector.DataFlushedCallback {
    IOChannel peerCh;

    public ProxyFlushedCallback(IOChannel ch2, IOChannel clientChannel2) {
        peerCh = ch2;
    }

    @Override
    public void handleFlushed(IOChannel ch) throws IOException {
        if (ch.getOut().isClosedAndEmpty()) {
            if (!peerCh.getOut().isAppendClosed()) {
                peerCh.close();
            }
        }
    }
}