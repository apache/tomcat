<?xml version="1.0" encoding="ISO-8859-1"?>
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

<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
  version="1.0">


  <!-- Output method -->
  <xsl:output method="html"
            encoding="iso-8859-1"
              indent="no"/>


  <!-- Defined parameters (overrideable) -->
  <xsl:param    name="home-name"        select="'The Tomcat JDBC Pool Project'"/>
  <xsl:param    name="home-href"        select="'http://tomcat.apache.org/'"/>
  <xsl:param    name="home-logo"        select="'/images/tomcat.gif'"/>
  <xsl:param    name="printer-logo"     select="'/images/printer.gif'"/>
  <xsl:param    name="apache-logo"      select="'/images/asf-logo.gif'"/>
  <xsl:param    name="relative-path"    select="'.'"/>
  <xsl:param    name="void-image"       select="'/images/void.gif'"/>
  <xsl:param    name="project-menu"     select="'menu'"/>
  <xsl:param    name="standalone"       select="''"/>
  <xsl:param    name="buglink"          select="'http://issues.apache.org/bugzilla/show_bug.cgi?id='"/>
  <xsl:param    name="revlink"          select="'http://svn.apache.org/viewvc?view=rev&amp;rev='"/>

  <!-- Defined variables (non-overrideable) -->
  <xsl:variable name="body-bg"          select="'#ffffff'"/>
  <xsl:variable name="body-fg"          select="'#000000'"/>
  <xsl:variable name="body-link"        select="'#525D76'"/>
  <xsl:variable name="banner-bg"        select="'#525D76'"/>
  <xsl:variable name="banner-fg"        select="'#ffffff'"/>
  <xsl:variable name="sub-banner-bg"    select="'#828DA6'"/>
  <xsl:variable name="sub-banner-fg"    select="'#ffffff'"/>
  <xsl:variable name="source-color"     select="'#023264'"/>
  <xsl:variable name="attributes-color" select="'#023264'"/>
  <xsl:variable name="table-th-bg"      select="'#039acc'"/>
  <xsl:variable name="table-td-bg"      select="'#a0ddf0'"/>

  <!-- Process an entire document into an HTML page -->
  <xsl:template match="document">
  <xsl:variable name="project"
              select="document('project.xml')/project"/>
    <html>
    <head>
    <title><xsl:value-of select="project/title"/> - <xsl:value-of select="properties/title"/></title>
    <xsl:for-each select="properties/author">
      <xsl:variable name="name">
        <xsl:value-of select="."/>
      </xsl:variable>
      <xsl:variable name="email">
        <xsl:value-of select="@email"/>
      </xsl:variable>
      <meta name="author" value="{$name}"/>
      <meta name="email" value="{$email}"/>
    </xsl:for-each>
    <style type="text/css" media="print">
