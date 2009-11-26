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


import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Stack;
import java.util.TimeZone;
import java.util.Vector;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.tomcat.servlets.util.FastHttpDateFormat;
import org.apache.tomcat.servlets.util.Range;
import org.apache.tomcat.servlets.util.RequestUtil;
import org.apache.tomcat.servlets.util.UrlUtils;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;



/**
 * Servlet which adds support for WebDAV level 1. All the basic HTTP requests
 * are handled by the DefaultServlet. 
 * 
 * Based on Catalina WebdavServlet, with following changes:
 *  - removed the JNDI abstraction, use File instead
 *  - removed WebDAV 2 support ( moved to Webdav2Servlet ) - i.e. no locks
 *    supported
 *  - simplified and cleaned up the code
 *
 * @author Remy Maucherat
 * @author Costin Manolache
 */
public class WebdavServlet extends DefaultServlet {


    // -------------------------------------------------------------- Constants

    protected static final String METHOD_PROPFIND = "PROPFIND";
    protected static final String METHOD_PROPPATCH = "PROPPATCH";
    protected static final String METHOD_MKCOL = "MKCOL";
    protected static final String METHOD_COPY = "COPY";
    protected static final String METHOD_DELETE = "DELETE";
    protected static final String METHOD_MOVE = "MOVE";
    protected static final String METHOD_LOCK = "LOCK";
    protected static final String METHOD_UNLOCK = "UNLOCK";


    /**
     * Default depth is infite.
     */
    protected static final int INFINITY = 3; // To limit tree browsing a bit


    /**
     * PROPFIND - Specify a property mask.
     */
    protected static final int FIND_BY_PROPERTY = 0;


    /**
     * PROPFIND - Display all properties.
     */
    protected static final int FIND_ALL_PROP = 1;


    /**
     * PROPFIND - Return property names.
     */
    protected static final int FIND_PROPERTY_NAMES = 2;

    /**
     * Default namespace.
     */
    protected static final String DEFAULT_NAMESPACE = "DAV:";


    /**
     * Simple date format for the creation date ISO representation (partial).
     * TODO: ThreadLocal
     */
    protected static final SimpleDateFormat creationDateFormat =
        new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");


    static {
        creationDateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
    }

    // TODO: replace it with writeRole - who is enabled to write
    protected boolean readOnly = true;

    /**
     * Repository of the lock-null resources.
     * <p>
     * Key : path of the collection containing the lock-null resource<br>
     * Value : Vector of lock-null resource which are members of the
     * collection. Each element of the Vector is the path associated with
     * the lock-null resource.
     */
    protected Hashtable lockNullResources = new Hashtable();


    // --------------------------------------------------------- Public Methods


    /**
     * Initialize this servlet.
     */
    public void init()
        throws ServletException {

        super.init();

        try {
            String value = getServletConfig().getInitParameter("readonly");
            if (value != null)
                readOnly = (new Boolean(value)).booleanValue();
        } catch (Throwable t) {
            ;
        }
        log("Starting webdav");
    }

    public void setReadonly(boolean ro) {
        readOnly = ro;
    }
    
    public boolean getReadonly() {
        return readOnly;
    }
    // ------------------------------------------------------ Protected Methods

    /**
     * Handles the special WebDAV methods.
     */
    protected void service(HttpServletRequest req, HttpServletResponse resp)
        throws ServletException, IOException {

        String method = req.getMethod();

        if (method.equals(METHOD_PROPFIND)) {
            doPropfind(req, resp);
        } else if (method.equals(METHOD_PROPPATCH)) {
            doProppatch(req, resp);
        } else if (method.equals(METHOD_MKCOL)) {
            doMkcol(req, resp);
        } else if (method.equals(METHOD_COPY)) {
            doCopy(req, resp);
        } else if (method.equals(METHOD_MOVE)) {
            doMove(req, resp);
        } else if (method.equals(METHOD_LOCK)) {
            doLock(req, resp);
        } else if (method.equals(METHOD_UNLOCK)) {
            doUnlock(req, resp);
        } else if (method.equals(METHOD_DELETE)) {
            doDelete(req, resp);
        } else {
            // DefaultServlet processing - GET, HEAD, POST
            super.service(req, resp);
        }

    }

    /**
     * Check if the conditions specified in the optional If headers are
     * satisfied.
     *
     * @param request The servlet request we are processing
     * @param response The servlet response we are creating
     * @param resourceAttributes The resource information
     * @return boolean true if the resource meets all the specified conditions,
     * and false if any of the conditions is not satisfied, in which case
     * request processing is stopped
     */
    protected boolean checkIfHeaders(HttpServletRequest request,
                                     HttpServletResponse response,
                                     File resourceAttributes)
        throws IOException {

        if (!super.checkIfHeaders(request, response, resourceAttributes))
            return false;

        // TODO : Checking the WebDAV If header
        return true;

    }


    /**
     * OPTIONS Method.
     *
     * @param req The request
     * @param resp The response
     * @throws ServletException If an error occurs
     * @throws IOException If an IO error occurs
     */
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        resp.addHeader("DAV", "1"); // And not: ,2");

