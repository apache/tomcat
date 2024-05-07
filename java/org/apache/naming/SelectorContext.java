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
package org.apache.naming;

import java.util.Hashtable;

import javax.naming.Binding;
import javax.naming.Context;
import javax.naming.Name;
import javax.naming.NameClassPair;
import javax.naming.NameParser;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

/**
 * Catalina JNDI Context implementation.
 *
 * @author Remy Maucherat
 */
public class SelectorContext implements Context {


    // -------------------------------------------------------------- Constants


    /**
     * Namespace URL.
     */
    public static final String prefix = "java:";


    /**
     * Namespace URL length.
     */
    public static final int prefixLength = prefix.length();


    /**
     * Initial context prefix.
     */
    public static final String IC_PREFIX = "IC_";


    private static final Log log = LogFactory.getLog(SelectorContext.class);

    // ----------------------------------------------------------- Constructors


    /**
     * Builds a Catalina selector context using the given environment.
     * @param env The environment
     */
    public SelectorContext(Hashtable<String,Object> env) {
        this.env = env;
        this.initialContext = false;
    }


    /**
     * Builds a Catalina selector context using the given environment.
     * @param env The environment
     * @param initialContext <code>true</code> if this is the main
     *  initial context
     */
    public SelectorContext(Hashtable<String,Object> env,
            boolean initialContext) {
        this.env = env;
        this.initialContext = initialContext;
    }


    // ----------------------------------------------------- Instance Variables


    /**
     * Environment.
     */
    protected final Hashtable<String,Object> env;


    /**
     * The string manager for this package.
     */
    protected static final StringManager sm = StringManager.getManager(SelectorContext.class);


    /**
     * Request for an initial context.
     */
    protected final boolean initialContext;


    // --------------------------------------------------------- Public Methods


    // -------------------------------------------------------- Context Methods


    @Override
    public Object lookup(Name name)
        throws NamingException {

        if (log.isTraceEnabled()) {
            log.trace(sm.getString("selectorContext.methodUsingName", "lookup",
                    name));
        }

        // Strip the URL header
        // Find the appropriate NamingContext according to the current bindings
        // Execute the lookup on that context
        return getBoundContext().lookup(parseName(name));
    }


    @Override
    public Object lookup(String name)
        throws NamingException {

        if (log.isTraceEnabled()) {
            log.trace(sm.getString("selectorContext.methodUsingString", "lookup",
                    name));
        }

        // Strip the URL header
        // Find the appropriate NamingContext according to the current bindings
        // Execute the lookup on that context
        return getBoundContext().lookup(parseName(name));
    }


    @Override
    public void bind(Name name, Object obj)
        throws NamingException {
        getBoundContext().bind(parseName(name), obj);
    }


    @Override
    public void bind(String name, Object obj)
        throws NamingException {
        getBoundContext().bind(parseName(name), obj);
    }


    @Override
    public void rebind(Name name, Object obj)
        throws NamingException {
        getBoundContext().rebind(parseName(name), obj);
    }


    @Override
    public void rebind(String name, Object obj)
        throws NamingException {
        getBoundContext().rebind(parseName(name), obj);
    }


    @Override
    public void unbind(Name name)
        throws NamingException {
        getBoundContext().unbind(parseName(name));
    }


    @Override
    public void unbind(String name)
        throws NamingException {
        getBoundContext().unbind(parseName(name));
    }


    @Override
    public void rename(Name oldName, Name newName)
        throws NamingException {
        getBoundContext().rename(parseName(oldName), parseName(newName));
    }


    @Override
    public void rename(String oldName, String newName)
        throws NamingException {
        getBoundContext().rename(parseName(oldName), parseName(newName));
    }


    @Override
    public NamingEnumeration<NameClassPair> list(Name name)
        throws NamingException {

        if (log.isTraceEnabled()) {
            log.trace(sm.getString("selectorContext.methodUsingName", "list",
                    name));
        }

        return getBoundContext().list(parseName(name));
    }


    @Override
    public NamingEnumeration<NameClassPair> list(String name)
        throws NamingException {

        if (log.isTraceEnabled()) {
            log.trace(sm.getString("selectorContext.methodUsingString", "list",
                    name));
        }

        return getBoundContext().list(parseName(name));
    }


    @Override
    public NamingEnumeration<Binding> listBindings(Name name)
        throws NamingException {

        if (log.isTraceEnabled()) {
            log.trace(sm.getString("selectorContext.methodUsingName",
                    "listBindings", name));
        }

        return getBoundContext().listBindings(parseName(name));
    }


    @Override
    public NamingEnumeration<Binding> listBindings(String name)
        throws NamingException {

        if (log.isTraceEnabled()) {
            log.trace(sm.getString("selectorContext.methodUsingString",
                    "listBindings", name));
        }

        return getBoundContext().listBindings(parseName(name));
    }


