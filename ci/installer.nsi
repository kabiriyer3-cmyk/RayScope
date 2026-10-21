; RayScope installer (NSIS 3, Unicode, per-user, no admin rights).
; Built on Linux by ci/desktop.sh:
;   makensis -INPUTCHARSET UTF8 -DVERSION=1.2.3 -DDISPLAY_VERSION=1.2.3 -DSRC=dir -DICON=app.ico -DOUTFILE=setup.exe ci/installer.nsi

Unicode true
ManifestDPIAware true
SetCompressor /SOLID lzma
RequestExecutionLevel user

!ifndef VERSION
  !define VERSION "0.0.0"
!endif
!ifndef DISPLAY_VERSION
  !define DISPLAY_VERSION "${VERSION}"
!endif

!define UNKEY "Software\Microsoft\Windows\CurrentVersion\Uninstall\RayScope"

!include "MUI2.nsh"

Name "RayScope"
Caption "RayScope ${DISPLAY_VERSION}"
OutFile "${OUTFILE}"
InstallDir "$LOCALAPPDATA\Programs\RayScope"
InstallDirRegKey HKCU "Software\RayScope" "InstallDir"
BrandingText "RayScope ${DISPLAY_VERSION}"
ShowInstDetails nevershow
ShowUninstDetails nevershow

!define MUI_ICON "${ICON}"
!define MUI_UNICON "${ICON}"
!define MUI_ABORTWARNING
!define MUI_FINISHPAGE_RUN "$INSTDIR\RayScope.exe"
!define MUI_FINISHPAGE_RUN_TEXT "Запустить RayScope"

!insertmacro MUI_PAGE_WELCOME
!insertmacro MUI_PAGE_DIRECTORY
!insertmacro MUI_PAGE_INSTFILES
!insertmacro MUI_PAGE_FINISH
!insertmacro MUI_UNPAGE_CONFIRM
!insertmacro MUI_UNPAGE_INSTFILES

!insertmacro MUI_LANGUAGE "Russian"

VIProductVersion "${VERSION}.0"
VIAddVersionKey /LANG=${LANG_RUSSIAN} "ProductName" "RayScope"
VIAddVersionKey /LANG=${LANG_RUSSIAN} "FileDescription" "Установка RayScope"
VIAddVersionKey /LANG=${LANG_RUSSIAN} "FileVersion" "${DISPLAY_VERSION}"
VIAddVersionKey /LANG=${LANG_RUSSIAN} "ProductVersion" "${DISPLAY_VERSION}"
VIAddVersionKey /LANG=${LANG_RUSSIAN} "LegalCopyright" "RayScope"

!macro CheckRunning
  FindWindow $0 "SunAwtFrame" "RayScope"
  StrCmp $0 0 +3
    MessageBox MB_OKCANCEL|MB_ICONEXCLAMATION "RayScope сейчас запущен. Закройте его (значок в трее, пункт «Выход») и нажмите OK." IDOK +2
    Abort
!macroend

Function .onInit
  !insertmacro CheckRunning
FunctionEnd

Function un.onInit
  !insertmacro CheckRunning
FunctionEnd

Section "RayScope" SecMain
  SetShellVarContext current
  ; old app files go away, settings live in %APPDATA% and stay
  RMDir /r "$INSTDIR\lib"
  RMDir /r "$INSTDIR\runtime"
  RMDir /r "$INSTDIR\core"
  SetOutPath "$INSTDIR"
  File /r "${SRC}/*"
  WriteUninstaller "$INSTDIR\Uninstall.exe"
  CreateShortcut "$SMPROGRAMS\RayScope.lnk" "$INSTDIR\RayScope.exe" "" "$INSTDIR\RayScope.exe" 0
  CreateShortcut "$DESKTOP\RayScope.lnk" "$INSTDIR\RayScope.exe" "" "$INSTDIR\RayScope.exe" 0
  WriteRegStr HKCU "Software\RayScope" "InstallDir" "$INSTDIR"
  WriteRegStr HKCU "${UNKEY}" "DisplayName" "RayScope"
  WriteRegStr HKCU "${UNKEY}" "DisplayVersion" "${DISPLAY_VERSION}"
  WriteRegStr HKCU "${UNKEY}" "Publisher" "RayScope"
  WriteRegStr HKCU "${UNKEY}" "DisplayIcon" "$INSTDIR\RayScope.exe"
  WriteRegStr HKCU "${UNKEY}" "InstallLocation" "$INSTDIR"
  WriteRegStr HKCU "${UNKEY}" "UninstallString" '"$INSTDIR\Uninstall.exe"'
  WriteRegDWORD HKCU "${UNKEY}" "NoModify" 1
  WriteRegDWORD HKCU "${UNKEY}" "NoRepair" 1
SectionEnd

Section "Uninstall"
  SetShellVarContext current
  Delete "$DESKTOP\RayScope.lnk"
  Delete "$SMPROGRAMS\RayScope.lnk"
  RMDir /r "$INSTDIR\lib"
  RMDir /r "$INSTDIR\runtime"
  RMDir /r "$INSTDIR\core"
  Delete "$INSTDIR\RayScope.exe"
  Delete "$INSTDIR\version.txt"
  Delete "$INSTDIR\README.txt"
  Delete "$INSTDIR\Uninstall.exe"
  RMDir "$INSTDIR"
  DeleteRegKey HKCU "${UNKEY}"
  DeleteRegKey HKCU "Software\RayScope"
  MessageBox MB_YESNO|MB_ICONQUESTION "Удалить и настройки с серверами (папка %APPDATA%\RayScope)?" /SD IDNO IDNO +2
  RMDir /r "$APPDATA\RayScope"
SectionEnd
