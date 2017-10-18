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
package org.apache.catalina.util;

import java.io.PrintWriter;
import java.io.Writer;

import org.apache.tomcat.util.security.Escape;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * A DOM writer optimised for use by WebDAV.
 */
public class DOMWriter {

    private final PrintWriter out;


    public DOMWriter(Writer writer) {
        out = new PrintWriter(writer);
    }


    /**
     * Prints the specified node, recursively.
     * @param node The node to output
     */
    public void print(Node node) {

        // is there anything to do?
        if (node == null) {
            return;
        }

        int type = node.getNodeType();
        switch (type) {
            // print document
            case Node.DOCUMENT_NODE:
                print(((Document) node).getDocumentElement());
                out.flush();
                break;

            // print element with attributes
            case Node.ELEMENT_NODE:
                out.print('<');
                out.print(node.getLocalName());
                Attr attrs[] = sortAttributes(node.getAttributes());
                for (int i = 0; i < attrs.length; i++) {
                    Attr attr = attrs[i];
                    out.print(' ');
                    out.print(attr.getLocalName());

                    out.print("=\"");
                    out.print(Escape.xml("", true, attr.getNodeValue()));
                    out.print('"');
                }
                out.print('>');
                printChildren(node);
                break;

            // handle entity reference nodes
            case Node.ENTITY_REFERENCE_NODE:
                printChildren(node);
                break;

            // print cdata sections
            case Node.CDATA_SECTION_NODE:
                out.print(Escape.xml("", true, node.getNodeValue()));
                break;

            // print text
            case Node.TEXT_NODE:
                out.print(Escape.xml("", true, node.getNodeValue()));
                break;

            // print processing instruction
            case Node.PROCESSING_INSTRUCTION_NODE:
                out.print("<?");
                out.print(node.getLocalName());

                String data = node.getNodeValue();
                if (data != null && data.length() > 0) {
                    out.print(' ');
                    out.print(data);
                }
                out.print("?>");
                break;
            }

        if (type == Node.ELEMENT_NODE) {
            out.print("</");
            out.print(node.getLocalName());
            out.print('>');
        }

        out.flush();

    } // print(Node)


    private void printChildren(Node node) {
        NodeList children = node.getChildNodes();
        if (children != null) {
            int len = children.getLength();
            for (int i = 0; i < len; i++) {
                print(children.item(i));
            }
        }
    }


    /**
     * Returns a sorted list of attributes.
     * @param attrs The map to sort
     * @return a sorted attribute array
     */
    private Attr[] sortAttributes(NamedNodeMap attrs) {
        if (attrs == null) {
            return new Attr[0];
        }

        int len = attrs.getLength();
        Attr array[] = new Attr[len];
        for (int i = 0; i < len; i++) {
            array[i] = (Attr) attrs.item(i);
        }
        for (int i = 0; i < len - 1; i++) {
            String name = null;
            name = array[i].getLocalName();
            int index = i;
            for (int j = i + 1; j < len; j++) {
                String curName = null;
                curName = array[j].getLocalName();
                if (curName.compareTo(name) < 0) {
                    name = curName;
                    index = j;
                }
            }
            if (index != i) {
                Attr temp = array[i];
                array[i] = array[index];
                array[index] = temp;
            }
        }

        return array;
    }
}
