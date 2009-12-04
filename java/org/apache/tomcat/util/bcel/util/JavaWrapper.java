/*
 * Copyright  2000-2009 The Apache Software Foundation
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); 
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License. 
 *
 */
package org.apache.tomcat.util.bcel.util;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Java interpreter replacement, i.e., wrapper that uses its own ClassLoader
 * to modify/generate classes as they're requested. You can take this as a template
 * for your own applications.<br>
 * Call this wrapper with
 * <pre>java org.apache.tomcat.util.bcel.util.JavaWrapper &lt;real.class.name&gt; [arguments]</pre>
 * <p>
 * To use your own class loader you can set the "bcel.classloader" system property
 * which defaults to "org.apache.tomcat.util.bcel.util.ClassLoader", e.g., with
 * <pre>java org.apache.tomcat.util.bcel.util.JavaWrapper -Dbcel.classloader=foo.MyLoader &lt;real.class.name&gt; [arguments]</pre>
 * </p>
 *
 * @version $Id$
 * @author  <A HREF="mailto:m.dahm@gmx.de">M. Dahm</A>
 * @see ClassLoader
 */
public class JavaWrapper {

    private java.lang.ClassLoader loader;


    private static java.lang.ClassLoader getClassLoader() {
        String s = System.getProperty("bcel.classloader");
        if ((s == null) || "".equals(s)) {
            s = "org.apache.tomcat.util.bcel.util.ClassLoader";
        }
        try {
            return (java.lang.ClassLoader) Class.forName(s).newInstance();
        } catch (Exception e) {
            throw new RuntimeException(e.toString(), e);
        }
    }


    public JavaWrapper(java.lang.ClassLoader loader) {
        this.loader = loader;
    }


    public JavaWrapper() {
        this(getClassLoader());
    }


    /** Runs the main method of the given class with the arguments passed in argv
     *
     * @param class_name the fully qualified class name
     * @param argv the arguments just as you would pass them directly
     */
    public void runMain( String class_name, String[] argv ) throws ClassNotFoundException {
        Class cl = loader.loadClass(class_name);
        Method method = null;
        try {
            method = cl.getMethod("main", new Class[] {
                argv.getClass()
            });
            /* Method main is sane ?
             */
            int m = method.getModifiers();
            Class r = method.getReturnType();
            if (!(Modifier.isPublic(m) && Modifier.isStatic(m)) || Modifier.isAbstract(m)
                    || (r != Void.TYPE)) {
                throw new NoSuchMethodException();
            }
        } catch (NoSuchMethodException no) {
            System.out.println("In class " + class_name
                    + ": public static void main(String[] argv) is not defined");
            return;
        }
        try {
            method.invoke(null, new Object[] {
                argv
            });
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }


    /** Default main method used as wrapper, expects the fully qualified class name
     * of the real class as the first argument.
     */
    public static void main( String[] argv ) throws Exception {
        /* Expects class name as first argument, other arguments are by-passed.
         */
        if (argv.length == 0) {
            System.out.println("Missing class name.");
            return;
        }
        String class_name = argv[0];
        String[] new_argv = new String[argv.length - 1];
        System.arraycopy(argv, 1, new_argv, 0, new_argv.length);
        JavaWrapper wrapper = new JavaWrapper();
        wrapper.runMain(class_name, new_argv);
    }
}
