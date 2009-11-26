/*
 */
package org.apache.tomcat.servlets.file;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/** 
 * Abstract the filesystem - lighter than the JNDI used in catalina.
 * 
 * This can be used to port the File/Dav servlets to environments that 
 * don't have a file system access, or in servlet engines with class-based 
 * sandboxing.
 */
public class Filesystem {

    
    public OutputStream getOutputStream(String name) throws IOException {
        return null;
    }

    public InputStream getInputStream(String name) throws IOException {
        return new FileInputStream(name);
    }
    
    
}
