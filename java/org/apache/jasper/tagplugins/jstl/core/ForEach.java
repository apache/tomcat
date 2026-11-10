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
package org.apache.jasper.tagplugins.jstl.core;

import org.apache.jasper.compiler.tagplugin.TagPlugin;
import org.apache.jasper.compiler.tagplugin.TagPluginContext;

/**
 * Tag plugin implementation for the JSTL &lt;c:forEach&gt; tag.
 */
public final class ForEach implements TagPlugin {
    /**
     * Creates a new ForEach tag plugin instance.
     */
    public ForEach() {
        // Default constructor
    }

    @Override
    public void doTag(TagPluginContext ctxt) {

        String index;

        boolean hasVarStatus = ctxt.isAttributeSpecified("varStatus");
        if (hasVarStatus) {
            ctxt.dontUseTagPlugin();
            return;
        }

        // Local variables, not instance fields: a single plugin instance is
        // shared by all translations of the web application, which may run
        // concurrently.
        boolean hasVar = ctxt.isAttributeSpecified("var");
        boolean hasBegin = ctxt.isAttributeSpecified("begin");
        boolean hasEnd = ctxt.isAttributeSpecified("end");
        boolean hasStep = ctxt.isAttributeSpecified("step");

        boolean hasItems = ctxt.isAttributeSpecified("items");
        if (hasItems) {
            doCollection(ctxt, hasVar, hasBegin, hasEnd, hasStep);
            return;
        }

        // We must have begin and end attributes. Evaluate the loop control
        // attributes once and validate them, as the setters of the reference
        // implementation LoopTagSupport do.
        index = ctxt.getTemporaryVariableName();

        String beginName = ctxt.getTemporaryVariableName();
        ctxt.generateJavaSource("int " + beginName + " = ");
        if (hasBegin) {
            ctxt.generateAttribute("begin");
        } else {
            ctxt.generateJavaSource("0");
        }
        ctxt.generateJavaSource(";");
        if (hasBegin) {
            LoopAttributeChecks.generateBeginCheck(ctxt, beginName);
        }

        String endName = ctxt.getTemporaryVariableName();
        ctxt.generateJavaSource("int " + endName + " = ");
        if (hasEnd) {
            ctxt.generateAttribute("end");
        } else {
            // Same default as the reference implementation, which results in
            // zero iterations when end is not specified.
            ctxt.generateJavaSource("-1");
        }
        ctxt.generateJavaSource(";");
        if (hasEnd) {
            LoopAttributeChecks.generateEndCheck(ctxt, endName);
        }

        String stepName = null;
        if (hasStep) {
            stepName = ctxt.getTemporaryVariableName();
            ctxt.generateJavaSource("int " + stepName + " = ");
            ctxt.generateAttribute("step");
            ctxt.generateJavaSource(";");
            LoopAttributeChecks.generateStepCheck(ctxt, stepName);
        }

        if (hasVar) {
            ctxt.generateJavaSource("try {");
        }
        ctxt.generateJavaSource("for (int " + index + " = " + beginName + "; " + index + " <= " + endName + ";");
        if (hasStep) {
            ctxt.generateJavaSource(" " + index + " += " + stepName + ") {");
        } else {
            ctxt.generateJavaSource(" " + index + "++) {");
        }

        // If var is specified, sync the var attribute
        if (hasVar /* && ctxt.hasEL() */) {
            ctxt.generateJavaSource("_jspx_page_context.setAttribute(");
            ctxt.generateAttribute("var");
            // The reference implementation exposes the index as an Integer.
            ctxt.generateJavaSource(", Integer.valueOf(" + index + "));");
        }
        ctxt.generateBody();
        ctxt.generateJavaSource("}");
        if (hasVar) {
            generateRemoveVar(ctxt);
        }
    }

