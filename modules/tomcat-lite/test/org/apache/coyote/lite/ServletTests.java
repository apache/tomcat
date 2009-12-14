/*
 */
package org.apache.coyote.lite;

import java.io.File;
import java.io.IOException;

import javax.servlet.ServletException;

import org.apache.catalina.LifecycleException;
import org.apache.tomcat.lite.servlet.TomcatLite;
import org.apache.tomcat.lite.servlet.TomcatLiteWatchdog;

import junit.framework.Test;

public class ServletTests extends TomcatLiteWatchdog {
    
    public ServletTests() {
        super();
        port = 7075;
        exclude = new String[] {
                "ServletToJSPErrorPageTest",
                "ServletToJSPError502PageTest",
        };
    }
    
    public ServletTests(String name) {
       super(name);
       port = 7075;
    }
    
    protected void addConnector(TomcatLite connector) {
       
    }
    
    public void initServerWithWatchdog(String wdDir) throws ServletException, 
            IOException {
        Tomcat tomcat = new Tomcat();
        
        File f = new File(wdDir + "/build/webapps");
        tomcat.setPort(port);
        tomcat.setBaseDir(f.getCanonicalPath());
        
        TomcatLiteCoyoteTest.setUp(tomcat, port);
        
        for (String s : new String[] {      
                "servlet-compat", 
                "servlet-tests",
                "jsp-tests"} ) {
            tomcat.addWebapp("/" + s, f.getCanonicalPath() + "/" + s);
        }

        try {
            tomcat.start();
        } catch (LifecycleException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    /** 
     * Magic JUnit method 
     */
    public static Test suite() {
        return new ServletTests().getSuite();
    }
}