.noPrint {display: none;}
td#mainBody {width: 100%;}
</style>
    </head>

    <body bgcolor="{$body-bg}" text="{$body-fg}" link="{$body-link}"
          alink="{$body-link}" vlink="{$body-link}">

    <table border="0" width="100%" cellspacing="0">

      <xsl:comment>PAGE HEADER</xsl:comment>
      <tr>
        <td>
        <xsl:if test="project/logo">
          <xsl:variable name="alt">
            <xsl:value-of select="project/logo"/>
          </xsl:variable>
          <xsl:variable name="home">
            <xsl:value-of select="project/@href"/>
          </xsl:variable>
          <xsl:variable name="src">
            <xsl:value-of select="$relative-path"/><xsl:value-of select="project/logo/@href"/>
          </xsl:variable>

          <xsl:comment>PROJECT LOGO</xsl:comment>
          <a href="{$home}">
            <img src="{$src}" align="right" alt="{$alt}" border="0"/>
          </a>
        </xsl:if>
        </td>
        <td>
          <font face="arial,helvetica,sanserif">
            <h1><xsl:value-of select="$project/title"/></h1>
          </font>
        </td>
        <td>
          <xsl:comment>APACHE LOGO</xsl:comment>
          <xsl:variable name="src">
            <xsl:value-of select="$relative-path"/><xsl:value-of select="$apache-logo"/>
          </xsl:variable>
          <a href="http://www.apache.org/">
            <img src="{$src}" align="right" alt="Apache Logo" border="0"/>
          </a>
        </td>
      </tr>
    </table>

    <table border="0" width="100%" cellspacing="4">

      <xsl:comment>HEADER SEPARATOR</xsl:comment>
      <tr>
        <td colspan="2">
          <hr noshade="noshade" size="1"/>
        </td>
      </tr>

      <tr>

        <xsl:comment>RIGHT SIDE MAIN BODY</xsl:comment>
        <td width="100%" valign="top" align="left" id="mainBody">
          <h1><xsl:value-of select="project/title"/></h1>
          <h2><xsl:value-of select="properties/title"/></h2>
          <xsl:apply-templates select="body/section"/>
        </td>

      </tr>

      <xsl:comment>FOOTER SEPARATOR</xsl:comment>
      <tr>
        <td colspan="2">
          <hr noshade="noshade" size="1"/>
        </td>
      </tr>

      <xsl:comment>PAGE FOOTER</xsl:comment>
      <tr><td colspan="2">
        <div align="center"><font color="{$body-link}" size="-1"><em>
        Copyright &#169; 1999-2014, Apache Software Foundation
        </em></font></div>
      </td></tr>

    </table>
    </body>
    </html>

  </xsl:template>


  <!-- Process a menu for the navigation bar -->
  <xsl:template match="menu">
    <p><strong><xsl:value-of select="@name"/></strong></p>
    <ul>
      <xsl:apply-templates select="item"/>
    </ul>
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
    <xsl:variable name="name">
      <xsl:value-of select="@name"/>
    </xsl:variable>
    <table border="0" cellspacing="0" cellpadding="2">
      <!-- Section heading -->
      <tr><td bgcolor="{$banner-bg}">
          <font color="{$banner-fg}" face="arial,helvetica.sanserif">
          <a name="{$name}">
          <strong><xsl:value-of select="@name"/></strong></a></font>
      </td></tr>
      <!-- Section body -->
      <tr><td><blockquote>
        <xsl:apply-templates/>
      </blockquote></td></tr>
    </table>
  </xsl:template>


  <!-- Process a documentation subsection -->
  <xsl:template match="subsection">
    <xsl:variable name="name">
      <xsl:value-of select="@name"/>
    </xsl:variable>
    <table border="0" cellspacing="0" cellpadding="2">
      <!-- Subsection heading -->
      <tr><td bgcolor="{$sub-banner-bg}">
          <font color="{$sub-banner-fg}" face="arial,helvetica.sanserif">
          <a name="{$name}">
          <strong><xsl:value-of select="@name"/></strong></a></font>
      </td></tr>
      <!-- Subsection body -->
      <tr><td><blockquote>
        <xsl:apply-templates/>
      </blockquote></td></tr>
    </table>
  </xsl:template>


  <!-- Process a source code example -->
  <xsl:template match="source">
    <xsl:variable name="void">
      <xsl:value-of select="$relative-path"/><xsl:value-of select="$void-image"/>
    </xsl:variable>
    <div align="left">
      <table cellspacing="4" cellpadding="0" border="0">
        <tr>
          <td bgcolor="{$source-color}" width="1" height="1">
            <img src="{$void}" width="1" height="1" vspace="0" hspace="0" border="0"/>
          </td>
          <td bgcolor="{$source-color}" height="1">
            <img src="{$void}" width="1" height="1" vspace="0" hspace="0" border="0"/>
          </td>
          <td bgcolor="{$source-color}" width="1" height="1">
            <img src="{$void}" width="1" height="1" vspace="0" hspace="0" border="0"/>
          </td>
        </tr>
        <tr>
          <td bgcolor="{$source-color}" width="1">
            <img src="{$void}" width="1" height="1" vspace="0" hspace="0" border="0"/>
          </td>
          <td bgcolor="#ffffff" height="1"><pre>
            <xsl:value-of select="."/>
          </pre></td>
          <td bgcolor="{$source-color}" width="1">
            <img src="{$void}" width="1" height="1" vspace="0" hspace="0" border="0"/>
          </td>
        </tr>
        <tr>
          <td bgcolor="{$source-color}" width="1" height="1">
            <img src="{$void}" width="1" height="1" vspace="0" hspace="0" border="0"/>
          </td>
          <td bgcolor="{$source-color}" height="1">
            <img src="{$void}" width="1" height="1" vspace="0" hspace="0" border="0"/>
          </td>
          <td bgcolor="{$source-color}" width="1" height="1">
            <img src="{$void}" width="1" height="1" vspace="0" hspace="0" border="0"/>
          </td>
        </tr>
      </table>
    </div>
  </xsl:template>


  <!-- Process an attributes list with nested attribute elements -->
  <xsl:template match="attributes">
    <table border="1" cellpadding="5">
      <tr>
        <th width="15%" bgcolor="{$attributes-color}">
          <font color="#ffffff">Attribute</font>
        </th>
        <th width="85%" bgcolor="{$attributes-color}">
          <font color="#ffffff">Description</font>
        </th>
      </tr>
      <xsl:for-each select="attribute">
        <tr>
          <td align="left" valign="center">
            <xsl:if test="@required = 'true'">
              <strong><code><xsl:value-of select="@name"/></code></strong>
            </xsl:if>
            <xsl:if test="@required != 'true'">
              <code><xsl:value-of select="@name"/></code>
            </xsl:if>
          </td>
          <td align="left" valign="center">
            <xsl:apply-templates/>
          </td>
        </tr>
      </xsl:for-each>
    </table>
  </xsl:template>

  <!-- Process a properties list with nested property elements -->
  <xsl:template match="properties">
    <table border="1" cellpadding="5">
      <tr>
        <th width="15%" bgcolor="{$attributes-color}">
          <font color="#ffffff">Property</font>
        </th>
        <th width="85%" bgcolor="{$attributes-color}">
          <font color="#ffffff">Description</font>
        </th>
      </tr>
      <xsl:for-each select="property">
        <tr>
          <td align="left" valign="center">
            <code><xsl:value-of select="@name"/></code>
          </td>
          <td align="left" valign="center">
            <xsl:apply-templates/>
          </td>
        </tr>
      </xsl:for-each>
    </table>
  </xsl:template>

  <!-- Fix relative links in printer friendly versions of the docs -->
  <xsl:template match="a">
    <xsl:variable name="href" select="@href"/>
    <xsl:choose>
      <xsl:when test="$standalone = 'standalone'">
        <xsl:apply-templates/>
      </xsl:when>
      <xsl:when test="$project-menu != 'menu' and starts-with(@href,'../')">
        <a href="../{$href}"><xsl:apply-templates/></a>
      </xsl:when>
      <xsl:when test="$project-menu != 'menu' and starts-with(@href,'./') and contains(substring(@href,3),'/')">
        <a href=".{$href}"><xsl:apply-templates/></a>
      </xsl:when>
      <xsl:when test="$project-menu != 'menu' and not(contains(@href,'//')) and not(starts-with(@href,'/')) and not(starts-with(@href,'#')) and contains(@href,'/')">
        <a href="../{$href}"><xsl:apply-templates/></a>
      </xsl:when>
      <xsl:when test="$href != ''">
        <a href="{$href}"><xsl:apply-templates/></a>
      </xsl:when>
      <xsl:otherwise>
        <xsl:variable name="name" select="@name"/>
        <a name="{$name}"><xsl:apply-templates/></a>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>

  <!-- Changelog related tags -->
  <xsl:template match="changelog">
    <table border="0" cellpadding="2" cellspacing="2">
      <xsl:apply-templates/>
    </table>
  </xsl:template>

  <xsl:template match="changelog/add">
    <tr>
      <xsl:variable name="src"><xsl:value-of select="$relative-path"/>/images/add.gif</xsl:variable>
      <td><img alt="add" class="icon" src="{$src}"/></td>
      <td><xsl:apply-templates/></td>
    </tr>
  </xsl:template>

  <xsl:template match="changelog/update">
    <tr>
      <xsl:variable name="src"><xsl:value-of select="$relative-path"/>/images/update.gif</xsl:variable>
      <td><img alt="update" class="icon" src="{$src}"/></td>
      <td><xsl:apply-templates/></td>
    </tr>
  </xsl:template>

  <xsl:template match="changelog/design">
    <tr>
      <xsl:variable name="src"><xsl:value-of select="$relative-path"/>/images/design.gif</xsl:variable>
      <td><img alt="design" class="icon" src="{$src}"/></td>
      <td><xsl:apply-templates/></td>
    </tr>
  </xsl:template>

  <xsl:template match="changelog/docs">
    <tr>
      <xsl:variable name="src"><xsl:value-of select="$relative-path"/>/images/docs.gif</xsl:variable>
      <td><img alt="docs" class="icon" src="{$src}"/></td>
      <td><xsl:apply-templates/></td>
    </tr>
  </xsl:template>

  <xsl:template match="changelog/fix">
    <tr>
      <xsl:variable name="src"><xsl:value-of select="$relative-path"/>/images/fix.gif</xsl:variable>
      <td><img alt="fix" class="icon" src="{$src}"/></td>
      <td><xsl:apply-templates/></td>
    </tr>
  </xsl:template>

  <xsl:template match="changelog/scode">
    <tr>
      <xsl:variable name="src"><xsl:value-of select="$relative-path"/>/images/code.gif</xsl:variable>
      <td><img alt="code" class="icon" src="{$src}"/></td>
      <td><xsl:apply-templates/></td>
    </tr>
  </xsl:template>

  <!-- Process an attributes list with nested attribute elements -->
  <xsl:template match="status">
    <table border="1" cellpadding="5">
      <tr>
        <th width="15%" bgcolor="{$attributes-color}">
          <font color="#ffffff">Priority</font>
        </th>
        <th width="50%" bgcolor="{$attributes-color}">
          <font color="#ffffff">Action Item</font>
        </th>
        <th width="25%" bgcolor="{$attributes-color}">
          <font color="#ffffff">Volunteers</font>
        </th>
        <xsl:for-each select="item">
        <tr>
          <td align="left" valign="center">
            <xsl:value-of select="@priority"/>
          </td>
          <td align="left" valign="center">
            <xsl:apply-templates/>
          </td>
          <td align="left" valign="center">
            <xsl:value-of select="@owner"/>
          </td>
        </tr>
        </xsl:for-each>
      </tr>
    </table>
  </xsl:template>

  <!-- Link to a bug report -->
  <xsl:template match="bug">
      <xsl:variable name="link"><xsl:value-of select="$buglink"/><xsl:value-of select="text()"/></xsl:variable>
      <a href="{$link}"><xsl:apply-templates/></a>
  </xsl:template>

  <!-- Link to a SVN revision report -->
  <xsl:template match="rev">
      <xsl:variable name="link"><xsl:value-of select="$revlink"/><xsl:value-of select="text()"/></xsl:variable>
      <a href="{$link}"><xsl:apply-templates/></a>
  </xsl:template>

  <!-- Process everything else by just passing it through -->
  <xsl:template match="*|@*">
    <xsl:copy>
      <xsl:apply-templates select="@*|*|text()"/>
    </xsl:copy>
  </xsl:template>

</xsl:stylesheet>
