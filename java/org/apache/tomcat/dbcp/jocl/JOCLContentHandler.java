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

package org.apache.tomcat.dbcp.jocl;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;
import org.xml.sax.helpers.XMLReaderFactory;
import java.lang.reflect.InvocationTargetException;
import java.io.InputStream;
import java.io.Reader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.ArrayList;

// to do:
//  + add support for strings as CDATA (makes multiline strings easier, for example)
//  ? some kind of support for invoking methods?

/**
 * A {@link org.xml.sax.ContentHandler}
 * for the Java Object Configuration Language.
 * <p>
 * JOCL provides an XML syntax for constructing arbitrary Java
 * {@link java.lang.Object} instances.  It does not define a full
 * XML document type (there's no root element), but rather an
 * XML fragment describing the {@link java.lang.Object Objects} to be
 * constructed.
 * <p>
 * In a JOCL fragment, one may define a series of objects using
 * the <code>object</code> element.  A trivial example is:
 * <pre> &lt;object class="java.util.Date"/&gt;</pre>
 * which constructs an instance of <code>java.util.Date</code>
 * using the no-argument constructor.
 * <p>
 * After a "root-level" <code>&lt;object&gt;</code> element has been processed
 * (that is, once {@link #endElement(java.lang.String,java.lang.String,java.lang.String)}
 * has been invoked by the {@link XMLReader}), it will be appended to a list of <code>Object</code>s
 * maintained by the <code>JOCLContentHandler</code>.
 * <p>
 * (See {@link #size},
 * {@link #clear},
 * {@link #clear(int)},
 * {@link #getType(int)},
 * {@link #getValue(int)},
 * {@link #getTypeArray},
 * and
 * {@link #getValueArray}.)
 * <p>
 * You can list multiple <code>object</code> elements in a fragment.  For example,
 * after processing the JOCL fragment:
 * <pre> &lt;object class="java.util.Date"/&gt;
 * &lt;object class="java.util.Date"/&gt;</pre>
 * The {@link #getTypeArray} method
 * will return an array composed
 * of two instances of <code>java.util.Date</code>.  The sequence of
 * {@link java.lang.Object Objects} in the array
 * will correspond to the sequence of <code>&lt;object&gt;</code> elements in the JOCL fragment.
 * <p>
 * As we've seen, when used with no child-elements, the <code>&lt;object&gt;</code>
 * tag will cause the no-argument constructor of the specified class to be invoked.
 * It is also possible to nest <code>&lt;object&gt;</code> tags to provide arguments
 * for the constructor.
 * For example, the fragment:
 * <pre> &lt;object class="mypackage.Foo"&gt;
 *   &lt;object class="mypackage.Bar"/&gt;
 * &lt;/object&gt;</pre>
 * will add an instance of <code>mypackage.Foo</code> to the object list, constructed via
 * <code>new mypackage.Foo(new mypackage.Bar())</code>.
 * <p>
 * There is a special syntax available creating primitive values and arguments,
 * as well as for constructing {@link java.lang.String String}s. Some examples:
 * <pre> &lt;byte value="3"/&gt;
 * &lt;boolean value="false"/&gt;
 * &lt;char value="c"/&gt;
 * &lt;double value="3.14159"/&gt;
 * &lt;float value="3.14"/&gt;
 * &lt;int value="17"/&gt;
 * &lt;long value="1700000"/&gt;
 * &lt;short value="1"/&gt;
 * &lt;string value="The quick brown fox..."/&gt;</pre>
 * <p>
 * When invoked at the "root" level (that is, with no <code>&lt;object&gt;</code> parent),
 * this will cause the corresponding "object wrapper" to be added to the list of
 * {@link java.lang.Object Object}s.  The {@link #getType type} for these
 * objects will reflect the proper primitive type, however.  When invoked with an
 * <code>&lt;object&gt;</code> parent, these will be treated as primitive arguments to the
 * specified {@link java.lang.Object Object}'s constructor.  For example, while:
 * <pre> &lt;int value="5"/&gt;
 * &lt;int value="26"/&gt;
 * &lt;int value="100"/&gt;</pre>
 * <p>
 * results in three {@link java.lang.Integer} instances being added to the
 * list of values, with types corresponding to {@link java.lang.Integer}, the fragment:
 * <pre> &lt;int value="5"/&gt;
 * &lt;int value="26"/&gt;
 * &lt;int value="100"/&gt;</pre>
 * <p>
 * results in three {@link java.lang.Integer} instances being added to the
 * list of values, with types corresponding to {@link java.lang.Integer#TYPE}.
 * <p>
 * Hence if you want to invoke the <code>mypackage.Foo(java.lang.Integer,java.lang.Integer,java.lang.Integer)</code>
 * constructor, use:
 * <pre> &lt;object class="mypackage.Foo"/&gt;
 *   &lt;object class="java.lang.Integer"&gt;&lt;int value="5"/&gt;&lt;/object&gt;
 *   &lt;object class="java.lang.Integer"&gt;&lt;int value="26"/&gt;&lt;/object&gt;
 *   &lt;object class="java.lang.Integer"&gt;&lt;int value="100"/&gt;&lt;/object&gt;
 * &lt;/object&gt;</pre>
 * <p>
 * If you want to invoke the <code>mypackage.Foo(int,int,int)</code>
 * constructor, use:
 * <pre> &lt;object class="mypackage.Foo"/&gt;
 *   &lt;int value="5"/&gt;
 *   &lt;int value="26"/&gt;
 *   &lt;int value="100"/&gt;
 * &lt;/object&gt;</pre>
 * <p>
 * If you'd like to creat a <code>null</code> object, use:
 * <pre> &lt;object class="mypackage.Bar" null="true"/&gt;</pre>
 * <p>
 * Here's a simple but complete example:
 * <pre> &lt;?xml version="1.0"?&gt;
 * &lt;arbitrary-root xmlns="http://apache.org/xml/xmlns/jakarta/commons/jocl"&gt;
 *   &lt;string value="Hello World!"/&gt;
 *   &lt;string/&gt;
 *   &lt;boolean/&gt;
 *   &lt;boolean value="true"/&gt;
 *   &lt;byte value="1"/&gt;
 *   &lt;short value="1"/&gt;
 *   &lt;int value="1"/&gt;
 *   &lt;long value="1"/&gt;
 *   &lt;float value="1.0"/&gt;
 *   &lt;double value="1.0"/&gt;
 *   &lt;object class="java.util.Date"/&gt;
 *   &lt;object class="java.util.Date"&gt;
 *    &lt;int value="1"/&gt;
 *    &lt;int value="1"/&gt;
 *    &lt;int value="1"/&gt;
 *   &lt;/object&gt;
 * &lt;/arbitrary-root&gt;</pre>
 * <p>
 * Formally, a DTD for the JOCL grammar is as follows:
 * <pre>
 * &lt;!ELEMENT object (object|array|collection|list|byte|boolean|char|double|float|int|long|short|string)*&gt;
 * &lt;!ATTLIST object
 *   class CDATA #REQUIRED
 *   null (true|false) "false"&gt;
 *
 * &lt;!ELEMENT byte EMPTY&gt;
 * &lt;!ATTLIST byte value CDATA #REQUIRED&gt;
 *
 * &lt;!ELEMENT boolean EMPTY&gt;
 * &lt;!ATTLIST boolean value (true|false) #REQUIRED&gt;
 *
 * &lt;!ELEMENT char EMPTY&gt;
 * &lt;!ATTLIST char value CDATA #REQUIRED&gt;
 *
 * &lt;!ELEMENT double EMPTY&gt;
 * &lt;!ATTLIST double value CDATA #REQUIRED&gt;
 *
 * &lt;!ELEMENT float EMPTY&gt;
 * &lt;!ATTLIST float value CDATA #REQUIRED&gt;
 *
 * &lt;!ELEMENT int EMPTY&gt;
 * &lt;!ATTLIST int value CDATA #REQUIRED&gt;
 *
 * &lt;!ELEMENT long EMPTY&gt;
 * &lt;!ATTLIST long value CDATA #REQUIRED&gt;
 *
 * &lt;!ELEMENT short EMPTY&gt;
 * &lt;!ATTLIST short value CDATA #REQUIRED&gt;
 *
 * &lt;!ELEMENT string EMPTY&gt;
 * &lt;!ATTLIST string value CDATA #REQUIRED&gt;
 * </pre>
 * <p>
 * This class can also be used as a base class for {@link org.xml.sax.ContentHandler}s
 * that include JOCL as part of their grammar.  Simply extend this class, and override the
 * {@link #startElement},
 * {@link #characters},
 * and {@link #endElement} methods to handle
 * your tags, and invoke the method of the parent class (i.e., <code>super.<i>XXX</i></code> for
 * elements and data that you don't handle.
 * <p>
 * A number of static methods are available for simply reading a list of objects from
 * a {@link InputStream}, {@link Reader} or {@link InputSource}.
 * <p>
 * <b>Note that this class is not synchronized.</b>
 * <p>
 * @author Rodney Waldhoff
 */
