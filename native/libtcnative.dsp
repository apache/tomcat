# Microsoft Developer Studio Project File - Name="libtcnative" - Package Owner=<4>
# Microsoft Developer Studio Generated Build File, Format Version 6.00
# ** DO NOT EDIT **

# TARGTYPE "Win32 (x86) Dynamic-Link Library" 0x0102

CFG=libtcnative - Win32 Debug
!MESSAGE This is not a valid makefile. To build this project using NMAKE,
!MESSAGE use the Export Makefile command and run
!MESSAGE 
!MESSAGE NMAKE /f "libtcnative.mak".
!MESSAGE 
!MESSAGE You can specify a configuration when running NMAKE
!MESSAGE by defining the macro CFG on the command line. For example:
!MESSAGE 
!MESSAGE NMAKE /f "libtcnative.mak" CFG="libtcnative - Win32 Debug"
!MESSAGE 
!MESSAGE Possible choices for configuration are:
!MESSAGE 
!MESSAGE "libtcnative - Win32 Release" (based on "Win32 (x86) Dynamic-Link Library")
!MESSAGE "libtcnative - Win32 Debug" (based on "Win32 (x86) Dynamic-Link Library")
!MESSAGE 

# Begin Project
# PROP AllowPerConfigDependencies 0
# PROP Scc_ProjName ""
# PROP Scc_LocalPath ""
CPP=cl.exe
MTL=midl.exe
RSC=rc.exe

!IF  "$(CFG)" == "libtcnative - Win32 Release"

# PROP BASE Use_MFC 0
# PROP BASE Use_Debug_Libraries 0
# PROP BASE Output_Dir "Release"
# PROP BASE Intermediate_Dir "Release"
# PROP BASE Target_Dir ""
# PROP Use_MFC 0
# PROP Use_Debug_Libraries 0
# PROP Output_Dir "Release"
# PROP Intermediate_Dir "Release"
# PROP Ignore_Export_Lib 0
# PROP Target_Dir ""
# ADD BASE CPP /nologo /MD /W3 /O2 /D "WIN32" /D "NDEBUG" /D "_WINDOWS" /FD /c
# ADD CPP /nologo /MD /W3 /Zi /O2 /I "./include" /I "../apr/include" /I "../apr/include/arch/win32" /I "$(JAVA_HOME)/include" /I "$(JAVA_HOME)/include/win32" /D "NDEBUG" /D "TCN_DECLARE_EXPORT" /D "WIN32" /D "_WINDOWS" /Fd"Release\libtcnative_src" /FD /c
# ADD BASE MTL /nologo /D "NDEBUG" /mktyplib203 /o /win32 "NUL"
# ADD MTL /nologo /D "NDEBUG" /mktyplib203 /o /win32 "NUL"
# ADD BASE RSC /l 0x409 /d "NDEBUG"
# ADD RSC /l 0x409 /d "NDEBUG"
BSC32=bscmake.exe
# ADD BASE BSC32 /nologo
# ADD BSC32 /nologo
LINK32=link.exe
# ADD BASE LINK32 kernel32.lib advapi32.lib ws2_32.lib mswsock.lib wldap32.lib ole32.lib /nologo /base:"0x6EE00000" /subsystem:windows /dll /debug /machine:I386 /opt:ref
# ADD LINK32 kernel32.lib advapi32.lib ws2_32.lib mswsock.lib wldap32.lib ole32.lib /nologo /base:"0x6EE00000" /subsystem:windows /dll /debug /machine:I386 /out:"Release/libtcnative-1.dll" /opt:ref

!ELSEIF  "$(CFG)" == "libtcnative - Win32 Debug"

