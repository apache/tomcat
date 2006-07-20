package javax.servlet.jsp.el;

import java.beans.FeatureDescriptor;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

import javax.el.ELContext;
import javax.el.ELException;
import javax.el.ELResolver;
import javax.el.PropertyNotFoundException;
import javax.el.PropertyNotWritableException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.servlet.jsp.JspContext;
import javax.servlet.jsp.PageContext;

/**
 *
 * @since 2.1
 */
public class ImplicitObjectELResolver extends ELResolver {

    private final static String[] SCOPE_NAMES = new String[] {
            "applicationScope", "cookie", "header", "headerValues",
            "initParam", "pageContext", "pageScope", "param", "paramValues",
            "requestScope", "sessionScope" };

    private final static int APPLICATIONSCOPE = 0;

    private final static int COOKIE = 1;

    private final static int HEADER = 2;

    private final static int HEADERVALUES = 3;

    private final static int INITPARAM = 4;

    private final static int PAGECONTEXT = 5;

    private final static int PAGESCOPE = 6;

    private final static int PARAM = 7;

    private final static int PARAM_VALUES = 8;

    private final static int REQUEST_SCOPE = 9;

    private final static int SESSION_SCOPE = 10;

    public ImplicitObjectELResolver() {
        super();
    }

    public Object getValue(ELContext context, Object base, Object property)
            throws NullPointerException, PropertyNotFoundException, ELException {
        if (context == null) {
            throw new NullPointerException();
        }

        if (base == null && property != null) {
            int idx = Arrays.binarySearch(SCOPE_NAMES, property.toString());

            if (idx >= 0) {
                PageContext page = (PageContext) context
                        .getContext(JspContext.class);
                context.setPropertyResolved(true);
                switch (idx) {
                case APPLICATIONSCOPE:
                    return ScopeManager.get(page).getApplicationScope();
                case COOKIE:
                    return ScopeManager.get(page).getCookie();
                case HEADER:
                    return ScopeManager.get(page).getHeader();
                case HEADERVALUES:
                    return ScopeManager.get(page).getHeaderValues();
                case INITPARAM:
                    return ScopeManager.get(page).getInitParam();
                case PAGECONTEXT:
                    return ScopeManager.get(page).getPageContext();
                case PAGESCOPE:
                    return ScopeManager.get(page).getPageScope();
                case PARAM:
                    return ScopeManager.get(page).getParam();
                case PARAM_VALUES:
                    return ScopeManager.get(page).getParamValues();
                case REQUEST_SCOPE:
                    return ScopeManager.get(page).getRequestScope();
                case SESSION_SCOPE:
                    return ScopeManager.get(page).getSessionScope();
                }
            }
        }
        return null;
    }

    public Class<?> getType(ELContext context, Object base, Object property)
            throws NullPointerException, PropertyNotFoundException, ELException {
        if (context == null) {
            throw new NullPointerException();
        }

        if (base == null && property != null) {
            int idx = Arrays.binarySearch(SCOPE_NAMES, property.toString());
            if (idx >= 0) {
                context.setPropertyResolved(true);
            }
        }
        return null;
    }

    public void setValue(ELContext context, Object base, Object property,
            Object value) throws NullPointerException,
            PropertyNotFoundException, PropertyNotWritableException,
            ELException {
        if (context == null) {
            throw new NullPointerException();
        }

        if (base == null && property != null) {
            int idx = Arrays.binarySearch(SCOPE_NAMES, property.toString());
            if (idx >= 0) {
                context.setPropertyResolved(true);
                throw new PropertyNotWritableException();
            }
        }
    }

    public boolean isReadOnly(ELContext context, Object base, Object property)
            throws NullPointerException, PropertyNotFoundException, ELException {
        if (context == null) {
            throw new NullPointerException();
        }

        if (base == null && property != null) {
            int idx = Arrays.binarySearch(SCOPE_NAMES, property.toString());
            if (idx >= 0) {
                context.setPropertyResolved(true);
                return true;
            }
        }
        return false;
    }

