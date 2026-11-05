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

import java.io.ByteArrayOutputStream;
import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.el.util.JreCompat;
import org.apache.jasper.JasperException;
import org.apache.jasper.JspCompilationContext;
import org.apache.jasper.compiler.SmapStratum.LineInfo;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

/**
 * Contains static utilities for generating SMAP data based on the current version of Jasper.
 */
public class SmapUtil {

    /**
     * Default constructor.
     */
    public SmapUtil() {
    }

    private static final Charset SMAP_ENCODING = StandardCharsets.UTF_8;

    // The SMAP data in the SourceDebugExtension attribute starts with this line
    private static final String SMAP_HEADER = "SMAP\n";


    /**
     * Generates an appropriate SMAP representing the current compilation context. (JSR-045.)
     *
     * @param ctxt      Current compilation context
     * @param pageNodes The current JSP page
     *
     * @return a SMAP for the page
     *
     * @throws IOException Error writing SMAP
     */
    public static Map<String,SmapStratum> generateSmap(JspCompilationContext ctxt, Node.Nodes pageNodes)
            throws IOException {

        Map<String,SmapStratum> smapInfo = new HashMap<>();

        // Scan the nodes for presence of Jasper generated inner classes
        PreScanVisitor psVisitor = new PreScanVisitor();
        try {
            pageNodes.visit(psVisitor);
        } catch (JasperException ex) {
            // Ignore
        }
        HashMap<String,SmapStratum> map = psVisitor.getMap();

        // Assemble info about our own stratum (JSP) using JspLineMap
        SmapStratum s = new SmapStratum();

        // Map out Node.Nodes
        evaluateNodes(pageNodes, s, map, ctxt.getOptions().getMappedFile());
        s.optimizeLineSection();
        s.setOutputFileName(unqualify(ctxt.getServletJavaFileName()));

        String classFileName = ctxt.getClassFileName();
        s.setClassFileName(classFileName);

        smapInfo.put(ctxt.getFQCN(), s);

        if (ctxt.getOptions().isSmapDumped()) {
            File outSmap = new File(classFileName + ".smap");
            PrintWriter so = new PrintWriter(new OutputStreamWriter(new FileOutputStream(outSmap), SMAP_ENCODING));
            so.print(s.getSmapString());
            so.close();
        }

        for (Map.Entry<String,SmapStratum> entry : map.entrySet()) {
            String innerClass = entry.getKey();
            s = entry.getValue();
            s.optimizeLineSection();
            s.setOutputFileName(unqualify(ctxt.getServletJavaFileName()));
            String innerClassFileName =
                    classFileName.substring(0, classFileName.indexOf(".class")) + '$' + innerClass + ".class";
            s.setClassFileName(innerClassFileName);

            smapInfo.put(ctxt.getFQCN() + "." + innerClass, s);

            if (ctxt.getOptions().isSmapDumped()) {
                File outSmap = new File(innerClassFileName + ".smap");
                PrintWriter so = new PrintWriter(new OutputStreamWriter(new FileOutputStream(outSmap), SMAP_ENCODING));
                so.print(s.getSmapString());
                so.close();
            }
        }

        return smapInfo;
    }

    /**
     * Installs SMAP data into the compiled class files by writing the SourceDebugExtension
     * attribute.
     * @param smapInfo the SMAP data keyed by fully qualified class name
     * @throws IOException if an I/O error occurs while writing class files
     */
    public static void installSmap(Map<String,SmapStratum> smapInfo) throws IOException {
        if (smapInfo == null) {
            return;
        }

        for (Map.Entry<String,SmapStratum> entry : smapInfo.entrySet()) {
            File outServlet = new File(entry.getValue().getClassFileName());
            SDEInstaller.install(outServlet, entry.getValue().getSmapString().getBytes(StandardCharsets.ISO_8859_1));
        }
    }


    /**
     * Returns an unqualified version of the given file path.
     */
    private static String unqualify(String path) {
        path = path.replace('\\', '/');
        return path.substring(path.lastIndexOf('/') + 1);
    }


    private static class SDEInstaller {

        private final Log log = LogFactory.getLog(SDEInstaller.class); // must not be static

        static final String nameSDE = "SourceDebugExtension";