# PROP BASE Use_MFC 0
# PROP BASE Use_Debug_Libraries 1
# PROP BASE Output_Dir "Debug"
# PROP BASE Intermediate_Dir "Debug"
# PROP BASE Target_Dir ""
# PROP Use_MFC 0
# PROP Use_Debug_Libraries 1
# PROP Output_Dir "Debug"
# PROP Intermediate_Dir "Debug"
# PROP Ignore_Export_Lib 0
# PROP Target_Dir ""
# ADD BASE CPP /nologo /MDd /W3 /GX /Zi /Od /D "WIN32" /D "_DEBUG" /D "_WINDOWS" /FD /c
# ADD CPP /nologo /MDd /W4 /GX /Zi /Od /I "./include" /I "../apr/include" /I "../apr/include/arch/win32" /I "$(JAVA_HOME)/include" /I "$(JAVA_HOME)/include/win32" /D "_DEBUG" /D "TCN_DECLARE_EXPORT" /D "WIN32" /D "_WINDOWS" /Fd"Debug\libtcnative_src" /FD /c
# ADD BASE MTL /nologo /D "_DEBUG" /mktyplib203 /o /win32 "NUL"
# ADD MTL /nologo /D "_DEBUG" /mktyplib203 /o /win32 "NUL"
# ADD BASE RSC /l 0x409 /d "_DEBUG"
# ADD RSC /l 0x409 /d "_DEBUG"
BSC32=bscmake.exe
# ADD BASE BSC32 /nologo
# ADD BSC32 /nologo
LINK32=link.exe
# ADD BASE LINK32 kernel32.lib advapi32.lib ws2_32.lib mswsock.lib wldap32.lib ole32.lib /nologo /base:"0x6EE00000" /subsystem:windows /dll /incremental:no /debug /machine:I386
# ADD LINK32 kernel32.lib advapi32.lib ws2_32.lib mswsock.lib wldap32.lib ole32.lib /nologo /base:"0x6EE00000" /subsystem:windows /dll /incremental:no /debug /machine:I386 /out:"Debug/libtcnative-1.dll"

!ENDIF 

# Begin Target

# Name "libtcnative - Win32 Release"
# Name "libtcnative - Win32 Debug"
# Begin Group "Source Files"

# PROP Default_Filter ""
# Begin Source File

SOURCE=.\src\dir.c
# End Source File
# Begin Source File

SOURCE=.\src\error.c
# End Source File
# Begin Source File

SOURCE=.\src\file.c
# End Source File
# Begin Source File

SOURCE=.\src\info.c
# End Source File
# Begin Source File

SOURCE=.\src\jnilib.c
# End Source File
# Begin Source File

SOURCE=.\src\lock.c
# End Source File
# Begin Source File

SOURCE=.\src\misc.c
# End Source File
# Begin Source File

SOURCE=.\src\mmap.c
# End Source File
# Begin Source File

SOURCE=.\src\network.c
# End Source File
# Begin Source File

SOURCE=.\src\poll.c
# End Source File
# Begin Source File

SOURCE=.\src\pool.c
# End Source File
# Begin Source File

SOURCE=.\src\proc.c
# End Source File
# Begin Source File

SOURCE=.\src\shm.c
# End Source File
# Begin Source File

SOURCE=.\src\stdlib.c
# End Source File
# Begin Source File

SOURCE=.\src\user.c
# End Source File
# End Group
# Begin Group "Generated Files"

# PROP Default_Filter ""
# End Group
# Begin Group "Header Files"

# PROP Default_Filter ""
# Begin Source File

SOURCE=.\include\tcn.h
# End Source File
# Begin Source File

SOURCE=.\include\tcn_version.h
# End Source File
# End Group
# Begin Group "Platform Files"

# PROP Default_Filter ""
# Begin Source File

SOURCE=.\os\win32\system.c
# End Source File
# End Group
# Begin Source File

SOURCE=.\libtcnative.rc
# End Source File
# Begin Source File

SOURCE=.\build\win32ver.awk

!IF  "$(CFG)" == "libtcnative - Win32 Release"

# PROP Ignore_Default_Tool 1
USERDEP__WIN32="./include/tcn_version.h"	
# Begin Custom Build - Creating Version Resource
InputPath=.\build\win32ver.awk

".\libtcnative.rc" : $(SOURCE) "$(INTDIR)" "$(OUTDIR)"
	awk -f ./build/win32ver.awk libtcnative-1.dll "Tomcat Native Java Library"  ./include/tcn_version.h > .\libtcnative.rc

# End Custom Build

!ELSEIF  "$(CFG)" == "libtcnative - Win32 Debug"

# PROP Ignore_Default_Tool 1
USERDEP__WIN32="./include/tcn_version.h"	
# Begin Custom Build - Creating Version Resource
InputPath=.\build\win32ver.awk

".\libtcnative.rc" : $(SOURCE) "$(INTDIR)" "$(OUTDIR)"
	awk -f ./build/win32ver.awk libtcnative-1.dll "Tomcat Native Java Library"  ./include/tcn_version.h > .\libtcnative.rc

# End Custom Build

!ENDIF 

# End Source File
# End Target
# End Project
