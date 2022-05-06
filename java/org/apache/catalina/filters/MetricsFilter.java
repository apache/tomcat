package org.apache.catalina.filters;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import jakarta.servlet.FilterChain;
import jakarta.servlet.GenericFilter;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

public class MetricsFilter extends GenericFilter {
   private ConcurrentHashMap<String, AtomicInteger> calls;
   private ConcurrentHashMap<String, Double> meanTimes;
   private ConcurrentHashMap<String, Long> maxTimes;
   private ConcurrentHashMap<String, Long> minTimes;

   private String pathMetrics;
   public static final String PATH_METRICS = "PATH_METRICS";

   private transient Log log = LogFactory.getLog(MetricsFilter.class); // must not be static

   /**
    * 
    * @param json true: the response in JSON otherwise int text
    */
   public MetricsFilter() {
      this.calls = new ConcurrentHashMap<>();
      this.meanTimes = new ConcurrentHashMap<>();
      this.maxTimes = new ConcurrentHashMap<>();
      this.minTimes = new ConcurrentHashMap<>();

   }

   @Override
   public void doFilter(ServletRequest req, ServletResponse resp, FilterChain fc) throws ServletException, IOException {

      if (!(req instanceof HttpServletRequest) ||
            !(resp instanceof HttpServletResponse)) {
         throw new ServletException("Only HTTP requests are accepted");
      }
      log.debug("Metrics filter");

      // Safe to downcast at this point.
      HttpServletRequest request = (HttpServletRequest) req;
      HttpServletResponse response = (HttpServletResponse) resp;

      if (request.getRequestURI().contains(pathMetrics))
         sendMetrics(response);
      else {
         String method = request.getMethod();

         if (calls.get(method) == null)
            calls.put(method, new AtomicInteger(0));
         int n = calls.get(method).incrementAndGet();
         long t0 = System.currentTimeMillis();

         fc.doFilter(req, resp);
         HttpServletResponse r = (HttpServletResponse) resp;
         if (r.getStatus() == 200) {
            // Update mean time with the new datum
            long t1 = System.currentTimeMillis();
            long diff = t1 - t0;
            if (meanTimes.get(method) == null)
               meanTimes.put(method, 0.0);
            double m = meanTimes.get(method);
            meanTimes.replace(method, ((m * (n - 1)) + diff) / n);

            // Keep max time value
            if (maxTimes.get(method) == null)
               maxTimes.put(method, Long.MIN_VALUE);
            long max = maxTimes.get(method);
            if (diff > max)
               maxTimes.put(method, diff);

            // Keep min time value
            if (minTimes.get(method) == null)
               minTimes.put(method, Long.MAX_VALUE);
            long min = minTimes.get(method);
            if (diff < min)
               minTimes.put(method, diff);

            log.info("Time to serve the request " + diff + " ms");
         }
      }
   }

   @Override
   public String toString() {
      return "MetricFilter";
   }

   private void sendMetrics(HttpServletResponse response) throws IOException {
      // Metrics are requested
      response.setStatus(HttpServletResponse.SC_OK);
      PrintWriter pw = response.getWriter();
      response.setHeader("Content-Type", "text/plain");
      for (String m : calls.keySet()) {
         String base = "method_" + m + "_";
         pw.println(base + "calls: " + calls.get(m).get());
         pw.println(base + "mean_time: " + meanTimes.get(m));
         pw.println(base + "min_time: " + minTimes.get(m));
         pw.println(base + "max_time: " + maxTimes.get(m));
      }
      pw.flush();
      pw.close();
   }

   /**
    * Init filter with init parameters
    */
   @Override
   public void init() throws ServletException {
      pathMetrics = getInitParameter(PATH_METRICS);
      log.info("Metrics Filter init");
   }

   @Override
   public void destroy() {
      this.calls = null;
      this.maxTimes = null;
      this.meanTimes = null;
      this.minTimes = null;
      this.pathMetrics = null;
   }

}
