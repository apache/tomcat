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

import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;

import javax.naming.Binding;
import javax.naming.CompositeName;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.LinkRef;
import javax.naming.Name;
import javax.naming.NameAlreadyBoundException;
import javax.naming.NameClassPair;
import javax.naming.NameNotFoundException;
import javax.naming.NameParser;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.NotContextException;
import javax.naming.OperationNotSupportedException;
import javax.naming.Reference;
import javax.naming.Referenceable;
import javax.naming.spi.NamingManager;
import javax.naming.spi.ObjectFactory;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

/**
 * Catalina JNDI Context implementation.
 *
 * @author Remy Maucherat
 */
public class NamingContext implements Context {


    // -------------------------------------------------------------- Constants


    /**
     * Name parser for this context.
     */
    protected static final NameParser nameParser = new NameParserImpl();


    private static final Log log = LogFactory.getLog(NamingContext.class);


    // ----------------------------------------------------------- Constructors


    /**
     * Builds a naming context.
     *
     * @param env The environment to use to construct the naming context
     * @param name The name of the associated Catalina Context
     */
    public NamingContext(Hashtable<String,Object> env, String name) {
        this(env, name, new HashMap<>());
    }


    /**
     * Builds a naming context.
     *
     * @param env The environment to use to construct the naming context
     * @param name The name of the associated Catalina Context
     * @param bindings The initial bindings for the naming context
     */
    public NamingContext(Hashtable<String,Object> env, String name,
            HashMap<String,NamingEntry> bindings) {

        this.env = new Hashtable<>();
        this.name = name;
        // Populating the environment hashtable
        if (env != null ) {
            Enumeration<String> envEntries = env.keys();
            while (envEntries.hasMoreElements()) {
                String entryName = envEntries.nextElement();
                addToEnvironment(entryName, env.get(entryName));
            }
        }
        this.bindings = bindings;
    }


    // ----------------------------------------------------- Instance Variables


    /**
     * Environment.
     */
    protected final Hashtable<String,Object> env;


    /**
     * The string manager for this package.
     */
    protected static final StringManager sm = StringManager.getManager(NamingContext.class);


    /**
     * Bindings in this Context.
     */
    protected final HashMap<String,NamingEntry> bindings;


    /**
     * Name of the associated Catalina Context.
     */
    protected final String name;


    /**
     * Determines if an attempt to write to a read-only context results in an
     * exception or if the request is ignored.
     */
    private boolean exceptionOnFailedWrite = true;
    public boolean getExceptionOnFailedWrite() {
        return exceptionOnFailedWrite;
    }
    public void setExceptionOnFailedWrite(boolean exceptionOnFailedWrite) {
        this.exceptionOnFailedWrite = exceptionOnFailedWrite;
    }


    // -------------------------------------------------------- Context Methods

    @Override
    public Object lookup(Name name)
        throws NamingException {
        return lookup(name, true);
    }


    @Override
    public Object lookup(String name)
        throws NamingException {
        return lookup(new CompositeName(name), true);
    }


    @Override
    public void bind(Name name, Object obj)
        throws NamingException {
        bind(name, obj, false);
    }


    @Override
    public void bind(String name, Object obj)
        throws NamingException {
        bind(new CompositeName(name), obj);
    }


    @Override
    public void rebind(Name name, Object obj)
        throws NamingException {
        bind(name, obj, true);
    }


    @Override
    public void rebind(String name, Object obj)
        throws NamingException {
        rebind(new CompositeName(name), obj);
    }


    @Override
    public void unbind(Name name) throws NamingException {

        if (!checkWritable()) {
            return;
        }

        while ((!name.isEmpty()) && (name.get(0).length() == 0)) {
            name = name.getSuffix(1);
        }
        if (name.isEmpty()) {
            throw new NamingException
                (sm.getString("namingContext.invalidName"));
        }

        NamingEntry entry = bindings.get(name.get(0));

        if (entry == null) {
            throw new NameNotFoundException
                (sm.getString("namingContext.nameNotBound", name, name.get(0)));
        }

        if (name.size() > 1) {
            if (entry.type == NamingEntry.CONTEXT) {
                ((Context) entry.value).unbind(name.getSuffix(1));
            } else {
                throw new NamingException
                    (sm.getString("namingContext.contextExpected"));
            }
        } else {
            bindings.remove(name.get(0));
        }

    }