        StringBuilder methodsAllowed = determineMethodsAllowed(basePath,
                                                              req);
        resp.addHeader("Allow", methodsAllowed.toString());
        resp.addHeader("MS-Author-Via", "DAV");
    }

    // ------------------ PROPFIND --------------------

    /**
     * PROPFIND Method.
     */
    protected void doPropfind(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        String path = getRelativePath(req);
        if (path.endsWith("/"))
            path = path.substring(0, path.length() - 1);

        if ((path.toUpperCase().startsWith("/WEB-INF")) ||
            (path.toUpperCase().startsWith("/META-INF"))) {
            resp.sendError(WebdavStatus.SC_FORBIDDEN);
            return;
        }

        // Propfind depth
        int depth = getDepth(req);

        File object = new File(basePath, path);

        if (!object.exists()) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, path);
            return;
        }

        // Properties which are to be displayed.
        Vector properties = new Vector();
        int type = getPropfindType(req, properties);

        resp.setStatus(WebdavStatus.SC_MULTI_STATUS);
        resp.setContentType("text/xml; charset=UTF-8");

        // Create multistatus object
        XMLWriter generatedXML = new XMLWriter(resp.getWriter());
        generatedXML.writeXMLHeader();

        generatedXML.writeElement(null, "multistatus"
                                  + generateNamespaceDeclarations(),
                                  XMLWriter.OPENING);

        if (depth == 0) {
            parseProperties(req, generatedXML, path, type,
                            properties);
        } else {
            propfindRecurse(req, path, depth, properties, 
                    type, generatedXML);
        }

        generatedXML.writeElement(null, "multistatus",
                                  XMLWriter.CLOSING);
        generatedXML.sendData();
    }


    private void propfindRecurse(HttpServletRequest req, String path, int depth, Vector properties, int type, XMLWriter generatedXML) throws IOException {
        File object;
        // The stack always contains the object of the current level
        Stack stack = new Stack();
        stack.push(path);

        // Stack of the objects one level below
        Stack stackBelow = new Stack();

        while ((!stack.isEmpty()) && (depth >= 0)) {
            String currentPath = (String) stack.pop();
            parseProperties(req, generatedXML, currentPath,
                            type, properties);

            object = new File(basePath,currentPath);
            if (!object.exists()) {
                continue;
            }

            if ((object.isDirectory()) && (depth > 0)) {

                File[] files = object.listFiles();
                for (int i=0; i < files.length; i++) {
                    String newPath = currentPath;
                    if (!(newPath.endsWith("/")))
                        newPath += "/";
                    newPath += files[i].getName();
                    stackBelow.push(newPath);
                }
            }

            if (stack.isEmpty()) {
                depth--;
                stack = stackBelow;
                stackBelow = new Stack();
            }

            generatedXML.sendData();
        }
    }


    private int getPropfindType(HttpServletRequest req, Vector properties) throws ServletException {
        // Propfind type
        int type = FIND_ALL_PROP;

        DocumentBuilder documentBuilder = getDocumentBuilder();
        try {
            Document document = documentBuilder.parse
                (new InputSource(req.getInputStream()));
            // Get the root element of the document
            Element rootElement = document.getDocumentElement();
            NodeList childList = rootElement.getChildNodes();

            for (int i=0; i < childList.getLength(); i++) {
                Node currentNode = childList.item(i);
                switch (currentNode.getNodeType()) {
                case Node.TEXT_NODE:
                    break;
                case Node.ELEMENT_NODE:
                    if (currentNode.getNodeName().endsWith("prop")) {
                        type = FIND_BY_PROPERTY;
                        Node propNode = currentNode;
                        NodeList childListPN = propNode.getChildNodes();
                        for (int iPN=0; iPN < childListPN.getLength(); iPN++) {
                            Node currentNodePN = childListPN.item(iPN);
                            switch (currentNodePN.getNodeType()) {
                            case Node.TEXT_NODE:
                                break;
                            case Node.ELEMENT_NODE:
                                String nodeName = currentNodePN.getNodeName();
                                String propertyName = null;
                                if (nodeName.indexOf(':') != -1) {
                                    propertyName = nodeName.substring
                                        (nodeName.indexOf(':') + 1);
                                } else {
                                    propertyName = nodeName;
                                }
                                // href is a live property which is handled differently
                                properties.addElement(propertyName);
                                break;
                            }
                        }
                    }
                    if (currentNode.getNodeName().endsWith("propname")) {
                        type = FIND_PROPERTY_NAMES;
                    }
                    if (currentNode.getNodeName().endsWith("allprop")) {
                        type = FIND_ALL_PROP;
                    }
                    break;
                }
            }
        } catch(Exception e) {
            // Most likely there was no content : we use the defaults.
            // TODO : Enhance that !
        }
        return type;
    }


    private int getDepth(HttpServletRequest req) {
        int depth = INFINITY;
        String depthStr = req.getHeader("Depth");

        if (depthStr == null) {
            depth = INFINITY;
        } else {
            if (depthStr.equals("0")) {
                depth = 0;
            } else if (depthStr.equals("1")) {
                depth = 1;
            } else if (depthStr.equals("infinity")) {
                depth = INFINITY;
            }
        }
        return depth;
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
     * Propfind helper method.
     *
     * @param req The servlet request
     * @param resources Resources object associated with this context
     * @param out XML response to the Propfind request
     * @param path Path of the current resource
     * @param type Propfind type
     * @param propertiesVector If the propfind type is find properties by
     * name, then this Vector contains those properties
     */
    protected void parseProperties(HttpServletRequest req,
                                 XMLWriter out,
                                 String path, int type,
                                 Vector propertiesVector) {

        // Exclude any resource in the /WEB-INF and /META-INF subdirectories
        // (the "toUpperCase()" avoids problems on Windows systems)
        if (path.toUpperCase().startsWith("/WEB-INF") ||
            path.toUpperCase().startsWith("/META-INF"))
            return;

        File cacheEntry = new File(basePath, path);

        out.writeElement(null, "response", XMLWriter.OPENING);
        String status = new String("HTTP/1.1 " + WebdavStatus.SC_OK + " "
                                   + WebdavStatus.getStatusText
                                   (WebdavStatus.SC_OK));

        // Generating href element
        out.writeElement(null, "href", XMLWriter.OPENING);

        String href = req.getContextPath();// + req.getServletPath() + 
        //  req.getPathInfo();
        
        // ??? 
        if ((href.endsWith("/")) && (path.startsWith("/")))
            href += path.substring(1);
        else
            href += path;
        if ((cacheEntry.isDirectory()) && (!href.endsWith("/")))
            href += "/";

        out.writeText(rewriteUrl(href));

        out.writeElement(null, "href", XMLWriter.CLOSING);

        String resourceName = path;
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash != -1)
            resourceName = resourceName.substring(lastSlash + 1);

        switch (type) {

        case FIND_ALL_PROP :

            out.writeElement(null, "propstat", XMLWriter.OPENING);
            out.writeElement(null, "prop", XMLWriter.OPENING);

            out.writeProperty
                (null, "creationdate",
                 getISOCreationDate(cacheEntry.lastModified()));
            out.writeElement(null, "displayname", XMLWriter.OPENING);
            out.writeData(resourceName);
            out.writeElement(null, "displayname", XMLWriter.CLOSING);
            if (!cacheEntry.isDirectory()) {
                out.writeProperty(null, "getlastmodified", 
                        FastHttpDateFormat.formatDate(cacheEntry.lastModified(), 
                                null));
                out.writeProperty(null, "getcontentlength",  
                        String.valueOf(cacheEntry.length()));
                String contentType = 
                    getServletContext().getMimeType(cacheEntry.getName());
                if (contentType != null) {
                    out.writeProperty(null, "getcontenttype",  contentType);
                }
                out.writeProperty(null, "getetag",  getETag(cacheEntry));
                out.writeElement(null, "resourcetype", XMLWriter.NO_CONTENT);
            } else {
                out.writeElement(null, "resourcetype", XMLWriter.OPENING);
                out.writeElement(null, "collection", XMLWriter.NO_CONTENT);
                out.writeElement(null, "resourcetype", XMLWriter.CLOSING);
            }

            out.writeProperty(null, "source", "");
            out.writeElement(null, "prop", XMLWriter.CLOSING);
            out.writeElement(null, "status", XMLWriter.OPENING);
            out.writeText(status);
            out.writeElement(null, "status", XMLWriter.CLOSING);
            out.writeElement(null, "propstat", XMLWriter.CLOSING);
            break;

        case FIND_PROPERTY_NAMES :
            out.writeElement(null, "propstat", XMLWriter.OPENING);
            out.writeElement(null, "prop", XMLWriter.OPENING);
            out.writeElement(null, "creationdate",  XMLWriter.NO_CONTENT);
            out.writeElement(null, "displayname", XMLWriter.NO_CONTENT);
            if (! cacheEntry.isDirectory()) {
                out.writeElement(null, "getcontentlanguage",
                                          XMLWriter.NO_CONTENT);
                out.writeElement(null, "getcontentlength",
                                          XMLWriter.NO_CONTENT);
                out.writeElement(null, "getcontenttype",
                                          XMLWriter.NO_CONTENT);
                out.writeElement(null, "getetag",
                                          XMLWriter.NO_CONTENT);
                out.writeElement(null, "getlastmodified",
                                          XMLWriter.NO_CONTENT);
            }
            out.writeElement(null, "resourcetype", XMLWriter.NO_CONTENT);
            out.writeElement(null, "source", XMLWriter.NO_CONTENT);
            out.writeElement(null, "lockdiscovery", XMLWriter.NO_CONTENT);

            out.writeElement(null, "prop", XMLWriter.CLOSING);
            out.writeElement(null, "status", XMLWriter.OPENING);
            out.writeText(status);
            out.writeElement(null, "status", XMLWriter.CLOSING);
            out.writeElement(null, "propstat", XMLWriter.CLOSING);

            break;

        case FIND_BY_PROPERTY :

            Vector propertiesNotFound = new Vector();

            // Parse the list of properties
            out.writeElement(null, "propstat", XMLWriter.OPENING);
            out.writeElement(null, "prop", XMLWriter.OPENING);

            genPropertiesFound(out, propertiesVector, cacheEntry, 
                    resourceName, propertiesNotFound);

            out.writeElement(null, "prop", XMLWriter.CLOSING);
            out.writeElement(null, "status", XMLWriter.OPENING);
            out.writeText(status);
            out.writeElement(null, "status", XMLWriter.CLOSING);
            out.writeElement(null, "propstat", XMLWriter.CLOSING);

            Enumeration propertiesNotFoundList = propertiesNotFound.elements();

            if (propertiesNotFoundList.hasMoreElements()) {
                status = new String("HTTP/1.1 " + WebdavStatus.SC_NOT_FOUND
                                    + " " + WebdavStatus.getStatusText
                                    (WebdavStatus.SC_NOT_FOUND));

                out.writeElement(null, "propstat", XMLWriter.OPENING);
                out.writeElement(null, "prop", XMLWriter.OPENING);
                while (propertiesNotFoundList.hasMoreElements()) {
                    out.writeElement
                        (null, (String) propertiesNotFoundList.nextElement(),
                         XMLWriter.NO_CONTENT);
                }
                out.writeElement(null, "prop", XMLWriter.CLOSING);
                out.writeElement(null, "status", XMLWriter.OPENING);
                out.writeText(status);
                out.writeElement(null, "status", XMLWriter.CLOSING);
                out.writeElement(null, "propstat", XMLWriter.CLOSING);
            }
            break;

        }

        out.writeElement(null, "response", XMLWriter.CLOSING);
    }


    private void genPropertiesFound(XMLWriter out, Vector propertiesVector, 
                                    File cacheEntry, String resourceName, 
                                    Vector propertiesNotFound) {
        Enumeration properties = propertiesVector.elements();

        while (properties.hasMoreElements()) {
            String property = (String) properties.nextElement();
            if (property.equals("creationdate")) {
                out.writeProperty(null, "creationdate",
                     getISOCreationDate(cacheEntry.lastModified()));
            } else if (property.equals("displayname")) {
                out.writeElement(null, "displayname", XMLWriter.OPENING);
                out.writeData(resourceName);
                out.writeElement(null, "displayname", XMLWriter.CLOSING);
            } else if (property.equals("getcontentlanguage")) {
                if (cacheEntry.isDirectory()) {
                    propertiesNotFound.addElement(property);
                } else {
                    out.writeElement(null, "getcontentlanguage",
                            XMLWriter.NO_CONTENT);
                }
            } else if (property.equals("getcontentlength")) {
                if (cacheEntry.isDirectory()) {
                    propertiesNotFound.addElement(property);
                } else {
                    out.writeProperty(null, "getcontentlength",
                         (String.valueOf(cacheEntry.length())));
                }
            } else if (property.equals("getcontenttype")) {
                if (cacheEntry.isDirectory()) {
                    propertiesNotFound.addElement(property);
                } else {
                    out.writeProperty(null, "getcontenttype",
                         getServletContext().getMimeType(cacheEntry.getName()));
                }
            } else if (property.equals("getetag")) {
                if (cacheEntry.isDirectory()) {
                    propertiesNotFound.addElement(property);
                } else {
                    out.writeProperty(null, "getetag", getETag(cacheEntry));
                }
            } else if (property.equals("getlastmodified")) {
                if (cacheEntry.isDirectory()) {
                    propertiesNotFound.addElement(property);
                } else {
                    out.writeProperty(null, "getlastmodified", 
                            FastHttpDateFormat
                            .formatDate(cacheEntry.lastModified(), null));
                }
            } else if (property.equals("resourcetype")) {
                if (cacheEntry.isDirectory()) {
                    out.writeElement(null, "resourcetype",
                                              XMLWriter.OPENING);
                    out.writeElement(null, "collection",
                                              XMLWriter.NO_CONTENT);
                    out.writeElement(null, "resourcetype",
                                              XMLWriter.CLOSING);
                } else {
                    out.writeElement(null, "resourcetype",
                                              XMLWriter.NO_CONTENT);
                }
            } else if (property.equals("source")) {
                out.writeProperty(null, "source", "");
            } else if (property.equals("supportedlock")) {
                // TODO: hook for Webdav2
                propertiesNotFound.addElement(property);
            } else if (property.equals("lockdiscovery")) {
                propertiesNotFound.addElement(property);
            } else {
                propertiesNotFound.addElement(property);
            }

        }
    }

    // ------------------ PROPPATCH --------------------
    
    /**
     * PROPPATCH Method.
     */
    protected void doProppatch(HttpServletRequest req,
                               HttpServletResponse resp)
            throws ServletException, IOException {
        resp.sendError(WebdavStatus.SC_FORBIDDEN);
    }

    // ------------------ MKCOL --------------------

    /**
     * MKCOL Method.
     */
    protected void doMkcol(HttpServletRequest req, HttpServletResponse resp)
        throws ServletException, IOException {

        if (readOnly) {
            resp.sendError(WebdavStatus.SC_FORBIDDEN);
            return;
        }

        if (isLocked(req)) {
            resp.sendError(WebdavStatus.SC_LOCKED);
            return;
        }

        String path = getRelativePath(req);

        if ((path.toUpperCase().startsWith("/WEB-INF")) ||
            (path.toUpperCase().startsWith("/META-INF"))) {
            resp.sendError(WebdavStatus.SC_FORBIDDEN);
            return;
        }

        File object = new File(basePath,path);
        
        // Can't create a collection if a resource already exists at the given
        // path
        if (object.exists()) {
            // Get allowed methods
            StringBuilder methodsAllowed = determineMethodsAllowed(basePath,
                                                                  req);

            resp.addHeader("Allow", methodsAllowed.toString());

            resp.sendError(WebdavStatus.SC_METHOD_NOT_ALLOWED);
            return;
        }

        if (req.getInputStream().available() > 0) {
            DocumentBuilder documentBuilder = getDocumentBuilder();
            try {
                Document document = documentBuilder.parse
                    (new InputSource(req.getInputStream()));
                // TODO : Process this request body
                resp.sendError(WebdavStatus.SC_NOT_IMPLEMENTED);
                return;

            } catch(SAXException saxe) {
                // Parse error - assume invalid content
                resp.sendError(WebdavStatus.SC_BAD_REQUEST);
                return;
            }
        }

        boolean result = true;
        
        File newDir = new File(basePath, path);
        result = newDir.mkdir();

        if (!result) {
            resp.sendError(WebdavStatus.SC_CONFLICT,
                           WebdavStatus.getStatusText
                           (WebdavStatus.SC_CONFLICT));
        } else {
            resp.setStatus(WebdavStatus.SC_CREATED);
            // Removing any lock-null resource which would be present
            lockNullResources.remove(path);
        }

    }

    /**
     * DELETE Method.
     */
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp)
        throws ServletException, IOException {

        if (readOnly) {
            resp.sendError(WebdavStatus.SC_FORBIDDEN);
            return;
        }

        if (isLocked(req)) {
            resp.sendError(WebdavStatus.SC_LOCKED);
            return;
        }

        deleteResource(req, resp);

    }


    /**
     * This is not part of DefaultServlet - all PUT and write operations
     * should be handled by webdav servlet, which allows more control
     */
    protected void doPut(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        if (isLocked(req)) {
            resp.sendError(WebdavStatus.SC_LOCKED);
            return;
        }

        if (readOnly) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        String path = getRelativePath(req);

        if (path.indexOf("..") >= 0) {
            // not supported, too dangerous
            // what else to escape ?
            resp.setStatus(404);
            return;
        }

        File resFile = new File(basePath, path);
        boolean exists = resFile.exists();
        
        // extra check
        if (!resFile.getCanonicalPath().startsWith(basePathName)) {
            //log.info("File outside basedir " + basePathS + " " + f);
            resp.setStatus(404);
            return;
        }


        boolean result = true;

        // Temp. content file used to support partial PUT
        //File contentFile = null;

        String rangeHeader = req.getHeader("Content-Range");
        Range range = null;
        if ( rangeHeader != null ) {
            // we have a header, but has bad value.
            // original catalina code has a bug I think
            range = Range.parseContentRange(rangeHeader);
            if (range == null) { // can't parse
                resp.sendError(HttpServletResponse.SC_BAD_REQUEST);
                return;
            }
        }

        InputStream resourceInputStream = null;

        // Append data specified in ranges to existing content for this
        // resource - create a temp. file on the local filesystem to
        // perform this operation
        // Assume just one range is specified for now
        if (range != null) {
            //contentFile = executePartialPut(req, range, path);
            //resourceInputStream = new FileInputStream(contentFile);
            resp.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED);
            return;
        } else {
            resourceInputStream = req.getInputStream();
        }

        try {
            // will override 
            OutputStream fos = getFilesystem().getOutputStream(resFile.getPath());
            CopyUtils.copy(resourceInputStream, fos);
        } catch(IOException e) {
            result = false;
        }

        if (result) {
            if (exists) {
                resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
            } else {
                resp.setStatus(HttpServletResponse.SC_CREATED);
            }
        } else {
            resp.sendError(HttpServletResponse.SC_CONFLICT);
        }

        // Removing any lock-null resource which would be present
        lockNullResources.remove(path);
    }


    /**
     * Handle a partial PUT.  New content specified in request is appended to
     * existing content in oldRevisionContent (if present). This code does
     * not support simultaneous partial updates to the same resource.
     */
