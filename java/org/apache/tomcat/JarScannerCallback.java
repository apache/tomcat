package org.apache.tomcat;

import java.io.File;
import java.io.IOException;
import java.net.JarURLConnection;

public interface JarScannerCallback {

    /**
     * 
     * @param urlConn
     * @throws IOException
     */
    public void scan(JarURLConnection urlConn) throws IOException;
    
    public void scan(File file) throws IOException ;

}
