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
package org.apache.jasper.compiler;

import java.io.IOException;
import java.net.URL;
import java.security.AccessController;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.ServletContext;

import org.apache.jasper.Constants;
import org.apache.jasper.JasperException;
import org.apache.jasper.compiler.tagplugin.TagPlugin;
import org.apache.jasper.compiler.tagplugin.TagPluginContext;
import org.apache.tomcat.util.descriptor.tagplugin.TagPluginParser;
import org.apache.tomcat.util.security.PrivilegedGetTccl;
import org.apache.tomcat.util.security.PrivilegedSetTccl;
import org.xml.sax.SAXException;

/**
 * Manages tag plugin optimizations.
 *
 * @author Kin-man Chung
 */
public class TagPluginManager {

    private static final String META_INF_JASPER_TAG_PLUGINS_XML =
            "META-INF/org.apache.jasper/tagPlugins.xml";
    private static final String TAG_PLUGINS_XML = "/WEB-INF/tagPlugins.xml";
    private final ServletContext ctxt;
    private HashMap<String, TagPlugin> tagPlugins;
    private boolean initialized = false;

    public TagPluginManager(ServletContext ctxt) {
        this.ctxt = ctxt;
    }

    public void apply(Node.Nodes page, ErrorDispatcher err, PageInfo pageInfo)
            throws JasperException {

        init(err);
        if (!tagPlugins.isEmpty()) {
            page.visit(new NodeVisitor(this, pageInfo));
        }
    }

    private void init(ErrorDispatcher err) throws JasperException {
        if (initialized)
            return;

        String blockExternalString = ctxt.getInitParameter(
                Constants.XML_BLOCK_EXTERNAL_INIT_PARAM);
        boolean blockExternal;
        if (blockExternalString == null) {
            blockExternal = true;
        } else {
            blockExternal = Boolean.parseBoolean(blockExternalString);
        }

        TagPluginParser parser;
        ClassLoader original;
        if (Constants.IS_SECURITY_ENABLED) {
            PrivilegedGetTccl pa = new PrivilegedGetTccl();
            original = AccessController.doPrivileged(pa);
        } else {
            original = Thread.currentThread().getContextClassLoader();
        }
        try {
            if (Constants.IS_SECURITY_ENABLED) {
                PrivilegedSetTccl pa =
                        new PrivilegedSetTccl(TagPluginManager.class.getClassLoader());
                AccessController.doPrivileged(pa);
            } else {
                Thread.currentThread().setContextClassLoader(
                        TagPluginManager.class.getClassLoader());
            }

            parser = new TagPluginParser(ctxt, blockExternal);

            Enumeration<URL> urls =
                    ctxt.getClassLoader().getResources(META_INF_JASPER_TAG_PLUGINS_XML);
            if (urls != null) {
                while (urls.hasMoreElements()) {
                    URL url = urls.nextElement();
                    parser.parse(url);
                }
            }

            URL url = ctxt.getResource(TAG_PLUGINS_XML);
            if (url != null) {
                parser.parse(url);
            }
        } catch (IOException | SAXException e) {
            throw new JasperException(e);
        } finally {
            if (Constants.IS_SECURITY_ENABLED) {
                PrivilegedSetTccl pa = new PrivilegedSetTccl(original);
                AccessController.doPrivileged(pa);
            } else {
                Thread.currentThread().setContextClassLoader(original);
            }
        }

        Map<String, String> plugins = parser.getPlugins();
        tagPlugins = new HashMap<>(plugins.size());
        for (Map.Entry<String, String> entry : plugins.entrySet()) {
            try {
                String tagClass = entry.getKey();
                String pluginName = entry.getValue();
                Class<?> pluginClass = ctxt.getClassLoader().loadClass(pluginName);
                TagPlugin plugin = (TagPlugin) pluginClass.newInstance();
                tagPlugins.put(tagClass, plugin);
            } catch (Exception e) {
                err.jspError(e);
            }
        }
        initialized = true;
    }

