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
package org.apache.jasper.runtime;

import java.io.IOException;
import java.security.AccessController;
import java.security.PrivilegedAction;

import jakarta.servlet.Servlet;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.jsp.JspApplicationContext;
import jakarta.servlet.jsp.JspEngineInfo;
import jakarta.servlet.jsp.JspFactory;
import jakarta.servlet.jsp.PageContext;

import org.apache.jasper.Constants;

/**
 * Implementation of JspFactory.
 *
 * @author Anil K. Vijendran
 */
public class JspFactoryImpl extends JspFactory {

    private final ThreadLocal<PageContextPool> localPool = new ThreadLocal<>();
    private int poolSize = -1;

    @Override
    public PageContext getPageContext(Servlet servlet, ServletRequest request,
            ServletResponse response, String errorPageURL, boolean needsSession,
            int bufferSize, boolean autoflush) {

        if( Constants.IS_SECURITY_ENABLED ) {
            PrivilegedGetPageContext dp = new PrivilegedGetPageContext(
                    this, servlet, request, response, errorPageURL,
                    needsSession, bufferSize, autoflush);
            return AccessController.doPrivileged(dp);
        } else {
            return internalGetPageContext(servlet, request, response,
                    errorPageURL, needsSession,
                    bufferSize, autoflush);
        }
    }

    @Override
    public void releasePageContext(PageContext pc) {
        if( pc == null )
            return;
        if( Constants.IS_SECURITY_ENABLED ) {
            PrivilegedReleasePageContext dp = new PrivilegedReleasePageContext(
                    this,pc);
            AccessController.doPrivileged(dp);
        } else {
            internalReleasePageContext(pc);
        }
    }

    @Override
    public JspEngineInfo getEngineInfo() {
        return new JspEngineInfo() {
            @Override
            public String getSpecificationVersion() {
                return Constants.SPEC_VERSION;
            }
        };
    }

    public void setPoolSize(int poolSize) {
        this.poolSize = poolSize;
    }

    private PageContext internalGetPageContext(Servlet servlet, ServletRequest request,
            ServletResponse response, String errorPageURL, boolean needsSession,
            int bufferSize, boolean autoflush) {

        PageContext pc;
        if (poolSize > 0) {
            PageContextPool pool = localPool.get();
            if (pool == null) {
                pool = new PageContextPool(poolSize);
                localPool.set(pool);
            }
            pc = pool.get();
            if (pc == null) {
                pc = new PageContextImpl();
            }
        } else {
            pc = new PageContextImpl();
        }

        try {
            pc.initialize(servlet, request, response, errorPageURL,
                    needsSession, bufferSize, autoflush);
        } catch (IOException ioe) {
            // Implementation never throws IOE but can't change the signature
            // since it is part of the JSP API
        }

        return pc;
    }

    private void internalReleasePageContext(PageContext pc) {
        pc.release();
        if (poolSize > 0 && (pc instanceof PageContextImpl)) {
            localPool.get().put(pc);
        }
    }

    private static class PrivilegedGetPageContext
            implements PrivilegedAction<PageContext> {

        private JspFactoryImpl factory;
        private Servlet servlet;
        private ServletRequest request;
        private ServletResponse response;
        private String errorPageURL;
        private boolean needsSession;
        private int bufferSize;
        private boolean autoflush;

        PrivilegedGetPageContext(JspFactoryImpl factory, Servlet servlet,
                ServletRequest request, ServletResponse response, String errorPageURL,
                boolean needsSession, int bufferSize, boolean autoflush) {
            this.factory = factory;
            this.servlet = servlet;
            this.request = request;
            this.response = response;
            this.errorPageURL = errorPageURL;
            this.needsSession = needsSession;
            this.bufferSize = bufferSize;
            this.autoflush = autoflush;
        }

        @Override
        public PageContext run() {
            return factory.internalGetPageContext(servlet, request, response,
                    errorPageURL, needsSession, bufferSize, autoflush);
        }
    }

    private static class PrivilegedReleasePageContext
            implements PrivilegedAction<Void> {

        private JspFactoryImpl factory;
        private PageContext pageContext;

        PrivilegedReleasePageContext(JspFactoryImpl factory,
                PageContext pageContext) {
            this.factory = factory;
            this.pageContext = pageContext;
        }

        @Override
        public Void run() {
            factory.internalReleasePageContext(pageContext);
            return null;
        }
    }

    private static final class PageContextPool  {

        private final PageContext[] pool;

        private int current = -1;

        public PageContextPool(int poolSize) {
            this.pool = new PageContext[poolSize];
        }

        public void put(PageContext o) {
            if (current < (pool.length - 1)) {
                current++;
                pool[current] = o;
            }
        }

        public PageContext get() {
            PageContext item = null;
            if (current >= 0) {
                item = pool[current];
                current--;
            }
            return item;
        }

    }

    @Override
    public JspApplicationContext getJspApplicationContext(
            final ServletContext context) {
        if (Constants.IS_SECURITY_ENABLED) {
            return AccessController.doPrivileged(
                    (PrivilegedAction<JspApplicationContext>) () -> JspApplicationContextImpl.getInstance(context));
        } else {
            return JspApplicationContextImpl.getInstance(context);
        }
    }
}
