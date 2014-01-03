<?xml version="1.0" encoding="UTF-8"?>
<!--
  Licensed to the Apache Software Foundation (ASF) under one or more
  contributor license agreements.  See the NOTICE file distributed with
  this work for additional information regarding copyright ownership.
  The ASF licenses this file to You under the Apache License, Version 2.0
  (the "License"); you may not use this file except in compliance with
  the License.  You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
-->
<!-- Content Stylesheet for "tomcat-docs" Documentation -->

<!-- $Id: style.xsl 572120 2007-09-02 19:32:11Z markt $ -->

<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
  version="3.0">


  <!-- Output method -->
  <xsl:output method="html"
              html-version="5.0"
              encoding="UTF-8"
              indent="no"
              doctype-system="about:legacy-compat"/>


  <!-- Defined parameters (overrideable) -->
  <xsl:param    name="home-name"        select="'Apache Tomcat'"/>
  <xsl:param    name="home-href"        select="'http://tomcat.apache.org/'"/>
  <xsl:param    name="home-logo"        select="'/images/tomcat.png'"/>
  <xsl:param    name="home-stylesheet"  select="'/images/docs-stylesheet.css'"/>
  <xsl:param    name="apache-logo"      select="'/images/asf-feather.png'"/>
  <xsl:param    name="relative-path"    select="'.'"/>
  <xsl:param    name="buglink"          select="'http://issues.apache.org/bugzilla/show_bug.cgi?id='"/>

  <!-- Defined variables (non-overrideable) -->


  <!-- Process an entire document into an HTML page -->
  <xsl:template match="document">
<html lang="en">
<head>
  <!-- Note: XLST seems to always output a
       <META http-equiv="Content-Type" content="text/html; charset=UTF-8">
       when method="html",
       therefore we can't use
       <meta charset="UTF-8"/>.

       In XHTML, this is not needed as the encoding will be
       specified in the XML declaration.
  -->

  <xsl:variable name="css-src">
    <xsl:value-of select="$relative-path"/><xsl:value-of select="$home-stylesheet"/>
  </xsl:variable>
  <link href="{$css-src}" rel="stylesheet" type="text/css"/>
  <link href="{$relative-path}/images/style.css" rel="stylesheet" type="text/css"/>
  <title><xsl:value-of select="project/title"/> - <xsl:value-of select="properties/title"/></title>
  <xsl:for-each select="properties/author">
    <xsl:variable name="name">
      <xsl:value-of select="."/>
    </xsl:variable>
    <xsl:variable name="email">
      <xsl:value-of select="@email"/>
    </xsl:variable>
    <meta name="author" content="{$name}"/>
  </xsl:for-each>
  </head>

  <body>
  <div id="wrapper">
  <!-- Header -->
  <header><div id="header">
    <div>
      <div>
        <xsl:if test="project/logo">
          <xsl:variable name="src">
            <xsl:value-of select="$relative-path"/><xsl:value-of select="$home-logo"/>
          </xsl:variable>
          <div class="logo noPrint">
            <a href="{project/@href}"><img alt="Tomcat Home" src="{$src}"/></a>
          </div>
        </xsl:if>

        <div style="height: 1px;"/>
        <xsl:variable name="src">
          <xsl:value-of select="$relative-path"/><xsl:value-of select="$apache-logo"/>
        </xsl:variable>
        <div class="asfLogo noPrint">
          <a href="http://www.apache.org/" target="_blank"><img src="{$src}" alt="The Apache Software Foundation" style="width: 266px; height: 83px;"/></a>
        </div>
        <h1><xsl:value-of select="project/title"/></h1>
        <div style="height: 1px;"/>
        <div style="clear: left;"/>
      </div>
    </div>
  </div></header>

  <div id="middle">
    <div>
      <div id="mainLeft" class="noprint">
        <div>
          <!-- Navigation -->
          <nav>
            <xsl:apply-templates select="project/body/menu"/>
          </nav>
        </div>
      </div>
      <div id="mainRight">
        <div id="content">
          <!-- Main Part -->
            <h2><xsl:value-of select="properties/title"/></h2>
            <xsl:apply-templates select="body/section"/>


        </div>
      </div>
    </div>
  </div>

  <!-- Footer -->
  <footer><div id="footer">
    Copyright © 2008-2014, The Apache Software Foundation
  </div></footer>