public class JOCLContentHandler extends DefaultHandler {

    //--- Static Methods ---------------------------------------------
    /**
     * A simple tester method.  Reads a JOCL document from standard in
     * and prints a list of the objects created to standard out.
     * (Use the <code>org.xml.sax.driver</code> system property to specify
     * an {@link XMLReader}.
     */
    public static void main(String[] args) throws Exception {
        JOCLContentHandler jocl = JOCLContentHandler.parse(System.in,null);
        for(int i=0;i<jocl.size();i++) {
            System.out.println("<" + jocl.getType(i) + ">\t" + jocl.getValue(i));
        }
    }

    /**
     * Parses a JOCL document from the specified file, using the
     * {@link XMLReader} specified by the <code>org.xml.sax.driver</code>
     * property.
     * The returned {@link JOCLContentHandler} will contain the
     * list of objects described by the file.
     * @param f a {@link File} containing the JOCL document
     * @return a {@link JOCLContentHandler} containing the list of objects described by the JOCL document
     */
    public static JOCLContentHandler parse(File f) throws SAXException, FileNotFoundException, IOException {
        return JOCLContentHandler.parse(new FileInputStream(f),null);
    }

    /**
     * Parses a JOCL document from the specified {@link Reader}, using the
     * {@link XMLReader} specified by the <code>org.xml.sax.driver</code>
     * property.
     * The returned {@link JOCLContentHandler} will contain the
     * list of objects described by the file.
     * @param in a {@link Reader} containing the JOCL document
     * @return a {@link JOCLContentHandler} containing the list of objects described by the JOCL document
     */
    public static JOCLContentHandler parse(Reader in) throws SAXException, IOException {
        return JOCLContentHandler.parse(new InputSource(in),null);
    }