    @Override
    public void destroySubcontext(Name name)
        throws NamingException {
        getBoundContext().destroySubcontext(parseName(name));
    }


    @Override
    public void destroySubcontext(String name)
        throws NamingException {
        getBoundContext().destroySubcontext(parseName(name));
    }


    @Override
    public Context createSubcontext(Name name)
        throws NamingException {
        return getBoundContext().createSubcontext(parseName(name));
    }


    @Override
    public Context createSubcontext(String name)
        throws NamingException {
        return getBoundContext().createSubcontext(parseName(name));
    }


    @Override
    public Object lookupLink(Name name)
        throws NamingException {

        if (log.isTraceEnabled()) {
            log.trace(sm.getString("selectorContext.methodUsingName",
                    "lookupLink", name));
        }

        return getBoundContext().lookupLink(parseName(name));
    }


    @Override
    public Object lookupLink(String name)
        throws NamingException {

        if (log.isTraceEnabled()) {
            log.trace(sm.getString("selectorContext.methodUsingString",
                    "lookupLink", name));
        }

        return getBoundContext().lookupLink(parseName(name));
    }


    @Override
    public NameParser getNameParser(Name name)
        throws NamingException {
        return getBoundContext().getNameParser(parseName(name));
    }


    @Override
    public NameParser getNameParser(String name)
        throws NamingException {
        return getBoundContext().getNameParser(parseName(name));
    }


    @Override
    public Name composeName(Name name, Name prefix)
        throws NamingException {
        Name prefixClone = (Name) prefix.clone();
        return prefixClone.addAll(name);
    }


    @Override
    public String composeName(String name, String prefix)
        throws NamingException {
        return prefix + "/" + name;
    }


    @Override
    public Object addToEnvironment(String propName, Object propVal)
        throws NamingException {
        return getBoundContext().addToEnvironment(propName, propVal);
    }


    @Override
    public Object removeFromEnvironment(String propName)
        throws NamingException {
        return getBoundContext().removeFromEnvironment(propName);
    }


    @Override
    public Hashtable<?,?> getEnvironment()
        throws NamingException {
        return getBoundContext().getEnvironment();
    }


    @Override
    public void close()
        throws NamingException {
        getBoundContext().close();
    }


    @Override
    public String getNameInNamespace()
        throws NamingException {
        return prefix;
    }


    // ------------------------------------------------------ Protected Methods


    /**
     * Get the bound context.
     * @return the Context bound with either the current thread or
     *  the current classloader
     * @throws NamingException Bindings exception
     */
    protected Context getBoundContext()
        throws NamingException {

        if (initialContext) {
            String ICName = IC_PREFIX;
            if (ContextBindings.isThreadBound()) {
                ICName += ContextBindings.getThreadName();
            } else if (ContextBindings.isClassLoaderBound()) {
                ICName += ContextBindings.getClassLoaderName();
            }
            Context initialContext = ContextBindings.getContext(ICName);
            if (initialContext == null) {
                // Allocating a new context and binding it to the appropriate
                // name
                initialContext = new NamingContext(env, ICName);
                ContextBindings.bindContext(ICName, initialContext);
            }
            return initialContext;
        } else {
            if (ContextBindings.isThreadBound()) {
                return ContextBindings.getThread();
            } else {
                return ContextBindings.getClassLoader();
            }
        }

    }


    /**
     * Strips the URL header.
     * @param name The name
     * @return the parsed name
     * @throws NamingException if there is no "java:" header or if no
     * naming context has been bound to this thread
     */
    protected String parseName(String name)
        throws NamingException {

        if ((!initialContext) && (name.startsWith(prefix))) {
            return name.substring(prefixLength);
        } else {
            if (initialContext) {
                return name;
            } else {
                throw new NamingException
                    (sm.getString("selectorContext.noJavaUrl"));
            }
        }

    }


    /**
     * Strips the URL header.
     * @param name The name
     * @return the parsed name
     * @throws NamingException if there is no "java:" header or if no
     * naming context has been bound to this thread
     */
    protected Name parseName(Name name)
        throws NamingException {

        if (!initialContext && !name.isEmpty() &&
                name.get(0).startsWith(prefix)) {
            if (name.get(0).equals(prefix)) {
                return name.getSuffix(1);
            } else {
                Name result = name.getSuffix(1);
                result.add(0, name.get(0).substring(prefixLength));
                return result;
            }
        } else {
            if (initialContext) {
                return name;
            } else {
                throw new NamingException(
                        sm.getString("selectorContext.noJavaUrl"));
            }
        }

    }


}