    @Override
    public void unbind(String name)
        throws NamingException {
        unbind(new CompositeName(name));
    }


    @Override
    public void rename(Name oldName, Name newName)
        throws NamingException {
        Object value = lookup(oldName);
        bind(newName, value);
        unbind(oldName);
    }


    @Override
    public void rename(String oldName, String newName)
        throws NamingException {
        rename(new CompositeName(oldName), new CompositeName(newName));
    }


    @Override
    public NamingEnumeration<NameClassPair> list(Name name)
        throws NamingException {
        // Removing empty parts
        while ((!name.isEmpty()) && (name.get(0).length() == 0)) {
            name = name.getSuffix(1);
        }
        if (name.isEmpty()) {
            return new NamingContextEnumeration(bindings.values().iterator());
        }

        NamingEntry entry = bindings.get(name.get(0));

        if (entry == null) {
            throw new NameNotFoundException
                (sm.getString("namingContext.nameNotBound", name, name.get(0)));
        }

        if (entry.type != NamingEntry.CONTEXT) {
            throw new NamingException
                (sm.getString("namingContext.contextExpected"));
        }
        return ((Context) entry.value).list(name.getSuffix(1));
    }


    @Override
    public NamingEnumeration<NameClassPair> list(String name)
        throws NamingException {
        return list(new CompositeName(name));
    }


    @Override
    public NamingEnumeration<Binding> listBindings(Name name)
        throws NamingException {
        // Removing empty parts
        while ((!name.isEmpty()) && (name.get(0).length() == 0)) {
            name = name.getSuffix(1);
        }
        if (name.isEmpty()) {
            return new NamingContextBindingsEnumeration(bindings.values().iterator(), this);
        }

        NamingEntry entry = bindings.get(name.get(0));

        if (entry == null) {
            throw new NameNotFoundException
                (sm.getString("namingContext.nameNotBound", name, name.get(0)));
        }

        if (entry.type != NamingEntry.CONTEXT) {
            throw new NamingException
                (sm.getString("namingContext.contextExpected"));
        }
        return ((Context) entry.value).listBindings(name.getSuffix(1));
    }


    @Override
    public NamingEnumeration<Binding> listBindings(String name)
        throws NamingException {
        return listBindings(new CompositeName(name));
    }


    @Override
    public void destroySubcontext(Name name) throws NamingException {

        if (!checkWritable()) {
            return;
        }

        while ((!name.isEmpty()) && (name.get(0).length() == 0)) {
            name = name.getSuffix(1);
        }
        if (name.isEmpty()) {
            throw new NamingException
                (sm.getString("namingContext.invalidName"));
        }

        NamingEntry entry = bindings.get(name.get(0));

        if (entry == null) {
            throw new NameNotFoundException
                (sm.getString("namingContext.nameNotBound", name, name.get(0)));
        }

        if (name.size() > 1) {
            if (entry.type == NamingEntry.CONTEXT) {
                ((Context) entry.value).destroySubcontext(name.getSuffix(1));
            } else {
                throw new NamingException
                    (sm.getString("namingContext.contextExpected"));
            }
        } else {
            if (entry.type == NamingEntry.CONTEXT) {
                ((Context) entry.value).close();
                bindings.remove(name.get(0));
            } else {
                throw new NotContextException
                    (sm.getString("namingContext.contextExpected"));
            }
        }

    }


    @Override
    public void destroySubcontext(String name)
        throws NamingException {
        destroySubcontext(new CompositeName(name));
    }


    @Override
    public Context createSubcontext(Name name) throws NamingException {
        if (!checkWritable()) {
            return null;
        }

        NamingContext newContext = new NamingContext(env, this.name);
        bind(name, newContext);

        newContext.setExceptionOnFailedWrite(getExceptionOnFailedWrite());

        return newContext;
    }


    @Override
    public Context createSubcontext(String name)
        throws NamingException {
        return createSubcontext(new CompositeName(name));
    }


    @Override
    public Object lookupLink(Name name)
        throws NamingException {
        return lookup(name, false);
    }


    @Override
    public Object lookupLink(String name)
        throws NamingException {
        return lookup(new CompositeName(name), false);
    }


