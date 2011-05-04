; Licensed to the Apache Software Foundation (ASF) under one or more
; contributor license agreements.  See the NOTICE file distributed with
; this work for additional information regarding copyright ownership.
; The ASF licenses this file to You under the Apache License, Version 2.0
; (the "License"); you may not use this file except in compliance with
; the License.  You may obtain a copy of the License at
;
;     http://www.apache.org/licenses/LICENSE-2.0
;
; Unless required by applicable law or agreed to in writing, software
; distributed under the License is distributed on an "AS IS" BASIS,
; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
; See the License for the specific language governing permissions and
; limitations under the License.

; Tomcat script for Nullsoft Installer
; $Id$

  ;Compression options
  CRCCheck on
  SetCompressor /SOLID lzma

  Name "Apache Tomcat"

  ;Product information
  VIAddVersionKey ProductName "Apache Tomcat"
  VIAddVersionKey CompanyName "Apache Software Foundation"
  VIAddVersionKey LegalCopyright "Copyright (c) 1999-@YEAR@ The Apache Software Foundation"
  VIAddVersionKey FileDescription "Apache Tomcat Installer"
  VIAddVersionKey FileVersion "2.0"
  VIAddVersionKey ProductVersion "@VERSION@"
  VIAddVersionKey Comments "tomcat.apache.org"
  VIAddVersionKey InternalName "apache-tomcat-@VERSION@.exe"
  VIProductVersion @VERSION_NUMBER@

!include "MUI2.nsh"
!include "nsDialogs.nsh"
!include "StrFunc.nsh"
!include "LogicLib.nsh"
!include "FileFunc.nsh"
${StrRep}

Var JavaHome
Var JavaExe
Var JvmDll
Var Arch
Var ResetInstDir
Var TomcatPort
Var TomcatAdminEnable
Var TomcatAdminUsername
Var TomcatAdminPassword
Var TomcatAdminRoles

; Variables that store handles of dialog controls
Var CtlJavaHome
Var CtlTomcatPort
Var CtlTomcatAdminUsername
Var CtlTomcatAdminPassword
Var CtlTomcatAdminRoles

; Handle of the service-install.log file
; It is opened in "Core" section and closed in "-post"
Var ServiceInstallLog

;--------------------------------
;Configuration

  !define MUI_HEADERIMAGE
  !define MUI_HEADERIMAGE_RIGHT
  !define MUI_HEADERIMAGE_BITMAP header.bmp
  !define MUI_WELCOMEFINISHPAGE_BITMAP side_left.bmp
  !define MUI_FINISHPAGE_SHOWREADME "$INSTDIR\webapps\ROOT\RELEASE-NOTES.txt"
  !define MUI_FINISHPAGE_RUN $INSTDIR\bin\tomcat@VERSION_MAJOR@w.exe
  !define MUI_FINISHPAGE_RUN_PARAMETERS //MR//Tomcat@VERSION_MAJOR@
  !define MUI_FINISHPAGE_NOREBOOTSUPPORT

  !define MUI_ABORTWARNING

  !define MUI_ICON tomcat.ico
  !define MUI_UNICON tomcat.ico

  ;General
  OutFile tomcat-installer.exe

  ;Install Options pages
  LangString TEXT_JVM_TITLE ${LANG_ENGLISH} "Java Virtual Machine"
  LangString TEXT_JVM_SUBTITLE ${LANG_ENGLISH} "Java Virtual Machine path selection."
  LangString TEXT_JVM_PAGETITLE ${LANG_ENGLISH} ": Java Virtual Machine path selection"

  LangString TEXT_CONF_TITLE ${LANG_ENGLISH} "Configuration"
  LangString TEXT_CONF_SUBTITLE ${LANG_ENGLISH} "Tomcat basic configuration."
  LangString TEXT_CONF_PAGETITLE ${LANG_ENGLISH} ": Configuration Options"

  LangString TEXT_JVM_LABEL1 ${LANG_ENGLISH} "Please select the path of a Java SE 6.0 or later JRE installed on your system."
  LangString TEXT_CONF_LABEL_PORT ${LANG_ENGLISH} "HTTP/1.1 Connector Port"
  LangString TEXT_CONF_LABEL_ADMIN ${LANG_ENGLISH} "Tomcat Administrator Login (optional)"
  LangString TEXT_CONF_LABEL_ADMINUSERNAME ${LANG_ENGLISH} "User Name"
  LangString TEXT_CONF_LABEL_ADMINPASSWORD ${LANG_ENGLISH} "Password"
  LangString TEXT_CONF_LABEL_ADMINROLES ${LANG_ENGLISH} "Roles"

  ;Install Page order
  !insertmacro MUI_PAGE_WELCOME
  !insertmacro MUI_PAGE_LICENSE INSTALLLICENSE
  ; Use custom onLeave function with COMPONENTS page
  !define MUI_PAGE_CUSTOMFUNCTION_LEAVE pageComponentsLeave
  !insertmacro MUI_PAGE_COMPONENTS
  Page custom pageConfiguration pageConfigurationLeave "$(TEXT_CONF_PAGETITLE)"
  Page custom pageChooseJVM pageChooseJVMLeave "$(TEXT_JVM_PAGETITLE)"
  !insertmacro MUI_PAGE_DIRECTORY
  !insertmacro MUI_PAGE_INSTFILES
  Page custom CheckUserType
  !insertmacro MUI_PAGE_FINISH

  ;Uninstall Page order
  !insertmacro MUI_UNPAGE_CONFIRM
  !insertmacro MUI_UNPAGE_INSTFILES

  ;License dialog
  LicenseData License.rtf

  ;Component-selection page
    ;Descriptions
    LangString DESC_SecTomcat ${LANG_ENGLISH} "Install the Tomcat Servlet container as a Windows service."
    LangString DESC_SecTomcatCore ${LANG_ENGLISH} "Install the Tomcat Servlet container core and create the Windows service."
    LangString DESC_SecTomcatService ${LANG_ENGLISH} "Automatically start the Tomcat service when the computer is started."
    LangString DESC_SecTomcatNative ${LANG_ENGLISH} "Install APR based Tomcat native .dll for better performance and scalability in production environments."
    LangString DESC_SecMenu ${LANG_ENGLISH} "Create a Start Menu program group for Tomcat."
    LangString DESC_SecDocs ${LANG_ENGLISH} "Install the Tomcat documentation bundle. This includes documentation on the servlet container and its configuration options, on the Jasper JSP page compiler, as well as on the native webserver connectors."
    LangString DESC_SecManager ${LANG_ENGLISH} "Install the Tomcat Manager administrative web application."
    LangString DESC_SecHostManager ${LANG_ENGLISH} "Install the Tomcat Host Manager administrative web application."
    LangString DESC_SecExamples ${LANG_ENGLISH} "Install the Servlet and JSP examples web application."

  ;Language
  !insertmacro MUI_LANGUAGE English

  ;Install types
  InstType Normal
  InstType Minimum
  InstType Full

  ; Main registry key
  InstallDirRegKey HKLM "SOFTWARE\Apache Software Foundation\Tomcat\@VERSION_MAJOR_MINOR@" ""

  ReserveFile "${NSISDIR}\Plugins\System.dll"
  ReserveFile "${NSISDIR}\Plugins\nsDialogs.dll"
  ReserveFile confinstall\server_1.xml
  ReserveFile confinstall\server_2.xml
  ReserveFile confinstall\tomcat-users_1.xml
  ReserveFile confinstall\tomcat-users_2.xml

