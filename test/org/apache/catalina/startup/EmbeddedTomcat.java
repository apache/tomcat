package org.apache.catalina.startup;

import org.apache.catalina.Context;
import org.apache.catalina.connector.Connector;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.scan.StandardJarScanFilter;
import org.apache.tomcat.util.scan.StandardJarScanner;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.LogManager;

public class EmbeddedTomcat {

    private static void resetLogging() {
        final String loggingConfig = "handlers = java.util.logging.ConsoleHandler\n" +
            ".handlers = java.util.logging.ConsoleHandler\n" +
            "java.util.logging.ConsoleHandler.level = FINE\n" +
            "java.util.logging.ConsoleHandler.formatter = org.apache.juli.OneLineFormatter\n" +
            "java.util.logging.ConsoleHandler.encoding = UTF-8\n";
        try {
            InputStream is = new ByteArrayInputStream(loggingConfig.getBytes(StandardCharsets.UTF_8));
            LogManager.getLogManager().readConfiguration(is);
            LogFactory.getLog(EmbeddedTomcat.class).info("Logger configured to System.out");
        } catch (SecurityException | IOException e) {
            // Ignore, the VM default will be used
        }
    }

    public static void main(String... args) throws Exception {
        Tomcat tomcat = new Tomcat();
        resetLogging();
        tomcat.setPort(8080);
        Connector connector = tomcat.getConnector();
        connector.setProperty("bindOnInit", "false");
        // No file system docBase required
        Context ctx = tomcat.addContext("", null);
        skipTldsForResourceJars(ctx);
        CounterServlet counterServlet = new CounterServlet();
        Tomcat.addServlet(ctx, "counterServlet", counterServlet);
        ctx.addServletMappingDecoded("/", "counterServlet");

        tomcat.start();
        Thread.sleep(60*1000);
    }

    public static void skipTldsForResourceJars(Context context) {
        StandardJarScanner scanner = (StandardJarScanner) context.getJarScanner();
        StandardJarScanFilter filter = (StandardJarScanFilter) scanner.getJarScanFilter();
        filter.setTldSkip(filter.getTldSkip() + ",resources*.jar");
    }

    private static class CounterServlet extends HttpServlet {

        private AtomicInteger callCount = new AtomicInteger(0);

        @Override
        protected void service(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
            resp.setContentType("text/plain");
            resp.getWriter().print("OK: " + req.getRequestURL() + "[" + callCount.incrementAndGet()+ "]");
        }
    }
}