//    protected File executePartialPut(HttpServletRequest req, Range range,
//                                     String path)
//        throws IOException {
//
//        // Append data specified in ranges to existing content for this
//        // resource - create a temp. file on the local filesystem to
//        // perform this operation
//        File tempDir = (File) getServletContext().getAttribute
//            ("javax.servlet.context.tempdir");
//        // Convert all '/' characters to '.' in resourcePath
//        String convertedResourcePath = path.replace('/', '.');
//        File contentFile = new File(tempDir, convertedResourcePath);
//        if (contentFile.createNewFile()) {
//            // Clean up contentFile when Tomcat is terminated
//            contentFile.deleteOnExit();
//        }
//
//        RandomAccessFile randAccessContentFile =
//            new RandomAccessFile(contentFile, "rw");
//
//        Resource oldResource = null;
//        try {
//            Object obj = resources.lookup(path);
//            if (obj instanceof Resource)
//                oldResource = (Resource) obj;
//        } catch (NamingException e) {
//        }
//
//        // Copy data in oldRevisionContent to contentFile
//        if (oldResource != null) {
//            BufferedInputStream bufOldRevStream =
//                new BufferedInputStream(oldResource.streamContent(),
//                                        BUFFER_SIZE);
//
//            int numBytesRead;
//            byte[] copyBuffer = new byte[BUFFER_SIZE];
//            while ((numBytesRead = bufOldRevStream.read(copyBuffer)) != -1) {
//                randAccessContentFile.write(copyBuffer, 0, numBytesRead);
//            }
//
//            bufOldRevStream.close();
//        }
//
//        randAccessContentFile.setLength(range.length);
//
//        // Append data in request input stream to contentFile
//        randAccessContentFile.seek(range.start);
//        int numBytesRead;
//        byte[] transferBuffer = new byte[BUFFER_SIZE];
//        BufferedInputStream requestBufInStream =
//            new BufferedInputStream(req.getInputStream(), BUFFER_SIZE);
//        while ((numBytesRead = requestBufInStream.read(transferBuffer)) != -1) {
//            randAccessContentFile.write(transferBuffer, 0, numBytesRead);
//        }
//        randAccessContentFile.close();
//        requestBufInStream.close();
//
//        return contentFile;
//
//    }


    /**
     * COPY Method.
     */
    protected void doCopy(HttpServletRequest req, HttpServletResponse resp)
        throws ServletException, IOException {

        if (readOnly) {
            resp.sendError(WebdavStatus.SC_FORBIDDEN);
            return;
        }

        copyResource(req, resp);
    }


    /**
     * MOVE Method.
     */
    protected void doMove(HttpServletRequest req, HttpServletResponse resp)
        throws ServletException, IOException {

        if (readOnly) {
            resp.sendError(WebdavStatus.SC_FORBIDDEN);
            return;
        }

        if (isLocked(req)) {
            resp.sendError(WebdavStatus.SC_LOCKED);
            return;
        }

        String path = getRelativePath(req);

        if (copyResource(req, resp)) {
            deleteResource(path, req, resp, false);
        }

    }


    /**
     * LOCK Method.
     */
    protected void doLock(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        resp.sendError(WebdavStatus.SC_NOT_IMPLEMENTED);
    }

    /**
     * UNLOCK Method.
     */
    protected void doUnlock(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        resp.sendError(WebdavStatus.SC_NOT_IMPLEMENTED);
    }

    // -------------------------------------------------------- protected Methods

    /**
     * Generate the namespace declarations.
     */
    protected String generateNamespaceDeclarations() {
        return " xmlns=\"" + DEFAULT_NAMESPACE + "\"";
    }


    /**
     * Check to see if a resource is currently write locked. The method
     * will look at the "If" header to make sure the client
     * has give the appropriate lock tokens.
     *
     * @param req Servlet request
     * @return boolean true if the resource is locked (and no appropriate
     * lock token has been found for at least one of the non-shared locks which
     * are present on the resource).
     */
    protected boolean isLocked(HttpServletRequest req) {
        return false;
    }

    /**
     * Check to see if a resource is currently write locked.
     *
     * @param path Path of the resource
     * @param ifHeader "If" HTTP header which was included in the request
     * @return boolean true if the resource is locked (and no appropriate
     * lock token has been found for at least one of the non-shared locks which
     * are present on the resource).
     */
    protected boolean isLocked(String path, String ifHeader) {
        return false;
    }


    /**
     * Copy a resource.
     *
     * @param req Servlet request
     * @param resp Servlet response
     * @return boolean true if the copy is successful
     */
    protected boolean copyResource(HttpServletRequest req,
                                 HttpServletResponse resp)
        throws ServletException, IOException {

        // Parsing destination header

        String destinationPath = req.getHeader("Destination");

        if (destinationPath == null) {
            resp.sendError(WebdavStatus.SC_BAD_REQUEST);
            return false;
        }

        // Remove url encoding from destination
        destinationPath = RequestUtil.URLDecode(destinationPath, "UTF8");

        destinationPath = removeDestinationPrefix(req, destinationPath);

        // Normalise destination path (remove '.' and '..')
        destinationPath = UrlUtils.normalize(destinationPath);

        String contextPath = req.getContextPath();
        if ((contextPath != null) &&
            (destinationPath.startsWith(contextPath))) {
            destinationPath = destinationPath.substring(contextPath.length());
        }

        String pathInfo = req.getPathInfo();
        if (pathInfo != null) {
            String servletPath = req.getServletPath();
            if ((servletPath != null) &&
                (destinationPath.startsWith(servletPath))) {
                destinationPath = destinationPath.substring(servletPath.length());
            }
        }


        if ((destinationPath.toUpperCase().startsWith("/WEB-INF")) ||
            (destinationPath.toUpperCase().startsWith("/META-INF"))) {
            resp.sendError(WebdavStatus.SC_FORBIDDEN);
            return false;
        }

        String path = getRelativePath(req);

        if ((path.toUpperCase().startsWith("/WEB-INF")) ||
            (path.toUpperCase().startsWith("/META-INF"))) {
            resp.sendError(WebdavStatus.SC_FORBIDDEN);
            return false;
        }

        if (destinationPath.equals(path)) {
            resp.sendError(WebdavStatus.SC_FORBIDDEN);
            return false;
        }

        // Parsing overwrite header

        boolean overwrite = true;
        String overwriteHeader = req.getHeader("Overwrite");

        if (overwriteHeader != null) {
            if (overwriteHeader.equalsIgnoreCase("T")) {
                overwrite = true;
            } else {
                overwrite = false;
            }
        }

        // Overwriting the destination

        boolean exists = true;
        File f1 = new File(basePath, destinationPath);
        if (!f1.exists()) {
            exists = false;
        }

        if (overwrite) {
            // Delete destination resource, if it exists
            if (exists) {
                if (!deleteResource(destinationPath, req, resp, true)) {
                    return false;
                }
            } else {
                resp.setStatus(WebdavStatus.SC_CREATED);
            }
        } else {
            // If the destination exists, then it's a conflict
            if (exists) {
                resp.sendError(WebdavStatus.SC_PRECONDITION_FAILED);
                return false;
            }
        }

        // Copying source to destination

        Hashtable errorList = new Hashtable();

        boolean result = copyResource(basePath, errorList,
                                      path, destinationPath);

        if ((!result) || (!errorList.isEmpty())) {
            sendReport(req, resp, errorList);
            return false;
        }

        // Removing any lock-null resource which would be present at
        // the destination path
        lockNullResources.remove(destinationPath);
        return true;

    }


    private String removeDestinationPrefix(HttpServletRequest req, 
                                           String destinationPath) {
        int protocolIndex = destinationPath.indexOf("://");
        if (protocolIndex >= 0) {
            // if the Destination URL contains the protocol, we can safely
            // trim everything upto the first "/" character after "://"
            int firstSeparator =
                destinationPath.indexOf("/", protocolIndex + 4);
            if (firstSeparator < 0) {
                destinationPath = "/";
            } else {
                destinationPath = destinationPath.substring(firstSeparator);
            }
        } else {
            String hostName = req.getServerName();
            if ((hostName != null) && (destinationPath.startsWith(hostName))) {
                destinationPath = destinationPath.substring(hostName.length());
            }

            int portIndex = destinationPath.indexOf(":");
            if (portIndex >= 0) {
                destinationPath = destinationPath.substring(portIndex);
            }

            if (destinationPath.startsWith(":")) {
                int firstSeparator = destinationPath.indexOf("/");
                if (firstSeparator < 0) {
                    destinationPath = "/";
                } else {
                    destinationPath =
                        destinationPath.substring(firstSeparator);
                }
            }
        }
        return destinationPath;
    }


    /**
     * Copy a collection.
     *
     * @param resources Resources implementation to be used
     * @param errorList Hashtable containing the list of errors which occurred
     * during the copy operation
     * @param source Path of the resource to be copied
     * @param dest Destination path
     */
    protected boolean copyResource(File resources, Hashtable errorList,
                                   String source, String dest) {

        File object = new File(basePath, source);
        File destF = new File(basePath, dest);
        
        if (object.isDirectory()) {

            boolean done = destF.mkdirs();
            if (!done) {
                errorList.put
                    (dest, new Integer(WebdavStatus.SC_CONFLICT));
                return false;
            }

            File[] enumeration = object.listFiles();
            for (int i=0; i<enumeration.length; i++) {
                String childDest = dest;
                if (!childDest.equals("/"))
                    childDest += "/";
                childDest += enumeration[i].getName();
                String childSrc = source;
                if (!childSrc.equals("/"))
                    childSrc += "/";
                childSrc += enumeration[i].getName();
                copyResource(resources, errorList, childSrc, childDest);
            }

        } else {

            try {
                CopyUtils.copy(getFilesystem().getInputStream(object.getPath()), 
                    getFilesystem().getOutputStream(dest));
            } catch(IOException ex ) {
                errorList.put
                (source,
                        new Integer(WebdavStatus.SC_INTERNAL_SERVER_ERROR));
                return false;
            }

        }

        return true;

    }


    /**
     * Delete a resource.
     *
     * @param req Servlet request
     * @param resp Servlet response
     * @return boolean true if the copy is successful
     */
    protected boolean deleteResource(HttpServletRequest req,
                                   HttpServletResponse resp)
        throws ServletException, IOException {

        String path = getRelativePath(req);

        return deleteResource(path, req, resp, true);

    }


    /**
     * Delete a resource.
     *
     * @param path Path of the resource which is to be deleted
     * @param req Servlet request
     * @param resp Servlet response
     * @param setStatus Should the response status be set on successful
     *                  completion
     */
    protected boolean deleteResource(String path, HttpServletRequest req,
                                   HttpServletResponse resp, boolean setStatus)
        throws ServletException, IOException {

        if ((path.toUpperCase().startsWith("/WEB-INF")) ||
            (path.toUpperCase().startsWith("/META-INF"))) {
            resp.sendError(WebdavStatus.SC_FORBIDDEN);
            return false;
        }

        String ifHeader = req.getHeader("If");
        if (ifHeader == null)
            ifHeader = "";

        String lockTokenHeader = req.getHeader("Lock-Token");
        if (lockTokenHeader == null)
            lockTokenHeader = "";

        if (isLocked(path, ifHeader + lockTokenHeader)) {
            resp.sendError(WebdavStatus.SC_LOCKED);
            return false;
        }

        boolean exists = true;
        File object = new File(basePath, path);
        if (!object.exists()) {
            exists = false;
        }

        if (!exists) {
            resp.sendError(WebdavStatus.SC_NOT_FOUND);
            return false;
        }

        boolean collection = object.isDirectory();

        if (!collection) {
            boolean deleted = object.delete();
            if (!deleted) {
                resp.sendError(WebdavStatus.SC_INTERNAL_SERVER_ERROR);
                return false;
            }
        } else {

            Hashtable errorList = new Hashtable();

            deleteCollection(req, basePath, path, errorList);
            boolean deleted = object.delete();
            if (!deleted) {
                errorList.put(path, new Integer
                    (WebdavStatus.SC_INTERNAL_SERVER_ERROR));
            }

            if (!errorList.isEmpty()) {

                sendReport(req, resp, errorList);
                return false;

            }

        }
        if (setStatus) {
            resp.setStatus(WebdavStatus.SC_NO_CONTENT);
        }
        return true;

    }


    /**
     * Deletes a collection.
     *
     * @param resources Resources implementation associated with the context
     * @param path Path to the collection to be deleted
     * @param errorList Contains the list of the errors which occurred
     */
    protected void deleteCollection(HttpServletRequest req,
                                  File resources,
                                  String path, Hashtable errorList) {

        if ((path.toUpperCase().startsWith("/WEB-INF")) ||
            (path.toUpperCase().startsWith("/META-INF"))) {
            errorList.put(path, new Integer(WebdavStatus.SC_FORBIDDEN));
            return;
        }

        String ifHeader = req.getHeader("If");
        if (ifHeader == null)
            ifHeader = "";

        String lockTokenHeader = req.getHeader("Lock-Token");
        if (lockTokenHeader == null)
            lockTokenHeader = "";

        File f = new File(basePath, path);
        File[] enumeration = f.listFiles();

        for (int i=0; i<enumeration.length; i++) {
            String childName = path;
            if (!childName.equals("/"))
                childName += "/";
            
            childName += enumeration[i].getName();

            if (isLocked(childName, ifHeader + lockTokenHeader)) {

                errorList.put(childName, new Integer(WebdavStatus.SC_LOCKED));

            } else {
                File object = enumeration[i];
                if (object.isDirectory()) {
                    deleteCollection(req, resources, childName, errorList);
                }
                
                boolean deleted = object.delete();
                if (!deleted) {
                    if (!(object.isDirectory())) {
                        // If it's not a collection, then it's an unknown
                        // error
                        errorList.put
                        (childName, new Integer
                                (WebdavStatus.SC_INTERNAL_SERVER_ERROR));
                    }
                }
            }

        }

    }


    /**
     * Send a multistatus element containing a complete error report to the
     * client.
     *
     * @param req Servlet request
     * @param resp Servlet response
     * @param errorList List of error to be displayed
     */
    protected void sendReport(HttpServletRequest req, HttpServletResponse resp,
                            Hashtable errorList)
        throws ServletException, IOException {

        resp.setStatus(WebdavStatus.SC_MULTI_STATUS);

        String absoluteUri = req.getRequestURI();
        String relativePath = getRelativePath(req);

        XMLWriter generatedXML = new XMLWriter();
        generatedXML.writeXMLHeader();

        generatedXML.writeElement(null, "multistatus"
                                  + generateNamespaceDeclarations(),
                                  XMLWriter.OPENING);

        Enumeration pathList = errorList.keys();
        while (pathList.hasMoreElements()) {

            String errorPath = (String) pathList.nextElement();
            int errorCode = ((Integer) errorList.get(errorPath)).intValue();

            generatedXML.writeElement(null, "response", XMLWriter.OPENING);

            generatedXML.writeElement(null, "href", XMLWriter.OPENING);
            String toAppend = errorPath.substring(relativePath.length());
            if (!toAppend.startsWith("/"))
                toAppend = "/" + toAppend;
            generatedXML.writeText(absoluteUri + toAppend);
            generatedXML.writeElement(null, "href", XMLWriter.CLOSING);
            generatedXML.writeElement(null, "status", XMLWriter.OPENING);
            generatedXML
                .writeText("HTTP/1.1 " + errorCode + " "
                           + WebdavStatus.getStatusText(errorCode));
            generatedXML.writeElement(null, "status", XMLWriter.CLOSING);

            generatedXML.writeElement(null, "response", XMLWriter.CLOSING);

        }

        generatedXML.writeElement(null, "multistatus", XMLWriter.CLOSING);

        Writer writer = resp.getWriter();
        writer.write(generatedXML.toString());
        writer.close();

    }




    /**
     * Return JAXP document builder instance.
     */
    protected DocumentBuilder getDocumentBuilder()
        throws ServletException {
        DocumentBuilder documentBuilder = null;
        DocumentBuilderFactory documentBuilderFactory = null;
        try {
            documentBuilderFactory = DocumentBuilderFactory.newInstance();
            documentBuilderFactory.setNamespaceAware(true);
            documentBuilder = documentBuilderFactory.newDocumentBuilder();
        } catch(ParserConfigurationException e) {
            throw new ServletException("Parser error " + e);
        }
        return documentBuilder;
    }



    /**
     * Get creation date in ISO format.
     */
    protected String getISOCreationDate(long creationDate) {
        StringBuilder creationDateValue = new StringBuilder
            (creationDateFormat.format
             (new Date(creationDate)));
        /*
        int offset = Calendar.getInstance().getTimeZone().getRawOffset()
            / 3600000; // FIXME ?
        if (offset < 0) {
            creationDateValue.append("-");
            offset = -offset;
        } else if (offset > 0) {
            creationDateValue.append("+");
        }
        if (offset != 0) {
            if (offset < 10)
                creationDateValue.append("0");
            creationDateValue.append(offset + ":00");
        } else {
            creationDateValue.append("Z");
        }
        */
        return creationDateValue.toString();
    }

    /**
     * Determines the methods normally allowed for the resource.
     *
     */
    protected StringBuilder determineMethodsAllowed(File basePath,
                                                   HttpServletRequest req) {

        StringBuilder methodsAllowed = new StringBuilder();
        String path = getRelativePath(req);
        File object = new File(basePath, path);
        if (!object.exists()) {
            methodsAllowed.append("OPTIONS, MKCOL, PUT");
            return methodsAllowed;
        }
        methodsAllowed.append("OPTIONS, GET, HEAD, POST, DELETE, TRACE");
        methodsAllowed.append(", PROPPATCH, COPY, MOVE, PROPFIND");

        if (object.isDirectory()) {
            methodsAllowed.append(", PUT");
        }

        return methodsAllowed;
    }
}


