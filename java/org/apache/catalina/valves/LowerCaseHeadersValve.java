package org.apache.catalina.valves;

import org.apache.catalina.connector.Response;
import org.apache.catalina.valves.ValveBase;
import org.apache.tomcat.util.buf.MessageBytes;
import org.apache.tomcat.util.http.MimeHeaders;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.*;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

public class LowerCaseHeadersValve extends ValveBase {
	private static final Log log = LogFactory.getLog(LowerCaseHeadersValve.class);

    @Override
    public void invoke(org.apache.catalina.connector.Request request, Response response) throws IOException, ServletException {
		log.debug("in");
        // Access the Coyote request (Tomcat's internal request object)
        org.apache.coyote.Request coyoteRequest = request.getCoyoteRequest();
        MimeHeaders mimeHeaders = coyoteRequest.getMimeHeaders();

        // Preserve original header names with case
        Map<String, List<String>> originalHeaders = new LinkedHashMap<>();

        for (int i = 0; i < mimeHeaders.size(); i++) {
            MessageBytes nameMessageBytes = mimeHeaders.getName(i);
            MessageBytes valueMessageBytes = mimeHeaders.getValue(i);

            String name = nameMessageBytes.toString();
            String value = valueMessageBytes.toString();

			if(log.isDebugEnabled()) {
				log.debug("raw: "+name+": "+value);
			}

            originalHeaders.computeIfAbsent(name, k -> new ArrayList<>()).add(value);

			nameMessageBytes.setString(name.toLowerCase());

			if(log.isDebugEnabled()) {
            	name = nameMessageBytes.toString();
            	value = valueMessageBytes.toString();
				log.debug("current: "+name+": "+value);
			}
        }

        // Store headers in request attributes for downstream use
        request.setAttribute("originalHeaders", originalHeaders);

        // Continue pipeline
        getNext().invoke(request, response);
    }
}

