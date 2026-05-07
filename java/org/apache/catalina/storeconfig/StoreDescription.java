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
package org.apache.catalina.storeconfig;

import java.util.ArrayList;
import java.util.List;

/**
 * Bean of a StoreDescription
 *
 * <pre>
 *
 *  &lt;Description
 *  tag=&quot;Context&quot;
 *  standard=&quot;true&quot;
 *  default=&quot;true&quot;
 *  externalAllowed=&quot;true&quot;
 *  storeSeparate=&quot;true&quot;
 *  backup=&quot;true&quot;
 *  children=&quot;true&quot;
 *  tagClass=&quot;org.apache.catalina.core.StandardContext&quot;
 *  storeFactoryClass=&quot;org.apache.catalina.storeconfig.StandardContextSF&quot;
 *  storeAppenderClass=&quot;org.apache.catalina.storeconfig.StoreContextAppender&quot;&gt;
 *     &lt;TransientAttribute&gt;available&lt;/TransientAttribute&gt;
 *     &lt;TransientAttribute&gt;configFile&lt;/TransientAttribute&gt;
 *     &lt;TransientAttribute&gt;configured&lt;/TransientAttribute&gt;
 *     &lt;TransientAttribute&gt;displayName&lt;/TransientAttribute&gt;
 *     &lt;TransientAttribute&gt;distributable&lt;/TransientAttribute&gt;
 *     &lt;TransientAttribute&gt;domain&lt;/TransientAttribute&gt;
 *     &lt;TransientAttribute&gt;engineName&lt;/TransientAttribute&gt;
 *     &lt;TransientAttribute&gt;name&lt;/TransientAttribute&gt;
 *     &lt;TransientAttribute&gt;publicId&lt;/TransientAttribute&gt;
 *     &lt;TransientAttribute&gt;replaceWelcomeFiles&lt;/TransientAttribute&gt;
 *     &lt;TransientAttribute&gt;saveConfig&lt;/TransientAttribute&gt;
 *     &lt;TransientAttribute&gt;sessionTimeout&lt;/TransientAttribute&gt;
 *     &lt;TransientAttribute&gt;startupTime&lt;/TransientAttribute&gt;
 *     &lt;TransientAttribute&gt;tldScanTime&lt;/TransientAttribute&gt;
 *  &lt;/Description&gt;
 * </pre>
 */
public class StoreDescription {

    /**
     * Constructs a new StoreDescription with default settings.
     */
    public StoreDescription() {
    }

    private String id;

    private String tag;

    private String tagClass;

    private boolean standard = false;

    private boolean backup = false;

    private boolean externalAllowed = false;

    private boolean externalOnly = false;

    private boolean myDefault = false;

    private boolean attributes = true;

    private String storeFactoryClass;

    private IStoreFactory storeFactory;

    private String storeWriterClass;

    private boolean children = false;

    private List<String> transientAttributes;

    private List<String> transientChildren;

    private boolean storeSeparate = false;

    /**
     * Indicates whether external (separate file) storage is allowed for this description.
     *
     * @return true if external storage is allowed
     */
    public boolean isExternalAllowed() {
        return externalAllowed;
    }

    /**
     * Sets whether external (separate file) storage is allowed for this description.
     *
     * @param external true if external storage is allowed
     */
    public void setExternalAllowed(boolean external) {
        this.externalAllowed = external;
    }

    /**
     * Indicates whether this description is restricted to external storage only.
     *
     * @return true if external storage is the only allowed option
     */
    public boolean isExternalOnly() {
        return externalOnly;
    }

    /**
     * Sets whether this description is restricted to external storage only.
     *
     * @param external true if external storage is the only allowed option
     */
    public void setExternalOnly(boolean external) {
        this.externalOnly = external;
    }

    /**
     * Indicates whether this description represents a standard component.
     *
     * @return true if this is a standard component
     */
    public boolean isStandard() {
        return standard;
    }

    /**
     * Sets whether this description represents a standard component.
     *
     * @param standard true if this is a standard component
     */
    public void setStandard(boolean standard) {
        this.standard = standard;
    }

    /**
     * Indicates whether a backup of the configuration should be created.
     *
     * @return true if backup is enabled
     */
    public boolean isBackup() {
        return backup;
    }

    /**
     * Sets whether a backup of the configuration should be created.
     *
     * @param backup true if backup is enabled
     */
    public void setBackup(boolean backup) {
        this.backup = backup;
    }

    /**
     * Indicates whether this description represents a default component.
     *
     * @return true if this is a default component
     */
    public boolean isDefault() {
        return myDefault;
    }

    /**
     * Sets whether this description represents a default component.
     *
     * @param aDefault true if this is a default component
     */
    public void setDefault(boolean aDefault) {
        this.myDefault = aDefault;
    }

    /**
     * Returns the fully qualified class name of the StoreFactory implementation.
     *
     * @return the StoreFactory class name
     */
    public String getStoreFactoryClass() {
        return storeFactoryClass;
    }

    /**
     * Sets the fully qualified class name of the StoreFactory implementation.
     *
     * @param storeFactoryClass the StoreFactory class name
     */
    public void setStoreFactoryClass(String storeFactoryClass) {
        this.storeFactoryClass = storeFactoryClass;
    }

