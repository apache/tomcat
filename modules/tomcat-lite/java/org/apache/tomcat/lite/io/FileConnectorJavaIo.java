/*
 */
package org.apache.tomcat.lite.io;

import java.io.File;
import java.io.IOException;


/**
 * Catalina uses JNDI to abstract filesystem - this is both heavy and
 * a bit complex.
 *
 * This is also a bit complex - but hopefully we can implement it as
 * non-blocking and without much copy.
 *
 */
public class FileConnectorJavaIo extends FileConnector {
    File base;

    public FileConnectorJavaIo(File file) {
        this.base = file;
    }

    @Override
    public boolean isDirectory(String path) {
        File file = new File(base, path);
        return file.isDirectory();
    }

    @Override
    public boolean isFile(String path) {
        File file = new File(base, path);
        return file.exists() && !file.isDirectory();
    }

    @Override
    public void acceptor(ConnectedCallback sc,
            CharSequence port,
            Object extra) throws IOException {
        // TODO: unix domain socket impl.
        // Maybe: detect new files in the filesystem ?
    }

    @Override
    public void connect(String host, int port, ConnectedCallback sc)
            throws IOException {
    }

}