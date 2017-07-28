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

import java.lang.reflect.Method;

import javax.servlet.jsp.tagext.TagAttributeInfo;
import javax.servlet.jsp.tagext.TagVariableInfo;
import javax.servlet.jsp.tagext.VariableInfo;

import org.apache.tomcat.util.digester.Digester;
import org.apache.tomcat.util.digester.Rule;
import org.apache.tomcat.util.digester.RuleSet;
import org.xml.sax.Attributes;

/**
 * RulesSet for digesting TLD files.
 */
public class TldRuleSet implements RuleSet {
    private static final String PREFIX = "taglib";
    private static final String VALIDATOR_PREFIX = PREFIX + "/validator";
    private static final String TAG_PREFIX = PREFIX + "/tag";
    private static final String TAGFILE_PREFIX = PREFIX + "/tag-file";
    private static final String FUNCTION_PREFIX = PREFIX + "/function";

    @Override
    public void addRuleInstances(Digester digester) {

        digester.addCallMethod(PREFIX + "/tlibversion", "setTlibVersion", 0);
        digester.addCallMethod(PREFIX + "/tlib-version", "setTlibVersion", 0);
        digester.addCallMethod(PREFIX + "/jspversion", "setJspVersion", 0);
        digester.addCallMethod(PREFIX + "/jsp-version", "setJspVersion", 0);
        digester.addRule(PREFIX, new Rule() {
            // for TLD 2.0 and later, jsp-version is set by version attribute
            @Override
            public void begin(String namespace, String name, Attributes attributes) {
                TaglibXml taglibXml = (TaglibXml) digester.peek();
                taglibXml.setJspVersion(attributes.getValue("version"));
            }
        });
        digester.addCallMethod(PREFIX + "/shortname", "setShortName", 0);
        digester.addCallMethod(PREFIX + "/short-name", "setShortName", 0);

        // common rules
        digester.addCallMethod(PREFIX + "/uri", "setUri", 0);
        digester.addCallMethod(PREFIX + "/info", "setInfo", 0);
        digester.addCallMethod(PREFIX + "/description", "setInfo", 0);
        digester.addCallMethod(PREFIX + "/listener/listener-class", "addListener", 0);

        // validator
        digester.addObjectCreate(VALIDATOR_PREFIX, ValidatorXml.class.getName());
        digester.addCallMethod(VALIDATOR_PREFIX + "/validator-class", "setValidatorClass", 0);
        digester.addCallMethod(VALIDATOR_PREFIX + "/init-param", "addInitParam", 2);
        digester.addCallParam(VALIDATOR_PREFIX + "/init-param/param-name", 0);
        digester.addCallParam(VALIDATOR_PREFIX + "/init-param/param-value", 1);
        digester.addSetNext(VALIDATOR_PREFIX, "setValidator", ValidatorXml.class.getName());


        // tag
        digester.addObjectCreate(TAG_PREFIX, TagXml.class.getName());
        addDescriptionGroup(digester, TAG_PREFIX);
        digester.addCallMethod(TAG_PREFIX + "/name", "setName", 0);
        digester.addCallMethod(TAG_PREFIX + "/tagclass", "setTagClass", 0);
        digester.addCallMethod(TAG_PREFIX + "/tag-class", "setTagClass", 0);
        digester.addCallMethod(TAG_PREFIX + "/teiclass", "setTeiClass", 0);
        digester.addCallMethod(TAG_PREFIX + "/tei-class", "setTeiClass", 0);
        digester.addCallMethod(TAG_PREFIX + "/bodycontent", "setBodyContent", 0);
        digester.addCallMethod(TAG_PREFIX + "/body-content", "setBodyContent", 0);

        digester.addRule(TAG_PREFIX + "/variable", new ScriptVariableRule());
        digester.addCallMethod(TAG_PREFIX + "/variable/name-given", "setNameGiven", 0);
        digester.addCallMethod(TAG_PREFIX + "/variable/name-from-attribute",
                "setNameFromAttribute", 0);
        digester.addCallMethod(TAG_PREFIX + "/variable/variable-class", "setClassName", 0);
        digester.addRule(TAG_PREFIX + "/variable/declare",
                new GenericBooleanRule(Variable.class, "setDeclare"));
        digester.addCallMethod(TAG_PREFIX + "/variable/scope", "setScope", 0);

        digester.addRule(TAG_PREFIX + "/attribute", new TagAttributeRule());
        digester.addCallMethod(TAG_PREFIX + "/attribute/description", "setDescription", 0);
        digester.addCallMethod(TAG_PREFIX + "/attribute/name", "setName", 0);
        digester.addRule(TAG_PREFIX + "/attribute/required",
                new GenericBooleanRule(Attribute.class, "setRequired"));
        digester.addRule(TAG_PREFIX + "/attribute/rtexprvalue",
                new GenericBooleanRule(Attribute.class, "setRequestTime"));
        digester.addCallMethod(TAG_PREFIX + "/attribute/type", "setType", 0);
        digester.addCallMethod(TAG_PREFIX + "/attribute/deferred-value", "setDeferredValue");
        digester.addCallMethod(TAG_PREFIX + "/attribute/deferred-value/type",
                "setExpectedTypeName", 0);
        digester.addCallMethod(TAG_PREFIX + "/attribute/deferred-method", "setDeferredMethod");
        digester.addCallMethod(TAG_PREFIX + "/attribute/deferred-method/method-signature",
                "setMethodSignature", 0);
        digester.addRule(TAG_PREFIX + "/attribute/fragment",
                new GenericBooleanRule(Attribute.class, "setFragment"));

        digester.addRule(TAG_PREFIX + "/dynamic-attributes",
                new GenericBooleanRule(TagXml.class, "setDynamicAttributes"));
        digester.addSetNext(TAG_PREFIX, "addTag", TagXml.class.getName());

        // tag-file
        digester.addObjectCreate(TAGFILE_PREFIX, TagFileXml.class.getName());
        addDescriptionGroup(digester, TAGFILE_PREFIX);
        digester.addCallMethod(TAGFILE_PREFIX + "/name", "setName", 0);
        digester.addCallMethod(TAGFILE_PREFIX + "/path", "setPath", 0);
        digester.addSetNext(TAGFILE_PREFIX, "addTagFile", TagFileXml.class.getName());

        // function
        digester.addCallMethod(FUNCTION_PREFIX, "addFunction", 3);
        digester.addCallParam(FUNCTION_PREFIX + "/name", 0);
        digester.addCallParam(FUNCTION_PREFIX + "/function-class", 1);
        digester.addCallParam(FUNCTION_PREFIX + "/function-signature", 2);
    }

