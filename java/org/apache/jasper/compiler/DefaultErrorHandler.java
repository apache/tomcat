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

package org.apache.jasper.compiler;

import org.apache.jasper.JasperException;

/**
 * Default implementation of ErrorHandler interface.
 *
 * @author Jan Luehe
 */
class DefaultErrorHandler implements ErrorHandler {
    
    /*
     * Processes the given JSP parse error.
     *
     * @param fname Name of the JSP file in which the parse error occurred
     * @param line Parse error line number
     * @param column Parse error column number
     * @param errMsg Parse error message
     * @param exception Parse exception
     */
    public void jspError(String fname, int line, int column, String errMsg,
            Exception ex) throws JasperException {
        throw new JasperException(fname + "(" + line + "," + column + ")"
                + " " + errMsg, ex);
    }
    
    /*
     * Processes the given JSP parse error.
     *
     * @param errMsg Parse error message
     * @param exception Parse exception
     */
    public void jspError(String errMsg, Exception ex) throws JasperException {
        throw new JasperException(errMsg, ex);
    }
    
    /*
     * Processes the given javac compilation errors.
     *
     * @param details Array of JavacErrorDetail instances corresponding to the
     * compilation errors
     */
    public void javacError(JavacErrorDetail[] details) throws JasperException {
        
        if (details == null) {
            return;
        }
        
        Object[] args = null;
        StringBuffer buf = new StringBuffer();
        
        for (int i=0; i < details.length; i++) {
            if (details[i].getJspBeginLineNumber() >= 0) {
                args = new Object[] {
                        new Integer(details[i].getJspBeginLineNumber()), 
                        details[i].getJspFileName() };
                buf.append("\n\n");
                buf.append(Localizer.getMessage("jsp.error.single.line.number",
                        args));
                buf.append("\n");
                buf.append(details[i].getErrorMessage());
                buf.append("\n");
                buf.append(details[i].getJspExtract());
            } else {
                args = new Object[] {
                        new Integer(details[i].getJavaLineNumber()) };
                buf.append("\n\n");
                buf.append(Localizer.getMessage("jsp.error.java.line.number",
                        args));
                buf.append("\n");
                buf.append(details[i].getErrorMessage());
            }
        }
        buf.append("\n\nStacktrace:");
        throw new JasperException(
                Localizer.getMessage("jsp.error.unable.compile") + ": " + buf);
    }
    
    /**
     * Processes the given javac error report and exception.
     *
     * @param errorReport Compilation error report
     * @param exception Compilation exception
     */
    public void javacError(String errorReport, Exception exception)
    throws JasperException {
        
        throw new JasperException(
                Localizer.getMessage("jsp.error.unable.compile"), exception);
    }
    
}