;--------------------------------
;Installer Sections

SubSection "Tomcat" SecTomcat

Section "Core" SecTomcatCore

  SectionIn 1 2 3 RO

  ${If} ${Silent}
    Call checkJava
  ${EndIf}

  SetOutPath $INSTDIR
  File tomcat.ico
  File LICENSE
  File NOTICE
  SetOutPath $INSTDIR\lib
  File /r lib\*.*
  ; Note: just calling 'SetOutPath' will create the empty folders for us
  SetOutPath $INSTDIR\logs
  SetOutPath $INSTDIR\work
  SetOutPath $INSTDIR\temp
  SetOutPath $INSTDIR\bin
  File bin\bootstrap.jar
  File bin\tomcat-juli.jar
  SetOutPath $INSTDIR\conf
  File conf\*.*
  SetOutPath $INSTDIR\webapps\ROOT
  File /r webapps\ROOT\*.*

  Call configure

  DetailPrint "Using Jvm: $JavaHome"

  SetOutPath $INSTDIR\bin
  File bin\tomcat@VERSION_MAJOR@w.exe

  ; Get the current platform x86 / AMD64 / IA64
  ${If} $Arch == "x86"
    File /oname=tomcat@VERSION_MAJOR@.exe bin\tomcat@VERSION_MAJOR@.exe
  ${ElseIf} $Arch == "x64"
    File /oname=tomcat@VERSION_MAJOR@.exe bin\x64\tomcat@VERSION_MAJOR@.exe
  ${ElseIf} $Arch == "i64"
    File /oname=tomcat@VERSION_MAJOR@.exe bin\i64\tomcat@VERSION_MAJOR@.exe
  ${EndIf}

  FileOpen $ServiceInstallLog "$INSTDIR\logs\service-install.log" a
  FileSeek $ServiceInstallLog 0 END

  InstallRetry:
  FileWrite $ServiceInstallLog '"$INSTDIR\bin\tomcat@VERSION_MAJOR@.exe" //IS//Tomcat@VERSION_MAJOR@ --DisplayName "Apache Tomcat @VERSION_MAJOR@" --Description "Apache Tomcat @VERSION@ Server - http://tomcat.apache.org/" --LogPath "$INSTDIR\logs" --Install "$INSTDIR\bin\tomcat@VERSION_MAJOR@.exe" --Jvm "$JvmDll" --StartPath "$INSTDIR" --StopPath "$INSTDIR"'
  FileWrite $ServiceInstallLog "$\r$\n"
  ClearErrors
  DetailPrint "Installing Tomcat@VERSION_MAJOR@ service"
  nsExec::ExecToStack '"$INSTDIR\bin\tomcat@VERSION_MAJOR@.exe" //IS//Tomcat@VERSION_MAJOR@ --DisplayName "Apache Tomcat @VERSION_MAJOR@" --Description "Apache Tomcat @VERSION@ Server - http://tomcat.apache.org/" --LogPath "$INSTDIR\logs" --Install "$INSTDIR\bin\tomcat@VERSION_MAJOR@.exe" --Jvm "$JvmDll" --StartPath "$INSTDIR" --StopPath "$INSTDIR"'
  Pop $0
  Pop $1
  StrCmp $0 "0" InstallOk
    FileWrite $ServiceInstallLog "Install failed: $0 $1$\r$\n"
    MessageBox MB_ABORTRETRYIGNORE|MB_ICONSTOP \
      "Failed to install Tomcat@VERSION_MAJOR@ service.$\r$\nCheck your settings and permissions.$\r$\nIgnore and continue anyway (not recommended)?" \
       /SD IDIGNORE IDIGNORE InstallOk IDRETRY InstallRetry
  Quit
  InstallOk:
  ClearErrors

  ; Will be closed in "-post" section
  ; FileClose $ServiceInstallLog
