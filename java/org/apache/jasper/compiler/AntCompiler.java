/*
 * Copyright 1999,2004 The Apache Software Foundation.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.jasper.compiler;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.util.StringTokenizer;

import org.apache.jasper.JasperException;
import org.apache.jasper.util.SystemLogHandler;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.DefaultLogger;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.Javac;
import org.apache.tools.ant.types.Path;
import org.apache.tools.ant.types.PatternSet;

/**
 * Main JSP compiler class. This class uses Ant for compiling.
 *
 * @author Anil K. Vijendran
 * @author Mandar Raje
 * @author Pierre Delisle
 * @author Kin-man Chung
 * @author Remy Maucherat
 * @author Mark Roth
 */
public class AntCompiler extends Compiler {

    static {
        System.setErr(new SystemLogHandler(System.err));
    }

    // ----------------------------------------------------- Instance Variables

    protected Project project=null;
    protected JasperAntLogger logger;

    // ------------------------------------------------------------ Constructor

    // Lazy eval - if we don't need to compile we probably don't need the project
    protected Project getProject() {
        
        if( project!=null ) return project;
        
        // Initializing project
        project = new Project();
        logger = new JasperAntLogger();
        logger.setOutputPrintStream(System.out);
        logger.setErrorPrintStream(System.err);
        logger.setMessageOutputLevel(Project.MSG_INFO);
        project.addBuildListener( logger);
        if (System.getProperty("catalina.home") != null) {
            project.setBasedir( System.getProperty("catalina.home"));
        }
        
        if( options.getCompiler() != null ) {
            if( log.isDebugEnabled() )
                log.debug("Compiler " + options.getCompiler() );
            project.setProperty("build.compiler", options.getCompiler() );
        }
        project.init();
        return project;
    }
    
    class JasperAntLogger extends DefaultLogger {
        
        protected StringBuffer reportBuf = new StringBuffer();
        
        protected void printMessage(final String message,
                final PrintStream stream,
                final int priority) {
        }
        
        protected void log(String message) {
            reportBuf.append(message);
            reportBuf.append(System.getProperty("line.separator"));
        }
        
        protected String getReport() {
            String report = reportBuf.toString();
            reportBuf.setLength(0);
            return report;
        }
    }
    
    // --------------------------------------------------------- Public Methods