// --------------------------------------------------------  WebdavStatus Class


/**
 * Wraps the HttpServletResponse class to abstract the
 * specific protocol used.  To support other protocols
 * we would only need to modify this class and the
 * WebDavRetCode classes.
 *
 * @author              Marc Eaddy
 * @version             1.0, 16 Nov 1997
 */
class WebdavStatus {


    // ----------------------------------------------------- Instance Variables


    /**
     * This Hashtable contains the mapping of HTTP and WebDAV
     * status codes to descriptive text.  This is a static
     * variable.
     */
    protected static Hashtable mapStatusCodes = new Hashtable();


    // ------------------------------------------------------ HTTP Status Codes


    /**
     * Status code (200) indicating the request succeeded normally.
     */
    public static final int SC_OK = HttpServletResponse.SC_OK;


    /**
     * Status code (201) indicating the request succeeded and created
     * a new resource on the server.
     */
    public static final int SC_CREATED = HttpServletResponse.SC_CREATED;


    /**
     * Status code (202) indicating that a request was accepted for
     * processing, but was not completed.
     */
    public static final int SC_ACCEPTED = HttpServletResponse.SC_ACCEPTED;


    /**
     * Status code (204) indicating that the request succeeded but that
     * there was no new information to return.
     */
    public static final int SC_NO_CONTENT = HttpServletResponse.SC_NO_CONTENT;