    private void addDescriptionGroup(Digester digester, String prefix) {
        digester.addCallMethod(prefix + "/info", "setInfo", 0);
        digester.addCallMethod(prefix + "small-icon", "setSmallIcon", 0);
        digester.addCallMethod(prefix + "large-icon", "setLargeIcon", 0);

        digester.addCallMethod(prefix + "/description", "setInfo", 0);
        digester.addCallMethod(prefix + "/display-name", "setDisplayName", 0);
        digester.addCallMethod(prefix + "/icon/small-icon", "setSmallIcon", 0);
        digester.addCallMethod(prefix + "/icon/large-icon", "setLargeIcon", 0);
    }

    private static class TagAttributeRule extends Rule {
        @Override
        public void begin(String namespace, String name, Attributes attributes) throws Exception {
            TaglibXml taglibXml = (TaglibXml) digester.peek(digester.getCount() - 1);
            digester.push(new Attribute("1.2".equals(taglibXml.getJspVersion())));
        }

        @Override
        public void end(String namespace, String name) throws Exception {
            Attribute attribute = (Attribute) digester.pop();
            TagXml tag = (TagXml) digester.peek();
            tag.getAttributes().add(attribute.toTagAttributeInfo());
        }
    }

    public static class Attribute {
        private final boolean allowShortNames;
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

        private Attribute(boolean allowShortNames) {
            this.allowShortNames = allowShortNames;
        }

        public void setName(String name) {
            this.name = name;
        }

        public void setRequired(boolean required) {
            this.required = required;
        }