    /**
     * Generate codes for Collections.
     */
    private void doCollection(TagPluginContext ctxt, boolean hasVar,
            boolean hasBegin, boolean hasEnd, boolean hasStep) {

        ctxt.generateImport("java.util.*");
        generateIterators(ctxt);

        String itemsV = ctxt.getTemporaryVariableName();
        ctxt.generateJavaSource("Object " + itemsV + "= ");
        ctxt.generateAttribute("items");
        ctxt.generateJavaSource(";");

        String indexV = null, beginV = null, endV = null, stepV = null;
        if (hasBegin) {
            beginV = ctxt.getTemporaryVariableName();
            ctxt.generateJavaSource("int " + beginV + " = ");
            ctxt.generateAttribute("begin");
            ctxt.generateJavaSource(";");
            LoopAttributeChecks.generateBeginCheck(ctxt, beginV);
        }
        if (hasEnd) {
            indexV = ctxt.getTemporaryVariableName();
            ctxt.generateJavaSource("int " + indexV + " = 0;");
            endV = ctxt.getTemporaryVariableName();
            ctxt.generateJavaSource("int " + endV + " = ");
            ctxt.generateAttribute("end");
            ctxt.generateJavaSource(";");
            LoopAttributeChecks.generateEndCheck(ctxt, endV);
        }
        if (hasStep) {
            stepV = ctxt.getTemporaryVariableName();
            ctxt.generateJavaSource("int " + stepV + " = ");
            ctxt.generateAttribute("step");
            ctxt.generateJavaSource(";");
            LoopAttributeChecks.generateStepCheck(ctxt, stepV);
        }

        if (hasVar) {
            ctxt.generateJavaSource("try {");
        }
        // An empty range must not execute the body or consume any items.
        if (hasBegin && hasEnd) {
            ctxt.generateJavaSource("if (" + beginV + " <= " + endV + ") {");
        }

        String iterV = ctxt.getTemporaryVariableName();
        ctxt.generateJavaSource("Iterator " + iterV + " = null;");
        // Object[]
        ctxt.generateJavaSource("if (" + itemsV + " instanceof Object[])");
        ctxt.generateJavaSource(iterV + "=toIterator((Object[])" + itemsV + ");");
        // boolean[]
        ctxt.generateJavaSource("else if (" + itemsV + " instanceof boolean[])");
        ctxt.generateJavaSource(iterV + "=toIterator((boolean[])" + itemsV + ");");
        // byte[]
        ctxt.generateJavaSource("else if (" + itemsV + " instanceof byte[])");
        ctxt.generateJavaSource(iterV + "=toIterator((byte[])" + itemsV + ");");
        // char[]
        ctxt.generateJavaSource("else if (" + itemsV + " instanceof char[])");
        ctxt.generateJavaSource(iterV + "=toIterator((char[])" + itemsV + ");");
        // short[]
        ctxt.generateJavaSource("else if (" + itemsV + " instanceof short[])");
        ctxt.generateJavaSource(iterV + "=toIterator((short[])" + itemsV + ");");
        // int[]
        ctxt.generateJavaSource("else if (" + itemsV + " instanceof int[])");
        ctxt.generateJavaSource(iterV + "=toIterator((int[])" + itemsV + ");");
        // long[]
        ctxt.generateJavaSource("else if (" + itemsV + " instanceof long[])");
        ctxt.generateJavaSource(iterV + "=toIterator((long[])" + itemsV + ");");
        // float[]
        ctxt.generateJavaSource("else if (" + itemsV + " instanceof float[])");
        ctxt.generateJavaSource(iterV + "=toIterator((float[])" + itemsV + ");");
        // double[]
        ctxt.generateJavaSource("else if (" + itemsV + " instanceof double[])");
        ctxt.generateJavaSource(iterV + "=toIterator((double[])" + itemsV + ");");

        // Collection
        ctxt.generateJavaSource("else if (" + itemsV + " instanceof Collection)");
        ctxt.generateJavaSource(iterV + "=((Collection)" + itemsV + ").iterator();");

        // Iterator
        ctxt.generateJavaSource("else if (" + itemsV + " instanceof Iterator)");
        ctxt.generateJavaSource(iterV + "=(Iterator)" + itemsV + ";");

        // Enumeration
        ctxt.generateJavaSource("else if (" + itemsV + " instanceof Enumeration)");
        ctxt.generateJavaSource(iterV + "=toIterator((Enumeration)" + itemsV + ");");

        // Map
        ctxt.generateJavaSource("else if (" + itemsV + " instanceof Map)");
        ctxt.generateJavaSource(iterV + "=((Map)" + itemsV + ").entrySet().iterator();");

        // String
        ctxt.generateJavaSource("else if (" + itemsV + " instanceof String)");
        ctxt.generateJavaSource(iterV + "=toIterator(new StringTokenizer((String)" + itemsV + ", \",\"));");

        // Not null
        ctxt.generateJavaSource("if (" + iterV + " != null) {");

        if (hasBegin) {
            String tV = ctxt.getTemporaryVariableName();
            ctxt.generateJavaSource(
                    "for (int " + tV + "=" + beginV + ";" + tV + ">0 && " + iterV + ".hasNext(); " + tV + "--)");
            ctxt.generateJavaSource(iterV + ".next();");
        }

        ctxt.generateJavaSource("while (" + iterV + ".hasNext()){");
        if (hasVar) {
            ctxt.generateJavaSource("_jspx_page_context.setAttribute(");
            ctxt.generateAttribute("var");
            ctxt.generateJavaSource(", " + iterV + ".next());");
        }

        ctxt.generateBody();

        if (hasStep) {
            String tV = ctxt.getTemporaryVariableName();
            ctxt.generateJavaSource(
                    "for (int " + tV + "=" + stepV + "-1;" + tV + ">0 && " + iterV + ".hasNext(); " + tV + "--)");
            ctxt.generateJavaSource(iterV + ".next();");
        }
        if (hasEnd) {
            if (hasStep) {
                ctxt.generateJavaSource(indexV + "+=" + stepV + ";");
            } else {
                ctxt.generateJavaSource(indexV + "++;");
            }
            if (hasBegin) {
                ctxt.generateJavaSource("if(" + beginV + "+" + indexV + ">" + endV + ")");
            } else {
                ctxt.generateJavaSource("if(" + indexV + ">" + endV + ")");
            }
            ctxt.generateJavaSource("break;");
        }
        ctxt.generateJavaSource("}"); // while
        ctxt.generateJavaSource("}"); // Not Null
        if (hasBegin && hasEnd) {
            ctxt.generateJavaSource("}"); // Non-empty range
        }
        if (hasVar) {
            generateRemoveVar(ctxt);
        }
    }


