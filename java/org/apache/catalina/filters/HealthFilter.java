package org.apache.catalina.filters;

import java.io.IOException;
import java.io.PrintWriter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.GenericFilter;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

public class HealthFilter extends GenericFilter {
   private String pathHealth;
   public static final String PATH_HEALTH="PATH_HEALTH";

   private transient Log log = LogFactory.getLog(HealthFilter.class); // must not be static

   @Override
   public void doFilter(ServletRequest req, ServletResponse resp, FilterChain fc) throws ServletException, IOException {

      if (!(req instanceof HttpServletRequest) ||
            !(resp instanceof HttpServletResponse)) {
         throw new ServletException("Only HTTP requests are accepted");
      }
      
      log.debug("Health filter");

      // Safe to downcast at this point.
      HttpServletRequest request = (HttpServletRequest) req;
      HttpServletResponse response = (HttpServletResponse) resp;

      if (request.getRequestURI().contains(pathHealth)){
         response.setStatus(HttpServletResponse.SC_OK);
         PrintWriter pw = resp.getWriter();
         pw.print("{\"status\": \" OK \"}");
         pw.flush();
         pw.close();         
      }else{
         fc.doFilter(req, resp);
      }
   }

   	/**
	 * Init filter with init parameters
	 */
	@Override
	public void init() throws ServletException {
		pathHealth = getInitParameter(PATH_HEALTH);
      log.info("Health Filter init");
	}


}