    @Override
    public NameParser getNameParser(Name name)
        throws NamingException {

        while ((!name.isEmpty()) && (name.get(0).length() == 0)) {
            name = name.getSuffix(1);
        }
        if (name.isEmpty()) {
            return nameParser;
        }

        if (name.size() > 1) {
            Object obj = bindings.get(name.get(0));
            if (obj instanceof Context) {
                return ((Context) obj).getNameParser(name.getSuffix(1));
            } else {
                throw new NotContextException
                    (sm.getString("namingContext.contextExpected"));
            }
        }

        return nameParser;

    }


    @Override
    public NameParser getNameParser(String name)
        throws NamingException {
        return getNameParser(new CompositeName(name));
    }


    @Override
    public Name composeName(Name name, Name prefix) throws NamingException {
        prefix = (Name) prefix.clone();
        return prefix.addAll(name);
    }


    @Override
    public String composeName(String name, String prefix) {
        return prefix + "/" + name;
    }


    @Override
    public Object addToEnvironment(String propName, Object propVal) {
        return env.put(propName, propVal);
    }


    @Override
    public Object removeFromEnvironment(String propName){
        return env.remove(propName);
    }


    @Override
    public Hashtable<?,?> getEnvironment() {
        return env;
    }


    @Override
    public void close() throws NamingException {
        if (!checkWritable()) {
            return;
        }
        env.clear();
    }


    @Override
    public String getNameInNamespace()
        throws NamingException {
        throw  new OperationNotSupportedException
            (sm.getString("namingContext.noAbsoluteName"));
    }


    // ------------------------------------------------------ Protected Methods


    private static final boolean GRAAL;

    static {
        boolean result = false;
        try {
            Class<?> nativeImageClazz = Class.forName("org.graalvm.nativeimage.ImageInfo");
            result = Boolean.TRUE.equals(nativeImageClazz.getMethod("inImageCode").invoke(null));
        } catch (ClassNotFoundException e) {
            // Must be Graal
        } catch (ReflectiveOperationException | IllegalArgumentException e) {
            // Should never happen
        }
        GRAAL = result || System.getProperty("org.graalvm.nativeimage.imagecode") != null;
    }

    /**
     * Retrieves the named object.
     *
     * @param name the name of the object to look up
     * @param resolveLinks If true, the links will be resolved
     * @return the object bound to name
     * @exception NamingException if a naming exception is encountered
     */
    protected Object lookup(Name name, boolean resolveLinks)
        throws NamingException {

        // Removing empty parts
        while ((!name.isEmpty()) && (name.get(0).length() == 0)) {
            name = name.getSuffix(1);
        }
        if (name.isEmpty()) {
            // If name is empty, a newly allocated naming context is returned
            return new NamingContext(env, this.name, bindings);
        }

        NamingEntry entry = bindings.get(name.get(0));

        if (entry == null) {
            throw new NameNotFoundException
                (sm.getString("namingContext.nameNotBound", name, name.get(0)));
        }

        if (name.size() > 1) {
            // If the size of the name is greater that 1, then we go through a
            // number of subcontexts.
            if (entry.type != NamingEntry.CONTEXT) {
                throw new NamingException
                    (sm.getString("namingContext.contextExpected"));
            }
            return ((Context) entry.value).lookup(name.getSuffix(1));
        } else {
            if ((resolveLinks) && (entry.type == NamingEntry.LINK_REF)) {
                String link = ((LinkRef) entry.value).getLinkName();
                if (link.startsWith(".")) {
                    // Link relative to this context
                    return lookup(link.substring(1));
                } else {
                    return new InitialContext(env).lookup(link);
                }
            } else if (entry.type == NamingEntry.REFERENCE) {
                try {
                    Object obj = null;
                    if (!GRAAL) {
                        obj = NamingManager.getObjectInstance(entry.value, name, this, env);
                    } else {
                        // NamingManager.getObjectInstance would simply return the reference here
                        // Use the configured object factory to resolve it directly if possible
                        // Note: This may need manual constructor reflection configuration
                        Reference reference = (Reference) entry.value;
                        String factoryClassName = reference.getFactoryClassName();
                        if (factoryClassName != null) {
                            Class<?> factoryClass = getClass().getClassLoader().loadClass(factoryClassName);
                            ObjectFactory factory = (ObjectFactory) factoryClass.getDeclaredConstructor().newInstance();
                            obj = factory.getObjectInstance(entry.value, name, this, env);
                        }
                    }
                    if (entry.value instanceof ResourceRef) {
                        boolean singleton = Boolean.parseBoolean(
                                    (String) ((ResourceRef) entry.value).get(
                                            ResourceRef.SINGLETON).getContent());
                        if (singleton) {
                            entry.type = NamingEntry.ENTRY;
                            entry.value = obj;
                        }
                    }
                    if (obj == null) {
                        throw new NamingException(sm.getString("namingContext.failResolvingReference"));
                    }
                    return obj;
                } catch (NamingException e) {
                    throw e;
                } catch (Exception e) {
                    String msg = sm.getString("namingContext.failResolvingReference");
                    log.warn(msg, e);
                    NamingException ne = new NamingException(msg);
                    ne.initCause(e);
                    throw ne;
                }
            } else {
                return entry.value;
            }
        }

    }