    /**
     * Status code (301) indicating that the resource has permanently
     * moved to a new location, and that future references should use a
     * new URI with their requests.
     */
    public static final int SC_MOVED_PERMANENTLY =
        HttpServletResponse.SC_MOVED_PERMANENTLY;


    /**
     * Status code (302) indicating that the resource has temporarily
     * moved to another location, but that future references should
     * still use the original URI to access the resource.
     */
    public static final int SC_MOVED_TEMPORARILY =
        HttpServletResponse.SC_MOVED_TEMPORARILY;


    /**
     * Status code (304) indicating that a conditional GET operation
     * found that the resource was available and not modified.
     */
    public static final int SC_NOT_MODIFIED =
        HttpServletResponse.SC_NOT_MODIFIED;


    /**
     * Status code (400) indicating the request sent by the client was
     * syntactically incorrect.
     */
    public static final int SC_BAD_REQUEST =
        HttpServletResponse.SC_BAD_REQUEST;


    /**
     * Status code (401) indicating that the request requires HTTP
     * authentication.
     */
    public static final int SC_UNAUTHORIZED =
        HttpServletResponse.SC_UNAUTHORIZED;


    /**
     * Status code (403) indicating the server understood the request
     * but refused to fulfill it.
     */
    public static final int SC_FORBIDDEN = HttpServletResponse.SC_FORBIDDEN;


