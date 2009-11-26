/*
 */
package org.apache.tomcat.servlets.config.deploy;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;

import org.apache.tomcat.servlets.config.ConfigLoader;
import org.apache.tomcat.servlets.config.ServletContextConfig;

/**
 * Independent of tomcat-lite - will read the current context config, 
 * parse classes/jars for annotations - and generate a .ser file 
 * containing all info. 
 * 
 * This can be used to display informations about the config ( in a 
 * container-independent servlet ), or by the container for faster 
 * load times. 
 *  
 * @author Costin Manolache
 */
public class WarDeploy extends ConfigLoader implements Runnable {

    public ServletContextConfig loadConfig(String basePath) {
        ServletContextConfig contextConfig = super.loadConfig(basePath);

        boolean needsDeploy = contextConfig == null;
        
        if (contextConfig != null) {
            if (contextConfig.version != ServletContextConfig.CURRENT_VERSION) {
                needsDeploy = true;
            } else {
                // Check web.xml and other dep file timestamp(s)
                for (String fn : contextConfig.fileName) {
                    File f = new File(fn);
                    if (f.lastModified() > contextConfig.timestamp) {
                        needsDeploy = true;
                        break;
                    }
                }
            }
        }
        
        if (needsDeploy) {
            setBase(basePath);
            run();
            contextConfig = super.loadConfig(basePath);
        }

        return contextConfig;
    }
    
    @Override
    public void run() {
        if (base == null) {
            return; // nothing we can do
        }
        
        ServletContextConfig contextConfig = new ServletContextConfig();
        contextConfig.timestamp = System.currentTimeMillis();

        File webXmlF = new File(base + "/WEB-INF/web.xml");
        boolean needsAnnotations = true;
        
        if (webXmlF.exists()) {
            WebXml webXml = new WebXml(contextConfig);
            try {
                webXml.readWebXml(base + "/WEB-INF/web.xml");
                if (contextConfig.metadataComplete) {
                    needsAnnotations = false;
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        
        try {
        if (needsAnnotations) {
            AnnotationsProcessor ap = new AnnotationsProcessor(contextConfig);
            ap.processWebapp(base);
        }
        } catch (IOException ex) {
            ex.printStackTrace();
        }


        // Save
        try {
            ObjectOutputStream ois = 
                new ObjectOutputStream(new FileOutputStream(base + 
                        ServletContextConfig.SERIALIZED_PATH));
            ois.writeObject(contextConfig);
            ois.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        
    }

    String base;
    

    public static void main(String[] args) {
        String base = args[0];
        WarDeploy wd = new WarDeploy();
        wd.setBase(base);
        wd.run();
    }

    private void setBase(String base) {
        this.base = base;
    }

}
