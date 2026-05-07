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
package org.apache.tomcat.util.xreflection;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Set;
import java.util.TreeSet;

import org.apache.tomcat.util.IntrospectionUtils;

/**
 * Represents a class in the reflection-based property setter/getter code
 * generation system. Tracks properties, parent/child class relationships,
 * and generates optimized reflection-free property access code.
 */
public final class SetPropertyClass implements Comparable<SetPropertyClass> {

    /**
     * Variable name used in generated code for the target object.
     */
    static final String OBJECT_VAR_NAME = "o";
    /**
     * Variable name used in generated code for the property name.
     */
    static final String NAME_VAR_NAME = "name";
    /**
     * Variable name used in generated code for the property value.
     */
    static final String VALUE_VAR_NAME = "value";
    /**
     * Variable name used in generated code for the setProperty flag.
     */
    static final String SETP_VAR_NAME = "invokeSetProperty";

    private final SetPropertyClass parent;
    private final Class<?> clazz;
    private final Set<SetPropertyClass> children = new TreeSet<>();
    private final Set<ReflectionProperty> properties = new TreeSet<>();
    private final boolean isAbstract;
    private final Method genericSetPropertyMethod;
    private final Method genericGetPropertyMethod;

    SetPropertyClass(Class<?> clazz, SetPropertyClass parent) {
        this.clazz = clazz;
        this.parent = parent;
        this.isAbstract = Modifier.isAbstract(clazz.getModifiers());
        Method classSetter, classGetter;
        try {
            classSetter = clazz.getDeclaredMethod("setProperty", String.class, String.class);
        } catch (NoSuchMethodException e) {
            try {
                classSetter = clazz.getDeclaredMethod("setProperty", String.class, Object.class);
            } catch (NoSuchMethodException x) {
                classSetter = null;
            }
        }
        try {
            classGetter = clazz.getDeclaredMethod("getProperty", String.class);
        } catch (NoSuchMethodException e) {
            classGetter = null;
        }
        genericSetPropertyMethod = classSetter;
        genericGetPropertyMethod = classGetter;
    }

    boolean isAbstract() {
        return isAbstract;
    }

    void addSubClass(SetPropertyClass clazz) {
        this.children.add(clazz);
    }

    boolean isBaseClass() {
        return parent == null;
    }

    /**
     * Returns the set of child classes registered under this class.
     * @return the child classes
     */
    public Set<SetPropertyClass> getChildren() {
        return children;
    }

    /**
     * Returns the set of reflection properties for this class.
     * @return the reflection properties
     */
    public Set<ReflectionProperty> getProperties() {
        return properties;
    }

    /**
     * Returns the generic setProperty method if one exists.
     * @return the generic setProperty method, or {@code null}
     */
    public Method getGenericSetPropertyMethod() {
        return genericSetPropertyMethod;
    }

    /**
     * Returns the generic getProperty method if one exists.
     * @return the generic getProperty method, or {@code null}
     */
    public Method getGenericGetPropertyMethod() {
        return genericGetPropertyMethod;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        SetPropertyClass that = (SetPropertyClass) o;

        return clazz.equals(that.clazz);
    }

    @Override
    public int hashCode() {
        return clazz.hashCode();
    }

    /**
     * Returns the parent class.
     * @return the parent class, or {@code null} if this is the root class
     */
    public SetPropertyClass getParent() {
        return parent;
    }

    /**
     * Returns the {@link Class} represented by this instance.
     * @return the represented class
     */
    public Class<?> getClazz() {
        return clazz;
    }

    @Override
    public String toString() {
        return "SetPropertyClass{" + "clazz=" + clazz.getName() + '}';
    }

    /**
     * Add a property to this class.
     *
     * @param property the property to add
     */
    public void addProperty(ReflectionProperty property) {
        properties.add(property);
    }