    /**
     * Status code (404) indicating that the requested resource is not
     * available.
     */
    public static final int SC_NOT_FOUND = HttpServletResponse.SC_NOT_FOUND;


    /**
     * Status code (500) indicating an error inside the HTTP service
     * which prevented it from fulfilling the request.
     */
    public static final int SC_INTERNAL_SERVER_ERROR =
        HttpServletResponse.SC_INTERNAL_SERVER_ERROR;


    /**
     * Status code (501) indicating the HTTP service does not support
     * the functionality needed to fulfill the request.
     */
    public static final int SC_NOT_IMPLEMENTED =
        HttpServletResponse.SC_NOT_IMPLEMENTED;


    /**
     * Status code (502) indicating that the HTTP server received an
     * invalid response from a server it consulted when acting as a
     * proxy or gateway.
     */
    public static final int SC_BAD_GATEWAY =
        HttpServletResponse.SC_BAD_GATEWAY;


    /**
     * Status code (503) indicating that the HTTP service is
     * temporarily overloaded, and unable to handle the request.
     */
    public static final int SC_SERVICE_UNAVAILABLE =
        HttpServletResponse.SC_SERVICE_UNAVAILABLE;


    /**
     * Status code (100) indicating the client may continue with
     * its request.  This interim response is used to inform the
     * client that the initial part of the request has been
     * received and has not yet been rejected by the server.
     */
    public static final int SC_CONTINUE = 100;


