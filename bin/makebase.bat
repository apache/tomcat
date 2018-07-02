:: Licensed to the Apache Software Foundation (ASF) under one or more
:: contributor license agreements.  See the NOTICE file distributed with
:: this work for additional information regarding copyright ownership.
:: The ASF licenses this file to You under the Apache License, Version 2.0
:: (the "License"); you may not use this file except in compliance with
:: the License.  You may obtain a copy of the License at
::
::     http://www.apache.org/licenses/LICENSE-2.0
::
:: Unless required by applicable law or agreed to in writing, software
:: distributed under the License is distributed on an "AS IS" BASIS,
:: WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
:: See the License for the specific language governing permissions and
:: limitations under the License.

:: This script creates the directory structure required for running Tomcat
:: in a separate directory by pointing %CATALINA_BASE% to it. It copies the
:: conf directory from %CATALINA_HOME%, and creates empty directories for
:: bin, lib, logs, temp, webapps, and work.
::
:: If the file %CATALINA_HOME%/bin/setenv.sh exists then it is copied to
:: the target directory as well.
::
:: Usage: makebase <path-to-target-directory>

@echo off

:: first arg is the target directory
set BASE_TGT=%1

if %BASE_TGT%.==. (
    :: target directory not provided; exit
    echo "Usage: makebase <path-to-target-directory>"
    goto :EOF
)

set CURR_DIR=%~dp0
set HOME_DIR=%CURR_DIR%..\

if exist %BASE_TGT% (
  :: target directory exists
  echo "Target directory exists"

    :: exit if target directory is not empty
    for /F %%i in ('dir /b "%BASE_TGT%\*.*"') do (
        echo "Target directory is not empty"
        goto :EOF
    )
) else (
    :: create the target directory
    mkdir %BASE_TGT%
)

:: create empty directories for bin, lib, logs, temp, webapps, and work
for %%d in (bin, lib, logs, temp, webapps, work) do (
    mkdir "%BASE_TGT%\%%d"
)

:: copy conf directory
robocopy %HOME_DIR%\conf %BASE_TGT%\conf > nul

:: copy setenv.bat if exists
robocopy %HOME_DIR%\bin %BASE_TGT%\bin setenv.bat > nul

echo "Created CATALINA_BASE directory at $BASE_TGT"

echo "Attention: The ports in conf/server.xml might be bound by a "
echo "           different instance. Please review your config files "
echo "           and update them where necessary."

:EOF
