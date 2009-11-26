/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tomcat.servlets.file;


import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.tomcat.servlets.util.URLEncoder;

/**
 * Handles directory listing
 */
public class Dir2Html {

    /**
     * Array containing the safe characters set.
     */
    protected static URLEncoder urlEncoder;

    /**
     * Allow a readme file to be included.
     */
    protected String readmeFile = null;

    // TODO: find a better default
    /**
     * The input buffer size to use when serving resources.
     */
    protected int input = 2048;

    /**
     * The output buffer size to use when serving resources.
     */
    protected int output = 2048;

    /**
     * File encoding to be used when reading static files. If none is specified
     * the platform default is used.
     */
    protected String fileEncoding = null;

    ThreadLocal formatTL = new ThreadLocal();
        
    /**
     * Full range marker.
     */
    protected static ArrayList FULL = new ArrayList();

    // Context base dir
    protected File basePath;
    protected String basePathName;

    public static final String TOMCAT_CSS =
        "H1 {font-family:Tahoma,Arial,sans-serif;color:white;background-color:#525D76;font-size:22px;} " +
        "H2 {font-family:Tahoma,Arial,sans-serif;color:white;background-color:#525D76;font-size:16px;} " +
        "H3 {font-family:Tahoma,Arial,sans-serif;color:white;background-color:#525D76;font-size:14px;} " +
        "BODY {font-family:Tahoma,Arial,sans-serif;color:black;background-color:white;} " +
        "B {font-family:Tahoma,Arial,sans-serif;color:white;background-color:#525D76;} " +
        "P {font-family:Tahoma,Arial,sans-serif;background:white;color:black;font-size:12px;}" +
        "A {color : black;}" +
        "A.name {color : black;}" +
        "HR {color : #525D76;}";

    // ----------------------------------------------------- Static Initializer


    /**
     * GMT timezone - all HTTP dates are on GMT
     */
    static {
        urlEncoder = new URLEncoder();
        urlEncoder.addSafeCharacter('-');
        urlEncoder.addSafeCharacter('_');
        urlEncoder.addSafeCharacter('.');
        urlEncoder.addSafeCharacter('*');
        urlEncoder.addSafeCharacter('/');
    }


    /**
     * MIME multipart separation string
     */
    protected static final String mimeSeparation = "TOMCAT_MIME_BOUNDARY";


    // --------------------------------------------------------- Public Methods

    /**
     * Serve the specified resource, optionally including the data content.
     *
     * @param request The servlet request we are processing
     * @param response The servlet response we are creating
     * @param content Should the content be included?
     *
     * @exception IOException if an input/output error occurs
     * @exception ServletException if a servlet-specified error occurs
     */
    protected void serveResource(HttpServletRequest request,
                                 HttpServletResponse response,
                                 boolean content, 
                                 String relativePath)
        throws IOException, ServletException {

        // Identify the requested resource path - checks include attributes
        String path = DefaultServlet.getRelativePath(request);
        
        // TODO: Check the file cache to avoid a bunch of FS accesses.
        
        File resFile = new File(basePath, path);

        if (!resFile.exists()) {
            DefaultServlet.send404(request, response);
            return;
        }

        boolean isDir = resFile.isDirectory();
        
        if (isDir) {
            renderDir(request, response, resFile,"UTF=8", content,
                    relativePath);
            return;
        }
        
    }


    
    // ----------------- Directory rendering --------------------
    
    // Just basic HTML rendering - extend or replace for xslt

