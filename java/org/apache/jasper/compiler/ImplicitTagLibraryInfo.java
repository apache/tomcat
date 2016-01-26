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

import java.io.IOException;
import java.net.URL;
import java.util.Collection;
import java.util.Hashtable;
import java.util.Set;
import java.util.Vector;

import javax.servlet.ServletContext;
import javax.servlet.jsp.tagext.FunctionInfo;
import javax.servlet.jsp.tagext.TagFileInfo;
import javax.servlet.jsp.tagext.TagInfo;
import javax.servlet.jsp.tagext.TagLibraryInfo;

import org.apache.jasper.Constants;
import org.apache.jasper.JasperException;
import org.apache.jasper.JspCompilationContext;
import org.apache.tomcat.util.descriptor.tld.ImplicitTldRuleSet;
import org.apache.tomcat.util.descriptor.tld.TaglibXml;
import org.apache.tomcat.util.descriptor.tld.TldParser;
import org.apache.tomcat.util.descriptor.tld.TldResourcePath;
import org.xml.sax.SAXException;

/**
 * Class responsible for generating an implicit tag library containing tag
 * handlers corresponding to the tag files in "/WEB-INF/tags/" or a
 * subdirectory of it.
 *
 * @author Jan Luehe
 */
class ImplicitTagLibraryInfo extends TagLibraryInfo {

    private static final String WEB_INF_TAGS = "/WEB-INF/tags";
    private static final String TAG_FILE_SUFFIX = ".tag";
    private static final String TAGX_FILE_SUFFIX = ".tagx";
    private static final String TAGS_SHORTNAME = "tags";
    private static final String TLIB_VERSION = "1.0";
    private static final String JSP_VERSION = "2.0";
    private static final String IMPLICIT_TLD = "implicit.tld";

    // Maps tag names to tag file paths
    private final Hashtable<String,String> tagFileMap;

    private final ParserController pc;
    private final PageInfo pi;
    private final Vector<TagFileInfo> vec;


    public ImplicitTagLibraryInfo(JspCompilationContext ctxt,
            ParserController pc,
            PageInfo pi,
            String prefix,
            String tagdir,
            ErrorDispatcher err) throws JasperException {
        super(prefix, null);
        this.pc = pc;
        this.pi = pi;
        this.tagFileMap = new Hashtable<>();
        this.vec = new Vector<>();

        // Implicit tag libraries have no functions:
        this.functions = new FunctionInfo[0];

        tlibversion = TLIB_VERSION;
        jspversion = JSP_VERSION;

        if (!tagdir.startsWith(WEB_INF_TAGS)) {
            err.jspError("jsp.error.invalid.tagdir", tagdir);
        }

        // Determine the value of the <short-name> subelement of the
        // "imaginary" <taglib> element
        if (tagdir.equals(WEB_INF_TAGS)
                || tagdir.equals( WEB_INF_TAGS + "/")) {
            shortname = TAGS_SHORTNAME;
        } else {
            shortname = tagdir.substring(WEB_INF_TAGS.length());
            shortname = shortname.replace('/', '-');
        }

        // Populate mapping of tag names to tag file paths
        Set<String> dirList = ctxt.getResourcePaths(tagdir);
        if (dirList != null) {
            for (String path : dirList) {
                if (path.endsWith(TAG_FILE_SUFFIX)
                        || path.endsWith(TAGX_FILE_SUFFIX)) {
                    /*
                     * Use the filename of the tag file, without the .tag or
                     * .tagx extension, respectively, as the <name> subelement
                     * of the "imaginary" <tag-file> element
                     */
                    String suffix = path.endsWith(TAG_FILE_SUFFIX) ?
                            TAG_FILE_SUFFIX : TAGX_FILE_SUFFIX;
                    String tagName = path.substring(path.lastIndexOf('/') + 1);
                    tagName = tagName.substring(0,
                            tagName.lastIndexOf(suffix));
                    tagFileMap.put(tagName, path);
                } else if (path.endsWith(IMPLICIT_TLD)) {
                    TaglibXml taglibXml;
                    try {
                        URL url = ctxt.getResource(path);
                        TldResourcePath resourcePath = new TldResourcePath(url, path);
                        ServletContext servletContext = ctxt.getServletContext();
                        boolean validate = Boolean.parseBoolean(
                                servletContext.getInitParameter(
                                        Constants.XML_VALIDATION_TLD_INIT_PARAM));
                        String blockExternalString = servletContext.getInitParameter(
                                Constants.XML_BLOCK_EXTERNAL_INIT_PARAM);
                        boolean blockExternal;
                        if (blockExternalString == null) {
                            blockExternal = true;
                        } else {
                            blockExternal = Boolean.parseBoolean(blockExternalString);
                        }
                        TldParser parser = new TldParser(true, validate,
                                new ImplicitTldRuleSet(), blockExternal);
                        taglibXml = parser.parse(resourcePath);
                    } catch (IOException | SAXException e) {
                        err.jspError(e);
                        // unreached
                        throw new JasperException(e);
                    }
                    this.tlibversion = taglibXml.getTlibVersion();
                    this.jspversion = taglibXml.getJspVersion();
                    try {
                        double version = Double.parseDouble(this.jspversion);
                        if (version < 2.0) {
                            err.jspError("jsp.error.invalid.implicit.version", path);
                        }
                    } catch (NumberFormatException e) {
                        err.jspError("jsp.error.invalid.implicit.version", path);
                    }

                    // Add implicit TLD to dependency list
                    if (pi != null) {
                        pi.addDependant(path, ctxt.getLastModified(path));
                    }
                }
            }
        }

    }

    /**
     * Checks to see if the given tag name maps to a tag file path,
     * and if so, parses the corresponding tag file.
     *
     * @return The TagFileInfo corresponding to the given tag name, or null if
     * the given tag name is not implemented as a tag file
     */
    @Override
    public TagFileInfo getTagFile(String shortName) {

        TagFileInfo tagFile = super.getTagFile(shortName);
        if (tagFile == null) {
            String path = tagFileMap.get(shortName);
            if (path == null) {
                return null;
            }

            TagInfo tagInfo = null;
            try {
                tagInfo = TagFileProcessor.parseTagFileDirectives(pc,
                        shortName,
                        path,
                        null,
                        this);
            } catch (JasperException je) {
                throw new RuntimeException(je.toString(), je);
            }

            tagFile = new TagFileInfo(shortName, path, tagInfo);
            vec.addElement(tagFile);

            this.tagFiles = new TagFileInfo[vec.size()];
            vec.copyInto(this.tagFiles);
        }

        return tagFile;
    }

    @Override
    public TagLibraryInfo[] getTagLibraryInfos() {
        Collection<TagLibraryInfo> coll = pi.getTaglibs();
        return coll.toArray(new TagLibraryInfo[0]);
    }

}
