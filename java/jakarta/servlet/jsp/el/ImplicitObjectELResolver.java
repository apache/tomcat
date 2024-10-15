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
package jakarta.servlet.jsp.el;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import jakarta.el.ELContext;
import jakarta.el.ELResolver;
import jakarta.el.PropertyNotWritableException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.jsp.JspContext;
import jakarta.servlet.jsp.PageContext;

/**
 * Provides resolution in EL for the implicit variables of a JSP page.
 *
 * @since JSP 2.1
 */
public class ImplicitObjectELResolver extends ELResolver {

    /**
     * Creates an instance of the implicit object resolver for EL.
     */
    public ImplicitObjectELResolver() {
        super();
    }

    @Override
    public Object getValue(ELContext context, Object base, Object property) {
        Objects.requireNonNull(context);

        if (base == null && property != null) {
            Scope scope = Scope.lookupMap.get(property.toString());
            if (scope != null) {
                return scope.getScopeValue(context, base, property);
            }
        }
        return null;
    }

    @Override
    public Class<?> getType(ELContext context, Object base, Object property) {
        Objects.requireNonNull(context);

        if (base == null && property != null) {
            if (Scope.lookupMap.containsKey(property.toString())) {
                context.setPropertyResolved(base, property);
            }
        }
        return null;
    }

    @Override
    public void setValue(ELContext context, Object base, Object property, Object value) {
        Objects.requireNonNull(context);

        if (base == null && property != null) {
            if (Scope.lookupMap.containsKey(property.toString())) {
                context.setPropertyResolved(base, property);
                throw new PropertyNotWritableException();
            }
        }
    }

    @Override
    public boolean isReadOnly(ELContext context, Object base, Object property) {
        Objects.requireNonNull(context);

        if (base == null && property != null) {
            if (Scope.lookupMap.containsKey(property.toString())) {
                context.setPropertyResolved(base, property);
                return true;
            }
        }
        return false;
    }

    @Override
    public Class<String> getCommonPropertyType(ELContext context, Object base) {
        if (base == null) {
            return String.class;
        }
        return null;
    }


    private static class ScopeManager {
        private static final String MNGR_KEY = ScopeManager.class.getName();

        private final PageContext page;

        private Map<String,Object> applicationScope;

        private Map<String,Cookie> cookie;

        private Map<String,String> header;

        private Map<String,String[]> headerValues;

        private Map<String,String> initParam;

        private Map<String,Object> pageScope;

        private Map<String,String> param;

        private Map<String,String[]> paramValues;

        private Map<String,Object> requestScope;

        private Map<String,Object> sessionScope;

        ScopeManager(PageContext page) {
            this.page = page;
        }

        public static ScopeManager get(PageContext page) {
            ScopeManager mngr = (ScopeManager) page.getAttribute(MNGR_KEY);
            if (mngr == null) {
                mngr = new ScopeManager(page);
                page.setAttribute(MNGR_KEY, mngr);
            }
            return mngr;
        }

        public Map<String,Object> getApplicationScope() {
            if (this.applicationScope == null) {
                this.applicationScope = new ScopeMap<>() {
                    @Override
                    protected void setAttribute(String name, Object value) {
                        page.getServletContext().setAttribute(name, value);
                    }

                    @Override
                    protected void removeAttribute(String name) {
                        page.getServletContext().removeAttribute(name);
                    }

                    @Override
                    protected Enumeration<String> getAttributeNames() {
                        return page.getServletContext().getAttributeNames();
                    }

                    @Override
                    protected Object getAttribute(String name) {
                        return page.getServletContext().getAttribute(name);
                    }
                };
            }
            return this.applicationScope;
        }

        public Map<String,Cookie> getCookie() {
            if (this.cookie == null) {
                this.cookie = new ScopeMap<>() {
                    @Override
                    protected Enumeration<String> getAttributeNames() {
                        Cookie[] cookies = ((HttpServletRequest) page.getRequest()).getCookies();
                        if (cookies != null) {
                            List<String> list = new ArrayList<>(cookies.length);
                            for (Cookie cookie : cookies) {
                                list.add(cookie.getName());
                            }
                            return Collections.enumeration(list);
                        }
                        return null;
                    }

                    @Override
                    protected Cookie getAttribute(String name) {
                        Cookie[] cookies = ((HttpServletRequest) page.getRequest()).getCookies();
                        if (cookies != null) {
                            for (Cookie cookie : cookies) {
                                if (name.equals(cookie.getName())) {
                                    return cookie;
                                }
                            }
                        }
                        return null;
                    }

                };
            }
            return this.cookie;
        }

        public Map<String,String> getHeader() {
            if (this.header == null) {
                this.header = new ScopeMap<>() {
                    @Override
                    protected Enumeration<String> getAttributeNames() {
                        return ((HttpServletRequest) page.getRequest()).getHeaderNames();
                    }

                    @Override
                    protected String getAttribute(String name) {
                        return ((HttpServletRequest) page.getRequest()).getHeader(name);
                    }
                };
            }
            return this.header;
        }