    public void renderDir(HttpServletRequest request, 
            HttpServletResponse response, 
            File resFile,
            String fileEncoding,
            boolean content,
            String relativePath) throws IOException {
        
        String contentType = "text/html;charset=" + fileEncoding;

        ServletOutputStream ostream = null;
        PrintWriter writer = null;
        
        if (content) {
            // Trying to retrieve the servlet output stream
            try {
                ostream = response.getOutputStream();
            } catch (IllegalStateException e) {
                // If it fails, we try to get a Writer instead if we're
                // trying to serve a text file
                if ( (contentType == null)
                     || (contentType.startsWith("text")) ) {
                    writer = response.getWriter();
                } else {
                    throw e;
                }
            }

        }

        // Set the appropriate output headers
        response.setContentType(contentType);
        
        InputStream renderResult = null;

        if (content) {
            // Serve the directory browser
            renderResult =
                render(request.getContextPath(), resFile, relativePath);
        }


        // Copy the input stream to our output stream (if requested)
        if (content) {
            try {
                response.setBufferSize(output);
            } catch (IllegalStateException e) {
                // Silent catch
            }
            if (ostream != null) {
                CopyUtils.copy(renderResult, ostream);
            } else {
                CopyUtils.copy(renderResult, writer, fileEncoding);
            }
        }
        
            
    }
    

    /**
     * URL rewriter.
     *
     * @param path Path which has to be rewiten
     */
    protected String rewriteUrl(String path) {
        return urlEncoder.encode( path );
    }



    /**
     *  Decide which way to render. HTML or XML.
     */
    protected InputStream render(String contextPath, File cacheEntry,
                                 String relativePath) {
        return renderHtml(contextPath, cacheEntry, relativePath);
    }