</div>
</body>
</html>


  </xsl:template>


  <!-- Process a menu for the navigation bar -->
  <xsl:template match="menu">
  <div>
    <h2><strong><xsl:value-of select="@name"/></strong></h2>
    <ul>
      <xsl:apply-templates select="item"/>
    </ul>
  </div>
  </xsl:template>


  <!-- Process a menu item for the navigation bar -->
  <xsl:template match="item">
    <xsl:variable name="href">
      <xsl:value-of select="@href"/>
    </xsl:variable>
    <li><a href="{$href}"><xsl:value-of select="@name"/></a></li>
  </xsl:template>


  <!-- Process a documentation section -->
  <xsl:template match="section">
    <xsl:variable name="name2">
      <xsl:choose>
        <xsl:when test="@anchor">
          <xsl:value-of select="@anchor" />
        </xsl:when>
        <xsl:otherwise>
          <xsl:value-of select="@name"/>
        </xsl:otherwise>
      </xsl:choose>
    </xsl:variable>
    <xsl:variable name="name">
      <xsl:value-of select="translate($name2, ' #', '__')"/>
    </xsl:variable>


    <!-- Section heading -->
    <h3 id="{$name}">
      <xsl:if test="@rtext">
        <!-- Additional right-aligned text cell in section heading. It is used by changelog.xml -->
        <span style="float: right;">
          <xsl:value-of select="@rtext"/>
        </span><xsl:text>&#x20;</xsl:text> <!-- Ensure a space follows after </span> -->
      </xsl:if>
      <xsl:value-of select="@name"/>
    </h3>
    <!-- Section body -->
    <div class="text">
      <xsl:apply-templates/>
    </div>

  </xsl:template>


  <!-- Process a documentation subsection -->
  <xsl:template match="subsection">
    <xsl:variable name="name2">
      <xsl:choose>
        <xsl:when test="@anchor">
          <xsl:value-of select="@anchor" />
        </xsl:when>
        <xsl:otherwise>
          <xsl:if test="
              count(//*[(local-name()='section' or local-name()='subsection') and @name=current()/@name]) &gt; 1
              ">
            <xsl:value-of select="concat(ancestor::section/@name, '/')"/>
          </xsl:if>
          <xsl:value-of select="@name"/>
        </xsl:otherwise>
      </xsl:choose>
    </xsl:variable>
    <xsl:variable name="name">
      <xsl:value-of select="translate($name2, ' #', '__')"/>
    </xsl:variable>


    <div class="subsection">
      <!-- Subsection heading -->
      <!-- TODO: When a <subsection> is nested in another <subsection>,
           the output should be <h5>, not <h4>. Same with <h6>. -->
      <h4 id="{$name}">
        <xsl:value-of select="@name"/>
      </h4>
      <!-- Subsection body -->
      <div class="text">
        <xsl:apply-templates/>
      </div>
    </div>

  </xsl:template>


  <!-- Process a source code example -->
  <xsl:template match="source">
  <div class="codeBox">
    <pre>
      <xsl:if test="@wrapped='true'">
        <xsl:attribute name="class">wrap</xsl:attribute>
      </xsl:if>
      <code><xsl:apply-templates/></code>
    </pre>
  </div>
  </xsl:template>


  <!-- Process an attributes list with nested attribute elements -->
  <xsl:template match="attributes">
    <table class="defaultTable">
      <tr>
        <th style="width: 15%;">
          Attribute
        </th>
        <th style="width: 85%;">
          Description
        </th>
      </tr>
      <xsl:for-each select="attribute">
        <tr>
          <td>
            <xsl:if test="@required = 'true'">
              <strong><code class="attributeName"><xsl:value-of select="@name"/></code></strong>
            </xsl:if>
            <xsl:if test="@required != 'true'">
              <code class="attributeName"><xsl:value-of select="@name"/></code>
            </xsl:if>
          </td>
          <td>
            <xsl:apply-templates/>
          </td>
        </tr>
      </xsl:for-each>
    </table>
  </xsl:template>


  <!-- Warning -->
  <xsl:template match="warn">
    <p>
    <span style="color: #ff0000;">
    <xsl:apply-templates/>
    </span>
    </p>
  </xsl:template>

  <!-- Changelog related tags -->
  <xsl:template match="changelog">
    <ul class="changelog">
      <xsl:apply-templates/>
    </ul>
  </xsl:template>

  <xsl:template match="changelog/add">
    <xsl:variable name="src"><xsl:value-of select="$relative-path"/>/images/add.gif</xsl:variable>
    <li>
      <img alt="Add: " class="icon" src="{$src}"/><xsl:apply-templates/>
    </li>
  </xsl:template>

  <xsl:template match="changelog/update">
    <xsl:variable name="src"><xsl:value-of select="$relative-path"/>/images/update.gif</xsl:variable>
    <li>
      <img alt="Update: " class="icon" src="{$src}"/><xsl:apply-templates/>
    </li>
  </xsl:template>

  <xsl:template match="changelog/design">
    <xsl:variable name="src"><xsl:value-of select="$relative-path"/>/images/design.gif</xsl:variable>
    <li>
      <img alt="Design: " class="icon" src="{$src}"/><xsl:apply-templates/>
    </li>
  </xsl:template>

  <xsl:template match="changelog/docs">
    <xsl:variable name="src"><xsl:value-of select="$relative-path"/>/images/docs.gif</xsl:variable>
    <li>
      <img alt="Docs: " class="icon" src="{$src}"/><xsl:apply-templates/>
    </li>
  </xsl:template>

  <xsl:template match="changelog/fix">
    <xsl:variable name="src"><xsl:value-of select="$relative-path"/>/images/fix.gif</xsl:variable>
    <li>
      <img alt="Fix: " class="icon" src="{$src}"/><xsl:apply-templates/>
    </li>
  </xsl:template>

  <xsl:template match="changelog/scode">
    <xsl:variable name="src"><xsl:value-of select="$relative-path"/>/images/code.gif</xsl:variable>
    <li>
      <img alt="Code: " class="icon" src="{$src}"/><xsl:apply-templates/>
    </li>
  </xsl:template>

  <!-- Link to a bug report -->
  <xsl:template match="bug">
      <xsl:variable name="link"><xsl:value-of select="$buglink"/><xsl:value-of select="text()"/></xsl:variable>
      <a href="{$link}"><xsl:apply-templates/></a>
  </xsl:template>



  <xsl:template match="todo">
    <p class="todo">
      This paragraph has not been written yet, but <b>you</b> can contribute to it.
      <xsl:if test="string-length(@note) > 0">
        The original author left a note attached to this TO-DO item:
        <b><xsl:value-of select="@note"/></b>
      </xsl:if>
    </p>
  </xsl:template>



  <!-- Process everything else by just passing it through -->
  <xsl:template match="*|@*">
    <xsl:copy>
      <xsl:apply-templates select="@*|*|text()"/>
    </xsl:copy>
  </xsl:template>

</xsl:stylesheet>