    /**
     * Invoke tag plugin for the given custom tag, if a plugin exists for
     * the custom tag's tag handler.
     * <p/>
     * The given custom tag node will be manipulated by the plugin.
     */
    private void invokePlugin(Node.CustomTag n, PageInfo pageInfo) {
        TagPlugin tagPlugin = tagPlugins.get(n.getTagHandlerClass().getName());
        if (tagPlugin == null) {
            return;
        }

        TagPluginContext tagPluginContext = new TagPluginContextImpl(n, pageInfo);
        n.setTagPluginContext(tagPluginContext);
        tagPlugin.doTag(tagPluginContext);
    }

    private static class NodeVisitor extends Node.Visitor {
        private final TagPluginManager manager;
        private final PageInfo pageInfo;

        public NodeVisitor(TagPluginManager manager, PageInfo pageInfo) {
            this.manager = manager;
            this.pageInfo = pageInfo;
        }

        @Override
        public void visit(Node.CustomTag n) throws JasperException {
            manager.invokePlugin(n, pageInfo);
            visitBody(n);
        }
    }

    private static class TagPluginContextImpl implements TagPluginContext {
        private final Node.CustomTag node;
        private final PageInfo pageInfo;
        private final HashMap<String, Object> pluginAttributes;
        private Node.Nodes curNodes;

        TagPluginContextImpl(Node.CustomTag n, PageInfo pageInfo) {
            this.node = n;
            this.pageInfo = pageInfo;
            curNodes = new Node.Nodes();
            n.setAtETag(curNodes);
            curNodes = new Node.Nodes();
            n.setAtSTag(curNodes);
            n.setUseTagPlugin(true);
            pluginAttributes = new HashMap<>();
        }

        @Override
        public TagPluginContext getParentContext() {
            Node parent = node.getParent();
            if (!(parent instanceof Node.CustomTag)) {
                return null;
            }
            return ((Node.CustomTag) parent).getTagPluginContext();
        }

        @Override
        public void setPluginAttribute(String key, Object value) {
            pluginAttributes.put(key, value);
        }

        @Override
        public Object getPluginAttribute(String key) {
            return pluginAttributes.get(key);
        }

        @Override
        public boolean isScriptless() {
            return node.getChildInfo().isScriptless();
        }

        @Override
        public boolean isConstantAttribute(String attribute) {
            Node.JspAttribute attr = getNodeAttribute(attribute);
            if (attr == null)
                return false;
            return attr.isLiteral();
        }

        @Override
        public String getConstantAttribute(String attribute) {
            Node.JspAttribute attr = getNodeAttribute(attribute);
            if (attr == null)
                return null;
            return attr.getValue();
        }

        @Override
        public boolean isAttributeSpecified(String attribute) {
            return getNodeAttribute(attribute) != null;
        }

        @Override
        public String getTemporaryVariableName() {
            return node.getRoot().nextTemporaryVariableName();
        }

        @Override
        public void generateImport(String imp) {
            pageInfo.addImport(imp);
        }

        @Override
        public void generateDeclaration(String id, String text) {
            if (pageInfo.isPluginDeclared(id)) {
                return;
            }
            curNodes.add(new Node.Declaration(text, node.getStart(), null));
        }

        @Override
        public void generateJavaSource(String sourceCode) {
            curNodes.add(new Node.Scriptlet(sourceCode, node.getStart(),
                    null));
        }

        @Override
        public void generateAttribute(String attributeName) {
            curNodes.add(new Node.AttributeGenerator(node.getStart(),
                    attributeName,
                    node));
        }

        @Override
        public void dontUseTagPlugin() {
            node.setUseTagPlugin(false);
        }

        @Override
        public void generateBody() {
            // Since we'll generate the body anyway, this is really a nop,
            // except for the fact that it lets us put the Java sources the
            // plugins produce in the correct order (w.r.t the body).
            curNodes = node.getAtETag();
        }

        @Override
        public boolean isTagFile() {
            return pageInfo.isTagFile();
        }

        private Node.JspAttribute getNodeAttribute(String attribute) {
            Node.JspAttribute[] attrs = node.getJspAttributes();
            for (int i = 0; attrs != null && i < attrs.length; i++) {
                if (attrs[i].getName().equals(attribute)) {
                    return attrs[i];
                }
            }
            return null;
        }
    }

}