    /**
     * Return an InputStream to an HTML representation of the contents
     * of this directory.
     *
     * @param contextPath Context path to which our internal paths are
     *  relative
     */
    protected InputStream renderHtml(String contextPath, File cacheEntry,
                                     String relativePath) {

        String dirName = cacheEntry.getName();

        // Number of characters to trim from the beginnings of filenames
//        int trim = relativePath.length();
//        if (!relativePath.endsWith("/"))
//            trim += 1;
//        if (relativePAth.equals("/"))
//            trim = 1;

        // Prepare a writer to a buffered area
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        OutputStreamWriter osWriter = null;
        try {
            osWriter = new OutputStreamWriter(stream, "UTF8");
        } catch (Exception e) {
            // Should never happen
            osWriter = new OutputStreamWriter(stream);
        }
        PrintWriter writer = new PrintWriter(osWriter);

        StringBuilder sb = new StringBuilder();
        
        // rewriteUrl(contextPath) is expensive. cache result for later reuse
        String rewrittenContextPath =  rewriteUrl(contextPath);

        // TODO: use a template for localization / customization
        
        // Render the page header
        sb.append("<html>\r\n");
        sb.append("<head>\r\n");
        sb.append("<title>");
        sb.append(dirName);
        sb.append("</title>\r\n");
        sb.append("<STYLE><!--");
        sb.append(TOMCAT_CSS);
        sb.append("--></STYLE> ");
        sb.append("</head>\r\n");
        sb.append("<body>");
        sb.append("<h1>");
        sb.append(dirName);
        sb.append("</h1>");

        sb.append("<HR size=\"1\" noshade=\"noshade\">");


        sb.append("<table width=\"100%\" cellspacing=\"0\"" +
                     " cellpadding=\"5\" align=\"center\">\r\n");

        // Render the column headings
        sb.append("<tr>\r\n");
        sb.append("<td align=\"left\"><font size=\"+1\"><strong>");
        sb.append("Name");
        sb.append("</strong></font></td>\r\n");
        sb.append("<td align=\"center\"><font size=\"+1\"><strong>");
        sb.append("Size");
        sb.append("</strong></font></td>\r\n");
        sb.append("<td align=\"right\"><font size=\"+1\"><strong>");
        sb.append("Last Modified");
        sb.append("</strong></font></td>\r\n");
        sb.append("</tr>");
        boolean shade = false;

        // Render the link to our parent (if required)
        String parentDirectory = relativePath;
        if (parentDirectory.endsWith("/")) {
            parentDirectory =
                parentDirectory.substring(0, parentDirectory.length() - 1);
        }
        int slash = parentDirectory.lastIndexOf('/');
        if (slash >= 0) {
            String parent = relativePath.substring(0, slash);
            sb.append("<tr>\r\n<td align=\"left\">&nbsp;&nbsp;\r\n<a href=\"..");
//            sb.append(rewrittenContextPath);
//            if (parent.equals(""))
//               parent = "/";
//            sb.append(rewriteUrl(parent));
//            if (!parent.endsWith("/"))
//                sb.append("/");
            sb.append("\">");
            //sb.append("<b>");
            sb.append("..");
            //sb.append("</b>");
            sb.append("</a></td></tr>");
            shade = true;
        }


        // Render the directory entries within this directory
        String[] files = cacheEntry.list();
        for (int i=0; i<files.length; i++) {

            String resourceName = files[i];
            String trimmed = resourceName;//.substring(trim);
            if (trimmed.equalsIgnoreCase("WEB-INF") ||
                    trimmed.equalsIgnoreCase("META-INF"))
                continue;

            File childCacheEntry = new File(cacheEntry, resourceName);

            sb.append("<tr");
            if (shade)
                sb.append(" bgcolor=\"#eeeeee\"");
            sb.append(">\r\n");
            shade = !shade;

            sb.append("<td align=\"left\">&nbsp;&nbsp;\r\n");
            sb.append("<a href=\"");
            if (! relativePath.endsWith("/")) {
                sb.append(dirName + "/");
            }
            //sb.append(rewrittenContextPath);
//            if (! rewrittenContextPath.endsWith("/")) {
//                sb.append("/");
//            }
//            if ( ! relativePath.equals("")) {
//                String link = rewriteUrl(relativePath);
//                sb.append(link).append("/");
//            }
            sb.append(resourceName);
            boolean isDir = childCacheEntry.isDirectory(); 
            if (isDir)
                sb.append("/");
            sb.append("\"><tt>");
            sb.append(trimmed);
            if (isDir)
                sb.append("/");
            sb.append("</tt></a></td>\r\n");

            sb.append("<td align=\"right\"><tt>");
            if (isDir)
                sb.append("&nbsp;");
            else
                displaySize(sb,childCacheEntry.length());
            sb.append("</tt></td>\r\n");

            sb.append("<td align=\"right\"><tt>");
            sb.append(DefaultServlet.lastModifiedHttp(childCacheEntry));
            sb.append("</tt></td>\r\n");

            sb.append("</tr>\r\n");
        }


        // Render the page footer
        sb.append("</table>\r\n");

        sb.append("<HR size=\"1\" noshade=\"noshade\">");

        String readme = getReadme(cacheEntry);
        if (readme!=null) {
            sb.append(readme);
            sb.append("<HR size=\"1\" noshade=\"noshade\">");
        }

        sb.append("</body>\r\n");
        sb.append("</html>\r\n");

        // Return an input stream to the underlying bytes
        writer.write(sb.toString());
        writer.flush();
        return (new ByteArrayInputStream(stream.toByteArray()));

    }

    /**
     * Display the size of a file.
     */
    protected void displaySize(StringBuilder buf, long filesize) {

        long leftside = filesize / 1024;
        long rightside = (filesize % 1024) / 103;  // makes 1 digit
        if (leftside == 0 && rightside == 0 && filesize != 0)
            rightside = 1;
        buf.append(leftside).append(".").append(rightside);
        buf.append(" KB");
    }

    /**
     * Get the readme file as a string.
     */
    protected String getReadme(File directory) {
        if (readmeFile!=null) {
            try {
                File rf = new File(directory, readmeFile);

                if (rf.exists()) {
                    StringWriter buffer = new StringWriter();
                    InputStream is = new FileInputStream(rf);
                    CopyUtils.copyRange(new InputStreamReader(is),
                              new PrintWriter(buffer));

                    return buffer.toString();
                 }
             } catch(Throwable e) {
                 ; /* Should only be IOException or NamingException
                    * can be ignored
                    */
             }
        }
        return null;
    }

}
