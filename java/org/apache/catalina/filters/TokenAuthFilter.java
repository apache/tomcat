package org.apache.catalina.filters;

import java.io.IOException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.GenericFilter;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

public class TokenAuthFilter extends GenericFilter {
	public static final String TOKEN_NAME="TOKEN_NAME";
	public static final String TOKEN_VALUE="TOKEN_VALUE";

	private String header;
	private String expectedToken;
	
	private transient Log log = LogFactory.getLog(TokenAuthFilter.class); // must not be static

	@Override
	public void doFilter(ServletRequest req, ServletResponse resp, FilterChain fc) throws ServletException, IOException {

		if (!(req instanceof HttpServletRequest) ||
				!(resp instanceof HttpServletResponse)) {
			throw new ServletException("Only HTTP requests are accepted");
		}

		// Safe to downcast at this point.
		HttpServletRequest request = (HttpServletRequest) req;
		HttpServletResponse response = (HttpServletResponse) resp;

		String token = request.getHeader(header);
		if ((token != null) && (token.equals(expectedToken))) {
			log.debug("The token is valid, passing the request to the next filter");
			fc.doFilter(req, resp);
		} else {
			log.info("Request not authorized");
			response.sendError(HttpServletResponse.SC_FORBIDDEN, "Not authorized");
		}
	}

	public String toString() {
		return "TokenAuthFilter";
	}

	/**
	 * Init filter with init parameters
	 */
	@Override
	public void init() throws ServletException {
		header = getInitParameter(TOKEN_NAME);
		expectedToken = getInitParameter(TOKEN_VALUE);
		log.info("TokenAuth Filter init");
	}
}
