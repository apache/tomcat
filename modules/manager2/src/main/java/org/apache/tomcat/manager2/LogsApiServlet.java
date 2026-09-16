/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.tomcat.manager2;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.Serial;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.catalina.ContainerServlet;
import org.apache.catalina.Context;
import org.apache.catalina.Host;
import org.apache.catalina.Wrapper;
import org.apache.catalina.Container;
import org.apache.catalina.Valve;
import org.apache.catalina.valves.AbstractAccessLogValve;
import org.apache.tomcat.util.res.StringManager;


/**
 * The Manager2 log API. Serves the list of server log files (JULI) and of access log files, a filtered tail of one
 * file, and the full raw file for download. Only the most recent part of a file is read for the tail (everything older
 * is discarded), and the records of a response are ordered from most recent to least recent.
 * <p>
 * Both log families are handled in their plain text <em>and</em> their JSON format (the JSON format is used when a log
 * handler or the access log valve is configured with the JSON formatter/valve). For the access log the pattern based
 * format is parsed with the pattern of the configured {@code AccessLogValve} (falling back to the common and combined
 * patterns), so the available fields - and therefore the available filters - depend on the configured pattern.
 * <p>
 * Like the rest of the API that is not part of the read-only status endpoints, this API requires the
 * {@code manager-gui} role.
 */
public class LogsApiServlet extends HttpServlet implements ContainerServlet {


    @Serial
    private static final long serialVersionUID = 1L;


    /**
     * The string manager for this package.
     */
    protected static final StringManager sm = Strings.manager();


    /**
     * The maximum number of records returned by one request.
     */
    private static final int MAX_LINES = 5000;

    /**
     * The default number of records returned by one request.
     */
    private static final int DEFAULT_LINES = 500;

    /**
     * The maximum number of bytes read from the end of one file.
     */
    private static final long READ_CAP = 16L * 1024 * 1024;

    /**
     * The number of bytes read from the start of a file to detect its format.
     */
    private static final int FORMAT_CAP = 64 * 1024;

    /**
     * Server log file names: the JULI log files of the default {@code logging.properties} (including the console
     * output).
     */
    private static final Pattern SERVER_LOG_NAME = Pattern
            .compile("^(catalina|localhost|manager|host-manager)(\\.\\d{4}-\\d{2}-\\d{2})?\\.(log|out|err)$");

    /**
     * Access log file names: the files written by the access log valve ({@code [host]_access_log.date.suffix} by
     * default).
     */
    private static final Pattern ACCESS_LOG_NAME = Pattern.compile(".*access_log.*\\.(txt|log)$");

    /**
     * A file name that may be used in the {@code name} request parameter.
     */
    private static final Pattern SAFE_NAME = Pattern.compile("[A-Za-z0-9._-]+");

    /**
     * The canonical order of the log levels in the level counts.
     */
    private static final String[] LEVEL_ORDER = { "SEVERE", "WARNING", "INFO", "CONFIG", "FINE", "FINER", "FINEST" };


    private transient Wrapper wrapper = null;

    private transient Context context = null;

    private transient Host host = null;


    // ------------------------------------------------ ContainerServlet API


    @Override
    public Wrapper getWrapper() {
        return wrapper;
    }


    @Override
    public void setWrapper(Wrapper wrapper) {
        this.wrapper = wrapper;
        if (wrapper == null) {
            context = null;
            host = null;
        } else {
            context = (Context) wrapper.getParent();
            host = (Host) context.getParent();
        }
    }


    // ------------------------------------------------------------ Request API


    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String path = request.getServletPath();
        String info = request.getPathInfo();
        if (info != null && !info.isEmpty()) {
            path = path + info;
        }

