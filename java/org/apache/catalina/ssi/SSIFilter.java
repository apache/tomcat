/*
 * Copyright 1999,2004 The Apache Software Foundation. Licensed under the
 * Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License
 * at http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable
 * law or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package org.apache.catalina.ssi;


import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.Writer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.Globals;
/**
 * Filter to process SSI requests within a webpage. Mapped to a content types
 * from within web.xml.
 * 
 * @author David Becker
 * @version $Revision: 303920 $, $Date: 2005-05-05 22:52:37 +0200 (jeu., 05 mai 2005) $
 * @see org.apache.catalina.ssi.SSIServlet
 */
public class SSIFilter implements Filter {
	protected FilterConfig config = null;
    /** Debug level for this servlet. */
    protected int debug = 0;
    /** Expiration time in seconds for the doc. */
    protected Long expires = null;
    /** virtual path can be webapp-relative */
    protected boolean isVirtualWebappRelative = false;
    /** regex pattern to match when evaluating content types */
	protected Pattern contentTypeRegEx = null;
	/** default pattern for ssi filter content type matching */
	protected Pattern shtmlRegEx =
        Pattern.compile("text/x-server-parsed-html(;.*)?");


    //----------------- Public methods.
    /**
     * Initialize this servlet.
     * 
     * @exception ServletException
     *                if an error occurs
     */
    public void init(FilterConfig config) throws ServletException {
    	this.config = config;
    	
        String value = null;
        try {
            value = config.getInitParameter("debug");
            debug = Integer.parseInt(value);
        } catch (Throwable t) {
            ;
        }
        try {
            value = config.getInitParameter("contentType");
            contentTypeRegEx = Pattern.compile(value);
        } catch (Throwable t) {
            contentTypeRegEx = shtmlRegEx;
            StringBuffer msg = new StringBuffer();
            msg.append("Invalid format or no contentType initParam; ");
            msg.append("expected regular expression; defaulting to ");
            msg.append(shtmlRegEx.pattern());
            config.getServletContext().log(msg.toString());
        }
        try {
            value = config.getInitParameter(
                    "isVirtualWebappRelative");
            isVirtualWebappRelative = Integer.parseInt(value) > 0?true:false;
        } catch (Throwable t) {
            ;
        }
        try {
            value = config.getInitParameter("expires");
            expires = Long.valueOf(value);
        } catch (NumberFormatException e) {
            expires = null;
            config.getServletContext().log(
                "Invalid format for expires initParam; expected integer (seconds)"
            );
        } catch (Throwable t) {
            ;
        }
        if (debug > 0)
            config.getServletContext().log(
                    "SSIFilter.init() SSI invoker started with 'debug'=" + debug);
    }

    public void doFilter(ServletRequest request, ServletResponse response,
            FilterChain chain) throws IOException, ServletException {
        // cast once
        HttpServletRequest req = (HttpServletRequest)request;
        HttpServletResponse res = (HttpServletResponse)response;
        
        // indicate that we're in SSI processing
        req.setAttribute(Globals.SSI_FLAG_ATTR, "true");           

        // setup to capture output
        ByteArrayServletOutputStream basos = new ByteArrayServletOutputStream();
        ResponseIncludeWrapper responseIncludeWrapper =
            new ResponseIncludeWrapper(config.getServletContext(),req, res, basos);

        // process remainder of filter chain
        chain.doFilter(req, responseIncludeWrapper);

        // we can't assume the chain flushed its output
        responseIncludeWrapper.flushOutputStreamOrWriter();
        byte[] bytes = basos.toByteArray();

        // get content type
        String contentType = responseIncludeWrapper.getContentType();

        // is this an allowed type for SSI processing?
        if (contentTypeRegEx.matcher(contentType).matches()) {
            String encoding = res.getCharacterEncoding();

            // set up SSI processing 
            SSIExternalResolver ssiExternalResolver =
                new SSIServletExternalResolver(config.getServletContext(), req,
                        res, isVirtualWebappRelative, debug, encoding);
            SSIProcessor ssiProcessor = new SSIProcessor(ssiExternalResolver,
                    debug);
            
            // prepare readers/writers
            Reader reader =
                new InputStreamReader(new ByteArrayInputStream(bytes), encoding);
            ByteArrayOutputStream ssiout = new ByteArrayOutputStream();
            PrintWriter writer =
                new PrintWriter(new OutputStreamWriter(ssiout, encoding));
            
            // do SSI processing  
            long lastModified = ssiProcessor.process(reader,
                    responseIncludeWrapper.getLastModified(), writer);
            
            // set output bytes
            writer.flush();
            bytes = ssiout.toByteArray();
            
            // override headers
            if (expires != null) {
                res.setDateHeader("expires", (new java.util.Date()).getTime()
                        + expires.longValue() * 1000);
            }
            if (lastModified > 0) {
                res.setDateHeader("last-modified", lastModified);
            }
            res.setContentLength(bytes.length);
            
            Matcher shtmlMatcher =
                shtmlRegEx.matcher(responseIncludeWrapper.getContentType());
            if (shtmlMatcher.matches()) {
            	// Convert shtml mime type to ordinary html mime type but preserve
                // encoding, if any.
            	String enc = shtmlMatcher.group(1);
            	res.setContentType("text/html" + ((enc != null) ? enc : ""));
            }
        }

        // write output
        try {
            OutputStream out = res.getOutputStream();
            out.write(bytes);
        } catch (Throwable t) {
            Writer out = res.getWriter();
            out.write(new String(bytes));
        }
    }

    public void destroy() {
    }
}