        byte[] orig;
        byte[] sdeAttr;
        byte[] gen;

        int origPos = 0;
        int genPos = 0;

        int sdeIndex;

        static void install(File classFile, byte[] smap) throws IOException {
            File tmpFile = new File(classFile.getPath() + "tmp");
            SDEInstaller installer = new SDEInstaller(classFile, smap);
            installer.install(tmpFile);
            if (!classFile.delete()) {
                throw new IOException(
                        Localizer.getMessage("jsp.error.unable.deleteClassFile", classFile.getAbsolutePath()));
            }
            if (!tmpFile.renameTo(classFile)) {
                throw new IOException(Localizer.getMessage("jsp.error.unable.renameClassFile",
                        tmpFile.getAbsolutePath(), classFile.getAbsolutePath()));
            }
        }

        SDEInstaller(File inClassFile, byte[] sdeAttr) throws IOException {
            if (!inClassFile.exists()) {
                throw new FileNotFoundException(Localizer.getMessage("jsp.error.noFile", inClassFile));
            }

            this.sdeAttr = sdeAttr;
            // get the bytes
            orig = readWhole(inClassFile);
            gen = new byte[orig.length + sdeAttr.length + 100];
        }

        void install(File outClassFile) throws IOException {
            // do it
            addSDE();

            // write result
            try (FileOutputStream outStream = new FileOutputStream(outClassFile)) {
                outStream.write(gen, 0, genPos);
            }
        }

        static byte[] readWhole(File input) throws IOException {
            int len = (int) input.length();
            byte[] bytes = new byte[len];
            try (FileInputStream inStream = new FileInputStream(input)) {
                if (inStream.read(bytes, 0, len) != len) {
                    throw new IOException(Localizer.getMessage("jsp.error.readContent", Integer.valueOf(len)));
                }
            }
            return bytes;
        }

        void addSDE() throws UnsupportedEncodingException, IOException {
            copy(4 + 2 + 2); // magic min/maj version
            int constantPoolCountPos = genPos;
            int constantPoolCount = readU2();
            if (log.isTraceEnabled()) {
                log.trace("constant pool count: " + constantPoolCount);
            }
            writeU2(constantPoolCount);

            // copy old constant pool return index of SDE symbol, if found
            sdeIndex = copyConstantPool(constantPoolCount);
            if (sdeIndex < 0) {
                // if "SourceDebugExtension" symbol not there add it
                writeUtf8ForSDE();

                // increment the constantPoolCount
                sdeIndex = constantPoolCount;
                ++constantPoolCount;
                randomAccessWriteU2(constantPoolCountPos, constantPoolCount);

                if (log.isTraceEnabled()) {
                    log.trace("SourceDebugExtension not found, installed at: " + sdeIndex);
                }
            } else {
                if (log.isTraceEnabled()) {
                    log.trace("SourceDebugExtension found at: " + sdeIndex);
                }
            }
            copy(2 + 2 + 2); // access, this, super
            int interfaceCount = readU2();
            writeU2(interfaceCount);
            if (log.isTraceEnabled()) {
                log.trace("interfaceCount: " + interfaceCount);
            }
            copy(interfaceCount * 2);
            copyMembers(); // fields
            copyMembers(); // methods
            int attrCountPos = genPos;
            int attrCount = readU2();
            writeU2(attrCount);
            if (log.isTraceEnabled()) {
                log.trace("class attrCount: " + attrCount);
            }
            // copy the class attributes, return true if SDE attr found (not copied)
            if (!copyAttrs(attrCount)) {
                // we will be adding SDE and it isn't already counted
                ++attrCount;
                randomAccessWriteU2(attrCountPos, attrCount);
                if (log.isTraceEnabled()) {
                    log.trace("class attrCount incremented");
                }
            }
            writeAttrForSDE(sdeIndex);
        }

        void copyMembers() {
            int count = readU2();
            writeU2(count);
            if (log.isTraceEnabled()) {
                log.trace("members count: " + count);
            }
            for (int i = 0; i < count; ++i) {
                copy(6); // access, name, descriptor
                int attrCount = readU2();
                writeU2(attrCount);
                if (log.isTraceEnabled()) {
                    log.trace("member attr count: " + attrCount);
                }
                copyAttrs(attrCount);
            }
        }