    /**
     * Parses a JOCL document from the specified {@link InputStream}, using the
     * {@link XMLReader} specified by the <code>org.xml.sax.driver</code>
     * property.
     * The returned {@link JOCLContentHandler} will contain the
     * list of objects described by the file.
     * @param in a {@link InputStream} containing the JOCL document
     * @return a {@link JOCLContentHandler} containing the list of objects described by the JOCL document
     */
    public static JOCLContentHandler parse(InputStream in) throws SAXException, IOException {
        return JOCLContentHandler.parse(new InputSource(in),null);
    }

    /**
     * Parses a JOCL document from the specified {@link InputSource}, using thethe
     * {@link XMLReader} specified by the <code>org.xml.sax.driver</code>
     * property.
     * The returned {@link JOCLContentHandler} will contain the
     * list of objects described by the file.
     * @param in a {@link InputSource} containing the JOCL document
     * @return a {@link JOCLContentHandler} containing the list of objects described by the JOCL document
     */
    public static JOCLContentHandler parse(InputSource in) throws SAXException, IOException {
        return JOCLContentHandler.parse(in,null);
    }

    /**
     * Parses a JOCL document from the specified file, using the
     * {@link XMLReader} specified by the <code>org.xml.sax.driver</code>
     * property.
     * The returned {@link JOCLContentHandler} will contain the
     * list of objects described by the file.
     * @param f a {@link File} containing the JOCL document
     * @param reader the {@link XMLReader} to use to parse the file
     * @return a {@link JOCLContentHandler} containing the list of objects described by the JOCL document
     */
    public static JOCLContentHandler parse(File f, XMLReader reader) throws SAXException, FileNotFoundException, IOException {
        return JOCLContentHandler.parse(new FileInputStream(f),reader);
    }

    /**
     * Parses a JOCL document from the specified {@link Reader}, using the specified
     * {@link XMLReader}.
     * The returned {@link JOCLContentHandler} will contain the
     * list of objects described by the file.
     * @param in a {@link Reader} containing the JOCL document
     * @param reader the {@link XMLReader} to use to parse the document
     * @return a {@link JOCLContentHandler} containing the list of objects described by the JOCL document
     */
    public static JOCLContentHandler parse(Reader in, XMLReader reader) throws SAXException, IOException {
        return JOCLContentHandler.parse(new InputSource(in),reader);
    }

