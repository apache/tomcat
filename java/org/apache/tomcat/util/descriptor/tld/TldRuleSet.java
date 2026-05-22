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

import jakarta.servlet.jsp.tagext.TagAttributeInfo;
import jakarta.servlet.jsp.tagext.TagVariableInfo;
import jakarta.servlet.jsp.tagext.VariableInfo;

import org.apache.tomcat.util.digester.Digester;
import org.apache.tomcat.util.digester.Rule;
import org.apache.tomcat.util.digester.RuleSet;
import org.xml.sax.Attributes;

/**
 * Digester rule set for parsing Tag Library Descriptor files.
 */
public class TldRuleSet implements RuleSet {

    /**
     * Constructs a new TldRuleSet.
     */
    public TldRuleSet() {
    }
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

                StringBuilder code = digester.getGeneratedCode();
                if (code != null) {
                    code.append(digester.toVariableName(taglibXml)).append(".setJspVersion(\"");
                    code.append(attributes.getValue("version")).append("\");");
                    code.append(System.lineSeparator());
                }
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
        digester.addCallMethod(TAG_PREFIX + "/variable/name-from-attribute", "setNameFromAttribute", 0);
        digester.addCallMethod(TAG_PREFIX + "/variable/variable-class", "setClassName", 0);
        digester.addRule(TAG_PREFIX + "/variable/declare", new GenericBooleanRule(Variable.class, "setDeclare"));
        digester.addCallMethod(TAG_PREFIX + "/variable/scope", "setScope", 0);

        digester.addRule(TAG_PREFIX + "/attribute", new TagAttributeRule());
        digester.addCallMethod(TAG_PREFIX + "/attribute/description", "setDescription", 0);
        digester.addCallMethod(TAG_PREFIX + "/attribute/name", "setName", 0);
        digester.addRule(TAG_PREFIX + "/attribute/required", new GenericBooleanRule(Attribute.class, "setRequired"));
        digester.addRule(TAG_PREFIX + "/attribute/rtexprvalue",
                new GenericBooleanRule(Attribute.class, "setRequestTime"));
        digester.addCallMethod(TAG_PREFIX + "/attribute/type", "setType", 0);
        digester.addCallMethod(TAG_PREFIX + "/attribute/deferred-value", "setDeferredValue");
        digester.addCallMethod(TAG_PREFIX + "/attribute/deferred-value/type", "setExpectedTypeName", 0);
        digester.addCallMethod(TAG_PREFIX + "/attribute/deferred-method", "setDeferredMethod");
        digester.addCallMethod(TAG_PREFIX + "/attribute/deferred-method/method-signature", "setMethodSignature", 0);
        digester.addRule(TAG_PREFIX + "/attribute/fragment", new GenericBooleanRule(Attribute.class, "setFragment"));

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
            boolean allowShortNames = "1.2".equals(taglibXml.getJspVersion());
            Attribute attribute = new Attribute(allowShortNames);
            digester.push(attribute);

