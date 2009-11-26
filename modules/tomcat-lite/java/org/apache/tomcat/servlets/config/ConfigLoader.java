/*
 */
package org.apache.tomcat.servlets.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;

public class ConfigLoader {

    public ServletContextConfig loadConfig(String basePath) {
        
        
        String fileName = basePath + ServletContextConfig.SERIALIZED_PATH;
        File f = new File(fileName);
        if (f.exists()) {
            ServletContextConfig contextConfig = new ServletContextConfig();
            try {
                ObjectInputStream ois = new ObjectInputStream(new FileInputStream(f));
                contextConfig = (ServletContextConfig) ois.readObject();
                return contextConfig;
            } catch (Throwable e) {
                System.err.println("Ignoring invalid .ser config " + e);
                // ignore 
            }
        }
        
        return null;
    }
    
}