    /** 
     * Compile the servlet from .java file to .class file
     */
    protected void generateClass(String[] smap)
        throws FileNotFoundException, JasperException, Exception {
        
        long t1 = 0;
        if (log.isDebugEnabled()) {
            t1 = System.currentTimeMillis();
        }

        String javaEncoding = ctxt.getOptions().getJavaEncoding();
        String javaFileName = ctxt.getServletJavaFileName();
        String classpath = ctxt.getClassPath(); 
        
        String sep = System.getProperty("path.separator");
        
        StringBuffer errorReport = new StringBuffer();
        
        StringBuffer info=new StringBuffer();
        info.append("Compile: javaFileName=" + javaFileName + "\n" );
        info.append("    classpath=" + classpath + "\n" );
        
        // Start capturing the System.err output for this thread
        SystemLogHandler.setThread();
        
        // Initializing javac task
        getProject();
        Javac javac = (Javac) project.createTask("javac");
        
        // Initializing classpath
        Path path = new Path(project);
        path.setPath(System.getProperty("java.class.path"));
        info.append("    cp=" + System.getProperty("java.class.path") + "\n");
        StringTokenizer tokenizer = new StringTokenizer(classpath, sep);
        while (tokenizer.hasMoreElements()) {
            String pathElement = tokenizer.nextToken();
            File repository = new File(pathElement);
            path.setLocation(repository);
            info.append("    cp=" + repository + "\n");
        }
        
        if( log.isDebugEnabled() )
            log.debug( "Using classpath: " + System.getProperty("java.class.path") + sep
                    + classpath);
        
        // Initializing sourcepath
        Path srcPath = new Path(project);
        srcPath.setLocation(options.getScratchDir());
        
        info.append("    work dir=" + options.getScratchDir() + "\n");
        
        // Initialize and set java extensions
        String exts = System.getProperty("java.ext.dirs");
        if (exts != null) {
            Path extdirs = new Path(project);
            extdirs.setPath(exts);
            javac.setExtdirs(extdirs);
            info.append("    extension dir=" + exts + "\n");
        }

        // Add endorsed directories if any are specified and we're forking
        // See Bugzilla 31257
        if(ctxt.getOptions().getFork()) {
            String endorsed = System.getProperty("java.endorsed.dirs");
            if(endorsed != null) {
                Javac.ImplementationSpecificArgument endorsedArg = 
                    javac.createCompilerArg();
                endorsedArg.setLine("-J-Djava.endorsed.dirs="+endorsed);
                info.append("    endorsed dir=" + endorsed + "\n");
            } else {
                info.append("    no endorsed dirs specified\n");
            }
        }
        
        // Configure the compiler object
        javac.setEncoding(javaEncoding);
        javac.setClasspath(path);
        javac.setDebug(ctxt.getOptions().getClassDebugInfo());
        javac.setSrcdir(srcPath);
        javac.setTempdir(options.getScratchDir());
        javac.setOptimize(! ctxt.getOptions().getClassDebugInfo() );
        javac.setFork(ctxt.getOptions().getFork());
        info.append("    srcDir=" + srcPath + "\n" );
        
        // Set the Java compiler to use
        if (options.getCompiler() != null) {
            javac.setCompiler(options.getCompiler());
            info.append("    compiler=" + options.getCompiler() + "\n");
        }

        if (options.getCompilerTargetVM() != null) {
            javac.setTarget(options.getCompilerTargetVM());
            info.append("   compilerTargetVM=" + options.getCompilerTargetVM() + "\n");
        }

        if (options.getCompilerSourceVM() != null) {
            javac.setSource(options.getCompilerSourceVM());
            info.append("   compilerSourceVM=" + options.getCompilerSourceVM() + "\n");
        }
        
        // Build includes path
        PatternSet.NameEntry includes = javac.createInclude();
        
        includes.setName(ctxt.getJavaPath());
        info.append("    include="+ ctxt.getJavaPath() + "\n" );
        
        BuildException be = null;
        
        try {
            if (ctxt.getOptions().getFork()) {
                javac.execute();
            } else {
                synchronized(javacLock) {
                    javac.execute();
                }
            }
        } catch (BuildException e) {
            be = e;
            log.error( "Javac exception ", e);
            log.error( "Env: " + info.toString());
        }
        
        errorReport.append(logger.getReport());

        // Stop capturing the System.err output for this thread
        String errorCapture = SystemLogHandler.unsetThread();
        if (errorCapture != null) {
            errorReport.append(System.getProperty("line.separator"));
            errorReport.append(errorCapture);
        }

        if (!ctxt.keepGenerated()) {
            File javaFile = new File(javaFileName);
            javaFile.delete();
        }
        
        if (be != null) {
            String errorReportString = errorReport.toString();
            log.error("Error compiling file: " + javaFileName + " "
                    + errorReportString);
            JavacErrorDetail[] javacErrors = ErrorDispatcher.parseJavacErrors(
                    errorReportString, javaFileName, pageNodes);
            if (javacErrors != null) {
                errDispatcher.javacError(javacErrors);
            } else {
                errDispatcher.javacError(errorReportString, be);
            }
        }
        
        if( log.isDebugEnabled() ) {
            long t2=System.currentTimeMillis();
            log.debug("Compiled " + ctxt.getServletJavaFileName() + " "
                      + (t2-t1) + "ms");
        }
        
        logger = null;
        project = null;
        
        if (ctxt.isPrototypeMode()) {
            return;
        }
        
        // JSR45 Support
        if (! options.isSmapSuppressed()) {
            SmapUtil.installSmap(smap);
        }
    }

    
}