SectionEnd

Section "Service Startup" SecTomcatService

  SectionIn 3

  ${If} $ServiceInstallLog != ""
    FileWrite $ServiceInstallLog '"$INSTDIR\bin\tomcat@VERSION_MAJOR@.exe" //US//Tomcat@VERSION_MAJOR@ --Startup auto'
    FileWrite $ServiceInstallLog "$\r$\n"
  ${EndIf}
  DetailPrint "Configuring Tomcat@VERSION_MAJOR@ service"
  nsExec::ExecToLog '"$INSTDIR\bin\tomcat@VERSION_MAJOR@.exe" //US//Tomcat@VERSION_MAJOR@ --Startup auto'
  ; Behave like Apache Httpd (put the icon in tray on login)
  WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Run" "ApacheTomcatMonitor@VERSION_MAJOR_MINOR@" '"$INSTDIR\bin\tomcat@VERSION_MAJOR@w.exe" //MS//Tomcat@VERSION_MAJOR@'

  ClearErrors

SectionEnd

Section "Native" SecTomcatNative

  SectionIn 3

  SetOutPath $INSTDIR\bin

  ${If} $Arch == "x86"
    File bin\tcnative-1.dll
  ${ElseIf} $Arch == "x64"
    File /oname=tcnative-1.dll bin\x64\tcnative-1.dll
  ${ElseIf} $Arch == "i64"
    File /oname=tcnative-1.dll bin\i64\tcnative-1.dll
  ${EndIf}

  ClearErrors

SectionEnd

SubSectionEnd

Section "Start Menu Items" SecMenu

  SectionIn 1 2 3

  Call createShortcuts
SectionEnd

Section "Documentation" SecDocs

  SectionIn 1 3
  SetOutPath $INSTDIR\webapps\docs
  File /r webapps\docs\*.*

SectionEnd

Section "Manager" SecManager

  SectionIn 1 3

  SetOverwrite on
  SetOutPath $INSTDIR\webapps\manager
  File /r webapps\manager\*.*

SectionEnd

Section "Host Manager" SecHostManager

  SectionIn 3

  SetOverwrite on
  SetOutPath $INSTDIR\webapps\host-manager
  File /r webapps\host-manager\*.*

SectionEnd

Section "Examples" SecExamples

  SectionIn 3

  SetOverwrite on
  SetOutPath $INSTDIR\webapps\examples
  File /r webapps\examples\*.*

SectionEnd

Section -post
  ${If} $ServiceInstallLog != ""
    FileWrite $ServiceInstallLog '"$INSTDIR\bin\tomcat@VERSION_MAJOR@.exe" //US//Tomcat@VERSION_MAJOR@ --Classpath "$INSTDIR\bin\bootstrap.jar;$INSTDIR\bin\tomcat-juli.jar" --StartClass org.apache.catalina.startup.Bootstrap --StopClass org.apache.catalina.startup.Bootstrap --StartParams start --StopParams stop  --StartMode jvm --StopMode jvm'
    FileWrite $ServiceInstallLog "$\r$\n"
    FileWrite $ServiceInstallLog '"$INSTDIR\bin\tomcat@VERSION_MAJOR@.exe" //US//Tomcat@VERSION_MAJOR@ --JvmOptions "-Dcatalina.home=$INSTDIR#-Dcatalina.base=$INSTDIR#-Djava.endorsed.dirs=$INSTDIR\endorsed#-Djava.io.tmpdir=$INSTDIR\temp#-Djava.util.logging.manager=org.apache.juli.ClassLoaderLogManager#-Djava.util.logging.config.file=$INSTDIR\conf\logging.properties"'
    FileWrite $ServiceInstallLog "$\r$\n"
    FileWrite $ServiceInstallLog '"$INSTDIR\bin\tomcat@VERSION_MAJOR@.exe" //US//Tomcat@VERSION_MAJOR@ --StdOutput auto --StdError auto'
    FileWrite $ServiceInstallLog "$\r$\n"
    FileClose $ServiceInstallLog
  ${EndIf}

  DetailPrint "Configuring Tomcat@VERSION_MAJOR@ service"
  nsExec::ExecToLog '"$INSTDIR\bin\tomcat@VERSION_MAJOR@.exe" //US//Tomcat@VERSION_MAJOR@ --Classpath "$INSTDIR\bin\bootstrap.jar;$INSTDIR\bin\tomcat-juli.jar" --StartClass org.apache.catalina.startup.Bootstrap --StopClass org.apache.catalina.startup.Bootstrap --StartParams start --StopParams stop  --StartMode jvm --StopMode jvm'
  nsExec::ExecToLog '"$INSTDIR\bin\tomcat@VERSION_MAJOR@.exe" //US//Tomcat@VERSION_MAJOR@ --JvmOptions "-Dcatalina.home=$INSTDIR#-Dcatalina.base=$INSTDIR#-Djava.endorsed.dirs=$INSTDIR\endorsed#-Djava.io.tmpdir=$INSTDIR\temp#-Djava.util.logging.manager=org.apache.juli.ClassLoaderLogManager#-Djava.util.logging.config.file=$INSTDIR\conf\logging.properties"'
  nsExec::ExecToLog '"$INSTDIR\bin\tomcat@VERSION_MAJOR@.exe" //US//Tomcat@VERSION_MAJOR@ --StdOutput auto --StdError auto'

  WriteUninstaller "$INSTDIR\Uninstall.exe"

  WriteRegStr HKLM "SOFTWARE\Apache Software Foundation\Tomcat\@VERSION_MAJOR_MINOR@" "InstallPath" $INSTDIR
  WriteRegStr HKLM "SOFTWARE\Apache Software Foundation\Tomcat\@VERSION_MAJOR_MINOR@" "Version" @VERSION@
  WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\Apache Tomcat @VERSION_MAJOR_MINOR@" \
                   "DisplayName" "Apache Tomcat @VERSION_MAJOR_MINOR@ (remove only)"
  WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\Apache Tomcat @VERSION_MAJOR_MINOR@" \
                   "UninstallString" '"$INSTDIR\Uninstall.exe"'

