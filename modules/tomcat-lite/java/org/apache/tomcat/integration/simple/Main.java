/*
 */
package org.apache.tomcat.integration.simple;

import org.apache.tomcat.integration.ObjectManager;

/**
 * Replacement for tomcat-lite specific Main, using the simple 
 * injection. SimpleObjectManager also has support for simple 
 * command line processing - CLI is treated the same with 
 * properties from the config file. 
 * 
 * @author Costin Manolache
 */
public class Main {

    public static void main(String args[]) 
    throws Exception {
        SimpleObjectManager om = new SimpleObjectManager();
        
        // Will process CLI. 
        // 'config' will load a config file.
        om.bind("Main.args", args);

        Runnable main = (Runnable) om.get("Main");
        if (main == null) {
            // TODO: look for a pre-defined name in local dir, resource,
            // manifest
            System.err.println("Using default tomcat-lite configuration");

            if (args.length == 0) {
                System.err.println("Example command line:");
                System.err.println("-context /:webapps/ROOT -Connector.port 9999");
            }
            
            String cfgFile = "org/apache/tomcat/lite/config.properties";
            om.loadResource(cfgFile);
            main = (Runnable) om.get("Main");
        }
        
        // add JMX support
        ObjectManager jmx = (ObjectManager) om.get("JMX");
        if (jmx != null) {
            jmx.register(om);
        }

        main.run();

    }    
}
