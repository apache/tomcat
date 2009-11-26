/*
 */
package org.apache.tomcat.lite.servlet;

import org.apache.tomcat.lite.servlet.TomcatLite;

import junit.framework.Test;

public class ServletTests extends TomcatLiteWatchdog {
    
    public ServletTests() {
        super();
        exclude = new String[] {
                "ServletToJSPErrorPageTest",
                "ServletToJSPError502PageTest",
        };
    }
    
    protected void addConnector(TomcatLite connector) {
        connector.setPort(7074);    
    }
    
    /** 
     * Magic JUnit method 
     */
    public static Test suite() {
        return new ServletTests().getSuite(7074);
    }
}