    /**
     * Generate a Java code snippet to set the given property value.
     *
     * @param property the property to generate code for
     * @return the generated code snippet, or {@code null} if no setter is available
     */
    public String generateSetPropertyMethod(ReflectionProperty property) {
        // this property has a setProperty method
        if (property.hasSetPropertySetter()) {
            return "((" + this.getClazz().getName().replace('$', '.') + ")" + OBJECT_VAR_NAME + ")." +
                    property.getSetMethod().getName() + "(" + NAME_VAR_NAME + ", " + VALUE_VAR_NAME + ");";
        }

        // direct setter
        if (property.hasSetter()) {
            return "((" + this.getClazz().getName().replace('$', '.') + ")" + OBJECT_VAR_NAME + ")." +
                    property.getSetMethod().getName() + "(" + property.getConversion(VALUE_VAR_NAME) + ");";
        }
        return null;
    }

    /**
     * Generate a Java code snippet to get the given property value.
     *
     * @param property the property to generate code for
     * @return the generated code snippet, or {@code null} if no getter is available
     */
    public String generateGetPropertyMethod(ReflectionProperty property) {
        // this property has a getProperty method
        if (property.hasGetPropertyGetter()) {
            return "result = ((" + this.getClazz().getName().replace('$', '.') + ")" + OBJECT_VAR_NAME + ")." +
                    property.getGetMethod().getName() + "(" + NAME_VAR_NAME + ");";
        }

        // direct getter
        if (property.hasGetter()) {
            return "result = ((" + this.getClazz().getName().replace('$', '.') + ")" + OBJECT_VAR_NAME + ")." +
                    property.getGetMethod().getName() + "();";
        }
        return null;
    }

    /**
     * Generate a complete setProperty method for this class with switch-case
     * statements for each known property.
     *
     * @return the generated Java source code
     */
    public String generateSetPropertyForMethod() {
        //@formatter:off
        StringBuilder code = new StringBuilder(ReflectionLessCodeGenerator.getIndent(1))
            .append(generatesSetPropertyForMethodHeader())
            .append(System.lineSeparator())
            .append(ReflectionLessCodeGenerator.getIndent(2))
            .append("switch (")
            .append(NAME_VAR_NAME)
            .append(") {")
            .append(System.lineSeparator());

        // case statements for each property
        for (ReflectionProperty property : getProperties()) {
            String invocation = generateSetPropertyMethod(property);
            if (invocation != null) {
                code.append(ReflectionLessCodeGenerator.getIndent(3))
                    .append("case \"")
                    .append(property.getPropertyName())
                    .append("\" : ")
                    .append(System.lineSeparator());

                code.append(ReflectionLessCodeGenerator.getIndent(4))
                    .append(invocation)
                    .append(System.lineSeparator())
                    .append(ReflectionLessCodeGenerator.getIndent(4))
                    .append("return true;")
                    .append(System.lineSeparator());

            } else {
                code.append(ReflectionLessCodeGenerator.getIndent(3)).append("//no set")
                    .append(IntrospectionUtils.capitalize(
                            property.getPropertyName())).append(" method found on this class")
                    .append(System.lineSeparator());
            }
        }

        // end switch statement
        code.append(ReflectionLessCodeGenerator.getIndent(2))
            .append('}')
            .append(System.lineSeparator());

        // we have a generic setProperty(String, String) method, invoke it
        if (getGenericSetPropertyMethod() != null) {
            ReflectionProperty p = new ReflectionProperty(
                clazz.getName(),
                "property",
                String.class,
                getGenericSetPropertyMethod(),
                null
            );
           code.append(ReflectionLessCodeGenerator.getIndent(2))
               .append("if (")
               .append(SETP_VAR_NAME)
               .append(") {")
               .append(System.lineSeparator())
               .append(ReflectionLessCodeGenerator.getIndent(3))
               .append(generateSetPropertyMethod(p))
               .append(System.lineSeparator())
               .append(ReflectionLessCodeGenerator.getIndent(3))
               .append("return true;")
               .append(System.lineSeparator())
               .append(ReflectionLessCodeGenerator.getIndent(2))
               .append('}')
               .append(System.lineSeparator());
        }

        // invoke parent or return false
        code.append(ReflectionLessCodeGenerator.getIndent(2))
            .append("return ")
            .append(getSetPropertyForExitStatement())
            .append(System.lineSeparator())
            .append(ReflectionLessCodeGenerator.getIndent(1))
            .append('}');

        return code.toString();
        //@formatter:on
    }