        boolean copyAttrs(int attrCount) {
            boolean sdeFound = false;
            for (int i = 0; i < attrCount; ++i) {
                int nameIndex = readU2();
                // don't write old SDE
                if (nameIndex == sdeIndex) {
                    sdeFound = true;
                    if (log.isTraceEnabled()) {
                        log.trace("SDE attr found");
                    }
                } else {
                    writeU2(nameIndex); // name
                    int len = readU4();
                    writeU4(len);
                    copy(len);
                    if (log.isTraceEnabled()) {
                        log.trace("attr len: " + len);
                    }
                }
            }
            return sdeFound;
        }

        void writeAttrForSDE(int index) {
            writeU2(index);
            writeU4(sdeAttr.length);
            for (byte b : sdeAttr) {
                writeU1(b);
            }
        }

        void randomAccessWriteU2(int pos, int val) {
            int savePos = genPos;
            genPos = pos;
            writeU2(val);
            genPos = savePos;
        }

        int readU1() {
            return orig[origPos++] & 0xFF;
        }

        int readU2() {
            int res = readU1();
            return (res << 8) + readU1();
        }

        int readU4() {
            int res = readU2();
            return (res << 16) + readU2();
        }

        void writeU1(int val) {
            gen[genPos++] = (byte) val;
        }

        void writeU2(int val) {
            writeU1(val >> 8);
            writeU1(val & 0xFF);
        }

        void writeU4(int val) {
            writeU2(val >> 16);
            writeU2(val & 0xFFFF);
        }

        void copy(int count) {
            for (int i = 0; i < count; ++i) {
                gen[genPos++] = orig[origPos++];
            }
        }

        byte[] readBytes(int count) {
            byte[] bytes = new byte[count];
            for (int i = 0; i < count; ++i) {
                bytes[i] = orig[origPos++];
            }
            return bytes;
        }

        void writeBytes(byte[] bytes) {
            for (byte aByte : bytes) {
                gen[genPos++] = aByte;
            }
        }

        int copyConstantPool(int constantPoolCount) throws UnsupportedEncodingException, IOException {
            int sdeIndex = -1;
            // copy const pool index zero not in class file
            for (int i = 1; i < constantPoolCount; ++i) {
                int tag = readU1();
                writeU1(tag);
                switch (tag) {
                    case 7: // Class
                    case 8: // String
                    case 16: // MethodType
                        if (log.isTraceEnabled()) {
                            log.trace(i + " copying 2 bytes");
                        }
                        copy(2);
                        break;
                    case 15: // MethodHandle
                        if (log.isTraceEnabled()) {
                            log.trace(i + " copying 3 bytes");
                        }
                        copy(3);
                        break;
                    case 9: // Field
                    case 10: // Method
                    case 11: // InterfaceMethod
                    case 3: // Integer
                    case 4: // Float
                    case 12: // NameAndType
                    case 18: // InvokeDynamic
                        if (log.isTraceEnabled()) {
                            log.trace(i + " copying 4 bytes");
                        }
                        copy(4);
                        break;
                    case 5: // Long
                    case 6: // Double
                        if (log.isTraceEnabled()) {
                            log.trace(i + " copying 8 bytes");
                        }
                        copy(8);
                        i++;
                        break;
                    case 1: // Utf8
                        int len = readU2();
                        writeU2(len);
                        byte[] utf8 = readBytes(len);
                        String str = new String(utf8, StandardCharsets.UTF_8);
                        if (log.isTraceEnabled()) {
                            log.trace(i + " read class attr -- '" + str + "'");
                        }
                        if (str.equals(nameSDE)) {
                            sdeIndex = i;
                        }
                        writeBytes(utf8);
                        break;
                    default:
                        throw new IOException(Localizer.getMessage("jsp.error.unexpectedTag", Integer.valueOf(tag)));
                }
            }
            return sdeIndex;
        }

        void writeUtf8ForSDE() {
            int len = nameSDE.length();
            writeU1(1); // Utf8 tag
            writeU2(len);
            for (int i = 0; i < len; ++i) {
                writeU1(nameSDE.charAt(i));
            }
        }
    }