SectionEnd

Function .onInit
  ${GetParameters} $R0
  ClearErrors

  ${GetOptions} "$R0" "/?" $R1
  ${IfNot} ${Errors}
    MessageBox MB_OK|MB_ICONINFORMATION 'Available options:$\r$\n\
               /S - Silent install.$\r$\n\
               /D=INSTDIR - Specify installation directory.'
    Abort
  ${EndIf}
  ClearErrors

  StrCpy $ResetInstDir "$INSTDIR"

  ;Initialize default values
  StrCpy $JavaHome ""
  StrCpy $TomcatPort "8080"
  StrCpy $TomcatAdminEnable "0"
  StrCpy $TomcatAdminUsername ""
  StrCpy $TomcatAdminPassword ""
  StrCpy $TomcatAdminRoles ""
FunctionEnd

Function pageChooseJVM
  !insertmacro MUI_HEADER_TEXT "$(TEXT_JVM_TITLE)" "$(TEXT_JVM_SUBTITLE)"
  ${If} $JavaHome == ""
    Call findJavaHome
    Pop $JavaHome
  ${EndIf}

  nsDialogs::Create 1018
  Pop $R0

  ${NSD_CreateLabel} 0 5u 100% 25u "$(TEXT_JVM_LABEL1)"
  Pop $R0
  ${NSD_CreateDirRequest} 0 65u 280u 13u "$JavaHome"
  Pop $CtlJavaHome
  ${NSD_CreateBrowseButton} 282u 65u 15u 13u "..."
  Pop $R0
  ${NSD_OnClick} $R0 pageChooseJVM_onDirBrowse

  ${NSD_SetFocus} $CtlJavaHome
  nsDialogs::Show
FunctionEnd

; onClick function for button next to DirRequest control
Function pageChooseJVM_onDirBrowse
  ; R0 is HWND of the button, it is on top of the stack
  Pop $R0

  ${NSD_GetText} $CtlJavaHome $R1
  nsDialogs::SelectFolderDialog "" "$R1"
  Pop $R1

  ${If} $R1 != "error"
    ${NSD_SetText} $CtlJavaHome $R1
  ${EndIf}
FunctionEnd

Function pageChooseJVMLeave
  ${NSD_GetText} $CtlJavaHome $JavaHome
  ${If} $JavaHome == ""
    Abort
  ${EndIf}

  Call checkJava
FunctionEnd

; onLeave function for the COMPONENTS page
; It updates options based on what components were selected.
;
Function pageComponentsLeave
  StrCpy $TomcatAdminEnable "0"
  StrCpy $TomcatAdminRoles ""

  SectionGetFlags ${SecManager} $0
  IntOp $0 $0 & ${SF_SELECTED}
  ${If} $0 <> 0
    StrCpy $TomcatAdminEnable "1"
    StrCpy $TomcatAdminRoles "manager-gui"
  ${EndIf}

  SectionGetFlags ${SecHostManager} $0
  IntOp $0 $0 & ${SF_SELECTED}
  ${If} $0 <> 0
    StrCpy $TomcatAdminEnable "1"
    ${If} $TomcatAdminRoles != ""
      StrCpy $TomcatAdminRoles "admin-gui,$TomcatAdminRoles"
    ${Else}
      StrCpy $TomcatAdminRoles "admin-gui"
    ${EndIf}
  ${EndIf}
FunctionEnd