        public void setType(String type) {
            if (allowShortNames) {
                switch (type) {
                    case "Boolean":
                        this.type = "java.lang.Boolean";
                        break;
                    case "Character":
                        this.type = "java.lang.Character";
                        break;
                    case "Byte":
                        this.type = "java.lang.Byte";
                        break;
                    case "Short":
                        this.type = "java.lang.Short";
                        break;
                    case "Integer":
                        this.type = "java.lang.Integer";
                        break;
                    case "Long":
                        this.type = "java.lang.Long";
                        break;
                    case "Float":
                        this.type = "java.lang.Float";
                        break;
                    case "Double":
                        this.type = "java.lang.Double";
                        break;
                    case "String":
                        this.type = "java.lang.String";
                        break;
                    case "Object":
                        this.type = "java.lang.Object";
                        break;
                    default:
                        this.type = type;
                        break;
                }
            } else {
                this.type = type;
            }
        }

        public void setRequestTime(boolean requestTime) {
            this.requestTime = requestTime;
        }

        public void setFragment(boolean fragment) {
            this.fragment = fragment;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public void setDeferredValue() {
            this.deferredValue = true;
        }

        public void setDeferredMethod() {
            this.deferredMethod = true;
        }

        public void setExpectedTypeName(String expectedTypeName) {
            this.expectedTypeName = expectedTypeName;
        }

        public void setMethodSignature(String methodSignature) {
            this.methodSignature = methodSignature;
        }

        public TagAttributeInfo toTagAttributeInfo() {
            if (fragment) {
                // JSP8.5.2: for a fragment type is fixed and rexprvalue is true
                type = "javax.servlet.jsp.tagext.JspFragment";
                requestTime = true;
            } else if (deferredValue) {
                type = "javax.el.ValueExpression";
                if (expectedTypeName == null) {
                    expectedTypeName = "java.lang.Object";
                }
            } else if (deferredMethod) {
                type = "javax.el.MethodExpression";
                if (methodSignature == null) {
                    methodSignature = "java.lang.Object method()";
                }
            }

            // According to JSP spec, for static values (those determined at
            // translation time) the type is fixed at java.lang.String.
            if (!requestTime && type == null) {
                type = "java.lang.String";
            }

            return new TagAttributeInfo(
                    name,
                    required,
                    type,
                    requestTime,
                    fragment,
                    description,
                    deferredValue,
                    deferredMethod,
                    expectedTypeName,
                    methodSignature);
        }
    }

    private static class ScriptVariableRule extends Rule {
        @Override
        public void begin(String namespace, String name, Attributes attributes) throws Exception {
            digester.push(new Variable());
        }

        @Override
        public void end(String namespace, String name) throws Exception {
            Variable variable = (Variable) digester.pop();
            TagXml tag = (TagXml) digester.peek();
            tag.getVariables().add(variable.toTagVariableInfo());
        }
    }

    public static class Variable {
        private String nameGiven;
        private String nameFromAttribute;
        private String className = "java.lang.String";
        private boolean declare = true;
        private int scope = VariableInfo.NESTED;

        public void setNameGiven(String nameGiven) {
            this.nameGiven = nameGiven;
        }

        public void setNameFromAttribute(String nameFromAttribute) {
            this.nameFromAttribute = nameFromAttribute;
        }

        public void setClassName(String className) {
            this.className = className;
        }

        public void setDeclare(boolean declare) {
            this.declare = declare;
        }

        public void setScope(String scopeName) {
            switch (scopeName) {
                case "NESTED":
                    scope = VariableInfo.NESTED;
                    break;
                case "AT_BEGIN":
                    scope = VariableInfo.AT_BEGIN;
                    break;
                case "AT_END":
                    scope = VariableInfo.AT_END;
                    break;
            }
        }

        public TagVariableInfo toTagVariableInfo() {
            return new TagVariableInfo(nameGiven, nameFromAttribute, className, declare, scope);
        }
    }

    private static class GenericBooleanRule extends Rule {
        private final Method setter;

        private GenericBooleanRule(Class<?> type, String setterName) {
            try {
                this.setter = type.getMethod(setterName, Boolean.TYPE);
            } catch (NoSuchMethodException e) {
                throw new IllegalArgumentException(e);
            }
        }

        @Override
        public void body(String namespace, String name, String text) throws Exception {
            if(null != text)
                text = text.trim();
            boolean value = "true".equalsIgnoreCase(text) || "yes".equalsIgnoreCase(text);
            setter.invoke(digester.peek(), Boolean.valueOf(value));
        }
    }
}
