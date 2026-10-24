; gw_paste98 - Java 5 / Windows 98 client for the Glitch Works pastes service
; Copyright (C) 2026 Glitch Works, LLC
;
; This program is free software: you can redistribute it and/or modify it
; under the terms of the GNU General Public License as published by the Free
; Software Foundation, either version 3 of the License, or (at your option)
; any later version. See the LICENSE file for details.
;
; Windows installer, built by the client module's Maven build (NSIS 3):
;
;   makensis -DVERSION=0.5.0 -DSTAGE=<staged files> -DOUTFILE=<setup.exe> gw_paste98.nsi
;
; ANSI on purpose: NSIS Unicode installers do not run on Windows 95/98/ME.
; Keep this file ASCII.

Unicode false
SetCompressor /SOLID lzma

!ifndef VERSION | STAGE | OUTFILE
  !error "Define VERSION, STAGE and OUTFILE (see the comment at the top)"
!endif

!include "MUI2.nsh"
!include "LogicLib.nsh"
!include "Sections.nsh"
!include "WordFunc.nsh"
!include "WinVer.nsh"

!define APP "gw_paste98"
!define UNINST_KEY "Software\Microsoft\Windows\CurrentVersion\Uninstall\gw_paste98"
!define JRE_KEY "SOFTWARE\JavaSoft\Java Runtime Environment"
!define MENU_DIR "$SMPROGRAMS\gw_paste98"
!define STARTUP_LNK "$SMSTARTUP\gw_paste98.lnk"
!define JAVA_ARGS "-Xmx32m -jar"
; Window class of the hidden window gwtray.dll creates (native/gwtray.c).
!define TRAY_CLASS "GwPaste98Tray"

Name "${APP} ${VERSION}"
OutFile "${OUTFILE}"
InstallDir "$PROGRAMFILES\gw_paste98"
InstallDirRegKey HKLM "${UNINST_KEY}" "InstallLocation"
RequestExecutionLevel admin
BrandingText "${APP} ${VERSION}"

VIProductVersion "${VERSION}.0"
VIAddVersionKey "ProductName" "${APP}"
VIAddVersionKey "ProductVersion" "${VERSION}"
VIAddVersionKey "FileVersion" "${VERSION}"
VIAddVersionKey "FileDescription" "${APP} setup"
VIAddVersionKey "CompanyName" "Glitch Works, LLC"
VIAddVersionKey "LegalCopyright" "Copyright (C) 2026 Glitch Works, LLC. GNU GPL v3 or later."

Var JAVAW      ; full path of javaw.exe from the registry
Var RUNNING    ; set by CheckRunning: 1 if the program is running
Var FAILED     ; names of shortcuts or files that could not be created

; ---- Pages ----------------------------------------------------------------

!define MUI_ICON "${STAGE}\GWTRAY.ICO"
!define MUI_UNICON "${STAGE}\GWTRAY.ICO"
!define MUI_ABORTWARNING
; Leave the log on screen after (un)installing; right-click it to copy it.
!define MUI_UNFINISHPAGE_NOAUTOCLOSE
ShowInstDetails show
ShowUninstDetails show
!define MUI_COMPONENTSPAGE_NODESC

!insertmacro MUI_PAGE_WELCOME

; The GPL is not an agreement to accept, so this page only informs.
!define MUI_LICENSEPAGE_TEXT_TOP "${APP} is free software under the GNU General Public License."
!define MUI_LICENSEPAGE_TEXT_BOTTOM "You do not need to accept this licence to use the program. It sets out your rights to copy, change and share it. A copy is installed as LICENSE.TXT."
!define MUI_LICENSEPAGE_BUTTON "$(^NextBtn)"
!insertmacro MUI_PAGE_LICENSE "${STAGE}\LICENSE.TXT"

!insertmacro MUI_PAGE_COMPONENTS
!insertmacro MUI_PAGE_DIRECTORY
!insertmacro MUI_PAGE_INSTFILES

; No Finish page: on Windows 98 (ThinkPad T22, 0.6.0) moving from the log to
; MUI2's Finish page crashed Windows with a fatal exception 0E in the ENABLE
; VxD, with or without its checkboxes. Setup ends on the log page instead, and
; "Start gw_paste98 when setup closes" replaces the Finish page's Run box.

!insertmacro MUI_UNPAGE_CONFIRM
!insertmacro MUI_UNPAGE_INSTFILES

!insertmacro MUI_LANGUAGE "English"

