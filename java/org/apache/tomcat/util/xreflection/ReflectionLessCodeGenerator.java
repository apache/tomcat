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

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Set;

final class ReflectionLessCodeGenerator {
    private static final String INDENT = "    ";

    static StringBuilder getIndent(int multiplier) {
        StringBuilder indent = new StringBuilder();
        while ((multiplier--) > 0) {
            indent.append(INDENT);
        }
        return indent;
    }

    static void generateCode(
        File directory,
        String className,
        String packageName,
        Set<SetPropertyClass> baseClasses
    ) throws IOException {
        //begin - class
        StringBuilder code = new StringBuilder(AL20_HEADER)
            .append("package ")
            .append(packageName)
            .append(";")
            .append(System.lineSeparator())
            .append(System.lineSeparator())
            .append("final class ")
            .append(className)
            .append(" {")
            .append(System.lineSeparator())
            .append(System.lineSeparator());

        //begin - isEnabled method
        code.append(getIndent(1))
            .append("static boolean isEnabled() {")
            .append(System.lineSeparator())
            .append(getIndent(2))
            .append("return true;")
            .append(System.lineSeparator())
            .append(getIndent(1))
            .append("}")
            .append(System.lineSeparator())
            .append(System.lineSeparator())
        ;
        //end - isEnabled method

        //begin - getInetAddress method
        code.append(getIndent(1))
            .append("private static java.net.InetAddress getInetAddress(String value) {")
            .append(System.lineSeparator())
            .append(getIndent(2))
            .append("try {")
            .append(System.lineSeparator())
            .append(getIndent(3))
            .append("return java.net.InetAddress.getByName(value);")
            .append(System.lineSeparator())
            .append(getIndent(2))
            .append("} catch (java.net.UnknownHostException x) { throw new RuntimeException(x); }")
            .append(System.lineSeparator())
            .append(getIndent(1))
            .append("}")
            .append(System.lineSeparator())
            .append(System.lineSeparator())
            ;
        //end - getInetAddress method

        //begin - getPropertyInternal method
        code.append(getIndent(1))
            .append("static Object getPropertyInternal(Object ")
            .append(SetPropertyClass.OBJECT_VAR_NAME)
            .append(", String ")
            .append(SetPropertyClass.NAME_VAR_NAME)
            .append(") {")
            .append(System.lineSeparator())
            .append(getIndent(2))
            .append("Class<?> checkThisClass = o.getClass();")
            .append(System.lineSeparator())
            .append(getIndent(2))
            .append("Object result = null;")
            .append(System.lineSeparator())
            .append(getIndent(2))
            .append("while (checkThisClass != Object.class && result == null) {")
            .append(System.lineSeparator())
            .append(getIndent(3))
            .append("switch (checkThisClass.getName()) {")
            .append(System.lineSeparator());

        //generate case statements for getPropertyInternal
        generateCaseStatementsForGetPropertyInternal(baseClasses, code);


        code
            .append(getIndent(3))
            .append("}")
            .append(System.lineSeparator())
            .append(getIndent(3))
            .append("checkThisClass = checkThisClass.getSuperclass();")
            .append(System.lineSeparator())
            .append(getIndent(2))
            .append("}")
            .append(System.lineSeparator())
            .append(getIndent(2))
            .append("return result;")
            .append(System.lineSeparator())
            .append(getIndent(1))
            .append("}")
            .append(System.lineSeparator());
        //end - getPropertyInternal method

        //begin - getPropertyForXXX methods
        generateGetPropertyForMethods(baseClasses, code);
        //end - getPropertyForXXX methods

        //begin - setPropertyInternal method
        code.append(getIndent(1))
            .append("static boolean setPropertyInternal(Object ")
            .append(SetPropertyClass.OBJECT_VAR_NAME)
            .append(", String ")
            .append(SetPropertyClass.NAME_VAR_NAME)
            .append(", String ")
            .append(SetPropertyClass.VALUE_VAR_NAME)
            .append(", boolean ")
            .append(SetPropertyClass.SETP_VAR_NAME)
            .append(") {")
            .append(System.lineSeparator())
            .append(getIndent(2))
            .append("Class<?> checkThisClass = o.getClass();")
            .append(System.lineSeparator())
            .append(getIndent(2))
            .append("while (checkThisClass != Object.class) {")
            .append(System.lineSeparator())
            .append(getIndent(3))
            .append("switch (checkThisClass.getName()) {")
            .append(System.lineSeparator());

        //generate case statements for setPropertyInternal
        generateCaseStatementsForSetPropertyInternal(baseClasses, code);


        code
            .append(getIndent(3))
            .append("}")
            .append(System.lineSeparator())
            .append(getIndent(3))
            .append("checkThisClass = checkThisClass.getSuperclass();")
            .append(System.lineSeparator())
            .append(getIndent(2))
            .append("}")
            .append(System.lineSeparator())
            .append(getIndent(2))
            .append("return false;")
            .append(System.lineSeparator())
            .append(getIndent(1))
            .append("}")
            .append(System.lineSeparator());
        //end - setPropertyInternal method

        //begin - setPropertyForXXX methods
        generateSetPropertyForMethods(baseClasses, code);
        //end - setPropertyForXXX methods

        code.append("}")
            .append(System.lineSeparator());
        //end - class
        File destination = new File(directory, className+".java");
        BufferedWriter writer = new BufferedWriter(new FileWriter(destination, false));
        writer.write(code.toString());
        writer.flush();
        writer.close();

    }

