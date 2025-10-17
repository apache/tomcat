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

<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
  version="3.0">

  <xsl:output method="html" html-version="5.0"
    encoding="UTF-8" indent="no"
    doctype-system="about:legacy-compat"/>

  <xsl:template match="listing">
   <html>
    <head>
      <title>
        Sample Directory Listing For
        <xsl:value-of select="@directory"/>
      </title>
      <style>
        h1 {color : white;background-color : #0086b2;}
        h3 {color : white;background-color : #0086b2;}
        body {font-family : sans-serif,Arial,Tahoma;
             color : black;background-color : white;}
        b {color : white;background-color : #0086b2;}
        a {color : black;} HR{color : #0086b2;}
        table td { padding: 5px; }
      </style>
    </head>
    <body>
      <h1>Sample Directory Listing For
            <xsl:value-of select="@directory"/>
      </h1>
      <hr style="height: 1px;" />
      <table style="width: 100%;">
        <tr>
          <th style="text-align: left;">Filename</th>
          <th style="text-align: center;">Size</th>
          <th style="text-align: right;">Last Modified</th>
        </tr>
        <xsl:apply-templates select="entries"/>
        </table>
      <xsl:apply-templates select="readme"/>
      <hr style="height: 1px;" />
      <h3>Apache Tomcat/12.0</h3>
    </body>
   </html>
  </xsl:template>


  <xsl:template match="entries">
    <xsl:apply-templates select="entry"/>
  </xsl:template>

  <xsl:template match="readme">
    <hr style="height: 1px;" />
    <pre><xsl:apply-templates/></pre>
  </xsl:template>

  <xsl:template match="entry">
    <tr>
      <td style="text-align: left;">
        <xsl:variable name="urlPath" select="@urlPath"/>
        <a href="{$urlPath}">
          <pre><xsl:apply-templates/></pre>
        </a>
      </td>
      <td style="text-align: right;">
        <pre><xsl:value-of select="@size"/></pre>
      </td>
      <td style="text-align: right;">
        <pre><xsl:value-of select="@date"/></pre>
      </td>
    </tr>
  </xsl:template>

</xsl:stylesheet>