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
package org.apache.tomcat.util.descriptor.tld;

import java.util.List;

/**
 *
 */
public class Tag {
    private String name;
    private String tagClass;
    private String teiClass;
    private String bodyContent;
    private String displayName;
    private String smallIcon;
    private String largeIcon;
    private String info;
    private boolean dynamicAttributes;
    private List<Variable> variables;
    private List<Attribute> attributes;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTagClass() {
        return tagClass;
    }

    public void setTagClass(String tagClass) {
        this.tagClass = tagClass;
    }

    public String getTeiClass() {
        return teiClass;
    }

    public void setTeiClass(String teiClass) {
        this.teiClass = teiClass;
    }

    public String getBodyContent() {
        return bodyContent;
    }

    public void setBodyContent(String bodyContent) {
        this.bodyContent = bodyContent;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getSmallIcon() {
        return smallIcon;
    }

    public void setSmallIcon(String smallIcon) {
        this.smallIcon = smallIcon;
    }

    public String getLargeIcon() {
        return largeIcon;
    }

    public void setLargeIcon(String largeIcon) {
        this.largeIcon = largeIcon;
    }

    public String getInfo() {
        return info;
    }

    public void setInfo(String info) {
        this.info = info;
    }

    public boolean hasDynamicAttributes() {
        return dynamicAttributes;
    }

    public void setDynamicAttributes(boolean dynamicAttributes) {
        this.dynamicAttributes = dynamicAttributes;
    }

    public static class Variable {
        private String nameGiven;
        private String nameFromAttribute;
        private String className;
        private boolean declare;
        private int scope;

        public String getNameGiven() {
            return nameGiven;
        }

        public void setNameGiven(String nameGiven) {
            this.nameGiven = nameGiven;
        }

        public String getNameFromAttribute() {
            return nameFromAttribute;
        }

        public void setNameFromAttribute(String nameFromAttribute) {
            this.nameFromAttribute = nameFromAttribute;
        }

        public String getClassName() {
            return className;
        }

        public void setClassName(String className) {
            this.className = className;
        }

        public boolean isDeclare() {
            return declare;
        }

        public void setDeclare(boolean declare) {
            this.declare = declare;
        }

        public int getScope() {
            return scope;
        }

        public void setScope(int scope) {
            this.scope = scope;
        }
    }

    public static class Attribute {
        private String name;
        private boolean required;
        private String type;
        private boolean requestTime;
        private boolean fragment;
        private String description;
        private boolean deferredValue;
        private boolean deferredMethod;
        private String expectedTypeName;
        private String methodSignature;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public boolean isRequired() {
            return required;
        }

        public void setRequired(boolean required) {
            this.required = required;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public boolean isRequestTime() {
            return requestTime;
        }

        public void setRequestTime(boolean requestTime) {
            this.requestTime = requestTime;
        }

        public boolean isFragment() {
            return fragment;
        }

        public void setFragment(boolean fragment) {
            this.fragment = fragment;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public boolean isDeferredValue() {
            return deferredValue;
        }

        public void setDeferredValue(boolean deferredValue) {
            this.deferredValue = deferredValue;
        }

        public boolean isDeferredMethod() {
            return deferredMethod;
        }

        public void setDeferredMethod(boolean deferredMethod) {
            this.deferredMethod = deferredMethod;
        }

        public String getExpectedTypeName() {
            return expectedTypeName;
        }

        public void setExpectedTypeName(String expectedTypeName) {
            this.expectedTypeName = expectedTypeName;
        }

        public String getMethodSignature() {
            return methodSignature;
        }

        public void setMethodSignature(String methodSignature) {
            this.methodSignature = methodSignature;
        }
    }
}