    /**
     * Reads the SMAP from the SourceDebugExtension attribute of a class file. This walks the structure of the class
     * file, as {@link SDEInstaller} does when it installs the attribute, so that exactly the bytes of the attribute are
     * returned. Searching the class file for the content of the SMAP is not reliable because the same bytes may appear
     * elsewhere in the class file, such as in a string constant, or within the SMAP itself.
     * <p>
     * The constant pool may contain more than one entry with the name of the attribute and other tools may add
     * SourceDebugExtension attributes that do not contain a SMAP. Only an attribute that contains a SMAP, which starts
     * with {@link SmapUtil#SMAP_HEADER}, is returned. If there is more than one the last is returned, since that is
     * where {@link SDEInstaller} adds it.
     */
    private static class SDEReader {

        private static final int MAGIC = 0xCAFEBABE;

        private static final byte[] SMAP_HEADER_BYTES = SMAP_HEADER.getBytes(StandardCharsets.ISO_8859_1);

        private final DataInputStream in;

        private SDEReader(InputStream is) {
            this.in = new DataInputStream(new BufferedInputStream(is));
        }

        /**
         * Read the SMAP from a class file.
         *
         * @param is The class file
         *
         * @return The content of the SourceDebugExtension attribute that contains the SMAP, including the first line,
         *             or {@code null} if the class file does not have such an attribute
         *
         * @throws IOException If the data is not a valid class file or reading it fails
         */
        static byte[] read(InputStream is) throws IOException {
            return new SDEReader(is).readSDE();
        }

        private byte[] readSDE() throws IOException {
            if (in.readInt() != MAGIC) {
                throw new IOException(Localizer.getMessage("jsp.error.invalidClassFile"));
            }
            skip(2 + 2); // minor and major version

            Set<Integer> sdeIndexes = readConstantPool();
            if (sdeIndexes.isEmpty()) {
                // Without the name in the constant pool there cannot be a SourceDebugExtension attribute
                return null;
            }

            skip(2 + 2 + 2); // access, this, super
            int interfaceCount = in.readUnsignedShort();
            skip(interfaceCount * 2L);
            skipMembers(); // fields
            skipMembers(); // methods

            // Class attributes
            byte[] smap = null;
            int attrCount = in.readUnsignedShort();
            for (int i = 0; i < attrCount; i++) {
                int nameIndex = in.readUnsignedShort();
                int len = readLength();
                if (sdeIndexes.contains(Integer.valueOf(nameIndex))) {
                    byte[] sde = in.readNBytes(len);
                    if (sde.length != len) {
                        throw new EOFException();
                    }
                    if (isSmap(sde)) {
                        smap = sde;
                    }
                } else {
                    skip(len);
                }
            }
            return smap;
        }

        private static boolean isSmap(byte[] sde) {
            return sde.length >= SMAP_HEADER_BYTES.length &&
                    Arrays.equals(sde, 0, SMAP_HEADER_BYTES.length, SMAP_HEADER_BYTES, 0, SMAP_HEADER_BYTES.length);
        }

        /*
         * Skips the constant pool and returns the indexes of the Utf8 entries that contain the name of the
         * SourceDebugExtension attribute. There is normally one but nothing prevents there being several.
         */
        private Set<Integer> readConstantPool() throws IOException {
            Set<Integer> sdeIndexes = new HashSet<>();
            int constantPoolCount = in.readUnsignedShort();
            // Constant pool index zero is not in the class file
            for (int i = 1; i < constantPoolCount; i++) {
                int tag = in.readUnsignedByte();
                switch (tag) {
                    case 7: // Class
                    case 8: // String
                    case 16: // MethodType
                    case 19: // Module
                    case 20: // Package
                        skip(2);
                        break;
                    case 15: // MethodHandle
                        skip(3);
                        break;
                    case 9: // Field
                    case 10: // Method
                    case 11: // InterfaceMethod
                    case 3: // Integer
                    case 4: // Float
                    case 12: // NameAndType
                    case 17: // Dynamic
                    case 18: // InvokeDynamic
                        skip(4);
                        break;
                    case 5: // Long
                    case 6: // Double
                        skip(8);
                        // Long and double values take two entries in the constant pool
                        i++;
                        break;
                    case 1: // Utf8
                        int len = in.readUnsignedShort();
                        if (len == SDEInstaller.nameSDE.length()) {
                            byte[] utf8 = in.readNBytes(len);
                            if (utf8.length != len) {
                                throw new EOFException();
                            }
                            if (SDEInstaller.nameSDE.equals(new String(utf8, StandardCharsets.UTF_8))) {
                                sdeIndexes.add(Integer.valueOf(i));
                            }
                        } else {
                            skip(len);
                        }
                        break;
                    default:
                        throw new IOException(Localizer.getMessage("jsp.error.unexpectedTag", Integer.valueOf(tag)));
                }
            }
            return sdeIndexes;
        }

