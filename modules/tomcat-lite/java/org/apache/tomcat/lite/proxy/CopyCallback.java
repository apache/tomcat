/*
 */
package org.apache.tomcat.lite.proxy;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.apache.tomcat.lite.http.HttpChannel;
import org.apache.tomcat.lite.io.IOBuffer;
import org.apache.tomcat.lite.io.IOChannel;
import org.apache.tomcat.lite.io.IOConnector;

/**
 *  Used by socks and http proxy. Will copy received data to a different
 *  channel.
 */
public class CopyCallback implements IOConnector.DataReceivedCallback {
        IOChannel mOutBuffer;

        public CopyCallback(IOChannel sc) {
            mOutBuffer = sc;
        }

        @Override
        public void handleReceived(IOChannel ch) throws IOException {
            IOBuffer inBuffer = ch.getIn();
            IOChannel outBuffer = mOutBuffer;
            if (outBuffer == null &&
                    ch instanceof HttpChannel) {
                outBuffer =
                    (IOChannel) ((HttpChannel)ch).getRequest().getAttribute("P");
            }
            // body.
            while (true) {
                if (outBuffer == null || outBuffer.getOut() == null) {
                    return;
                }
                if (outBuffer.getOut().isAppendClosed()) {
                    return;
                }

                ByteBuffer bb = outBuffer.getOut().getWriteBuffer();
                int rd = inBuffer.read(bb);
                outBuffer.getOut().releaseWriteBuffer(rd);

                if (rd == 0) {
                    outBuffer.startSending();
                    return;
                }
                if (rd < 0) {
                    outBuffer.getOut().close();
                    outBuffer.startSending();
                    return;
                }
            }
        }
    }