    /**
     * Parses a JOCL document from the specified {@link InputStream}, using the specified
     * {@link XMLReader}.
     * The returned {@link JOCLContentHandler} will contain the
     * list of objects described by the file.
     * @param in a {@link InputStream} containing the JOCL document
     * @param reader the {@link XMLReader} to use to parse the document
     * @return a {@link JOCLContentHandler} containing the list of objects described by the JOCL document
     */
    public static JOCLContentHandler parse(InputStream in, XMLReader reader) throws SAXException, IOException {
        return JOCLContentHandler.parse(new InputSource(in),reader);
    }

    /**
     * Parses a JOCL document from the specified {@link InputSource}, using the
     * specified {@link XMLReader}.
     * The returned {@link JOCLContentHandler} will contain the
     * list of objects described by the file.
     * @param in a {@link InputSource} containing the JOCL document
     * @param reader the {@link XMLReader} to use to parse the document
     * @return a {@link JOCLContentHandler} containing the list of objects described by the JOCL document
     */
    public static JOCLContentHandler parse(InputSource in, XMLReader reader) throws SAXException, IOException {
        JOCLContentHandler jocl = new JOCLContentHandler();
        if(null == reader) {
            reader = XMLReaderFactory.createXMLReader();
        }
        reader.setContentHandler(jocl);
        reader.parse(in);
        return jocl;
    }

    //--- Construtors ------------------------------------------------

    /**
     * Equivalent to {@link #JOCLContentHandler(boolean,boolean,boolean,boolean) JOCLContentHandler(true,true,true,true)}.
     */
    public JOCLContentHandler() {
        this(true,true,true,true);
    }

    /**
     * Construct a JOCLContentHandler.
     * @param emptyEltNS when <code>true</code> I should assume any element with an empty namespace is within the JOCL namespace
     * @param joclEltPrefix when <code>true</code> I should assume any element who's prefix is <code>jocl:</code> and who's namespace is empty is within the JOCL namespace
     * @param emptyAttrNS when <code>true</code> I should assume any attribute with an empty namespace is within the JOCL namespace
     * @param joclAttrPrefix when <code>true</code> I should assume any attribute who's prefix is <code>jocl:</code> and who's namespace is empty is within the JOCL namespace
     */
    public JOCLContentHandler(boolean emptyEltNS, boolean joclEltPrefix, boolean emptyAttrNS, boolean joclAttrPrefix) {
        _acceptEmptyNamespaceForElements = emptyEltNS;
        _acceptJoclPrefixForElements = joclEltPrefix;
        _acceptEmptyNamespaceForAttributes = emptyAttrNS;
        _acceptJoclPrefixForAttributes = joclAttrPrefix;
    }

    //--- Public Methods - Accessing Objects -------------------------

    /**
     * Returns the number of values and types in my list.
     * @return the number of values and types in my list.
     */
    public int size() {
        return _typeList.size();
    }

    /**
     * Clears all the values and types in my list.
     */
    public void clear() {
        _typeList = new ArrayList<Class<?>>();
        _valueList = new ArrayList<Object>();
    }

    /**
     * Removes the value/type pair at the specified index.
     */
    public void clear(int i) {
        _typeList.remove(i);
        _valueList.remove(i);
    }

    /**
     * Returns the type of the object at the specified index.
     */
    public Class<?> getType(int i) {
        return _typeList.get(i);
    }

    /**
     * Returns the value of the object at the specified index.
     */
    public Object getValue(int i) {
        return _valueList.get(i);
    }

    /**
     * Returns a shallow copy of my list of values.
     */
    public Object[] getValueArray() {
        return _valueList.toArray();
    }

    /**
     * Returns a shallow copy of my list of types.
     */
    public Object[] getTypeArray() {
        return _typeList.toArray();
    }

    //--- Public Methods - DocumentHandler ---------------------------