            StringBuilder code = digester.getGeneratedCode();
            if (code != null) {
                code.append(System.lineSeparator());
                code.append(TldRuleSet.class.getName()).append(".Attribute ").append(digester.toVariableName(attribute))
                        .append(" = new ");
                code.append(TldRuleSet.class.getName()).append(".Attribute").append('(')
                        .append(Boolean.toString(allowShortNames));
                code.append(");").append(System.lineSeparator());
            }
        }

        @Override
        public void end(String namespace, String name) throws Exception {
            Attribute attribute = (Attribute) digester.pop();
            TagXml tag = (TagXml) digester.peek();
            tag.getAttributes().add(attribute.toTagAttributeInfo());

            StringBuilder code = digester.getGeneratedCode();
            if (code != null) {
                code.append(digester.toVariableName(tag)).append(".getAttributes().add(");
                code.append(digester.toVariableName(attribute)).append(".toTagAttributeInfo());");
                code.append(System.lineSeparator());
            }
        }
    }

    /**
     * Intermediate representation of a tag attribute during TLD parsing.
     */
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

        /**
         * Sets the attribute name.
         * @param name the attribute name
         */
        public void setName(String name) {
            this.name = name;
        }

        /**
         * Sets whether the attribute is required.
         * @param required true if required
         */
        public void setRequired(boolean required) {
            this.required = required;
        }

        /**
         * Sets the attribute type, with short name resolution.
         * @param type the type name
         */
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

        /**
         * Sets whether the attribute is evaluated at request time.
         * @param requestTime true if request-time
         */
        public void setRequestTime(boolean requestTime) {
            this.requestTime = requestTime;
        }

        /**
         * Sets whether the attribute is a fragment type.
         * @param fragment true if fragment
         */
        public void setFragment(boolean fragment) {
            this.fragment = fragment;
        }

        /**
         * Sets the attribute description.
         * @param description the description
         */
        public void setDescription(String description) {
            this.description = description;
        }

        /**
         * Marks the attribute as a deferred value.
         */
        public void setDeferredValue() {
            this.deferredValue = true;
        }

        /**
         * Marks the attribute as a deferred method.
         */
        public void setDeferredMethod() {
            this.deferredMethod = true;
        }

        /**
         * Sets the expected type name for deferred attributes.
         * @param expectedTypeName the expected type name
         */
        public void setExpectedTypeName(String expectedTypeName) {
            this.expectedTypeName = expectedTypeName;
        }

        /**
         * Sets the method signature for deferred method attributes.
         * @param methodSignature the method signature
         */
        public void setMethodSignature(String methodSignature) {
            this.methodSignature = methodSignature;
        }

        /**
         * Converts this intermediate attribute representation to a TagAttributeInfo.
         * @return the TagAttributeInfo
         */
        public TagAttributeInfo toTagAttributeInfo() {
            if (fragment) {
                // JSP8.5.2: for a fragment type is fixed and rexprvalue is true
                type = "jakarta.servlet.jsp.tagext.JspFragment";
                requestTime = true;
            } else if (deferredValue) {
                type = "jakarta.el.ValueExpression";
                if (expectedTypeName == null) {
                    expectedTypeName = "java.lang.Object";
                }
            } else if (deferredMethod) {
                type = "jakarta.el.MethodExpression";
                if (methodSignature == null) {
                    methodSignature = "java.lang.Object method()";
                }
            }

            // According to JSP spec, for static values (those determined at
            // translation time) the type is fixed at java.lang.String.
            if (!requestTime && type == null) {
                type = "java.lang.String";
            }

            return new TagAttributeInfo(name, required, type, requestTime, fragment, description, deferredValue,
                    deferredMethod, expectedTypeName, methodSignature);
        }
    }

    private static class ScriptVariableRule extends Rule {
        @Override
        public void begin(String namespace, String name, Attributes attributes) throws Exception {
            Variable variable = new Variable();
            digester.push(variable);

            StringBuilder code = digester.getGeneratedCode();
            if (code != null) {
                code.append(System.lineSeparator());
                code.append(TldRuleSet.class.getName()).append(".Variable ").append(digester.toVariableName(variable))
                        .append(" = new ");
                code.append(TldRuleSet.class.getName()).append(".Variable").append("();")
                        .append(System.lineSeparator());
            }
        }

        @Override
        public void end(String namespace, String name) throws Exception {
            Variable variable = (Variable) digester.pop();
            TagXml tag = (TagXml) digester.peek();
            tag.getVariables().add(variable.toTagVariableInfo());

            StringBuilder code = digester.getGeneratedCode();
            if (code != null) {
                code.append(digester.toVariableName(tag)).append(".getVariables().add(");
                code.append(digester.toVariableName(variable)).append(".toTagVariableInfo());");
                code.append(System.lineSeparator());
            }
        }
    }

    /**
     * Intermediate representation of a tag variable during TLD parsing.
     */
    public static class Variable {
        /**
         * Constructs a new Variable.
         */
        public Variable() {
        }

        /**
         * The explicitly given variable name.
         */
        private String nameGiven;

        /**
         * The attribute name from which to derive the variable name.
         */
        private String nameFromAttribute;

        /**
         * The variable type class name.
         */
        private String className = "java.lang.String";

        /**
         * Whether to declare the variable.
         */
        private boolean declare = true;

        /**
         * The variable scope.
         */
        private int scope = VariableInfo.NESTED;

        /**
         * Sets the explicit variable name.
         * @param nameGiven the variable name
         */
        public void setNameGiven(String nameGiven) {
            this.nameGiven = nameGiven;
        }

        /**
         * Sets the attribute name from which to derive the variable name.
         * @param nameFromAttribute the attribute name
         */
        public void setNameFromAttribute(String nameFromAttribute) {
            this.nameFromAttribute = nameFromAttribute;
        }

        /**
         * Sets the variable type class name.
         * @param className the class name
         */
        public void setClassName(String className) {
            this.className = className;
        }

        /**
         * Sets whether to declare the variable.
         * @param declare true to declare
         */
        public void setDeclare(boolean declare) {
            this.declare = declare;
        }

        /**
         * Sets the variable scope.
         * @param scopeName the scope name
         */
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

        /**
         * Converts this intermediate variable representation to a TagVariableInfo.
         * @return the TagVariableInfo
         */
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
            if (null != text) {
                text = text.trim();
            }
            boolean value = "true".equalsIgnoreCase(text) || "yes".equalsIgnoreCase(text);
            setter.invoke(digester.peek(), Boolean.valueOf(value));

            StringBuilder code = digester.getGeneratedCode();
            if (code != null) {
                code.append(digester.toVariableName(digester.peek())).append('.').append(setter.getName());
                code.append('(').append(Boolean.valueOf(value)).append(");");
                code.append(System.lineSeparator());
            }
        }
    }
}