Function pageConfiguration
  !insertmacro MUI_HEADER_TEXT "$(TEXT_CONF_TITLE)" "$(TEXT_CONF_SUBTITLE)"

  nsDialogs::Create 1018
  Pop $R0

  ${NSD_CreateLabel} 0 5u 100u 15u "$(TEXT_CONF_LABEL_PORT)"
  Pop $R0

  ${NSD_CreateText} 150u 5u 50u 13u "$TomcatPort"
  Pop $CtlTomcatPort
  ${NSD_SetTextLimit} $CtlTomcatPort 5

  ${If} $TomcatAdminEnable == "1"
    ${NSD_CreateLabel} 0 30u 100% 15u "$(TEXT_CONF_LABEL_ADMIN)"
    Pop $R0
    ${NSD_CreateLabel} 10u 50u 140u 15u "$(TEXT_CONF_LABEL_ADMINUSERNAME)"
    Pop $R0
    ${NSD_CreateText} 150u 50u 110u 13u "$TomcatAdminUsername"
    Pop $CtlTomcatAdminUsername
    ${NSD_CreateLabel} 10u 70u 140u 15u "$(TEXT_CONF_LABEL_ADMINPASSWORD)"
    Pop $R0
    ${NSD_CreatePassword} 150u 70u 110u 13u "$TomcatAdminPassword"
    Pop $CtlTomcatAdminPassword
    ${NSD_CreateLabel} 10u 90u 140u 15u "$(TEXT_CONF_LABEL_ADMINROLES)"
    Pop $R0
    ${NSD_CreateText} 150u 90u 110u 13u "$TomcatAdminRoles"
    Pop $CtlTomcatAdminRoles
  ${EndIf}

  ${NSD_SetFocus} $CtlTomcatPort
  nsDialogs::Show
FunctionEnd

Function pageConfigurationLeave
  ${NSD_GetText} $CtlTomcatPort $TomcatPort
  ${If} $TomcatAdminEnable == "1"
    ${NSD_GetText} $CtlTomcatAdminUsername $TomcatAdminUsername
    ${NSD_GetText} $CtlTomcatAdminPassword $TomcatAdminPassword
    ${NSD_GetText} $CtlTomcatAdminRoles $TomcatAdminRoles
  ${EndIf}
FunctionEnd

; Not used
;Function Void
;FunctionEnd

;--------------------------------
;Descriptions

!insertmacro MUI_FUNCTION_DESCRIPTION_BEGIN
  !insertmacro MUI_DESCRIPTION_TEXT ${SecTomcat} $(DESC_SecTomcat)
  !insertmacro MUI_DESCRIPTION_TEXT ${SecTomcatCore} $(DESC_SecTomcatCore)
  !insertmacro MUI_DESCRIPTION_TEXT ${SecTomcatService} $(DESC_SecTomcatService)
  !insertmacro MUI_DESCRIPTION_TEXT ${SecTomcatNative} $(DESC_SecTomcatNative)
  !insertmacro MUI_DESCRIPTION_TEXT ${SecMenu} $(DESC_SecMenu)
  !insertmacro MUI_DESCRIPTION_TEXT ${SecDocs} $(DESC_SecDocs)
  !insertmacro MUI_DESCRIPTION_TEXT ${SecManager} $(DESC_SecManager)
  !insertmacro MUI_DESCRIPTION_TEXT ${SecHostManager} $(DESC_SecHostManager)
  !insertmacro MUI_DESCRIPTION_TEXT ${SecExamples} $(DESC_SecExamples)
!insertmacro MUI_FUNCTION_DESCRIPTION_END


; =====================
; CheckUserType Function
; =====================
;
; Check the user type, and warn if it's not an administrator.
; Taken from Examples/UserInfo that ships with NSIS.
Function CheckUserType
  ClearErrors
  UserInfo::GetName
  IfErrors Win9x
  Pop $0
  UserInfo::GetAccountType
  Pop $1
  StrCmp $1 "Admin" 0 +3
    ; This is OK, do nothing
    Goto done

    MessageBox MB_OK|MB_ICONEXCLAMATION 'Note: the current user is not an administrator. \
               To run Tomcat as a Windows service, you must be an administrator. \
               You can still run Tomcat from the command-line as this type of user.'
    Goto done

  Win9x:
    # This one means you don't need to care about admin or
    # not admin because Windows 9x doesn't either
    MessageBox MB_OK "Error! This DLL can't run under Windows 9x!"

  done:
FunctionEnd

