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

package org.apache.catalina.util;

import java.util.Date;

import java.text.DateFormat;
import java.text.FieldPosition;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;

/**
 * Fast date formatter that caches recently formatted date information
 * and uses it to avoid too-frequent calls to the underlying
 * formatter.  Note: breaks fieldPosition param of format(Date,
 * StringBuffer, FieldPosition).  If you care about the field
 * position, call the underlying DateFormat directly.
 *
 * @author Stan Bailes
 * @author Alex Chaffee
 **/
public class FastDateFormat extends DateFormat {
    DateFormat    df;
    long          lastSec = -1;
    StringBuffer  sb      = new StringBuffer();
    FieldPosition fp      = new FieldPosition(DateFormat.MILLISECOND_FIELD);

    public FastDateFormat(DateFormat df) {
        this.df = df;
    }

    public Date parse(String text, ParsePosition pos) {
        return df.parse(text, pos);
    }

    /**
     * Note: breaks functionality of fieldPosition param. Also:
     * there's a bug in SimpleDateFormat with "S" and "SS", use "SSS"
     * instead if you want a msec field.
     **/
    public StringBuffer format(Date date, StringBuffer toAppendTo,
                               FieldPosition fieldPosition) {
        long dt = date.getTime();
        long ds = dt / 1000;
        if (ds != lastSec) {
            sb.setLength(0);
            df.format(date, sb, fp);
            lastSec = ds;
        } else {
            // munge current msec into existing string
            int ms = (int)(dt % 1000);
            int pos = fp.getEndIndex();
            int begin = fp.getBeginIndex();
            if (pos > 0) {
                if (pos > begin)
                    sb.setCharAt(--pos, Character.forDigit(ms % 10, 10));
                ms /= 10;
                if (pos > begin)
                    sb.setCharAt(--pos, Character.forDigit(ms % 10, 10));
                ms /= 10;
                if (pos > begin)
                    sb.setCharAt(--pos, Character.forDigit(ms % 10, 10));
            }
        }
        toAppendTo.append(sb.toString());
        return toAppendTo;
    }

    public static void main(String[] args) {
        String format = "yyyy-MM-dd HH:mm:ss.SSS";
        if (args.length > 0)
            format = args[0];
        SimpleDateFormat sdf = new SimpleDateFormat(format);
        FastDateFormat fdf = new FastDateFormat(sdf);
        Date d = new Date();

        d.setTime(1); System.out.println(fdf.format(d) + "\t" + sdf.format(d));
        d.setTime(20); System.out.println(fdf.format(d) + "\t" + sdf.format(d));
        d.setTime(500); System.out.println(fdf.format(d) + "\t" + sdf.format(d));
        d.setTime(543); System.out.println(fdf.format(d) + "\t" + sdf.format(d));
        d.setTime(999); System.out.println(fdf.format(d) + "\t" + sdf.format(d));
        d.setTime(1050); System.out.println(fdf.format(d) + "\t" + sdf.format(d));
        d.setTime(2543); System.out.println(fdf.format(d) + "\t" + sdf.format(d));
        d.setTime(12345); System.out.println(fdf.format(d) + "\t" + sdf.format(d));
        d.setTime(12340); System.out.println(fdf.format(d) + "\t" + sdf.format(d));

        final int reps = 100000;
        {
            long start = System.currentTimeMillis();
            for (int i = 0; i < reps; i++) {
                d.setTime(System.currentTimeMillis());
                fdf.format(d);
            }
            long elap = System.currentTimeMillis() - start;
            System.out.println("fast: " + elap + " elapsed");
            System.out.println(fdf.format(d));
        }
        {
            long start = System.currentTimeMillis();
            for (int i = 0; i < reps; i++) {
                d.setTime(System.currentTimeMillis());
                sdf.format(d);
            }
            long elap = System.currentTimeMillis() - start;
            System.out.println("slow: " + elap + " elapsed");
            System.out.println(sdf.format(d));
        }
    }
}