    /**
     * Binds a name to an object. All intermediate contexts and the target
     * context (that named by all but terminal atomic component of the name)
     * must already exist.
     *
     * @param name the name to bind; may not be empty
     * @param obj the object to bind; possibly null
     * @param rebind if true, then perform a rebind (ie, overwrite)
     * @exception NameAlreadyBoundException if name is already bound
     * @exception javax.naming.directory.InvalidAttributesException if object
     * did not supply all mandatory attributes
     * @exception NamingException if a naming exception is encountered
     */
    protected void bind(Name name, Object obj, boolean rebind)
        throws NamingException {

        if (!checkWritable()) {
            return;
        }

        while ((!name.isEmpty()) && (name.get(0).length() == 0)) {
            name = name.getSuffix(1);
        }
        if (name.isEmpty()) {
            throw new NamingException
                (sm.getString("namingContext.invalidName"));
        }

        NamingEntry entry = bindings.get(name.get(0));

        if (name.size() > 1) {
            if (entry == null) {
                throw new NameNotFoundException(sm.getString(
                        "namingContext.nameNotBound", name, name.get(0)));
            }
            if (entry.type == NamingEntry.CONTEXT) {
                if (rebind) {
                    ((Context) entry.value).rebind(name.getSuffix(1), obj);
                } else {
                    ((Context) entry.value).bind(name.getSuffix(1), obj);
                }
            } else {
                throw new NamingException
                    (sm.getString("namingContext.contextExpected"));
            }
        } else {
            if ((!rebind) && (entry != null)) {
                throw new NameAlreadyBoundException
                    (sm.getString("namingContext.alreadyBound", name.get(0)));
            } else {
                // Getting the type of the object and wrapping it within a new
                // NamingEntry
                Object toBind =
                    NamingManager.getStateToBind(obj, name, this, env);
                if (toBind instanceof Context) {
                    entry = new NamingEntry(name.get(0), toBind,
                                            NamingEntry.CONTEXT);
                } else if (toBind instanceof LinkRef) {
                    entry = new NamingEntry(name.get(0), toBind,
                                            NamingEntry.LINK_REF);
                } else if (toBind instanceof Reference) {
                    entry = new NamingEntry(name.get(0), toBind,
                                            NamingEntry.REFERENCE);
                } else if (toBind instanceof Referenceable) {
                    toBind = ((Referenceable) toBind).getReference();
                    entry = new NamingEntry(name.get(0), toBind,
                                            NamingEntry.REFERENCE);
                } else {
                    entry = new NamingEntry(name.get(0), toBind,
                                            NamingEntry.ENTRY);
                }
                bindings.put(name.get(0), entry);
            }
        }

    }


    /**
     * @return <code>true</code> if writing is allowed on this context.
     */
    protected boolean isWritable() {
        return ContextAccessController.isWritable(name);
    }


    /**
     * Throws a naming exception is Context is not writable.
     * @return <code>true</code> if the Context is writable
     * @throws NamingException if the Context is not writable and
     *  <code>exceptionOnFailedWrite</code> is <code>true</code>
     */
    protected boolean checkWritable() throws NamingException {
        if (isWritable()) {
            return true;
        } else {
            if (exceptionOnFailedWrite) {
                throw new OperationNotSupportedException(sm.getString("namingContext.readOnly"));
            }
        }
        return false;
    }
}

