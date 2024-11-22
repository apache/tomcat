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

    @Override
    public void jspError(String fname, int line, int column, String errMsg, Exception ex) throws JasperException {
        throw new JasperException(fname + " (" +
                Localizer.getMessage("jsp.error.location", Integer.toString(line), Integer.toString(column)) + ") " +
                errMsg, ex);
    }

    @Override
    public void jspError(String errMsg, Exception ex) throws JasperException {
        throw new JasperException(errMsg, ex);
    }

    @Override
    public void javacError(JavacErrorDetail[] details) throws JasperException {

        if (details == null) {
            return;
        }

        Object[] args = null;
        StringBuilder buf = new StringBuilder();

        for (JavacErrorDetail detail : details) {
            if (detail.getJspBeginLineNumber() >= 0) {
                args = new Object[] { Integer.valueOf(detail.getJspBeginLineNumber()), detail.getJspFileName() };
                buf.append(System.lineSeparator());
                buf.append(System.lineSeparator());
                buf.append(Localizer.getMessage("jsp.error.single.line.number", args));
                buf.append(System.lineSeparator());
                buf.append(detail.getErrorMessage());
                buf.append(System.lineSeparator());
                buf.append(detail.getJspExtract());
            } else {
                args = new Object[] { Integer.valueOf(detail.getJavaLineNumber()), detail.getJavaFileName() };
                buf.append(System.lineSeparator());
                buf.append(System.lineSeparator());
                buf.append(Localizer.getMessage("jsp.error.java.line.number", args));
                buf.append(System.lineSeparator());
                buf.append(detail.getErrorMessage());
            }
        }
        buf.append(System.lineSeparator());
        buf.append(System.lineSeparator());
        buf.append("Stacktrace:");
        throw new JasperException(Localizer.getMessage("jsp.error.unable.compile") + ": " + buf);
    }

    @Override
    public void javacError(String errorReport, Exception exception) throws JasperException {

        throw new JasperException(Localizer.getMessage("jsp.error.unable.compile"), exception);
    }

}