        /*
         * Skips exactly the requested number of bytes. InputStream.skipNBytes() is not used as it is not available in
         * all supported Java versions and InputStream.skip() may skip fewer bytes than requested.
         */
        private void skip(long count) throws IOException {
            long remaining = count;
            while (remaining > 0) {
                long skipped = in.skip(remaining);
                if (skipped <= 0) {
                    // skip() is allowed to skip nothing. Read a byte to tell the end of the stream from a skip that
                    // made no progress.
                    if (in.read() == -1) {
                        throw new EOFException();
                    }
                    skipped = 1;
                }
                remaining -= skipped;
            }
        }


        private void skipMembers() throws IOException {
            int count = in.readUnsignedShort();
            for (int i = 0; i < count; i++) {
                skip(2 + 2 + 2); // access, name, descriptor
                int attrCount = in.readUnsignedShort();
                for (int j = 0; j < attrCount; j++) {
                    skip(2); // name
                    skip(readLength());
                }
            }
        }

        private int readLength() throws IOException {
            int len = in.readInt();
            if (len < 0) {
                // The length is a u4 but a valid attribute is never larger than the maximum size of an array
                throw new IOException(Localizer.getMessage("jsp.error.invalidClassFile"));
            }
            return len;
        }
    }

    /**
     * Traverses the JSP AST nodes and populates the given SmapStratum with line mapping data.
     * @param nodes the root JSP AST nodes
     * @param s the stratum to populate with line mappings
     * @param innerClassMap map to collect SMAP data for inner classes
     * @param breakAtLF whether to generate a mapping for each line feed in template text
     */
    public static void evaluateNodes(Node.Nodes nodes, SmapStratum s, HashMap<String,SmapStratum> innerClassMap,
            boolean breakAtLF) {
        try {
            nodes.visit(new SmapGenVisitor(s, breakAtLF, innerClassMap));
        } catch (JasperException ex) {
            // Ignore
        }
    }

    private static class SmapGenVisitor extends Node.Visitor {

        private SmapStratum smap;
        private final boolean breakAtLF;
        private final HashMap<String,SmapStratum> innerClassMap;

        SmapGenVisitor(SmapStratum s, boolean breakAtLF, HashMap<String,SmapStratum> map) {
            this.smap = s;
            this.breakAtLF = breakAtLF;
            this.innerClassMap = map;
        }

        @Override
        public void visitBody(Node n) throws JasperException {
            SmapStratum smapSave = smap;
            String innerClass = n.getInnerClassName();
            if (innerClass != null) {
                this.smap = innerClassMap.get(innerClass);
            }
            super.visitBody(n);
            smap = smapSave;
        }

        @Override
        public void visit(Node.Declaration n) throws JasperException {
            doSmapText(n);
        }

        @Override
        public void visit(Node.Expression n) throws JasperException {
            doSmapText(n);
        }

        @Override
        public void visit(Node.Scriptlet n) throws JasperException {
            doSmapText(n);
        }

        @Override
        public void visit(Node.IncludeAction n) throws JasperException {
            doSmap(n);
            visitBody(n);
        }

        @Override
        public void visit(Node.ForwardAction n) throws JasperException {
            doSmap(n);
            visitBody(n);
        }

        @Override
        public void visit(Node.GetProperty n) throws JasperException {
            doSmap(n);
            visitBody(n);
        }

        @Override
        public void visit(Node.SetProperty n) throws JasperException {
            doSmap(n);
            visitBody(n);
        }

        @Override
        public void visit(Node.UseBean n) throws JasperException {
            doSmap(n);
            visitBody(n);
        }