    /**
     * Status code (405) indicating the method specified is not
     * allowed for the resource.
     */
    public static final int SC_METHOD_NOT_ALLOWED = 405;


    /**
     * Status code (409) indicating that the request could not be
     * completed due to a conflict with the current state of the
     * resource.
     */
    public static final int SC_CONFLICT = 409;


    /**
     * Status code (412) indicating the precondition given in one
     * or more of the request-header fields evaluated to false
     * when it was tested on the server.
     */
    public static final int SC_PRECONDITION_FAILED = 412;


    /**
     * Status code (413) indicating the server is refusing to
     * process a request because the request entity is larger
     * than the server is willing or able to process.
     */
    public static final int SC_REQUEST_TOO_LONG = 413;


    /**
     * Status code (415) indicating the server is refusing to service
     * the request because the entity of the request is in a format
     * not supported by the requested resource for the requested
     * method.
     */
    public static final int SC_UNSUPPORTED_MEDIA_TYPE = 415;


    // -------------------------------------------- Extended WebDav status code


    /**
     * Status code (207) indicating that the response requires
     * providing status for multiple independent operations.
     */
    public static final int SC_MULTI_STATUS = 207;
    // This one colides with HTTP 1.1
    // "207 Parital Update OK"


    /**
     * Status code (418) indicating the entity body submitted with
     * the PATCH method was not understood by the resource.
     */
    public static final int SC_UNPROCESSABLE_ENTITY = 418;
    // This one colides with HTTP 1.1
    // "418 Reauthentication Required"


