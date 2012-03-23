/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.tomcat.util.http.parser;

/**
 * Represents a media-type as per section 3.7 of RFC 2616. Originally generated
 * by <a href="http://javacc.java.net/doc/JJTree.html"> JJTree</a>.
 */
public class AstMediaType extends SimpleNode {

    private static final String CHARSET = "charset";

    public AstMediaType(int id) {
        super(id);
    }

    public AstMediaType(HttpParser p, int id) {
        super(p, id);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(children[0].toString());
        sb.append('/');
        sb.append(children[1].toString());
        for (int i = 2; i < children.length; i++) {
            sb.append(';');
            sb.append(children[i].toString());
        }
        return sb.toString();
    }

    public String toStringNoCharset() {
        StringBuilder sb = new StringBuilder();
        sb.append(children[0].toString());
        sb.append('/');
        sb.append(children[1].toString());
        for (int i = 2; i < children.length; i++) {
            AstParameter p = (AstParameter) children[i];
            if (!CHARSET.equals(
                    p.children[0].jjtGetValue().toString().toLowerCase())) {
                sb.append(';');
                sb.append(p.toString());
            }
        }
        return sb.toString();
    }

    public String getCharset() {
        for (int i = 2; i < children.length; i++) {
            AstParameter p = (AstParameter) children[i];
            if (CHARSET.equals(
                    p.children[0].jjtGetValue().toString().toLowerCase())) {
                return p.children[1].jjtGetValue().toString();
            }
        }
        return null;
    }
}