        @Override
        public void visit(Node.PlugIn n) throws JasperException {
            doSmap(n);
            visitBody(n);
        }

        @Override
        public void visit(Node.CustomTag n) throws JasperException {
            doSmap(n);
            visitBody(n);
        }

        @Override
        public void visit(Node.UninterpretedTag n) throws JasperException {
            doSmap(n);
            visitBody(n);
        }

        @Override
        public void visit(Node.JspElement n) throws JasperException {
            doSmap(n);
            visitBody(n);
        }

        @Override
        public void visit(Node.JspText n) throws JasperException {
            doSmap(n);
            visitBody(n);
        }

        @Override
        public void visit(Node.NamedAttribute n) throws JasperException {
            visitBody(n);
        }

        @Override
        public void visit(Node.JspBody n) throws JasperException {
            doSmap(n);
            visitBody(n);
        }

        @Override
        public void visit(Node.InvokeAction n) throws JasperException {
            doSmap(n);
            visitBody(n);
        }

        @Override
        public void visit(Node.DoBodyAction n) throws JasperException {
            doSmap(n);
            visitBody(n);
        }

        @Override
        public void visit(Node.ELExpression n) throws JasperException {
            doSmap(n);
        }

        @Override
        public void visit(Node.TemplateText n) throws JasperException {
            Mark mark = n.getStart();
            if (mark == null) {
                return;
            }

            // Add the file information
            String fileName = mark.getFile();
            smap.addFile(unqualify(fileName), fileName);

            // Add a LineInfo that corresponds to the beginning of this node
            int iInputStartLine = mark.getLineNumber();
            int iOutputStartLine = n.getBeginJavaLine();
            int iOutputLineIncrement = breakAtLF ? 1 : 0;
            smap.addLineData(iInputStartLine, fileName, 1, iOutputStartLine, iOutputLineIncrement);

            // Output additional mappings in the text
            java.util.ArrayList<Integer> extraSmap = n.getExtraSmap();

            if (extraSmap != null) {
                for (Integer integer : extraSmap) {
                    iOutputStartLine += iOutputLineIncrement;
                    smap.addLineData(iInputStartLine + integer.intValue(), fileName, 1, iOutputStartLine,
                            iOutputLineIncrement);
                }
            }
        }

        private void doSmap(Node n, int inLineCount, int outIncrement, int skippedLines) {
            Mark mark = n.getStart();
            if (mark == null) {
                return;
            }

            String unqualifiedName = unqualify(mark.getFile());
            smap.addFile(unqualifiedName, mark.getFile());
            smap.addLineData(mark.getLineNumber() + skippedLines, mark.getFile(), inLineCount - skippedLines,
                    n.getBeginJavaLine() + skippedLines, outIncrement);
        }

        private void doSmap(Node n) {
            doSmap(n, 1, n.getEndJavaLine() - n.getBeginJavaLine(), 0);
        }

        private void doSmapText(Node n) {
            String text = n.getText();
            int index = 0;
            int next;
            int lineCount = 1;
            int skippedLines = 0;
            boolean slashStarSeen = false;
            boolean beginning = true;

            // Count lines inside text, but skipping comment lines at the
            // beginning of the text.
            while ((next = text.indexOf('\n', index)) > -1) {
                if (beginning) {
                    String line = JreCompat.getInstance().strip(text.substring(index, next));
                    if (!slashStarSeen && line.startsWith("/*")) {
                        slashStarSeen = true;
                    }
                    if (slashStarSeen) {
                        skippedLines++;
                        int endIndex = line.indexOf("*/");
                        if (endIndex >= 0) {
                            // End of /* */ comment
                            slashStarSeen = false;
                            if (endIndex < line.length() - 2) {
                                // Some executable code after comment
                                skippedLines--;
                                beginning = false;
                            }
                        }
                    } else if (line.isEmpty() || line.startsWith("//")) {
                        skippedLines++;
                    } else {
                        beginning = false;
                    }
                }
                lineCount++;
                index = next + 1;
            }

            doSmap(n, lineCount, 1, skippedLines);
        }
    }

    private static class PreScanVisitor extends Node.Visitor {

        HashMap<String,SmapStratum> map = new HashMap<>();

