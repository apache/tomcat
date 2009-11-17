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

package org.apache.juli;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

/**
 * Provides same information as default log format but on a single line to make
 * it easier to grep the logs. The only exception is stacktraces which are
 * always preceded by whitespace to make it simple to skip them.
 */
/*
 * Date processing based on AccessLogValve.
 */
public class OneLineFormatter extends Formatter {

    /**
     * The set of month abbreviations for log messages.
     */
    private static final String months[] = {"Jan", "Feb", "Mar", "Apr", "May",
        "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec" };

    private static final String LINE_SEP = System.getProperty("line.separator");
    private static final String ST_SEP = LINE_SEP + " ";

    private final SimpleDateFormat dayFormatter = new SimpleDateFormat("dd");
    private final SimpleDateFormat monthFormatter = new SimpleDateFormat("MM");
    private final SimpleDateFormat yearFormatter = new SimpleDateFormat("yyyy");
    private final SimpleDateFormat timeFormatter =
        new SimpleDateFormat("HH:mm:ss.S");
    
    private Date currentDate;
    private String currentDateString;

    @Override
    public String format(LogRecord record) {
        StringBuilder sb = new StringBuilder();
        
        // Timestamp
        addTimestamp(sb, new Date(record.getMillis()));
        
        // Severity
        sb.append(' ');
        sb.append(record.getLevel());
        
        // Source
        sb.append(' ');
        sb.append(record.getSourceClassName());
        sb.append('.');
        sb.append(record.getSourceMethodName());
        
        // Message
        sb.append(' ');
        sb.append(record.getMessage());
        
        // Stack trace
        if (record.getThrown() != null) {
            sb.append(ST_SEP);
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            record.getThrown().printStackTrace(pw);
            pw.close();
            sb.append(sw.getBuffer());
        }

        // New line for next record
        sb.append(LINE_SEP);

        return sb.toString();
    }

    public void addTimestamp(StringBuilder buf, Date date) {
        if (currentDate != date) {
            synchronized (this) {
                if (currentDate != date) {
                    StringBuilder current = new StringBuilder(32);
                    current.append(dayFormatter.format(date)); // Day
                    current.append('-');
                    current.append(lookup(monthFormatter.format(date))); // Month
                    current.append('-');
                    current.append(yearFormatter.format(date)); // Year
                    current.append(' ');
                    current.append(timeFormatter.format(date)); // Time
                    currentDateString = current.toString();
                    currentDate = date;
                }
            }
        }
        buf.append(currentDateString);
    }

    /**
     * Return the month abbreviation for the specified month, which must
     * be a two-digit String.
     *
     * @param month Month number ("01" .. "12").
     */
    private String lookup(String month) {
        int index;
        try {
            index = Integer.parseInt(month) - 1;
        } catch (Throwable t) {
            index = 0;  // Can not happen, in theory
        }
        return (months[index]);
    }
}