; ==================
; checkJava Function
; ==================
;
; Checks that a valid JVM has been specified or a suitable default is available
; Sets $JavaHome, $JavaExe and $JvmDll accordingly
; Determines if the JVM is 32-bit or 64-bit and sets $Arch accordingly. For
; 64-bit JVMs, also determines if it is x64 or ia64
Function checkJava

  ${If} $JavaHome == ""
    ; E.g. if a silent install
    Call findJavaHome
    Pop $JavaHome
  ${EndIf}

  ${If} $JavaHome == ""
  ${OrIfNot} ${FileExists} "$JavaHome\bin\java.exe"
    IfSilent +2
    MessageBox MB_OK|MB_ICONSTOP "No Java Virtual Machine found in folder:$\r$\n$JavaHome"
    DetailPrint "No Java Virtual Machine found in folder:$\r$\n$JavaHome"
    Quit
  ${EndIf}

  StrCpy "$JavaExe" "$JavaHome\bin\java.exe"

  ; Need path to jvm.dll to configure the service - uses $JavaHome
  Call findJVMPath
  Pop $5
  ${If} $5 == ""
    IfSilent +2
    MessageBox MB_OK|MB_ICONSTOP "No Java Virtual Machine found in folder:$\r$\n$5"
    DetailPrint "No Java Virtual Machine found in folder:$\r$\n$5"
    Quit
  ${EndIf}

  StrCpy "$JvmDll" $5

  ; Read PE header of JvmDll to check for architecture
  ; 1. Jump to 0x3c and read offset of PE header
  ; 2. Jump to offset. Read PE header signature. It must be 'PE'\0\0 (50 45 00 00).
  ; 3. The next word gives the machine type.
  ; 0x014c: x86
  ; 0x8664: x64
  ; 0x0200: i64
  ClearErrors
  FileOpen $R1 "$JvmDll" r
  IfErrors WrongPEHeader

  FileSeek $R1 0x3c SET
  FileReadByte $R1 $R2
  FileReadByte $R1 $R3
  IntOp $R3 $R3 << 8
  IntOp $R2 $R2 + $R3

  FileSeek $R1 $R2 SET
  FileReadByte $R1 $R2
  IntCmp $R2 0x50 +1 WrongPEHeader WrongPEHeader
  FileReadByte $R1 $R2
  IntCmp $R2 0x45 +1 WrongPEHeader WrongPEHeader
  FileReadByte $R1 $R2
  IntCmp $R2 0 +1 WrongPEHeader WrongPEHeader
  FileReadByte $R1 $R2
  IntCmp $R2 0 +1 WrongPEHeader WrongPEHeader

  FileReadByte $R1 $R2
  FileReadByte $R1 $R3
  IntOp $R3 $R3 << 8
  IntOp $R2 $R2 + $R3

  IntCmp $R2 0x014c +1 +3 +3
  StrCpy "$Arch" "x86"
  Goto DonePEHeader

  IntCmp $R2 0x8664 +1 +3 +3
  StrCpy "$Arch" "x64"
  Goto DonePEHeader

  IntCmp $R2 0x0200 +1 +3 +3
  StrCpy "$Arch" "i64"
  Goto DonePEHeader

WrongPEHeader:
  IfSilent +2
  MessageBox MB_OK|MB_ICONEXCLAMATION 'Cannot read PE header from "$JvmDll"$\r$\nWill assume that the architecture is x86.'
  DetailPrint 'Cannot read PE header from "$JvmDll". Assuming the architecture is x86.'
  StrCpy "$Arch" "x86"

DonePEHeader:
  FileClose $R1

  DetailPrint 'Architecture: "$Arch"'

  StrCpy $INSTDIR "$ResetInstDir"

  ; The default varies depending on 32-bit or 64-bit
  ${If} "$INSTDIR" == ""
    ${If} $Arch == "x86"
      StrCpy $INSTDIR "$PROGRAMFILES32\Apache Software Foundation\Tomcat @VERSION_MAJOR_MINOR@"
    ${Else}
      StrCpy $INSTDIR "$PROGRAMFILES64\Apache Software Foundation\Tomcat @VERSION_MAJOR_MINOR@"
    ${EndIf}
  ${EndIf}

FunctionEnd


; =====================
; findJavaHome Function
; =====================
;
; Find the JAVA_HOME used on the system, and put the result on the top of the
; stack
; Will return an empty string if the path cannot be determined
;
Function findJavaHome

  ClearErrors

  ; Use the 64-bit registry first on 64-bit machines
  ExpandEnvStrings $0 "%PROGRAMW6432%"
  ${If} $0 != "%PROGRAMW6432%"
    SetRegView 64
    ReadRegStr $2 HKLM "SOFTWARE\JavaSoft\Java Runtime Environment" "CurrentVersion"
    ReadRegStr $1 HKLM "SOFTWARE\JavaSoft\Java Runtime Environment\$2" "JavaHome"
    ReadRegStr $3 HKLM "SOFTWARE\JavaSoft\Java Runtime Environment\$2" "RuntimeLib"

    IfErrors 0 +2
    StrCpy $1 ""
    ClearErrors
  ${EndIf}

  ; If no 64-bit Java was found, look for 32-bit Java
  ${If} $1 == ""
    SetRegView 32
    ReadRegStr $2 HKLM "SOFTWARE\JavaSoft\Java Runtime Environment" "CurrentVersion"
    ReadRegStr $1 HKLM "SOFTWARE\JavaSoft\Java Runtime Environment\$2" "JavaHome"
    ReadRegStr $3 HKLM "SOFTWARE\JavaSoft\Java Runtime Environment\$2" "RuntimeLib"

    IfErrors 0 +2
    StrCpy $1 ""
    ClearErrors
    
    ; If using 64-bit, go back to using 64-bit registry
    ${If} $0 != "%PROGRAMW6432%"
      SetRegView 64
    ${EndIf}
  ${EndIf}

  ; Put the result in the stack
  Push $1

FunctionEnd