        public Map<String,String[]> getHeaderValues() {
            if (this.headerValues == null) {
                this.headerValues = new ScopeMap<>() {
                    @Override
                    protected Enumeration<String> getAttributeNames() {
                        return ((HttpServletRequest) page.getRequest()).getHeaderNames();
                    }

                    @Override
                    protected String[] getAttribute(String name) {
                        Enumeration<String> e = ((HttpServletRequest) page.getRequest()).getHeaders(name);
                        if (e != null) {
                            List<String> list = new ArrayList<>();
                            while (e.hasMoreElements()) {
                                list.add(e.nextElement());
                            }
                            return list.toArray(new String[0]);
                        }
                        return null;
                    }

                };
            }
            return this.headerValues;
        }

        public Map<String,String> getInitParam() {
            if (this.initParam == null) {
                this.initParam = new ScopeMap<>() {
                    @Override
                    protected Enumeration<String> getAttributeNames() {
                        return page.getServletContext().getInitParameterNames();
                    }

                    @Override
                    protected String getAttribute(String name) {
                        return page.getServletContext().getInitParameter(name);
                    }
                };
            }
            return this.initParam;
        }

        public PageContext getPageContext() {
            return this.page;
        }

        public Map<String,Object> getPageScope() {
            if (this.pageScope == null) {
                this.pageScope = new ScopeMap<>() {
                    @Override
                    protected void setAttribute(String name, Object value) {
                        page.setAttribute(name, value);
                    }

                    @Override
                    protected void removeAttribute(String name) {
                        page.removeAttribute(name);
                    }

                    @Override
                    protected Enumeration<String> getAttributeNames() {
                        return page.getAttributeNamesInScope(PageContext.PAGE_SCOPE);
                    }

                    @Override
                    protected Object getAttribute(String name) {
                        return page.getAttribute(name);
                    }
                };
            }
            return this.pageScope;
        }

        public Map<String,String> getParam() {
            if (this.param == null) {
                this.param = new ScopeMap<>() {
                    @Override
                    protected Enumeration<String> getAttributeNames() {
                        return page.getRequest().getParameterNames();
                    }

                    @Override
                    protected String getAttribute(String name) {
                        return page.getRequest().getParameter(name);
                    }
                };
            }
            return this.param;
        }

        public Map<String,String[]> getParamValues() {
            if (this.paramValues == null) {
                this.paramValues = new ScopeMap<>() {
                    @Override
                    protected String[] getAttribute(String name) {
                        return page.getRequest().getParameterValues(name);
                    }

                    @Override
                    protected Enumeration<String> getAttributeNames() {
                        return page.getRequest().getParameterNames();
                    }
                };
            }
            return this.paramValues;
        }

        public Map<String,Object> getRequestScope() {
            if (this.requestScope == null) {
                this.requestScope = new ScopeMap<>() {
                    @Override
                    protected void setAttribute(String name, Object value) {
                        page.getRequest().setAttribute(name, value);
                    }

                    @Override
                    protected void removeAttribute(String name) {
                        page.getRequest().removeAttribute(name);
                    }

                    @Override
                    protected Enumeration<String> getAttributeNames() {
                        return page.getRequest().getAttributeNames();
                    }

                    @Override
                    protected Object getAttribute(String name) {
                        return page.getRequest().getAttribute(name);
                    }
                };
            }
            return this.requestScope;
        }

        public Map<String,Object> getSessionScope() {
            if (this.sessionScope == null) {
                this.sessionScope = new ScopeMap<>() {
                    @Override
                    protected void setAttribute(String name, Object value) {
                        ((HttpServletRequest) page.getRequest()).getSession().setAttribute(name, value);
                    }

                    @Override
                    protected void removeAttribute(String name) {
                        HttpSession session = page.getSession();
                        if (session != null) {
                            session.removeAttribute(name);
                        }
                    }

                    @Override
                    protected Enumeration<String> getAttributeNames() {
                        HttpSession session = page.getSession();
                        if (session != null) {
                            return session.getAttributeNames();
                        }
                        return null;
                    }

                    @Override
                    protected Object getAttribute(String name) {
                        HttpSession session = page.getSession();
                        if (session != null) {
                            return session.getAttribute(name);
                        }
                        return null;
                    }
                };
            }
            return this.sessionScope;
        }
    }


    private abstract static class ScopeMap<V> extends AbstractMap<String,V> {

        protected abstract Enumeration<String> getAttributeNames();

        protected abstract V getAttribute(String name);

        @SuppressWarnings("unused")
        protected void removeAttribute(String name) {
            throw new UnsupportedOperationException();
        }