    /**
     * Close the {@code try} block that surrounds the loop and generate the code that removes the loop variable. The
     * variable has nested visibility so, as with the standard tag handler, it is removed from the page scope when the
     * loop ends, including when the body throws an exception.
     */
    private void generateRemoveVar(TagPluginContext ctxt) {
        ctxt.generateJavaSource("} finally {");
        ctxt.generateJavaSource("_jspx_page_context.removeAttribute(");
        ctxt.generateAttribute("var");
        ctxt.generateJavaSource(", jakarta.servlet.jsp.PageContext.PAGE_SCOPE);");
        ctxt.generateJavaSource("}");
    }

    /**
     * Generate iterators for data types supported in items
     */
    private void generateIterators(TagPluginContext ctxt) {
        //@formatter:off
        // Object[]
        ctxt.generateDeclaration("ObjectArrayIterator",
                "private Iterator toIterator(final Object[] a){\n" +
                "  return (new Iterator() {\n" +
                "    int index=0;\n" +
                "    public boolean hasNext() {\n" +
                "      return index < a.length;}\n" +
                "    public Object next() {\n" +
                "      return a[index++];}\n" +
                "    public void remove() {}\n" +
                "  });\n" +
                "}"
        );

        // boolean[]
        ctxt.generateDeclaration("booleanArrayIterator",
                "private Iterator toIterator(final boolean[] a){\n" +
                "  return (new Iterator() {\n" +
                "    int index=0;\n" +
                "    public boolean hasNext() {\n" +
                "      return index < a.length;}\n" +
                "    public Object next() {\n" +
                "      return Boolean.valueOf(a[index++]);}\n" +
                "    public void remove() {}\n" +
                "  });\n" +
                "}"
        );

        // byte[]
        ctxt.generateDeclaration("byteArrayIterator",
                "private Iterator toIterator(final byte[] a){\n" +
                "  return (new Iterator() {\n" +
                "    int index=0;\n" +
                "    public boolean hasNext() {\n" +
                "      return index < a.length;}\n" +
                "    public Object next() {\n" +
                "      return Byte.valueOf(a[index++]);}\n" +
                "    public void remove() {}\n" +
                "  });\n" +
                "}"
        );

        // char[]
        ctxt.generateDeclaration("charArrayIterator",
                "private Iterator toIterator(final char[] a){\n" +
                "  return (new Iterator() {\n" +
                "    int index=0;\n" +
                "    public boolean hasNext() {\n" +
                "      return index < a.length;}\n" +
                "    public Object next() {\n" +
                "      return Character.valueOf(a[index++]);}\n" +
                "    public void remove() {}\n" +
                "  });\n" +
                "}"
        );

        // short[]
        ctxt.generateDeclaration("shortArrayIterator",
                "private Iterator toIterator(final short[] a){\n" +
                "  return (new Iterator() {\n" +
                "    int index=0;\n" +
                "    public boolean hasNext() {\n" +
                "      return index < a.length;}\n" +
                "    public Object next() {\n" +
                "      return Short.valueOf(a[index++]);}\n" +
                "    public void remove() {}\n" +
                "  });\n" +
                "}"
        );

        // int[]
        ctxt.generateDeclaration("intArrayIterator",
                "private Iterator toIterator(final int[] a){\n" +
                "  return (new Iterator() {\n" +
                "    int index=0;\n" +
                "    public boolean hasNext() {\n" +
                "      return index < a.length;}\n" +
                "    public Object next() {\n" +
                "      return Integer.valueOf(a[index++]);}\n" +
                "    public void remove() {}\n" +
                "  });\n" +
                "}"
        );

        // long[]
        ctxt.generateDeclaration("longArrayIterator",
                "private Iterator toIterator(final long[] a){\n" +
                "  return (new Iterator() {\n" +
                "    int index=0;\n" +
                "    public boolean hasNext() {\n" +
                "      return index < a.length;}\n" +
                "    public Object next() {\n" +
                "      return Long.valueOf(a[index++]);}\n" +
                "    public void remove() {}\n" +
                "  });\n" +
                "}"
        );

        // float[]
        ctxt.generateDeclaration("floatArrayIterator",
                "private Iterator toIterator(final float[] a){\n" +
                "  return (new Iterator() {\n" +
                "    int index=0;\n" +
                "    public boolean hasNext() {\n" +
                "      return index < a.length;}\n" +
                "    public Object next() {\n" +
                "      return Float.valueOf(a[index++]);}\n" +
                "    public void remove() {}\n" +
                "  });\n" +
                "}"
        );

        // double[]
        ctxt.generateDeclaration("doubleArrayIterator",
                "private Iterator toIterator(final double[] a){\n" +
                "  return (new Iterator() {\n" +
                "    int index=0;\n" +
                "    public boolean hasNext() {\n" +
                "      return index < a.length;}\n" +
                "    public Object next() {\n" +
                "      return Double.valueOf(a[index++]);}\n" +
                "    public void remove() {}\n" +
                "  });\n" +
                "}"
        );

        // Enumeration
        ctxt.generateDeclaration("enumIterator",
                "private Iterator toIterator(final Enumeration e){\n" +
                "  return (new Iterator() {\n" +
                "    public boolean hasNext() {\n" +
                "      return e.hasMoreElements();}\n" +
                "    public Object next() {\n" +
                "      return e.nextElement();}\n" +
                "    public void remove() {}\n" +
                "  });\n" +
                "}"
        );
        //@formatter:on
    }
}