; ====================
; FindJVMPath Function
; ====================
;
; Find the full JVM path, and put the result on top of the stack
; Implicit argument: $JavaHome
; Will return an empty string if the path cannot be determined
;
Function findJVMPath

  ClearErrors

  ;Step one: Is this a JRE path (Program Files\Java\XXX)
  StrCpy $1 "$JavaHome"

  StrCpy $2 "$1\bin\hotspot\jvm.dll"
  IfFileExists "$2" FoundJvmDll
  StrCpy $2 "$1\bin\server\jvm.dll"
  IfFileExists "$2" FoundJvmDll
  StrCpy $2 "$1\bin\client\jvm.dll"
  IfFileExists "$2" FoundJvmDll
  StrCpy $2 "$1\bin\classic\jvm.dll"
  IfFileExists "$2" FoundJvmDll

  ;Step two: Is this a JDK path (Program Files\XXX\jre)
  StrCpy $1 "$JavaHome\jre"

  StrCpy $2 "$1\bin\hotspot\jvm.dll"
  IfFileExists "$2" FoundJvmDll
  StrCpy $2 "$1\bin\server\jvm.dll"
  IfFileExists "$2" FoundJvmDll
  StrCpy $2 "$1\bin\client\jvm.dll"
  IfFileExists "$2" FoundJvmDll
  StrCpy $2 "$1\bin\classic\jvm.dll"
  IfFileExists "$2" FoundJvmDll

  ClearErrors
  ;Step tree: Read defaults from registry

  ReadRegStr $1 HKLM "SOFTWARE\JavaSoft\Java Runtime Environment" "CurrentVersion"
  ReadRegStr $2 HKLM "SOFTWARE\JavaSoft\Java Runtime Environment\$1" "RuntimeLib"

  IfErrors 0 FoundJvmDll
  StrCpy $2 ""

  FoundJvmDll:
  ClearErrors

  ; Put the result in the stack
  Push $2

FunctionEnd


; ==================
; Configure Function
; ==================
;
; Writes server.xml and tomcat-users.xml
;
Function configure
  StrCpy $R4 'port="$TomcatPort"'
  StrCpy $R5 ''

  ${If} $TomcatAdminEnable == "1"
  ${AndIf} "$TomcatAdminUsername" != ""
  ${AndIf} "$TomcatAdminPassword" != ""
  ${AndIf} "$TomcatAdminRoles" != ""
    ; Escape XML
    Push $TomcatAdminUsername
    Call xmlEscape
    Pop $R1
    Push $TomcatAdminPassword
    Call xmlEscape
    Pop $R2
    Push $TomcatAdminRoles
    Call xmlEscape
    Pop $R3
    StrCpy $R5 '<user name="$R1" password="$R2" roles="$R3" />$\r$\n'
    DetailPrint 'Admin user added: "$TomcatAdminUsername"'
  ${EndIf}

  DetailPrint 'HTTP/1.1 Connector configured on port "$TomcatPort"'

  ; Extract these fragments to $PLUGINSDIR. That is a temporary directory,
  ; that is automatically deleted when the installer exits.
  InitPluginsDir
  SetOutPath $PLUGINSDIR
  File confinstall\server_1.xml
  File confinstall\server_2.xml
  File confinstall\tomcat-users_1.xml
  File confinstall\tomcat-users_2.xml

  ; Build final server.xml
  Delete "$INSTDIR\conf\server.xml"
  DetailPrint "Writing server.xml"
  FileOpen $R9 "$INSTDIR\conf\server.xml" w

  Push "$PLUGINSDIR\server_1.xml"
  Call copyFile
  FileWrite $R9 $R4
  Push "$PLUGINSDIR\server_2.xml"
  Call copyFile

  FileClose $R9
  DetailPrint "server.xml written"

  ; Build final tomcat-users.xml
  Delete "$INSTDIR\conf\tomcat-users.xml"
  DetailPrint "Writing tomcat-users.xml"
  FileOpen $R9 "$INSTDIR\conf\tomcat-users.xml" w
  ; File will be written using current windows codepage
  System::Call 'Kernel32::GetACP() i .r18'
  ${If} $R8 == "932"
    ; Special case where Java uses non-standard name for character set
    FileWrite $R9 "<?xml version='1.0' encoding='ms$R8'?>$\r$\n"
  ${Else}
    FileWrite $R9 "<?xml version='1.0' encoding='cp$R8'?>$\r$\n"
  ${EndIf}
  Push "$PLUGINSDIR\tomcat-users_1.xml"
  Call copyFile
  FileWrite $R9 $R5
  Push "$PLUGINSDIR\tomcat-users_2.xml"
  Call copyFile

  FileClose $R9
  DetailPrint "tomcat-users.xml written"

  Delete "$PLUGINSDIR\server_1.xml"
  Delete "$PLUGINSDIR\server_2.xml"
  Delete "$PLUGINSDIR\tomcat-users_1.xml"
  Delete "$PLUGINSDIR\tomcat-users_2.xml"
FunctionEnd


Function xmlEscape
  Pop $0
  ${StrRep} $0 $0 "&" "&amp;"
  ${StrRep} $0 $0 "$\"" "&quot;"
  ${StrRep} $0 $0 "<" "&lt;"
  ${StrRep} $0 $0 ">" "&gt;"
  Push $0
FunctionEnd


; =================
; CopyFile Function
; =================
;
; Copy specified file contents to $R9
;
Function copyFile

  ClearErrors

  Pop $0

  FileOpen $1 $0 r

 NoError:

  FileRead $1 $2
  IfErrors EOF 0
  FileWrite $R9 $2

  IfErrors 0 NoError

 EOF:

  FileClose $1

  ClearErrors

FunctionEnd