; All-users shortcuts on Windows NT. Windows 9x is in practice single-user, and
; its "All Users" Start Menu is not reliably shown, so use the current user's.
!macro SET_CONTEXT
  ${If} ${IsNT}
    SetShellVarContext all
  ${Else}
    SetShellVarContext current
  ${EndIf}
!macroend

; Records a failure in $FAILED if the error flag is set. ClearErrors first.
!macro CHECK_FILE name
  ${IfNot} ${FileExists} "$INSTDIR\${name}"
    DetailPrint "MISSING: $INSTDIR\${name}"
    StrCpy $FAILED "$FAILED$\n  $INSTDIR\${name}"
  ${EndIf}
!macroend

!macro CHECK_ERRORS what
  ${If} ${Errors}
    DetailPrint "FAILED: ${what}"
    StrCpy $FAILED "$FAILED$\n  ${what}"
  ${EndIf}
!macroend

; ---- Checks -----------------------------------------------------------------

; Sets $JAVAW to javaw.exe of Java 1.5 or later, or "" if there is none. Tries
; the registry's CurrentVersion first, then every installed version.
Function FindJava
  StrCpy $JAVAW ""
  ReadRegStr $0 HKLM "${JRE_KEY}" "CurrentVersion"
  Push $0
  Call TryJava
  StrCmp $JAVAW "" 0 done
  StrCpy $1 0
  loop:
    EnumRegKey $0 HKLM "${JRE_KEY}" $1
    StrCmp $0 "" done
    IntOp $1 $1 + 1
    Push $0
    Call TryJava
    StrCmp $JAVAW "" loop done
  done:
FunctionEnd

; Takes a version key name (e.g. "1.5" or "1.5.0_22") from the stack; sets
; $JAVAW if it is 1.5 or later and its javaw.exe exists.
Function TryJava
  Exch $R0
  Push $R1
  Push $R2
  StrCmp $R0 "" done
  ${VersionCompare} $R0 "1.5" $R1
  StrCmp $R1 "2" done ; older than 1.5
  ReadRegStr $R2 HKLM "${JRE_KEY}\$R0" "JavaHome"
  StrCmp $R2 "" done
  IfFileExists "$R2\bin\javaw.exe" 0 done
  StrCpy $JAVAW "$R2\bin\javaw.exe"
  done:
  Pop $R2
  Pop $R1
  Pop $R0
FunctionEnd