    @Override
    public void startElement(String uri, String localName, String qname, Attributes attr) throws SAXException {
        try {
            if(isJoclNamespace(uri,localName,qname)) {
                if(ELT_OBJECT.equals(localName)) {
                    String cname = getAttributeValue(ATT_CLASS,attr);
                    String isnullstr = getAttributeValue(ATT_ISNULL,attr,"false");
                    boolean isnull = ("true".equalsIgnoreCase(isnullstr) || "yes".equalsIgnoreCase(isnullstr));
                    _cur = new ConstructorDetails(cname,_cur,isnull);
                } else if(ELT_ARRAY.equals(localName)) {
                    _cur = new ConstructorDetails(Object[].class,_cur,false,true);
                } else if(ELT_COLLECTION.equals(localName)) {
                    _cur = new ConstructorDetails(Collection.class,_cur,false,true);
                } else if(ELT_LIST.equals(localName)) {
                    _cur = new ConstructorDetails(List.class,_cur,false,true);
                } else if(ELT_BOOLEAN.equals(localName)) {
                    String valstr = getAttributeValue(ATT_VALUE,attr,"false");
                    boolean val = ("true".equalsIgnoreCase(valstr) || "yes".equalsIgnoreCase(valstr));
                    addObject(Boolean.TYPE,Boolean.valueOf(val));
                } else if(ELT_BYTE.equals(localName)) {
                    byte val = Byte.parseByte(getAttributeValue(ATT_VALUE,attr,"0"));
                    addObject(Byte.TYPE, new Byte(val));
                } else if(ELT_CHAR.equals(localName)) {
                    char val = '\u0000';
                    String valstr = getAttributeValue(ATT_VALUE,attr);
                    if(null == valstr) {
                        val = '\u0000';
                    } else if(valstr.length() > 1) {
                        throw new SAXException("if present, char value must be exactly one character long");
                    } else if(valstr.length()==1) {
                        val = valstr.charAt(0);
                    } else if(valstr.length()==0) {
                        throw new SAXException("if present, char value must be exactly one character long");
                    }
                    addObject(Character.TYPE, new Character(val));
                } else if(ELT_DOUBLE.equals(localName)) {
                    double val = Double.parseDouble(getAttributeValue(ATT_VALUE,attr,"0"));
                    addObject(Double.TYPE, new Double(val));
                } else if(ELT_FLOAT.equals(localName)) {
                    float val = Float.parseFloat(getAttributeValue(ATT_VALUE,attr,"0"));
                    addObject(Float.TYPE, new Float(val));
                } else if(ELT_INT.equals(localName)) {
                    int val = Integer.parseInt(getAttributeValue(ATT_VALUE,attr,"0"));
                    addObject(Integer.TYPE, new Integer(val));
                } else if(ELT_LONG.equals(localName)) {
                    long val = Long.parseLong(getAttributeValue(ATT_VALUE,attr,"0"));
                    addObject(Long.TYPE, new Long(val));
                } else if(ELT_SHORT.equals(localName)) {
                    short val = Short.parseShort(getAttributeValue(ATT_VALUE,attr,"0"));
                    addObject(Short.TYPE, new Short(val));
                } else if(ELT_STRING.equals(localName)) {
                    String val = getAttributeValue(ATT_VALUE,attr);
                    addObject("".getClass(),val);
                } else {
                    // unrecognized JOCL element warning?
                }
            }
        } catch(NumberFormatException e) {
            throw new SAXException(e);
        } catch(ClassNotFoundException e) {
            throw new SAXException(e);
        }
    }

    @Override
    public void endElement(String uri, String localName, String qname) throws SAXException {
        try {
            if(isJoclNamespace(uri,localName,qname)) {
                if(ELT_OBJECT.equals(localName) || ELT_ARRAY.equals(localName)
                    || ELT_COLLECTION.equals(localName) || ELT_LIST.equals(localName)) {
                    ConstructorDetails temp = _cur;
                    _cur = _cur.getParent();
                    if(null == _cur) {
                        _typeList.add(temp.getType());
                        _valueList.add(temp.createObject());
                    } else {
                        _cur.addArgument(temp.getType(),temp.createObject());
                    }
                }
                /*
                else if(ELT_BOOLEAN.equals(localName)) {
                    // nothing to do here
                } else if(ELT_BYTE.equals(localName)) {
                    // nothing to do here
                } else if(ELT_CHAR.equals(localName)) {
                    // nothing to do here
                } else if(ELT_DOUBLE.equals(localName)) {
                    // nothing to do here
                } else if(ELT_FLOAT.equals(localName)) {
                    // nothing to do here
                } else if(ELT_INT.equals(localName)) {
                    // nothing to do here
                } else if(ELT_LONG.equals(localName)) {
                    // nothing to do here
                } else if(ELT_SHORT.equals(localName)) {
                    // nothing to do here
                } else if(ELT_STRING.equals(localName)) {
                    // nothing to do here
                } else {
                    // unrecognized JOCL element warning?
                }
                */
            }
        } catch(Exception e) {
            throw new SAXException(e);
        }
    }

