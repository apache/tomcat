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
 *
 *
 * </pre>
 */
public class StoreDescription {

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
     * @return Returns the external.
     */
    public boolean isExternalAllowed() {
        return externalAllowed;
    }

    /**
     * @param external
     *            The external to set.
     */
    public void setExternalAllowed(boolean external) {
        this.externalAllowed = external;
    }

    public boolean isExternalOnly() {
        return externalOnly;
    }

    public void setExternalOnly(boolean external) {
        this.externalOnly = external;
    }

    /**
     * @return Returns the standard.
     */
    public boolean isStandard() {
        return standard;
    }

    /**
     * @param standard
     *            The standard to set.
     */
    public void setStandard(boolean standard) {
        this.standard = standard;
    }

    /**
     * @return Returns the backup.
     */
    public boolean isBackup() {
        return backup;
    }

    /**
     * @param backup
     *            The backup to set.
     */
    public void setBackup(boolean backup) {
        this.backup = backup;
    }

    /**
     * @return Returns the myDefault.
     */
    public boolean isDefault() {
        return myDefault;
    }

    /**
     * @param aDefault
     *            The myDefault to set.
     */
    public void setDefault(boolean aDefault) {
        this.myDefault = aDefault;
    }

    /**
     * @return Returns the storeFactory.
     */
    public String getStoreFactoryClass() {
        return storeFactoryClass;
    }

    /**
     * @param storeFactoryClass
     *            The storeFactory to set.
     */
    public void setStoreFactoryClass(String storeFactoryClass) {
        this.storeFactoryClass = storeFactoryClass;
    }

    /**
     * @return Returns the storeFactory.
     */
    public IStoreFactory getStoreFactory() {
        return storeFactory;
    }

    /**
     * @param storeFactory
     *            The storeFactory to set.
     */
    public void setStoreFactory(IStoreFactory storeFactory) {
        this.storeFactory = storeFactory;
    }

    /**
     * @return Returns the storeWriterClass.
     */
    public String getStoreWriterClass() {
        return storeWriterClass;
    }

    /**
     * @param storeWriterClass
     *            The storeWriterClass to set.
     */
    public void setStoreWriterClass(String storeWriterClass) {
        this.storeWriterClass = storeWriterClass;
    }

    /**
     * @return Returns the tagClass.
     */
    public String getTag() {
        return tag;
    }

    /**
     * @param tag
     *            The tag to set.
     */
    public void setTag(String tag) {
        this.tag = tag;
    }

    /**
     * @return Returns the tagClass.
     */
    public String getTagClass() {
        return tagClass;
    }

    /**
     * @param tagClass
     *            The tagClass to set.
     */
    public void setTagClass(String tagClass) {
        this.tagClass = tagClass;
    }

    /**
     * @return Returns the transientAttributes.
     */
    public List<String> getTransientAttributes() {
        return transientAttributes;
    }

    /**
     * @param transientAttributes
     *            The transientAttributes to set.
     */
    public void setTransientAttributes(List<String> transientAttributes) {
        this.transientAttributes = transientAttributes;
    }

    public void addTransientAttribute(String attribute) {
        if (transientAttributes == null)
            transientAttributes = new ArrayList<>();
        transientAttributes.add(attribute);
    }

    public void removeTransientAttribute(String attribute) {
        if (transientAttributes != null)
            transientAttributes.remove(attribute);
    }

    /**
     * @return Returns the transientChildren.
     */
    public List<String> getTransientChildren() {
        return transientChildren;
    }

    /**
     * @param transientChildren
     *            The transientChildren to set.
     */
    public void setTransientChildren(List<String> transientChildren) {
        this.transientChildren = transientChildren;
    }

    public void addTransientChild(String classname) {
        if (transientChildren == null)
            transientChildren = new ArrayList<>();
        transientChildren.add(classname);
    }

    public void removeTransientChild(String classname) {
        if (transientChildren != null)
            transientChildren.remove(classname);
    }

    /**
     * Is child transient, please don't save this.
     *
     * @param classname The class name to check
     * @return is classname attribute?
     */
    public boolean isTransientChild(String classname) {
        if (transientChildren != null)
            return transientChildren.contains(classname);
        return false;
    }

    /**
     * Is attribute transient, please don't save this.
     *
     * @param attribute The attribute name to check
     * @return is transient attribute?
     */
    public boolean isTransientAttribute(String attribute) {
        if (transientAttributes != null)
            return transientAttributes.contains(attribute);
        return false;
    }

    /**
     * Return the real id or TagClass
     *
     * @return Returns the id.
     */
    public String getId() {
        if (id != null)
            return id;
        else
            return getTagClass();
    }

    /**
     * @param id
     *            The id to set.
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * @return Returns the attributes.
     */
    public boolean isAttributes() {
        return attributes;
    }

    /**
     * @param attributes
     *            The attributes to set.
     */
    public void setAttributes(boolean attributes) {
        this.attributes = attributes;
    }

    /**
     * @return True if it's a separate store
     */
    public boolean isStoreSeparate() {
        return storeSeparate;
    }

    public void setStoreSeparate(boolean storeSeparate) {
        this.storeSeparate = storeSeparate;
    }

    /**
     * @return Returns the children.
     */
    public boolean isChildren() {
        return children;
    }

    /**
     * @param children
     *            The children to set.
     */
    public void setChildren(boolean children) {
        this.children = children;
    }
}
