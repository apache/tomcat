package javax.servlet.jsp.el;

import java.beans.FeatureDescriptor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;

import javax.el.ELContext;
import javax.el.ELException;
import javax.el.ELResolver;
import javax.el.PropertyNotFoundException;
import javax.el.PropertyNotWritableException;
import javax.servlet.jsp.JspContext;
import javax.servlet.jsp.PageContext;

public class ScopedAttributeELResolver extends ELResolver {

	public ScopedAttributeELResolver() {
		super();
	}

	public Object getValue(ELContext context, Object base, Object property)
			throws NullPointerException, PropertyNotFoundException, ELException {
		if (context == null) {
			throw new NullPointerException();
		}

		if (base == null) {
			context.setPropertyResolved(true);
			if (property != null) {
				String key = property.toString();
				PageContext page = (PageContext) context
						.getContext(JspContext.class);
				return page.findAttribute(key);
			}
		}

		return null;
	}

	public Class<?> getType(ELContext context, Object base, Object property)
			throws NullPointerException, PropertyNotFoundException, ELException {
		if (context == null) {
			throw new NullPointerException();
		}

		if (base == null) {
			context.setPropertyResolved(true);
			return Object.class;
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

		if (base == null) {
			context.setPropertyResolved(true);
			if (property != null) {
				String key = property.toString();
				PageContext page = (PageContext) context
						.getContext(JspContext.class);
				int scope = page.getAttributesScope(key);
				if (scope != 0) {
					page.setAttribute(key, value, scope);
				} else {
					page.setAttribute(key, value);
				}
			}
		}
	}

	public boolean isReadOnly(ELContext context, Object base, Object property)
			throws NullPointerException, PropertyNotFoundException, ELException {
		if (context == null) {
			throw new NullPointerException();
		}

		if (base == null) {
			context.setPropertyResolved(true);
		}

		return false;
	}

	public Iterator getFeatureDescriptors(ELContext context, Object base) {

		PageContext ctxt = (PageContext) context.getContext(JspContext.class);
		List list = new ArrayList();
		Enumeration e;
		Object value;
		String name;

		e = ctxt.getAttributeNamesInScope(PageContext.PAGE_SCOPE);
		while (e.hasMoreElements()) {
			name = (String) e.nextElement();
			value = ctxt.getAttribute(name, PageContext.PAGE_SCOPE);
			FeatureDescriptor descriptor = new FeatureDescriptor();
			descriptor.setName(name);
			descriptor.setDisplayName(name);
			descriptor.setExpert(false);
			descriptor.setHidden(false);
			descriptor.setPreferred(true);
			descriptor.setShortDescription("page scoped attribute");
			descriptor.setValue("type", value.getClass());
			descriptor.setValue("resolvableAtDesignTime", Boolean.FALSE);
			list.add(descriptor);
		}

		e = ctxt.getAttributeNamesInScope(PageContext.REQUEST_SCOPE);
		while (e.hasMoreElements()) {
			name = (String) e.nextElement();
			value = ctxt.getAttribute(name, PageContext.REQUEST_SCOPE);
			FeatureDescriptor descriptor = new FeatureDescriptor();
			descriptor.setName(name);
			descriptor.setDisplayName(name);
			descriptor.setExpert(false);
			descriptor.setHidden(false);
			descriptor.setPreferred(true);
			descriptor.setShortDescription("request scope attribute");
			descriptor.setValue("type", value.getClass());
			descriptor.setValue("resolvableAtDesignTime", Boolean.FALSE);
			list.add(descriptor);
		}

		if (ctxt.getSession() != null) {
			e = ctxt.getAttributeNamesInScope(PageContext.SESSION_SCOPE);
			while (e.hasMoreElements()) {
				name = (String) e.nextElement();
				value = ctxt.getAttribute(name, PageContext.SESSION_SCOPE);
				FeatureDescriptor descriptor = new FeatureDescriptor();
				descriptor.setName(name);
				descriptor.setDisplayName(name);
				descriptor.setExpert(false);
				descriptor.setHidden(false);
				descriptor.setPreferred(true);
				descriptor.setShortDescription("session scoped attribute");
				descriptor.setValue("type", value.getClass());
				descriptor.setValue("resolvableAtDesignTime", Boolean.FALSE);
				list.add(descriptor);
			}
		}

		e = ctxt.getAttributeNamesInScope(PageContext.APPLICATION_SCOPE);
		while (e.hasMoreElements()) {
			name = (String) e.nextElement();
			value = ctxt.getAttribute(name, PageContext.APPLICATION_SCOPE);
			FeatureDescriptor descriptor = new FeatureDescriptor();
			descriptor.setName(name);
			descriptor.setDisplayName(name);
			descriptor.setExpert(false);
			descriptor.setHidden(false);
			descriptor.setPreferred(true);
			descriptor.setShortDescription("application scoped attribute");
			descriptor.setValue("type", value.getClass());
			descriptor.setValue("resolvableAtDesignTime", Boolean.FALSE);
			list.add(descriptor);
		}
		return list.iterator();
	}

	private static void appendEnumeration(Collection c, Enumeration e) {
		while (e.hasMoreElements()) {
			c.add(e.nextElement());
		}
	}

	public Class<?> getCommonPropertyType(ELContext context, Object base) {
		if (base == null) {
			return String.class;
		}
		return null;
	}
}
