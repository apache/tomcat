/*
 */
package org.apache.tomcat.lite.servlet;

import java.io.FileInputStream;
import java.io.IOException;

import junit.framework.TestCase;

import org.apache.tomcat.servlets.config.ServletContextConfig;
import org.apache.tomcat.servlets.config.deploy.AnnotationsProcessor;

public class AnnotationTest extends TestCase {

    // TODO: fix the build file to find the target dir
    // you can run this manually until this happens
    String eclipseBase = "test-webapp/WEB-INF/classes";
    
    public void testScanClasses() throws IOException {
        ServletContextConfig cfg = new ServletContextConfig();
        AnnotationsProcessor scanner = new AnnotationsProcessor(cfg);
//        scanner.processDir(eclipseBase);
//        
//        dump(cfg);
        
    }
    
    public void testScanClass() throws IOException {
        ServletContextConfig cfg = new ServletContextConfig();
        AnnotationsProcessor scanner = new AnnotationsProcessor(cfg);
            
        String path = eclipseBase + "/org/apache/tomcat/lite/Annotated2Servlet.class";
//        scanner.processClass(new FileInputStream(path), eclipseBase, path);
//        
//        dump(cfg);
        
    }

    private void dump(ServletContextConfig cfg) {
//        ObjectMapper jackson = new ObjectMapper();
//        try {
//            jackson.configure(SerializationConfig.Feature.INDENT_OUTPUT, true);
//            jackson.configure(SerializationConfig.Feature.WRITE_NULL_PROPERTIES, 
//                    false);
//
//            ByteArrayOutputStream out = new ByteArrayOutputStream();
//            jackson.writeValue(out, cfg);
//            System.err.println(out.toString());
//        } catch (Throwable t) {
//            t.printStackTrace();
//        }
    }
    
}