    /**
     * Status code (419) indicating that the resource does not have
     * sufficient space to record the state of the resource after the
     * execution of this method.
     */
    public static final int SC_INSUFFICIENT_SPACE_ON_RESOURCE = 419;
    // This one colides with HTTP 1.1
    // "419 Proxy Reauthentication Required"


    /**
     * Status code (420) indicating the method was not executed on
     * a particular resource within its scope because some part of
     * the method's execution failed causing the entire method to be
     * aborted.
     */
    public static final int SC_METHOD_FAILURE = 420;


    /**
     * Status code (423) indicating the destination resource of a
     * method is locked, and either the request did not contain a
     * valid Lock-Info header, or the Lock-Info header identifies
     * a lock held by another principal.
     */
    public static final int SC_LOCKED = 423;


    // ------------------------------------------------------------ Initializer


    static {
        // HTTP 1.0 tatus Code
        addStatusCodeMap(SC_OK, "OK");
        addStatusCodeMap(SC_CREATED, "Created");
        addStatusCodeMap(SC_ACCEPTED, "Accepted");
        addStatusCodeMap(SC_NO_CONTENT, "No Content");
        addStatusCodeMap(SC_MOVED_PERMANENTLY, "Moved Permanently");
        addStatusCodeMap(SC_MOVED_TEMPORARILY, "Moved Temporarily");
        addStatusCodeMap(SC_NOT_MODIFIED, "Not Modified");
        addStatusCodeMap(SC_BAD_REQUEST, "Bad Request");
        addStatusCodeMap(SC_UNAUTHORIZED, "Unauthorized");
        addStatusCodeMap(SC_FORBIDDEN, "Forbidden");
        addStatusCodeMap(SC_NOT_FOUND, "Not Found");
        addStatusCodeMap(SC_INTERNAL_SERVER_ERROR, "Internal Server Error");
        addStatusCodeMap(SC_NOT_IMPLEMENTED, "Not Implemented");
        addStatusCodeMap(SC_BAD_GATEWAY, "Bad Gateway");
        addStatusCodeMap(SC_SERVICE_UNAVAILABLE, "Service Unavailable");
        addStatusCodeMap(SC_CONTINUE, "Continue");
        addStatusCodeMap(SC_METHOD_NOT_ALLOWED, "Method Not Allowed");
        addStatusCodeMap(SC_CONFLICT, "Conflict");
        addStatusCodeMap(SC_PRECONDITION_FAILED, "Precondition Failed");
        addStatusCodeMap(SC_REQUEST_TOO_LONG, "Request Too Long");
        addStatusCodeMap(SC_UNSUPPORTED_MEDIA_TYPE, "Unsupported Media Type");
        // WebDav Status Codes
        addStatusCodeMap(SC_MULTI_STATUS, "Multi-Status");
        addStatusCodeMap(SC_UNPROCESSABLE_ENTITY, "Unprocessable Entity");
        addStatusCodeMap(SC_INSUFFICIENT_SPACE_ON_RESOURCE,
                         "Insufficient Space On Resource");
        addStatusCodeMap(SC_METHOD_FAILURE, "Method Failure");
        addStatusCodeMap(SC_LOCKED, "Locked");
    }


    // --------------------------------------------------------- Public Methods


    /**
     * Returns the HTTP status text for the HTTP or WebDav status code
     * specified by looking it up in the static mapping.  This is a
     * static function.
     *
     * @param   nHttpStatusCode [IN] HTTP or WebDAV status code
     * @return  A string with a short descriptive phrase for the
     *                  HTTP status code (e.g., "OK").
     */
    public static String getStatusText(int nHttpStatusCode) {
        Integer intKey = new Integer(nHttpStatusCode);

        if (!mapStatusCodes.containsKey(intKey)) {
            return "";
        } else {
            return (String) mapStatusCodes.get(intKey);
        }
    }


    // -------------------------------------------------------- protected Methods


    /**
     * Adds a new status code -> status text mapping.  This is a static
     * method because the mapping is a static variable.
     *
     * @param   nKey    [IN] HTTP or WebDAV status code
     * @param   strVal  [IN] HTTP status text
     */
    protected static void addStatusCodeMap(int nKey, String strVal) {
        mapStatusCodes.put(new Integer(nKey), strVal);
    }

};