    @Override
    public void setDocumentLocator(Locator locator) {
        _locator = locator;
    }

    //--- Protected Methods ------------------------------------------

    /**
     * Returns <code>true</code> if the given attributes define an
     * element within the JOCL namespace (according to my current
     * configuration.)
     *
     * @see #_acceptEmptyNamespaceForElements
     * @see #_acceptJoclPrefixForElements
     */
    protected boolean isJoclNamespace(String uri, @SuppressWarnings("unused") String localname, String qname) {
        if(JOCL_NAMESPACE_URI.equals(uri)) {
            return true;
        } else if(_acceptEmptyNamespaceForElements && (null == uri || "".equals(uri))) {
            return true;
        } else if(_acceptJoclPrefixForElements && (null == uri || "".equals(uri)) && qname.startsWith(JOCL_PREFIX)) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * Equivalent to {@link #getAttributeValue(java.lang.String,org.xml.sax.Attributes,java.lang.String) <code>getAttributeValue(localname,attr,null)</code>}.
     */
    protected String getAttributeValue(String localname, Attributes attr) {
        return getAttributeValue(localname,attr,null);
    }

    /**
     * Returns the value of attribute with the given
     * <code><i>localname</i></code> within the JOCL
     * namespace from the given set of {@link Attributes}.
     * If no such attribute can be found, returns
     * <code><i>implied</i></code>.
     *
     * @param localname the unqualified name of the attribute to look for
     * @param attr      the Attributes in which to find the value
     * @param implied   the default value for the attribute
     * @return the value of attribute with the given
     *         <code><i>localname</i></code> within the JOCL
     *         namespace from the given set of {@link Attributes}.
     *         If no such attribute can be found, returns
     *         <code><i>implied</i></code>.
     */
    protected String getAttributeValue(String localname, Attributes attr, String implied) {
        String val = attr.getValue(JOCL_NAMESPACE_URI,localname);
        if(null == val && _acceptEmptyNamespaceForAttributes) {
            val = attr.getValue("",localname);
        }
        if(null == val && _acceptJoclPrefixForAttributes) {
            val = attr.getValue("",JOCL_PREFIX + localname);
        }
        return(null == val ? implied : val);
    }

    /**
     * Add the specified object either to my type/value list, or
     * as an argument to the object I'm currently constructing.
     */
    protected void addObject(Class<?> type, Object val) {
        if(null == _cur) {
            _typeList.add(type);
            _valueList.add(val);
        } else {
            _cur.addArgument(type,val);
        }
    }

    //--- Protected Attributes ---------------------------------------

    /**
     * The JOCL namespace URI, <code>http://apache.org/xml/xmlns/jakarta/commons/jocl</code>.
     */
    public static final String JOCL_NAMESPACE_URI = "http://apache.org/xml/xmlns/jakarta/commons/jocl";

    /**
     * The default JOCL prefix, <code>jocl:</code>.
     */
    public static final String JOCL_PREFIX = "jocl:";

    /**
     * A list of the types ({@link Class}es) already created via the parse.
     */
    protected ArrayList<Class<?>> _typeList = new ArrayList<Class<?>>();

    /**
     * A list of the values ({@link Object}s) already created via the parse.
     */
    protected ArrayList<Object> _valueList = new ArrayList<Object>();

    /**
     * The object I'm currently working on.
     */
    protected ConstructorDetails _cur = null;

    /**
     * When <code>true</code>, I will treat elements with an
     * empty namespace URI as part of the JOCL namespace.
     *
     * @see #JOCL_NAMESPACE_URI
     */
    protected boolean _acceptEmptyNamespaceForElements = true;

    /**
     * When <code>true</code>, I will treat elements with the
     * {@link #JOCL_PREFIX} but no namespace URI as being
     * mapped to the jocl namespace.
     *
     * @see #JOCL_PREFIX
     * @see #JOCL_NAMESPACE_URI
     */
    protected boolean _acceptJoclPrefixForElements = true;

    /**
     * When <code>true</code>, I will treat attributes with an
     * empty namespace URI as part of the JOCL namespace.
     *
     * @see #JOCL_NAMESPACE_URI
     */
    protected boolean _acceptEmptyNamespaceForAttributes = true;

    /**
     * When <code>true</code>, I will treat attributes with the
     * {@link #JOCL_PREFIX} but no namespace URI as being
     * mapped to the jocl namespace.
     *
     * @see #JOCL_PREFIX
     * @see #JOCL_NAMESPACE_URI
     */
    protected boolean _acceptJoclPrefixForAttributes = true;

    /** My {@link Locator}. */
    protected Locator _locator = null;

    /** The name of the "object" element. */
    protected static final String ELT_OBJECT  = "object";

    /** The name of the "array" element.
     * @since 1.2.2
     */
    protected static final String ELT_ARRAY  = "array";

    /** The name of the "collection" element.
     * @since 1.2.2
     */
    protected static final String ELT_COLLECTION  = "collection";

    /** The name of the "list" element.
     * @since 1.2.2
     */
    protected static final String ELT_LIST = "list";

    /** The name of the "object" element's "class" attribute. */
    protected static final String ATT_CLASS   = "class";

    /** The name of the "object" element's "isnull" attribute. */
    protected static final String ATT_ISNULL  = "null";

    /** The name of the "boolean" element. */
    protected static final String ELT_BOOLEAN = "boolean";

    /** The name of the "byte" element. */
    protected static final String ELT_BYTE    = "byte";

    /** The name of the "char" element. */
    protected static final String ELT_CHAR    = "char";

    /** The name of the "double" element. */
    protected static final String ELT_DOUBLE  = "double";

    /** The name of the "float" element. */
    protected static final String ELT_FLOAT   = "float";

    /** The name of the "int" element. */
    protected static final String ELT_INT     = "int";

    /** The name of the "long" element. */
    protected static final String ELT_LONG    = "long";

    /** The name of the "short" element. */
    protected static final String ELT_SHORT   = "short";

    /** The name of the "string" element. */
    protected static final String ELT_STRING  = "string";

    /** The name of the "value" attribute. */
    protected static final String ATT_VALUE   = "value";

    static class ConstructorDetails {
        private ConstructorDetails _parent = null;
        private Class<?> _type = null;
        private ArrayList<Class<?>> _argTypes = null;
        private ArrayList<Object> _argValues = null;
        private boolean _isnull = false;
        private boolean _isgroup = false;

        public ConstructorDetails(String classname, ConstructorDetails parent) throws ClassNotFoundException {
            this(Class.forName(classname),parent,false,false);
        }

        public ConstructorDetails(String classname, ConstructorDetails parent, boolean isnull) throws ClassNotFoundException {
            this(Class.forName(classname),parent,isnull,false);
        }

        /**
         * @since 1.3
         */
        public ConstructorDetails(String classname, ConstructorDetails parent, boolean isnull, boolean isgroup) throws ClassNotFoundException {
            this(Class.forName(classname),parent,isnull,isgroup);
        }

        /**
         * @since 1.3
         */
        public ConstructorDetails(Class<?> type, ConstructorDetails parent, boolean isnull, boolean isgroup) {
            _parent = parent;
            _type = type;
            _argTypes = new ArrayList<Class<?>>();
            _argValues = new ArrayList<Object>();
            _isnull = isnull;
            _isgroup = isgroup;
        }

        public void addArgument(Object value) {
            addArgument(value.getClass(),value);
        }

        public void addArgument(Class<?> type, Object val) {
            if(_isnull) {
                throw new NullPointerException("can't add arguments to null instances");
            }
            _argTypes.add(type);
            _argValues.add(val);
        }

        public Class<?> getType() {
            return _type;
        }

        public ConstructorDetails getParent() {
            return _parent;
        }

        public Object createObject() throws InstantiationException, IllegalAccessException, InvocationTargetException {
            if(_isnull) {
                return null;
            } else if( _isgroup ) {
                if (_type.equals(Object[].class)) {
                    return _argValues.toArray();
                } else if (_type.equals(Collection.class) || _type.equals(List.class)) {
                    return _argValues;
                } else {
                    throw new IllegalStateException("implementation error: unhandled _type:" + _type);
                }
            } else {
                Class<?> k = getType();
                Class<?>[] argtypes = _argTypes.toArray(new Class[0]);
                Object[] argvals = _argValues.toArray();
                return ConstructorUtil.invokeConstructor(k,argtypes,argvals);
            }
        }
    }

}