    private static void generateCaseStatementForSetPropertyInternal(SetPropertyClass clazz, StringBuilder code) {
        for (SetPropertyClass child : clazz.getChildren()) {
            generateCaseStatementForSetPropertyInternal(child, code);
        }
        if (!clazz.isAbstract()) {
            code.append(clazz.generateInvocationSetForPropertyCaseStatement(4));
        }
    }

    private static void generateCaseStatementsForSetPropertyInternal(Set<SetPropertyClass> baseClasses, StringBuilder code) {
        for (SetPropertyClass clazz : baseClasses) {
            generateCaseStatementForSetPropertyInternal(clazz, code);
        }
    }

    private static void generateSetPropertyForMethod(SetPropertyClass clazz, StringBuilder code) {
        for (SetPropertyClass child : clazz.getChildren()) {
            generateSetPropertyForMethod(child, code);
        }
        code.append(clazz.generateSetPropertyForMethod())
            .append(System.lineSeparator())
            .append(System.lineSeparator());
    }

    private static void generateSetPropertyForMethods(Set<SetPropertyClass> baseClasses, StringBuilder code) {
        for (SetPropertyClass clazz : baseClasses) {
            generateSetPropertyForMethod(clazz, code);
        }
    }



    private static void generateCaseStatementForGetPropertyInternal(SetPropertyClass clazz, StringBuilder code) {
        for (SetPropertyClass child : clazz.getChildren()) {
            generateCaseStatementForGetPropertyInternal(child, code);
        }
        if (!clazz.isAbstract()) {
            code.append(clazz.generateInvocationGetForPropertyCaseStatement(4));
        }
    }

    private static void generateCaseStatementsForGetPropertyInternal(Set<SetPropertyClass> baseClasses, StringBuilder code) {
        for (SetPropertyClass clazz : baseClasses) {
            generateCaseStatementForGetPropertyInternal(clazz, code);
        }
    }

    private static void generateGetPropertyForMethod(SetPropertyClass clazz, StringBuilder code) {
        for (SetPropertyClass child : clazz.getChildren()) {
            generateGetPropertyForMethod(child, code);
        }
        code.append(clazz.generateGetPropertyForMethod())
            .append(System.lineSeparator())
            .append(System.lineSeparator());
    }

    private static void generateGetPropertyForMethods(Set<SetPropertyClass> baseClasses, StringBuilder code) {
        for (SetPropertyClass clazz : baseClasses) {
            generateGetPropertyForMethod(clazz, code);
        }
    }

    private static final String AL20_HEADER = "/*\n" +
        " * Licensed to the Apache Software Foundation (ASF) under one or more\n" +
        " * contributor license agreements.  See the NOTICE file distributed with\n" +
        " * this work for additional information regarding copyright ownership.\n" +
        " * The ASF licenses this file to You under the Apache License, Version 2.0\n" +
        " * (the \"License\"); you may not use this file except in compliance with\n" +
        " * the License.  You may obtain a copy of the License at\n" +
        " *\n" +
        " *      http://www.apache.org/licenses/LICENSE-2.0\n" +
        " *\n" +
        " * Unless required by applicable law or agreed to in writing, software\n" +
        " * distributed under the License is distributed on an \"AS IS\" BASIS,\n" +
        " * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.\n" +
        " * See the License for the specific language governing permissions and\n" +
        " * limitations under the License.\n" +
        " */\n";
}