        try {
            if ("/api/logs".equals(path)) {
                list(response, false);
            } else if ("/api/logs/file".equals(path)) {
                read(response, false, request);
            } else if ("/api/logs/download".equals(path)) {
                download(response, request);
            } else if ("/api/access-log".equals(path)) {
                list(response, true);
            } else if ("/api/access-log/file".equals(path)) {
                read(response, true, request);
            } else if ("/api/access-log/download".equals(path)) {
                download(response, request);
            } else {
                Api.notFound(response);
            }
        } catch (Exception e) {
            log(sm.getString("manager2.error.logs"), e);
            throw new ServletException(e);
        }
    }


    private void list(HttpServletResponse response, boolean access) throws IOException {

        File dir = logsDirectory();
        if (dir == null) {
            Api.error(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "LOGS_DIR_MISSING",
                    sm.getString("manager2.logsDirMissing"));
            return;
        }

        List<Map<String, Object>> files = new ArrayList<>();
        File[] children = dir.listFiles();
        if (children != null) {
            for (File file : children) {
                if (!file.isFile()) {
                    continue;
                }
                String name = file.getName();
                if (access) {
                    if (!ACCESS_LOG_NAME.matcher(name).matches()) {
                        continue;
                    }
                } else if (!SERVER_LOG_NAME.matcher(name).matches()) {
                    continue;
                }
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("name", name);
                entry.put("size", Long.valueOf(file.length()));
                entry.put("modified", Long.valueOf(file.lastModified()));
                String format = detectFormat(file);
                entry.put("format", format);
                if (access) {
                    String pattern = findPattern(file, format);
                    entry.put("pattern", pattern);
                    entry.put("fields", findFields(file, format, pattern));
                }
                files.add(entry);
            }
        }
        files.sort((a, b) -> {
            int byModified = ((Number) b.get("modified")).longValue() > ((Number) a.get("modified")).longValue() ? 1
                    : -1;
            if (byModified != 0) {
                return byModified;
            }
            return String.valueOf(a.get("name")).compareTo(String.valueOf(b.get("name")));
        });

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("logs", files);
        Api.json(response, payload);
    }


    private void read(HttpServletResponse response, boolean access, HttpServletRequest request) throws IOException {

        File file = resolveRequestFile(response, request);
        if (file == null) {
            return;
        }

        int lines;
        try {
            lines = Integer.parseInt(request.getParameter("lines"));
        } catch (Exception e) {
            lines = DEFAULT_LINES;
        }
        lines = Math.max(1, Math.min(MAX_LINES, lines));

        byte[] data = readTail(file);
        boolean truncated = file.length() > data.length;
        List<String> rawLines = new ArrayList<>();
        for (String line : new String(data, StandardCharsets.UTF_8).split("\\r?\\n", -1)) {
            if (!line.isEmpty()) {
                rawLines.add(line);
            }
        }

        Map<String, Object> payload;
        if (access) {
            payload = readAccessLog(file, rawLines, lines, truncated, param(request, "method"),
                    param(request, "status"), param(request, "user"), param(request, "session"),
                    param(request, "search"));
        } else {
            payload = readServerLog(file, rawLines, lines, truncated, param(request, "level"),
                    param(request, "search"));
        }
        Api.json(response, payload);
    }


    /**
     * Stream the full, unfiltered raw file so that it can be saved.
     */
    private void download(HttpServletResponse response, HttpServletRequest request) throws IOException {

        File file = resolveRequestFile(response, request);
        if (file == null) {
            return;
        }
        response.setContentType("text/plain; charset=utf-8");
        response.setContentLengthLong(file.length());
        response.setHeader("Content-Disposition", "attachment; filename=\"" + file.getName() + "\"");
        Files.copy(file.toPath(), response.getOutputStream());
        response.flushBuffer();
    }


    // --------------------------------------------------------- Server logs


    private Map<String, Object> readServerLog(File file, List<String> rawLines, int lines, boolean truncated,
            String level, String search) {

        String format = detectFormat(file);
        List<Map<String, Object>> records = new ArrayList<>();
        Map<String, Integer> levels = new LinkedHashMap<>();
        Map<String, Object> previous = null;
        for (String line : rawLines) {
            Map<String, Object> record = "json".equals(format) ? LogParser.parseJsonLogLine(line)
                    : LogParser.parseTextLogLine(line);
            if (record == null) {
                // Continuation line of a stack trace (plain format) or an
                // unparsable line.
                if (previous != null && !previous.containsKey("raw")) {
                    Object throwable = previous.get("throwable");
                    if (throwable instanceof String t) {
                        previous.put("throwable", t + "\n" + line);
                    } else if (throwable == null) {
                        previous.put("throwable", line);
                    }
                    continue;
                }
                if (previous != null && previous.containsKey("raw")) {
                    previous.put("raw", previous.get("raw") + "\n" + line);
                    continue;
                }
                record = new LinkedHashMap<>();
                record.put("raw", line);
                records.add(record);
                previous = record;
                continue;
            }
            String recordLevel = String.valueOf(record.get("level"));
            if (!"null".equals(recordLevel)) {
                levels.merge(recordLevel, 1, Integer::sum);
            }
            records.add(record);
            previous = record;
        }

        for (String levelName : LEVEL_ORDER) {
            Integer count = levels.remove(levelName);
            if (count != null) {
                levels.put(levelName, count);
            }
        }

        List<Map<String, Object>> matched = filter(records, level, search);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("name", file.getName());
        payload.put("format", format);
        payload.put("fields", List.of("time", "level", "thread", "source", "message"));
        payload.put("levels", levels);
        payload.put("total", Long.valueOf(records.size()));
        payload.put("matched", Long.valueOf(matched.size()));
        payload.put("readBytes", Long.valueOf(estimatedBytes(rawLines)));
        payload.put("totalBytes", Long.valueOf(file.length()));
        payload.put("truncated", Boolean.valueOf(truncated));
        payload.put("records", tail(matched, lines));
        return payload;
    }


    // ---------------------------------------------------------- Access logs


    private Map<String, Object> readAccessLog(File file, List<String> rawLines, int lines, boolean truncated,
            String method, String status, String user, String session, String search) {

        String format = detectFormat(file);
        LogParser.AccessParser parser = null;
        String pattern = null;
        if (!"json".equals(format)) {
            pattern = findPattern(file, format);
            if (pattern != null) {
                try {
                    parser = LogParser.compile(pattern);
                } catch (IllegalArgumentException e) {
                    parser = null;
                }
            }
        }

        List<Map<String, Object>> records = new ArrayList<>();
        Map<String, Integer> statusClasses = new LinkedHashMap<>();
        for (String bucket : new String[] { "1xx", "2xx", "3xx", "4xx", "5xx", "other" }) {
            statusClasses.put(bucket, 0);
        }
        Map<String, Integer> methodCounts = new LinkedHashMap<>();
        for (String line : rawLines) {
            Map<String, Object> record = "json".equals(format) ? LogParser.parseJsonAccessLine(line)
                    : (parser != null ? parser.parse(line) : null);
            if (record == null) {
                record = new LinkedHashMap<>();
                record.put("raw", line);
            }
            Object statusCode = record.get("statusCode");
            if (statusCode instanceof Number n) {
                int value = n.intValue();
                if (value >= 100 && value <= 599) {
                    statusClasses.merge((value / 100) + "xx", 1, Integer::sum);
                } else {
                    statusClasses.merge("other", 1, Integer::sum);
                }
            }
            if (record.get("method") instanceof String m) {
                methodCounts.merge(m, 1, Integer::sum);
            }
            records.add(record);
        }

        List<Map<String, Object>> matched = filter(records, method, status, user, session, search);

        List<Map<String, Object>> methods = new ArrayList<>();
        methodCounts.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).limit(20).forEach(entry -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", entry.getKey());
            item.put("count", entry.getValue());
            methods.add(item);
        });

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("name", file.getName());
        payload.put("format", format);
        payload.put("pattern", pattern);
        payload.put("fields", findFields(file, format, pattern));
        payload.put("statusClasses", statusClasses);
        payload.put("methods", methods);
        payload.put("total", Long.valueOf(records.size()));
        payload.put("matched", Long.valueOf(matched.size()));
        payload.put("readBytes", Long.valueOf(estimatedBytes(rawLines)));
        payload.put("totalBytes", Long.valueOf(file.length()));
        payload.put("truncated", Boolean.valueOf(truncated));
        payload.put("records", tail(matched, lines));
        return payload;
    }


    // -------------------------------------------------------------- Filters


    private static List<Map<String, Object>> filter(List<Map<String, Object>> records, String level, String search) {

        if ((level == null || level.isEmpty()) && (search == null || search.isEmpty())) {
            return records;
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> record : records) {
            if (level != null && !level.isEmpty()) {
                Object recordLevel = record.get("level");
                if (!(recordLevel instanceof String l) || !l.equalsIgnoreCase(level)) {
                    continue;
                }
            }
            if (search != null && !search.isEmpty() && !containsSearch(record, search)) {
                continue;
            }
            result.add(record);
        }
        return result;
    }


    private static List<Map<String, Object>> filter(List<Map<String, Object>> records, String method, String status,
            String user, String session, String search) {

        if (method == null && status == null && user == null && session == null && search == null) {
            return records;
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> record : records) {
            if (method != null && !method.isEmpty()) {
                Object recordMethod = record.get("method");
                if (!(recordMethod instanceof String m) || !m.equalsIgnoreCase(method)) {
                    continue;
                }
            }
            if (status != null && !status.isEmpty() && !statusMatches(record, status)) {
                continue;
            }
            if (user != null && !user.isEmpty()) {
                if (!fieldMatches(record, new String[] { "user", "logicalUserName" }, user)) {
                    continue;
                }
            }
            if (session != null && !session.isEmpty()) {
                if (!fieldMatches(record, new String[] { "sessionId" }, session)) {
                    continue;
                }
            }
            if (search != null && !search.isEmpty() && !containsSearch(record, search)) {
                continue;
            }
            result.add(record);
        }
        return result;
    }


    private static boolean statusMatches(Map<String, Object> record, String wanted) {
        Object statusCode = record.get("statusCode");
        if (!(statusCode instanceof Number n)) {
            return false;
        }
        int status = n.intValue();
        if (wanted.length() == 3 && wanted.charAt(2) == 'x' && Character.isDigit(wanted.charAt(0))) {
            return status / 100 == wanted.charAt(0) - '0';
        }
        if (wanted.length() == 3 && wanted.chars().allMatch(Character::isDigit)) {
            return status == Integer.parseInt(wanted);
        }
        return false;
    }


    /**
     * Case-insensitive "contains" match of the wanted text in one of the given fields.
     */
    private static boolean fieldMatches(Map<String, Object> record, String[] keys, String wanted) {
        String needle = wanted.toLowerCase(Locale.ROOT);
        for (String key : keys) {
            Object value = record.get(key);
            if (value instanceof String s && s.toLowerCase(Locale.ROOT).contains(needle)) {
                return true;
            }
        }
        return false;
    }


    /**
     * Case-insensitive "contains" match of the wanted text in the printable values of a record.
     */
    private static boolean containsSearch(Map<String, Object> record, String wanted) {
        String needle = wanted.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, Object> entry : record.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String s) {
                if (s.toLowerCase(Locale.ROOT).contains(needle)) {
                    return true;
                }
            } else if (value instanceof Number) {
                if (String.valueOf(value).contains(needle)) {
                    return true;
                }
            } else if (value instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof String s && s.toLowerCase(Locale.ROOT).contains(needle)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }


    // ------------------------------------------------------------- Helpers


    /**
     * The logs directory of this Tomcat instance.
     *
     * @return the directory or {@code null} when it cannot be determined or does not exist
     */
    private static File logsDirectory() {
        String base = System.getProperty("catalina.base");
        if (base == null) {
            return null;
        }
        File dir = new File(base, "logs");
        return dir.isDirectory() ? dir : null;
    }


    /**
     * Resolve the log file named by the {@code name} request parameter inside the logs directory. On failure the
     * appropriate error response is already sent.
     *
     * @return the file or {@code null} when an error was sent
     */
    private static File resolveRequestFile(HttpServletResponse response, HttpServletRequest request)
            throws IOException {
        File dir = logsDirectory();
        if (dir == null) {
            Api.error(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "LOGS_DIR_MISSING",
                    sm.getString("manager2.logsDirMissing"));
            return null;
        }
        String name = request.getParameter("name");
        if (name == null || !SAFE_NAME.matcher(name).matches()) {
            Api.error(response, HttpServletResponse.SC_BAD_REQUEST, "INVALID_NAME",
                    sm.getString("manager2.invalidLogName"));
            return null;
        }
        File file = resolveLogFile(dir, name);
        if (file == null) {
            Api.notFound(response);
            return null;
        }
        return file;
    }


    /**
     * A file inside the given directory, or {@code null} when the name escapes the directory or does not exist.
     */
    private static File resolveLogFile(File dir, String name) {
        File file = new File(dir, name);
        try {
            if (!file.getCanonicalFile().toPath().startsWith(dir.getCanonicalFile().toPath()) || !file.isFile()) {
                return null;
            }
        } catch (IOException e) {
            return null;
        }
        return file;
    }


    /**
     * Read the last {@link #READ_CAP} bytes of a file, starting on a line boundary.
     */
    private static byte[] readTail(File file) throws IOException {
        long length = file.length();
        long offset = Math.max(0, length - READ_CAP);
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            raf.seek(offset);
            byte[] buffer = new byte[(int) (length - offset)];
            if (buffer.length > 0) {
                raf.readFully(buffer);
            }
            if (offset == 0) {
                return buffer;
            }
            int i = 0;
            while (i < buffer.length && buffer[i] != '\n') {
                i++;
            }
            if (i >= buffer.length) {
                return new byte[0];
            }
            return Arrays.copyOfRange(buffer, i + 1, buffer.length);
        }
    }


    private static long estimatedBytes(List<String> lines) {
        long bytes = 0;
        for (String line : lines) {
            bytes += line.length() + 1;
        }
        return bytes;
    }


    /**
     * The {@code lines} most recent records, ordered from most recent to least recent (the records are built in file
     * order, i.e. from oldest to most recent).
     */
    private static List<Map<String, Object>> tail(List<Map<String, Object>> records, int lines) {
        int from = Math.max(0, records.size() - lines);
        List<Map<String, Object>> result = new ArrayList<>(records.subList(from, records.size()));
        Collections.reverse(result);
        return result;
    }


    /**
     * Detect the format of a log file from its first non-empty line: a line that starts with
     * {@code {} is JSON, anything else is plain text.
     */
    private static String detectFormat(File file) {
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            int toRead = (int) Math.min(FORMAT_CAP, file.length());
            byte[] buffer = new byte[toRead];
            if (toRead > 0) {
                raf.readFully(buffer);
            }
            String content = new String(buffer, StandardCharsets.UTF_8);
            for (String line : content.split("\\r?\\n", -1)) {
                if (line.isEmpty()) {
                    continue;
                }
                return line.charAt(0) == '{' ? "json" : "text";
            }
        } catch (IOException e) {
            return "text";
        }
        return "text";
    }


    /**
     * The access log pattern that applies to a file: the pattern of the configured access log valve (when present) or,
     * for plain text files, one of the standard patterns that matches the first line.
     */
    private String findPattern(File file, String format) {
        String valvePattern = findValvePattern();
        if (valvePattern != null) {
            return valvePattern;
        }
        if ("json".equals(format)) {
            return null;
        }
        String firstLine = firstNonEmptyLine(file);
        if (firstLine == null) {
            return null;
        }
        for (String candidate : new String[] { org.apache.catalina.valves.Constants.AccessLog.COMMON_PATTERN,
                org.apache.catalina.valves.Constants.AccessLog.COMBINED_PATTERN }) {
            try {
                if (LogParser.compile(candidate).parse(firstLine) != null) {
                    return candidate;
                }
            } catch (IllegalArgumentException e) {
                // Try the next candidate.
            }
        }
        return null;
    }


    /**
     * The ordered list of fields a record of this file can have.
     */
    private List<String> findFields(File file, String format, String pattern) {
        if ("json".equals(format)) {
            String firstLine = firstNonEmptyLine(file);
            if (firstLine == null) {
                return null;
            }
            Map<String, Object> record = LogParser.parseJsonAccessLine(firstLine);
            if (record == null) {
                return null;
            }
            return new ArrayList<>(record.keySet());
        }
        if (pattern != null) {
            try {
                return LogParser.compile(pattern).getFields();
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        return null;
    }


    private static String firstNonEmptyLine(File file) {
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            int toRead = (int) Math.min(FORMAT_CAP, file.length());
            byte[] buffer = new byte[toRead];
            if (toRead > 0) {
                raf.readFully(buffer);
            }
            String content = new String(buffer, StandardCharsets.UTF_8);
            for (String line : content.split("\\r?\\n", -1)) {
                if (!line.isEmpty()) {
                    return line;
                }
            }
        } catch (IOException e) {
            return null;
        }
        return null;
    }


    /**
     * The pattern of the access log valve installed on this host or its engine, or {@code null} when no access log
     * valve is configured.
     */
    private String findValvePattern() {
        if (host == null) {
            return null;
        }
        String pattern = firstValvePattern(host);
        if (pattern != null) {
            return pattern;
        }
        Container engine = host.getParent();
        return engine == null ? null : firstValvePattern(engine);
    }


    private static String firstValvePattern(Container container) {
        for (Valve valve : container.getPipeline().getValves()) {
            if (valve instanceof AbstractAccessLogValve accessLogValve) {
                String pattern = accessLogValve.getPattern();
                if (pattern != null && !pattern.isEmpty()) {
                    return pattern;
                }
            }
        }
        return null;
    }


    private static String param(HttpServletRequest request, String name) {
        String value = request.getParameter(name);
        return (value == null || value.isEmpty()) ? null : value;
    }


    @Override
    public String getServletInfo() {
        return "Manager2 Logs API";
    }
}