; =================
; createShortcuts Function
; =================
;
; This is called by the SecMenu section.
;
; The code is moved here, because ${SecManager} etc. are not visible
; in SecMenu, because they are defined lower than it.
;
Function createShortcuts

  SetOutPath "$SMPROGRAMS\Apache Tomcat @VERSION_MAJOR_MINOR@"

  CreateShortCut "$SMPROGRAMS\Apache Tomcat @VERSION_MAJOR_MINOR@\Tomcat Home Page.lnk" \
                 "http://tomcat.apache.org/"

  CreateShortCut "$SMPROGRAMS\Apache Tomcat @VERSION_MAJOR_MINOR@\Welcome.lnk" \
                 "http://127.0.0.1:$TomcatPort/"

  ${If} ${SectionIsSelected} ${SecManager}
    CreateShortCut "$SMPROGRAMS\Apache Tomcat @VERSION_MAJOR_MINOR@\Tomcat Manager.lnk" \
                   "http://127.0.0.1:$TomcatPort/manager/html"
  ${EndIf}

  ${If} ${SectionIsSelected} ${SecHostManager}
    CreateShortCut "$SMPROGRAMS\Apache Tomcat @VERSION_MAJOR_MINOR@\Tomcat Host Manager.lnk" \
                   "http://127.0.0.1:$TomcatPort/host-manager/html"
  ${EndIf}

  ${If} ${SectionIsSelected} ${SecDocs}
    CreateShortCut "$SMPROGRAMS\Apache Tomcat @VERSION_MAJOR_MINOR@\Tomcat Documentation.lnk" \
                   "$INSTDIR\webapps\docs\index.html"
  ${EndIf}

  CreateShortCut "$SMPROGRAMS\Apache Tomcat @VERSION_MAJOR_MINOR@\Uninstall Tomcat @VERSION_MAJOR_MINOR@.lnk" \
                 "$INSTDIR\Uninstall.exe"

  CreateShortCut "$SMPROGRAMS\Apache Tomcat @VERSION_MAJOR_MINOR@\Tomcat @VERSION_MAJOR_MINOR@ Program Directory.lnk" \
                 "$INSTDIR"

  CreateShortCut "$SMPROGRAMS\Apache Tomcat @VERSION_MAJOR_MINOR@\Monitor Tomcat.lnk" \
                 "$INSTDIR\bin\tomcat@VERSION_MAJOR@w.exe" \
                 '//MS//Tomcat@VERSION_MAJOR@' \
                 "$INSTDIR\tomcat.ico" 0 SW_SHOWNORMAL

  CreateShortCut "$SMPROGRAMS\Apache Tomcat @VERSION_MAJOR_MINOR@\Configure Tomcat.lnk" \
                 "$INSTDIR\bin\tomcat@VERSION_MAJOR@w.exe" \
                 '//ES//Tomcat@VERSION_MAJOR@' \
                 "$INSTDIR\tomcat.ico" 0 SW_SHOWNORMAL

FunctionEnd

;--------------------------------
;Uninstaller Section

Section Uninstall

  Delete "$INSTDIR\Uninstall.exe"

  ; Stop Tomcat service monitor if running
  DetailPrint "Stopping Tomcat@VERSION_MAJOR@ service monitor"
  nsExec::ExecToLog '"$INSTDIR\bin\tomcat@VERSION_MAJOR@w.exe" //MQ//Tomcat@VERSION_MAJOR@'
  ; Delete Tomcat service
  DetailPrint "Uninstalling Tomcat@VERSION_MAJOR@ service"
  nsExec::ExecToLog '"$INSTDIR\bin\tomcat@VERSION_MAJOR@.exe" //DS//Tomcat@VERSION_MAJOR@'
  ClearErrors

  DeleteRegKey HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\Apache Tomcat @VERSION_MAJOR_MINOR@"
  DeleteRegKey HKLM "SOFTWARE\Apache Software Foundation\Tomcat\@VERSION_MAJOR_MINOR@"
  DeleteRegValue HKLM "Software\Microsoft\Windows\CurrentVersion\Run" "ApacheTomcatMonitor@VERSION_MAJOR_MINOR@"
  RMDir /r "$SMPROGRAMS\Apache Tomcat @VERSION_MAJOR_MINOR@"
  Delete "$INSTDIR\tomcat.ico"
  Delete "$INSTDIR\LICENSE"
  Delete "$INSTDIR\NOTICE"
  RMDir /r "$INSTDIR\bin"
  RMDir /r "$INSTDIR\lib"
  Delete "$INSTDIR\conf\*.dtd"
  RMDir "$INSTDIR\logs"
  RMDir /r "$INSTDIR\webapps\docs"
  RMDir /r "$INSTDIR\webapps\examples"
  RMDir /r "$INSTDIR\work"
  RMDir /r "$INSTDIR\temp"
  RMDir "$INSTDIR"

  IfSilent Removed 0

  ; if $INSTDIR was removed, skip these next ones
  IfFileExists "$INSTDIR" 0 Removed
    MessageBox MB_YESNO|MB_ICONQUESTION \
      "Remove all files in your Tomcat @VERSION_MAJOR_MINOR@ directory? (If you have anything  \
 you created that you want to keep, click No)" IDNO Removed
    ; these would be skipped if the user hits no
    RMDir /r "$INSTDIR\webapps"
    RMDir /r "$INSTDIR\logs"
    RMDir /r "$INSTDIR\conf"
    Delete "$INSTDIR\*.*"
    RMDir /r "$INSTDIR"
    Sleep 500
    IfFileExists "$INSTDIR" 0 Removed
      MessageBox MB_OK|MB_ICONEXCLAMATION \
                 "Note: $INSTDIR could not be removed."
  Removed:

SectionEnd

;eof
