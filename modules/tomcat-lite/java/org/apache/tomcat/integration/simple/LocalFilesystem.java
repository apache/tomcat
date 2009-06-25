/*
 */
package org.apache.tomcat.integration.simple;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import org.apache.tomcat.addons.Filesystem;

public class LocalFilesystem extends Filesystem {

    public OutputStream getOutputStream(String name) throws IOException {
        return new FileOutputStream(name);
    }    
}