    public Iterator getFeatureDescriptors(ELContext context, Object base) {
        List<FeatureDescriptor> feats = new ArrayList<FeatureDescriptor>(
                SCOPE_NAMES.length);
        FeatureDescriptor feat;
        for (int i = 0; i < SCOPE_NAMES.length; i++) {
            feat = new FeatureDescriptor();
            feat.setDisplayName(SCOPE_NAMES[i]);
            feat.setExpert(false);
            feat.setHidden(false);
            feat.setName(SCOPE_NAMES[i]);
            feat.setPreferred(true);
            feat.setValue(RESOLVABLE_AT_DESIGN_TIME, Boolean.TRUE);
            feat.setValue(TYPE, String.class);
            feats.add(feat);
        }
        return feats.iterator();
    }

    public Class<?> getCommonPropertyType(ELContext context, Object base) {
        if (base == null) {
            return String.class;
        }
        return null;
    }

    private static class ScopeManager {
        private final static String MNGR_KEY = ScopeManager.class.getName();

        private final PageContext page;

        private Map applicationScope;

        private Map cookie;

        private Map header;

        private Map headerValues;

        private Map initParam;

        private Map pageScope;

        private Map param;

        private Map paramValues;

        private Map requestScope;

        private Map sessionScope;

        public ScopeManager(PageContext page) {
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

        public Map getApplicationScope() {
            if (this.applicationScope == null) {
                this.applicationScope = new ScopeMap() {
                    protected void setAttribute(String name, Object value) {
                        page.getServletContext().setAttribute(name, value);
                    }

                    protected void removeAttribute(String name) {
                        page.getServletContext().removeAttribute(name);
                    }

                    protected Enumeration getAttributeNames() {
                        return page.getServletContext().getAttributeNames();
                    }

                    protected Object getAttribute(String name) {
                        return page.getServletContext().getAttribute(name);
                    }
                };
            }
            return this.applicationScope;
        }

        public Map getCookie() {
            if (this.cookie == null) {
                this.cookie = new ScopeMap() {
                    protected Enumeration getAttributeNames() {
                        Cookie[] c = ((HttpServletRequest) page.getRequest())
                                .getCookies();
                        if (c != null) {
                            Vector v = new Vector();
                            for (int i = 0; i < c.length; i++) {
                                v.add(c[i].getName());
                            }
                            return v.elements();
                        }
                        return null;
                    }

                    protected Object getAttribute(String name) {
                        Cookie[] c = ((HttpServletRequest) page.getRequest())
                                .getCookies();
                        if (c != null) {
                            for (int i = 0; i < c.length; i++) {
                                if (name.equals(c[i].getName())) {
                                    return c[i];
                                }
                            }
                        }
                        return null;
                    }

                };
            }
            return this.cookie;
        }

        public Map getHeader() {
            if (this.header == null) {
                this.header = new ScopeMap() {
                    protected Enumeration getAttributeNames() {
                        return ((HttpServletRequest) page.getRequest())
                                .getHeaderNames();
                    }

                    protected Object getAttribute(String name) {
                        return ((HttpServletRequest) page.getRequest())
                                .getHeader(name);
                    }
                };
            }
            return this.header;
        }

        public Map getHeaderValues() {
            if (this.headerValues == null) {
                this.headerValues = new ScopeMap() {
                    protected Enumeration getAttributeNames() {
                        return ((HttpServletRequest) page.getRequest())
                                .getHeaderNames();
                    }

                    protected Object getAttribute(String name) {
                        Enumeration e = ((HttpServletRequest) page.getRequest())
                                .getHeaders(name);
                        if (e != null) {
                            List list = new ArrayList();
                            while (e.hasMoreElements()) {
                                list.add(e.nextElement().toString());
                            }
                            return (String[]) list.toArray(new String[list
                                    .size()]);
                        }
                        return null;
                    }

                };
            }
            return this.headerValues;
        }

        public Map getInitParam() {
            if (this.initParam == null) {
                this.initParam = new ScopeMap() {
                    protected Enumeration getAttributeNames() {
                        return page.getServletContext().getInitParameterNames();
                    }

                    protected Object getAttribute(String name) {
                        return page.getServletContext().getInitParameter(name);
                    }
                };
            }
            return this.initParam;
        }

        public PageContext getPageContext() {
            return this.page;
        }

        public Map getPageScope() {
            if (this.pageScope == null) {
                this.pageScope = new ScopeMap() {
                    protected void setAttribute(String name, Object value) {
                        page.setAttribute(name, value);
                    }

                    protected void removeAttribute(String name) {
                        page.removeAttribute(name);
                    }

                    protected Enumeration getAttributeNames() {
                        return page
                                .getAttributeNamesInScope(PageContext.PAGE_SCOPE);
                    }

                    protected Object getAttribute(String name) {
                        return page.getAttribute(name);
                    }
                };
            }
            return this.pageScope;
        }

        public Map getParam() {
            if (this.param == null) {
                this.param = new ScopeMap() {
                    protected Enumeration getAttributeNames() {
                        return page.getRequest().getParameterNames();
                    }

                    protected Object getAttribute(String name) {
                        return page.getRequest().getParameter(name);
                    }
                };
            }
            return this.param;
        }

        public Map getParamValues() {
            if (this.paramValues == null) {
                this.paramValues = new ScopeMap() {
                    protected Object getAttribute(String name) {
                        return page.getRequest().getParameterValues(name);
                    }

                    protected Enumeration getAttributeNames() {
                        return page.getRequest().getParameterNames();
                    }
                };
            }
            return this.paramValues;
        }

        public Map getRequestScope() {
            if (this.requestScope == null) {
                this.requestScope = new ScopeMap() {
                    protected void setAttribute(String name, Object value) {
                        page.getRequest().setAttribute(name, value);
                    }

                    protected void removeAttribute(String name) {
                        page.getRequest().removeAttribute(name);
                    }

                    protected Enumeration getAttributeNames() {
                        return page.getRequest().getAttributeNames();
                    }

                    protected Object getAttribute(String name) {
                        return page.getAttribute(name);
                    }
                };
            }
            return this.requestScope;
        }

        public Map getSessionScope() {
            if (this.sessionScope == null) {
                this.sessionScope = new ScopeMap() {
                    protected void setAttribute(String name, Object value) {
                        ((HttpServletRequest) page.getRequest()).getSession()
                                .setAttribute(name, value);
                    }

                    protected void removeAttribute(String name) {
                        HttpSession session = page.getSession();
                        if (session != null) {
                            session.removeAttribute(name);
                        }
                    }

                    protected Enumeration getAttributeNames() {
                        HttpSession session = page.getSession();
                        if (session != null) {
                            return session.getAttributeNames();
                        }
                        return null;
                    }

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

    private abstract static class ScopeMap extends AbstractMap {

        protected abstract Enumeration getAttributeNames();

        protected abstract Object getAttribute(String name);

        protected void removeAttribute(String name) {
            throw new UnsupportedOperationException();
        }

        protected void setAttribute(String name, Object value) {
            throw new UnsupportedOperationException();
        }

        public final Set entrySet() {
            Enumeration e = getAttributeNames();
            Set set = new HashSet();
            if (e != null) {
                while (e.hasMoreElements()) {
                    set.add(new ScopeEntry((String) e.nextElement()));
                }
            }
            return set;
        }

        private class ScopeEntry implements Map.Entry {

            private final String key;

            public ScopeEntry(String key) {
                this.key = key;
            }

            public Object getKey() {
                return (Object) this.key;
            }

            public Object getValue() {
                return getAttribute(this.key);
            }

            public Object setValue(Object value) {
                if (value == null) {
                    removeAttribute(this.key);
                } else {
                    setAttribute(this.key, value);
                }
                return null;
            }

            public boolean equals(Object obj) {
                return (obj != null && this.hashCode() == obj.hashCode());
            }

            public int hashCode() {
                return this.key.hashCode();
            }

        }

        public final Object get(Object key) {
            if (key != null) {
                return getAttribute(key.toString());
            }
            return null;
        }

        public final Object put(Object key, Object value) {
            if (key == null) {
                throw new NullPointerException();
            }
            if (value == null) {
                this.removeAttribute(key.toString());
            } else {
                this.setAttribute(key.toString(), value);
            }
            return null;
        }

        public final Object remove(Object key) {
            if (key == null) {
                throw new NullPointerException();
            }
            this.removeAttribute(key.toString());
            return null;
        }

    }

}