        @Override
        public void doVisit(Node n) {
            String inner = n.getInnerClassName();
            if (inner != null && !map.containsKey(inner)) {
                map.put(inner, new SmapStratum());
            }
        }

        HashMap<String,SmapStratum> getMap() {
            return map;
        }
    }

    /**
     * Loads SMAP data for the given class by reading the embedded SourceDebugExtension
     * attribute from the class file or an external .smap resource.
     * @param className the fully qualified class name
     * @param cl the class loader to use for loading the resource
     * @return the parsed SmapStratum, or {@code null} if no SMAP data is found
     * @throws NumberFormatException If the SMAP data is found but is malformed
     * @throws ArrayIndexOutOfBoundsException If the SMAP data is found but is malformed
     */
    public static SmapStratum loadSmap(String className, ClassLoader cl) {
        // Extract SMAP from class file. First line "SMAP" is not included
        String smap = getSmap(className, cl);

        if (smap == null) {
            return null;
        }

        SmapStratum smapStratum = new SmapStratum();

        String[] lines = smap.split("\n");
        int lineIndex = 0;

        // First line is output file name
        smapStratum.setOutputFileName(lines[lineIndex]);

        // There is only one stratum (JSP) so skip to the start of the file
        // section
        lineIndex = 4;

        while (!lines[lineIndex].equals("*L")) {
            int i = lines[lineIndex].lastIndexOf(' ');
            String fileName = lines[lineIndex].substring(i + 1);
            smapStratum.addFile(fileName, lines[++lineIndex]);
            lineIndex++;
            if (lineIndex == lines.length) {
                return null;
            }
        }

        // Skip *L
        lineIndex++;
        if (lineIndex == lines.length) {
            return null;
        }

        while (!lines[lineIndex].equals("*E")) {
            LineInfo li = new LineInfo();
            // Split into in and out
            String[] inOut = lines[lineIndex].split(":");
            // Split in on comma (might not be one)
            String[] in = inOut[0].split(",");
            if (in.length == 2) {
                // There is a count
                li.setInputLineCount(Integer.parseInt(in[1]));
            }
            // Check for fileID
            String[] start = in[0].split("#");
            if (start.length == 2) {
                // There is a file ID
                li.setLineFileID(Integer.parseInt(start[1]));
            }
            li.setInputStartLine(Integer.parseInt(start[0]));
            // Split out
            String[] out = inOut[1].split(",");
            if (out.length == 2) {
                // There is an increment
                li.setOutputLineIncrement(Integer.parseInt(out[1]));
            }
            li.setOutputStartLine(Integer.parseInt(out[0]));

            smapStratum.addLineInfo(li);

            lineIndex++;
            if (lineIndex == lines.length) {
                return null;
            }
        }

        return smapStratum;
    }


    private static String getSmap(String className, ClassLoader cl) {
        InputStream is = null;
        try {
            is = cl.getResourceAsStream(className.replace(".", "/") + ".smap");
            if (is != null) {
                return new String(readFully(is), SMAP_ENCODING);
            }

            is = cl.getResourceAsStream(className.replace(".", "/") + ".class");
            if (is != null) {
                // The SMAP is the content of the SourceDebugExtension attribute. It is installed as ISO-8859-1 (see
                // installSmap()). The first line, "SMAP", is not included in the result.
                byte[] sde = SDEReader.read(is);
                if (sde != null) {
                    return new String(sde, SMAP_HEADER.length(), sde.length - SMAP_HEADER.length(),
                            StandardCharsets.ISO_8859_1);
                }
            }
        } catch (IOException ioe) {
            Log log = LogFactory.getLog(SmapUtil.class);
            log.warn(Localizer.getMessage("jsp.warning.loadSmap", className), ioe);
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException ioe) {
                    Log log = LogFactory.getLog(SmapUtil.class);
                    log.warn(Localizer.getMessage("jsp.warning.loadSmap", className), ioe);
                }
            }
        }
        return null;
    }

    private static byte[] readFully(InputStream is) throws IOException {
        byte[] buf = new byte[1024];
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            int read;
            while ((read = is.read(buf)) >= 0) {
                out.write(buf, 0, read);
            }
            return out.toByteArray();
        }
    }
}