    /**
     * Returns the StoreFactory instance used to create objects for this description.
     *
     * @return the StoreFactory instance
     */
    public IStoreFactory getStoreFactory() {
        return storeFactory;
    }

    /**
     * Sets the StoreFactory instance used to create objects for this description.
     *
     * @param storeFactory the StoreFactory instance
     */
    public void setStoreFactory(IStoreFactory storeFactory) {
        this.storeFactory = storeFactory;
    }

    /**
     * Returns the fully qualified class name of the StoreWriter implementation.
     *
     * @return the StoreWriter class name
     */
    public String getStoreWriterClass() {
        return storeWriterClass;
    }

    /**
     * Sets the fully qualified class name of the StoreWriter implementation.
     *
     * @param storeWriterClass the StoreWriter class name
     */
    public void setStoreWriterClass(String storeWriterClass) {
        this.storeWriterClass = storeWriterClass;
    }

    /**
     * Returns the XML tag name for this description.
     *
     * @return the XML tag name
     */
    public String getTag() {
        return tag;
    }

    /**
     * Sets the XML tag name for this description.
     *
     * @param tag the XML tag name
     */
    public void setTag(String tag) {
        this.tag = tag;
    }

    /**
     * Returns the fully qualified class name associated with this description's tag.
     *
     * @return the tag class name
     */
    public String getTagClass() {
        return tagClass;
    }

    /**
     * Sets the fully qualified class name associated with this description's tag.
     *
     * @param tagClass the tag class name
     */
    public void setTagClass(String tagClass) {
        this.tagClass = tagClass;
    }

    /**
     * Returns the list of attribute names that should not be persisted.
     *
     * @return the list of transient attribute names
     */
    public List<String> getTransientAttributes() {
        return transientAttributes;
    }

    /**
     * Sets the list of attribute names that should not be persisted.
     *
     * @param transientAttributes the list of transient attribute names
     */
    public void setTransientAttributes(List<String> transientAttributes) {
        this.transientAttributes = transientAttributes;
    }

    /**
     * Adds an attribute name to the list of transient attributes that should not be persisted.
     *
     * @param attribute the attribute name to add
     */
    public void addTransientAttribute(String attribute) {
        if (transientAttributes == null) {
            transientAttributes = new ArrayList<>();
        }
        transientAttributes.add(attribute);
    }

    /**
     * Removes an attribute name from the list of transient attributes.
     *
     * @param attribute the attribute name to remove
     */
    public void removeTransientAttribute(String attribute) {
        if (transientAttributes != null) {
            transientAttributes.remove(attribute);
        }
    }

    /**
     * Returns the list of child class names that should not be persisted.
     *
     * @return the list of transient child class names
     */
    public List<String> getTransientChildren() {
        return transientChildren;
    }

    /**
     * Sets the list of child class names that should not be persisted.
     *
     * @param transientChildren the list of transient child class names
     */
    public void setTransientChildren(List<String> transientChildren) {
        this.transientChildren = transientChildren;
    }

    /**
     * Adds a child class name to the list of transient children that should not be persisted.
     *
     * @param classname the child class name to add
     */
    public void addTransientChild(String classname) {
        if (transientChildren == null) {
            transientChildren = new ArrayList<>();
        }
        transientChildren.add(classname);
    }

    /**
     * Removes a child class name from the list of transient children.
     *
     * @param classname the child class name to remove
     */
    public void removeTransientChild(String classname) {
        if (transientChildren != null) {
            transientChildren.remove(classname);
        }
    }

    /**
     * Is child transient, please don't save this.
     *
     * @param classname The class name to check
     *
     * @return is classname attribute?
     */
    public boolean isTransientChild(String classname) {
        if (transientChildren != null) {
            return transientChildren.contains(classname);
        }
        return false;
    }

    /**
     * Is attribute transient, please don't save this.
     *
     * @param attribute The attribute name to check
     *
     * @return is transient attribute?
     */
    public boolean isTransientAttribute(String attribute) {
        if (transientAttributes != null) {
            return transientAttributes.contains(attribute);
        }
        return false;
    }

    /**
     * Return the real id or TagClass
     *
     * @return Returns the id.
     */
    public String getId() {
        if (id != null) {
            return id;
        } else {
            return getTagClass();
        }
    }

    /**
     * Sets the unique identifier for this description.
     *
     * @param id the unique identifier
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * Indicates whether the attributes of this component should be stored.
     *
     * @return true if attributes should be stored
     */
    public boolean isAttributes() {
        return attributes;
    }

    /**
     * Sets whether the attributes of this component should be stored.
     *
     * @param attributes true if attributes should be stored
     */
    public void setAttributes(boolean attributes) {
        this.attributes = attributes;
    }

    /**
     * Indicates whether this component should be stored in a separate file.
     *
     * @return true if it's a separate store
     */
    public boolean isStoreSeparate() {
        return storeSeparate;
    }

    /**
     * Sets whether this component should be stored in a separate file.
     *
     * @param storeSeparate true if it should be stored separately
     */
    public void setStoreSeparate(boolean storeSeparate) {
        this.storeSeparate = storeSeparate;
    }

    /**
     * Indicates whether child components should be stored.
     *
     * @return true if children should be stored
     */
    public boolean isChildren() {
        return children;
    }

    /**
     * Sets whether child components should be stored.
     *
     * @param children true if children should be stored
     */
    public void setChildren(boolean children) {
        this.children = children;
    }
}
