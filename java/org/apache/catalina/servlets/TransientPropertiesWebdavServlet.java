/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.catalina.servlets;

import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.util.DOMWriter;
import org.apache.catalina.util.XMLWriter;
import org.w3c.dom.Node;

/**
 * Extended WebDAV Servlet that implements dead properties using storage in memory.
 */
public class TransientPropertiesWebdavServlet extends WebdavServlet {

    private static final long serialVersionUID = 1L;

    private final ConcurrentHashMap<String,ArrayList<Node>> deadProperties = new ConcurrentHashMap<>();

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.addHeader("DAV", "1,2,3");
        resp.addHeader("Allow", determineMethodsAllowed(req));
        resp.addHeader("MS-Author-Via", "DAV");
    }

    @Override
    protected void proppatchResource(String path, ArrayList<ProppatchOperation> operations) {
        boolean protectedProperty = false;
        // Check for the protected properties
        for (ProppatchOperation operation : operations) {
            if (operation.getProtectedProperty()) {
                protectedProperty = true;
                operation.setStatusCode(HttpServletResponse.SC_FORBIDDEN);
            }
        }
        if (protectedProperty) {
            for (ProppatchOperation operation : operations) {
                if (!operation.getProtectedProperty()) {
                    operation.setStatusCode(WebdavStatus.SC_FAILED_DEPENDENCY);
                }
            }
        } else {
            ArrayList<Node> properties = deadProperties.get(path);
            if (properties == null) {
                properties = new ArrayList<>();
                deadProperties.put(path, properties);
            }
            synchronized (properties) {
                for (ProppatchOperation operation : operations) {
                    if (operation.getUpdateType() == PropertyUpdateType.SET) {
                        Node node = operation.getPropertyNode().cloneNode(true);
                        boolean found = false;
                        for (int i = 0; i < properties.size(); i++) {
                            Node propertyNode = properties.get(i);
                            if (propertyEquals(node, propertyNode)) {
                                found = true;
                                properties.set(i, node);
                                break;
                            }
                        }
                        if (!found) {
                            properties.add(node);
                        }
                    }
                    if (operation.getUpdateType() == PropertyUpdateType.REMOVE) {
                        Node node = operation.getPropertyNode();
                        for (int i = 0; i < properties.size(); i++) {
                            Node propertyNode = properties.get(i);
                            if (propertyEquals(node, propertyNode)) {
                                properties.remove(i);
                                break;
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    protected boolean propfindResource(String path, Node property, boolean nameOnly, XMLWriter generatedXML) {
        ArrayList<Node> properties = deadProperties.get(path);
        if (properties != null) {
            synchronized (properties) {
                if (nameOnly) {
                    // Add the names of all properties
                    for (Node node : properties) {
                        generatedXML.writeElement(null, node.getNamespaceURI(), node.getLocalName(),
                                XMLWriter.NO_CONTENT);
                    }
                } else if (property != null) {
                    // Add a single property
                    Node foundNode = null;
                    for (Node node : properties) {
                        if (propertyEquals(node, property)) {
                            foundNode = node;
                        }
                    }
                    if (foundNode != null) {
                        StringWriter strWriter = new StringWriter();
                        DOMWriter domWriter = new DOMWriter(strWriter);
                        domWriter.print(foundNode);
                        generatedXML.writeRaw(strWriter.toString());
                        return true;
                    }
                } else {
                    StringWriter strWriter = new StringWriter();
                    DOMWriter domWriter = new DOMWriter(strWriter);
                    // Add all properties
                    for (Node node : properties) {
                        domWriter.print(node);
                    }
                    generatedXML.writeRaw(strWriter.toString());
                }
            }
        }
        return false;
    }

    @Override
    protected void copyResource(String source, String dest) {
        ArrayList<Node> properties = deadProperties.get(source);
        ArrayList<Node> propertiesDest = deadProperties.get(dest);
        if (properties != null) {
            if (propertiesDest == null) {
                propertiesDest = new ArrayList<>();
                deadProperties.put(dest, propertiesDest);
            }
            synchronized (properties) {
                synchronized (propertiesDest) {
                    for (Node node : properties) {
                        node = node.cloneNode(true);
                        boolean found = false;
                        for (int i = 0; i < propertiesDest.size(); i++) {
                            Node propertyNode = propertiesDest.get(i);
                            if (propertyEquals(node, propertyNode)) {
                                found = true;
                                propertiesDest.set(i, node);
                                break;
                            }
                        }
                        if (!found) {
                            propertiesDest.add(node);
                        }
                    }
                }
            }
        }
    }

    @Override
    protected void deleteResource(String path) {
        deadProperties.remove(path);
    }

    private boolean propertyEquals(Node node1, Node node2) {
        if (node1.getLocalName().equals(node2.getLocalName()) &&
                ((node1.getNamespaceURI() == null && node2.getNamespaceURI() == null) ||
                        (node1.getNamespaceURI() != null && node1.getNamespaceURI().equals(node2.getNamespaceURI())))) {
            return true;
        }
        return false;
    }
}