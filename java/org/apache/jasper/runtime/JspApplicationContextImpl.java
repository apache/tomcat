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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.el.ArrayELResolver;
import javax.el.BeanELResolver;
import javax.el.CompositeELResolver;
import javax.el.ELContextEvent;
import javax.el.ELContextListener;
import javax.el.ELResolver;
import javax.el.ExpressionFactory;
import javax.el.ListELResolver;
import javax.el.MapELResolver;
import javax.el.ResourceBundleELResolver;
import javax.servlet.ServletContext;
import javax.servlet.jsp.JspApplicationContext;
import javax.servlet.jsp.JspContext;
import javax.servlet.jsp.el.ImplicitObjectELResolver;
import javax.servlet.jsp.el.ScopedAttributeELResolver;

import org.apache.el.ExpressionFactoryImpl;
import org.apache.jasper.el.ELContextImpl;

/**
 * Implementation of JspApplicationContext
 * 
 * @author Jacob Hookom
 */
public class JspApplicationContextImpl implements JspApplicationContext {

	private final static String KEY = JspApplicationContextImpl.class.getName();

	private final static ExpressionFactory expressionFactory = new ExpressionFactoryImpl();

	private final List<ELContextListener> contextListeners = new ArrayList<ELContextListener>();

	private final List<ELResolver> resolvers = new ArrayList<ELResolver>();

	private boolean instantiated = false;

	private ELResolver resolver;

	public JspApplicationContextImpl() {

	}

	public void addELContextListener(ELContextListener listener) {
		if (listener == null) {
			throw new IllegalArgumentException("ELConextListener was null");
		}
		this.contextListeners.add(listener);
	}

	public static JspApplicationContextImpl getInstance(ServletContext context) {
		if (context == null) {
			throw new IllegalArgumentException("ServletContext was null");
		}
		JspApplicationContextImpl impl = (JspApplicationContextImpl) context
				.getAttribute(KEY);
		if (impl == null) {
			impl = new JspApplicationContextImpl();
			context.setAttribute(KEY, impl);
		}
		return impl;
	}

	public ELContextImpl createELContext(JspContext context) {
		if (context == null) {
			throw new IllegalArgumentException("JspContext was null");
		}

		// create ELContext for JspContext
		ELResolver r = this.createELResolver();
		ELContextImpl ctx = new ELContextImpl(r);
		ctx.putContext(JspContext.class, context);

		// alert all ELContextListeners
		ELContextEvent event = new ELContextEvent(ctx);
		for (int i = 0; i < this.contextListeners.size(); i++) {
			this.contextListeners.get(i).contextCreated(event);
		}

		return ctx;
	}

	private ELResolver createELResolver() {
		this.instantiated = true;
		if (this.resolver == null) {
			CompositeELResolver r = new CompositeELResolver();
			r.add(new ImplicitObjectELResolver());
			for (Iterator itr = this.resolvers.iterator(); itr.hasNext();) {
				r.add((ELResolver) itr.next());
			}
			r.add(new MapELResolver());
			r.add(new ResourceBundleELResolver());
			r.add(new ListELResolver());
			r.add(new ArrayELResolver());	
			r.add(new BeanELResolver());
			r.add(new ScopedAttributeELResolver());
			this.resolver = r;
		}
		return this.resolver;
	}

	public void addELResolver(ELResolver resolver) throws IllegalStateException {
		if (resolver == null) {
			throw new IllegalArgumentException("ELResolver was null");
		}
		if (this.instantiated) {
			throw new IllegalStateException(
					"cannot call addELResolver after the first request has been made");
		}
		this.resolvers.add(resolver);
	}

	public ExpressionFactory getExpressionFactory() {
		return expressionFactory;
	}

}
