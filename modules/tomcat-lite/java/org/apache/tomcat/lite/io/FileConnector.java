/*
 */
package org.apache.tomcat.lite.io;


/**
 * Initial abstraction for non-blocking File access and to
 * support other abstraction.
 *
 * Tomcat uses JNDI - but that's blocking, does lots of data copy,
 * is complex.
 *
 * Work in progress..
 */
public abstract class FileConnector extends IOConnector {

    public static class FileInfo {
        String type;
        int mode;
        long size;

    }

    public abstract boolean isDirectory(String path);

    public abstract boolean isFile(String path);
}