        @SuppressWarnings("unused")
        protected void setAttribute(String name, Object value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public final Set<Map.Entry<String,V>> entrySet() {
            Enumeration<String> e = getAttributeNames();
            Set<Map.Entry<String,V>> set = new HashSet<>();
            if (e != null) {
                while (e.hasMoreElements()) {
                    set.add(new ScopeEntry(e.nextElement()));
                }
            }
            return set;
        }

        @Override
        public final int size() {
            int size = 0;
            Enumeration<String> e = getAttributeNames();
            if (e != null) {
                while (e.hasMoreElements()) {
                    e.nextElement();
                    size++;
                }
            }
            return size;
        }

        @Override
        public final boolean containsKey(Object key) {
            if (key == null) {
                return false;
            }
            Enumeration<String> e = getAttributeNames();
            if (e != null) {
                while (e.hasMoreElements()) {
                    if (key.equals(e.nextElement())) {
                        return true;
                    }
                }
            }
            return false;
        }

        private class ScopeEntry implements Map.Entry<String,V> {

            private final String key;

            ScopeEntry(String key) {
                this.key = key;
            }

            @Override
            public String getKey() {
                return this.key;
            }

            @Override
            public V getValue() {
                return getAttribute(this.key);
            }

            @Override
            public V setValue(Object value) {
                if (value == null) {
                    removeAttribute(this.key);
                } else {
                    setAttribute(this.key, value);
                }
                return null;
            }

            @Override
            public boolean equals(Object obj) {
                return obj != null && this.hashCode() == obj.hashCode();
            }

            @Override
            public int hashCode() {
                return this.key.hashCode();
            }

        }

        @Override
        public final V get(Object key) {
            if (key != null) {
                return getAttribute((String) key);
            }
            return null;
        }

        @Override
        public final V put(String key, V value) {
            Objects.requireNonNull(key);
            if (value == null) {
                this.removeAttribute(key);
            } else {
                this.setAttribute(key, value);
            }
            return null;
        }

        @Override
        public final V remove(Object key) {
            Objects.requireNonNull(key);
            this.removeAttribute((String) key);
            return null;
        }
    }


    private enum Scope {
        APPLICATION_SCOPE("applicationScope") {
            @Override
            Object getScopeValue(ELContext context, Object base, Object property) {
                return getScopeManager(context, base, property).getApplicationScope();
            }
        },
        COOKIE("cookie") {
            @Override
            Object getScopeValue(ELContext context, Object base, Object property) {
                return getScopeManager(context, base, property).getCookie();
            }
        },
        HEADER("header") {
            @Override
            Object getScopeValue(ELContext context, Object base, Object property) {
                return getScopeManager(context, base, property).getHeader();
            }
        },
        HEADER_VALUES("headerValues") {
            @Override
            Object getScopeValue(ELContext context, Object base, Object property) {
                return getScopeManager(context, base, property).getHeaderValues();
            }
        },
        INIT_PARAM("initParam") {
            @Override
            Object getScopeValue(ELContext context, Object base, Object property) {
                return getScopeManager(context, base, property).getInitParam();
            }
        },
        PAGE_CONTEXT("pageContext") {
            @Override
            Object getScopeValue(ELContext context, Object base, Object property) {
                return getScopeManager(context, base, property).getPageContext();
            }
        },
        PAGE_SCOPE("pageScope") {
            @Override
            Object getScopeValue(ELContext context, Object base, Object property) {
                return getScopeManager(context, base, property).getPageScope();
            }
        },
        PARAM("param") {
            @Override
            Object getScopeValue(ELContext context, Object base, Object property) {
                return getScopeManager(context, base, property).getParam();
            }
        },
        PARAM_VALUES("paramValues") {
            @Override
            Object getScopeValue(ELContext context, Object base, Object property) {
                return getScopeManager(context, base, property).getParamValues();
            }
        },
        REQUEST_SCOPE("requestScope") {
            @Override
            Object getScopeValue(ELContext context, Object base, Object property) {
                return getScopeManager(context, base, property).getRequestScope();
            }
        },
        SESSION_SCOPE("sessionScope") {
            @Override
            Object getScopeValue(ELContext context, Object base, Object property) {
                return getScopeManager(context, base, property).getSessionScope();
            }
        };

        private static final Map<String,Scope> lookupMap = new HashMap<>();

        static {
            for (Scope scope : values()) {
                lookupMap.put(scope.implicitName, scope);
            }
        }

        private static ScopeManager getScopeManager(ELContext context, Object base, Object property) {
            PageContext page = (PageContext) context.getContext(JspContext.class);
            context.setPropertyResolved(base, property);
            return ScopeManager.get(page);
        }

        private final String implicitName;

        Scope(String implicitName) {
            this.implicitName = implicitName;
        }

        abstract Object getScopeValue(ELContext context, Object base, Object property);
    }
}