    private String getSetPropertyForExitStatement() {

        return (getParent() != null) ?
                // invoke the parent if we have one
                getParent().generateParentSetPropertyForMethodInvocation() :
                // if we invoke setProperty, return true, return false otherwise
                getGenericSetPropertyMethod() != null ? "true;" : "false;";
    }

    /**
     * Generate a switch case statement that invokes the setProperty method for
     * this class.
     *
     * @param level the indentation level
     * @return the generated Java source code
     */
    public String generateInvocationSetForPropertyCaseStatement(int level) {
        //@formatter:off
        StringBuilder code = new StringBuilder(ReflectionLessCodeGenerator.getIndent(level))
            .append("case \"")
            .append(getClazz().getName())
            .append("\" : ")
            .append(System.lineSeparator())
            .append(ReflectionLessCodeGenerator.getIndent(level+1))
            .append("return ")
            .append(generateParentSetPropertyForMethodInvocation())
            .append(System.lineSeparator());
        return code.toString();
        //@formatter:on
    }

    /**
     * Generate a method invocation string for the setProperty method of this
     * class, suitable for use from a parent class.
     *
     * @return the generated method invocation string
     */
    public String generateParentSetPropertyForMethodInvocation() {
        String[] classParts = clazz.getName().split("\\.|\\$");
        StringBuilder methodInvocation = new StringBuilder("setPropertyFor");
        for (String s : classParts) {
            methodInvocation.append(IntrospectionUtils.capitalize(s));
        }
        //@formatter:off
        methodInvocation.append('(')
            .append(OBJECT_VAR_NAME)
            .append(", ")
            .append(NAME_VAR_NAME)
            .append(", ")
            .append(VALUE_VAR_NAME)
            .append(", ")
            .append(SETP_VAR_NAME)
            .append(");");
        return methodInvocation.toString();
        //@formatter:on
    }

    /**
     * Generate the method header for the setProperty method for this class.
     *
     * @return the generated method header string
     */
    public String generatesSetPropertyForMethodHeader() {
        String[] classParts = clazz.getName().split("\\.|\\$");
        StringBuilder methodInvocation = new StringBuilder("private static boolean setPropertyFor");
        for (String s : classParts) {
            methodInvocation.append(IntrospectionUtils.capitalize(s));
        }
        //@formatter:off
        methodInvocation.append("(Object ")
            .append(OBJECT_VAR_NAME)
            .append(", String ")
            .append(NAME_VAR_NAME)
            .append(", String ")
            .append(VALUE_VAR_NAME)
            .append(", boolean ")
            .append(SETP_VAR_NAME)
            .append(") {");
        return methodInvocation.toString();
        //@formatter:on
    }

    /**
     * Generate a switch case statement that invokes the getProperty method for
     * this class.
     *
     * @param level the indentation level
     * @return the generated Java source code
     */
    public String generateInvocationGetForPropertyCaseStatement(int level) {
        //@formatter:off
        StringBuilder code = new StringBuilder(ReflectionLessCodeGenerator.getIndent(level))
            .append("case \"")
            .append(getClazz().getName())
            .append("\" : ")
            .append(System.lineSeparator())
            .append(ReflectionLessCodeGenerator.getIndent(level+1))
            .append("result = ")
            .append(generateParentGetPropertyForMethodInvocation())
            .append(System.lineSeparator())
            .append(ReflectionLessCodeGenerator.getIndent(level+1))
            .append("break;")
            .append(System.lineSeparator());
        return code.toString();
        //@formatter:on
    }

    /**
     * Generate a method invocation string for the getProperty method of this
     * class, suitable for use from a parent class.
     *
     * @return the generated method invocation string
     */
    public String generateParentGetPropertyForMethodInvocation() {
        String[] classParts = clazz.getName().split("\\.|\\$");
        StringBuilder methodInvocation = new StringBuilder("getPropertyFor");
        for (String s : classParts) {
            methodInvocation.append(IntrospectionUtils.capitalize(s));
        }
        //@formatter:off
        methodInvocation.append('(')
            .append(OBJECT_VAR_NAME)
            .append(", ")
            .append(NAME_VAR_NAME)
            .append(");");
        return methodInvocation.toString();
        //@formatter:on
    }