; Sets $RUNNING to 1 if gw_paste98 is running, either as a tray icon (the
; DLL's hidden window) or as the fallback window. Windows cannot replace or
; delete GWTRAY.DLL and GWPASTE.JAR while they are in use.
!macro CHECK_RUNNING un
Function ${un}CheckRunning
  StrCpy $RUNNING 0
  retry:
  FindWindow $0 "${TRAY_CLASS}" ""
  IntCmp $0 0 0 running running
  FindWindow $0 "SunAwtFrame" "${APP}"
  IntCmp $0 0 done running running
  running:
  MessageBox MB_RETRYCANCEL|MB_ICONEXCLAMATION \
      "${APP} is running.$\n$\nExit it first (right-click its icon in the notification area and choose Exit), then press Retry." \
      /SD IDCANCEL IDRETRY retry
  StrCpy $RUNNING 1
  done:
FunctionEnd
!macroend
!insertmacro CHECK_RUNNING ""
!insertmacro CHECK_RUNNING "un."


Function StartProgram
  SetOutPath "$INSTDIR"
  Exec '"$JAVAW" ${JAVA_ARGS} "$INSTDIR\GWPASTE.JAR"'
FunctionEnd

; ---- Sections ---------------------------------------------------------------

Section "${APP} (required)" SecMain
  SectionIn RO
  DetailPrint "Windows: $WINDIR"
  DetailPrint "Java: $JAVAW"
  DetailPrint "Program folder: $INSTDIR"
  DetailPrint "Start Menu programs folder: $SMPROGRAMS"
  StrCpy $FAILED ""
  SetOutPath "$INSTDIR"
  File "${STAGE}\GWPASTE.JAR"
  File "${STAGE}\GWTRAY.DLL"
  File "${STAGE}\GWTRAY.ICO"
  File "${STAGE}\GWPASTE.CHM"
  File "${STAGE}\LICENSE.TXT"
  ; Settings (PASTE98.PRF) are not in the installer, so they are never touched.
  Delete "$INSTDIR\README.TXT" ; 0.5.x; replaced by the help

  ; SENDFILE.BAT with full paths, so it works from any folder without Java on
  ; the PATH. Short (8.3) names keep the line within COMMAND.COM's limit.
  GetFullPathName /SHORT $0 "$JAVAW"
  StrCpy $0 "$0" -9 ; drop "javaw.exe"
  GetFullPathName /SHORT $1 "$INSTDIR\GWPASTE.JAR"
  FileOpen $2 "$INSTDIR\SENDFILE.BAT" w
  FileWrite $2 "@echo off$\r$\n"
  FileWrite $2 "rem Sends a text file as a paste and prints its address.$\r$\n"
  FileWrite $2 "rem Usage: SENDFILE filename   (written by setup; see the help)$\r$\n"
  FileWrite $2 "if $\"%1$\"==$\"$\" goto usage$\r$\n"
  FileWrite $2 "$0java.exe ${JAVA_ARGS} $1 --send %1 %2 %3$\r$\n"
  FileWrite $2 "goto end$\r$\n"
  FileWrite $2 ":usage$\r$\n"
  FileWrite $2 "echo Usage: SENDFILE filename$\r$\n"
  FileWrite $2 ":end$\r$\n"
  FileClose $2

  ClearErrors
  WriteUninstaller "$INSTDIR\UNINST.EXE"
  !insertmacro CHECK_ERRORS "$INSTDIR\UNINST.EXE"

  ; Check everything actually arrived.
  !insertmacro CHECK_FILE "GWPASTE.JAR"
  !insertmacro CHECK_FILE "GWTRAY.DLL"
  !insertmacro CHECK_FILE "GWTRAY.ICO"
  !insertmacro CHECK_FILE "GWPASTE.CHM"
  !insertmacro CHECK_FILE "LICENSE.TXT"
  !insertmacro CHECK_FILE "SENDFILE.BAT"
  !insertmacro CHECK_FILE "UNINST.EXE"

  WriteRegStr HKLM "${UNINST_KEY}" "DisplayName" "${APP} ${VERSION}"
  WriteRegStr HKLM "${UNINST_KEY}" "DisplayVersion" "${VERSION}"
  WriteRegStr HKLM "${UNINST_KEY}" "Publisher" "Glitch Works, LLC"
  WriteRegStr HKLM "${UNINST_KEY}" "URLInfoAbout" "https://github.com/glitchwrks/gw_paste98"
  WriteRegStr HKLM "${UNINST_KEY}" "DisplayIcon" "$INSTDIR\GWTRAY.ICO"
  WriteRegStr HKLM "${UNINST_KEY}" "InstallLocation" "$INSTDIR"
  WriteRegStr HKLM "${UNINST_KEY}" "UninstallString" '"$INSTDIR\UNINST.EXE"'
  WriteRegDWORD HKLM "${UNINST_KEY}" "NoModify" 1
  WriteRegDWORD HKLM "${UNINST_KEY}" "NoRepair" 1
SectionEnd

Section "Start Menu shortcuts" SecMenu
  SetOutPath "$INSTDIR" ; the shortcuts' working folder
  ClearErrors
  CreateDirectory "${MENU_DIR}"
  !insertmacro CHECK_ERRORS "${MENU_DIR}"
  ClearErrors
  CreateShortCut "${MENU_DIR}\${APP}.lnk" "$JAVAW" '${JAVA_ARGS} "$INSTDIR\GWPASTE.JAR"' "$INSTDIR\GWTRAY.ICO" 0
  !insertmacro CHECK_ERRORS "${MENU_DIR}\${APP}.lnk"
  ClearErrors
  ClearErrors
  CreateShortCut "${MENU_DIR}\${APP} Help.lnk" "$INSTDIR\GWPASTE.CHM"
  !insertmacro CHECK_ERRORS "${MENU_DIR}\${APP} Help.lnk"
  Delete "${MENU_DIR}\Read me.lnk" ; 0.5.x
  ClearErrors
  CreateShortCut "${MENU_DIR}\Uninstall ${APP}.lnk" "$INSTDIR\UNINST.EXE"
  !insertmacro CHECK_ERRORS "${MENU_DIR}\Uninstall ${APP}.lnk"
SectionEnd

Section /o "Start with Windows" SecStartup
  SetOutPath "$INSTDIR"
  ClearErrors
  CreateShortCut "${STARTUP_LNK}" "$JAVAW" '${JAVA_ARGS} "$INSTDIR\GWPASTE.JAR"' "$INSTDIR\GWTRAY.ICO" 0
  !insertmacro CHECK_ERRORS "${STARTUP_LNK}"
SectionEnd

; Upgrading: remove shortcuts for options now left unticked.
Section "Start ${APP} when setup closes" SecRun
  ; Done in .onInstSuccess, when you press Close.
SectionEnd

Section "-Remove unselected shortcuts"
  ${IfNot} ${SectionIsSelected} ${SecStartup}
    Delete "${STARTUP_LNK}"
  ${EndIf}
  ${IfNot} ${SectionIsSelected} ${SecMenu}
    Delete "${MENU_DIR}\${APP}.lnk"
    Delete "${MENU_DIR}\${APP} Help.lnk"
    Delete "${MENU_DIR}\Read me.lnk"
    Delete "${MENU_DIR}\Uninstall ${APP}.lnk"
    RMDir "${MENU_DIR}"
  ${EndIf}

  ${If} $FAILED != ""
    MessageBox MB_OK|MB_ICONEXCLAMATION \
        "Setup could not create:$FAILED$\n$\nThe details below say more. Right-click them and choose Copy Details To Clipboard to save them." \
        /SD IDOK
  ${EndIf}
SectionEnd

; Here rather than with the other functions: it needs the section indexes.
Function .onInit
  !insertmacro SET_CONTEXT

  Call FindJava
  ${If} $JAVAW == ""
    MessageBox MB_OK|MB_ICONSTOP \
        "${APP} needs Java 5 (1.5) or later, and none was found.$\n$\nInstall the Java 5 runtime (1.5.0_22 is the last update for Windows 98), then run this setup again." \
        /SD IDOK
    Abort
  ${EndIf}

  Call CheckRunning
  ${If} $RUNNING == 1
    Abort
  ${EndIf}

  ; Upgrading: keep an existing "Start with Windows" choice.
  ${If} ${FileExists} "${STARTUP_LNK}"
    !insertmacro SelectSection ${SecStartup}
  ${EndIf}
FunctionEnd

Function .onInstSuccess
  ${IfNot} ${Silent}
  ${AndIf} ${SectionIsSelected} ${SecRun}
    Call StartProgram
  ${EndIf}
FunctionEnd

; ---- Uninstaller ------------------------------------------------------------

Function un.onInit
  !insertmacro SET_CONTEXT
  Call un.CheckRunning
  ${If} $RUNNING == 1
    Abort
  ${EndIf}
FunctionEnd

Section "Uninstall"
  Delete "${STARTUP_LNK}"
  Delete "${MENU_DIR}\${APP}.lnk"
  Delete "${MENU_DIR}\${APP} Help.lnk"
  Delete "${MENU_DIR}\Read me.lnk"
  Delete "${MENU_DIR}\Uninstall ${APP}.lnk"
  RMDir "${MENU_DIR}"

  Delete "$INSTDIR\GWPASTE.JAR"
  Delete "$INSTDIR\GWTRAY.DLL"
  Delete "$INSTDIR\GWTRAY.ICO"
  Delete "$INSTDIR\GWPASTE.CHM"
  Delete "$INSTDIR\README.TXT"
  Delete "$INSTDIR\LICENSE.TXT"
  Delete "$INSTDIR\SENDFILE.BAT"
  Delete "$INSTDIR\PASTE98.LOG"
  Delete "$INSTDIR\UNINST.EXE"

  ${If} ${FileExists} "$INSTDIR\PASTE98.PRF"
  ${OrIf} ${FileExists} "$INSTDIR\PASTE98.BAK"
    MessageBox MB_YESNO|MB_ICONQUESTION \
        "Keep your settings and key pins?$\n$\nThey are in PASTE98.PRF in $INSTDIR. Keep them if you might install ${APP} again; choose No to delete them." \
        /SD IDYES IDYES keep_settings
  ${EndIf}
  Delete "$INSTDIR\PASTE98.PRF"
  Delete "$INSTDIR\PASTE98.BAK"
  Delete "$INSTDIR\PASTE98.$$$$$$"
  Delete "$INSTDIR\PASTE98S.PRF"
  Delete "$INSTDIR\PASTE98S.BAK"
  Delete "$INSTDIR\PASTE98S.$$$$$$"
  Delete "$INSTDIR\PASTE98.OLD"
  keep_settings:

  RMDir "$INSTDIR" ; only if empty
  DeleteRegKey HKLM "${UNINST_KEY}"
SectionEnd
