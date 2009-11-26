/*
 */
package org.apache.tomcat.servlets.file;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;


public class LocalFilesystem extends Filesystem {

    public OutputStream getOutputStream(String name) throws IOException {
        return new FileOutputStream(name);
    }    
}