    /**
     * Generate the method header for the getProperty method for this class.
     *
     * @return the generated method header string
     */
    public String generatesGetPropertyForMethodHeader() {
        String[] classParts = clazz.getName().split("\\.|\\$");
        StringBuilder methodInvocation = new StringBuilder("private static Object getPropertyFor");
        for (String s : classParts) {
            methodInvocation.append(IntrospectionUtils.capitalize(s));
        }
        //@formatter:off
        methodInvocation.append("(Object ")
            .append(OBJECT_VAR_NAME)
            .append(", String ")
            .append(NAME_VAR_NAME)
            .append(") {");
        return methodInvocation.toString();
        //@formatter:on
    }

    private String getGetPropertyForExitStatement() {
        if (getParent() != null) {
            return getParent().generateParentGetPropertyForMethodInvocation();
        }
        return "null;";
    }


    /**
     * Generate a complete getProperty method for this class with switch-case
     * statements for each known property.
     *
     * @return the generated Java source code
     */
    public String generateGetPropertyForMethod() {
        //@formatter:off
        StringBuilder code = new StringBuilder(ReflectionLessCodeGenerator.getIndent(1))
            .append(generatesGetPropertyForMethodHeader())
            .append(System.lineSeparator())
            .append(ReflectionLessCodeGenerator.getIndent(2))
            .append("Object result = null;")
            .append(System.lineSeparator())
            .append(ReflectionLessCodeGenerator.getIndent(2))
            .append("switch (")
            .append(NAME_VAR_NAME)
            .append(") {")
            .append(System.lineSeparator());

        // case statements for each property
        for (ReflectionProperty property : getProperties()) {
            String invocation = generateGetPropertyMethod(property);
            if (invocation != null) {
                code.append(ReflectionLessCodeGenerator.getIndent(3))
                    .append("case \"")
                    .append(property.getPropertyName())
                    .append("\" : ")
                    .append(System.lineSeparator());

                code.append(ReflectionLessCodeGenerator.getIndent(4))
                    .append(invocation)
                    .append(System.lineSeparator())
                    .append(ReflectionLessCodeGenerator.getIndent(4))
                    .append("break;")
                    .append(System.lineSeparator());
            } else {
                code.append(ReflectionLessCodeGenerator.getIndent(3)).append("//no get")
                    .append(IntrospectionUtils.capitalize(property.getPropertyName())).append(" method found on this class")
                    .append(System.lineSeparator());
            }
        }

        // end switch statement
        code.append(ReflectionLessCodeGenerator.getIndent(2))
            .append('}')
            .append(System.lineSeparator());

        // invoke parent or return null
        code.append(ReflectionLessCodeGenerator.getIndent(2))
            .append("if (result == null) {")
            .append(System.lineSeparator())
            .append(ReflectionLessCodeGenerator.getIndent(3))
            .append("result = ")
            .append(getGetPropertyForExitStatement())
            .append(System.lineSeparator())
            .append(ReflectionLessCodeGenerator.getIndent(2))
            .append('}')
            .append(System.lineSeparator());

        // we have a generic getProperty(String, String) method, invoke it
        if (getGenericGetPropertyMethod() != null) {
            ReflectionProperty p = new ReflectionProperty(
                clazz.getName(),
                "property",
                String.class,
                null,
                getGenericGetPropertyMethod()
            );
            code.append(ReflectionLessCodeGenerator.getIndent(2))
                .append("if (result == null) {")
                .append(System.lineSeparator())
                .append(ReflectionLessCodeGenerator.getIndent(3))
                .append(generateGetPropertyMethod(p))
                .append(System.lineSeparator())
                .append(ReflectionLessCodeGenerator.getIndent(2))
                .append('}')
                .append(System.lineSeparator());
        }
        code.append(ReflectionLessCodeGenerator.getIndent(2))
            .append("return result;")
            .append(System.lineSeparator())
            .append(ReflectionLessCodeGenerator.getIndent(1))
            .append('}')
            .append(System.lineSeparator());

        return code.toString();
        //@formatter:on
    }

    @Override
    public int compareTo(SetPropertyClass o) {
        return clazz.getName().compareTo(o.clazz.getName());
    }
}
