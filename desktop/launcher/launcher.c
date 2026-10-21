/*
 * RayScope.exe: starts the bundled Java runtime with the app, without a console window.
 * Layout next to the exe: runtime/ (JRE), lib/ (app jars), core/ (xray.exe).
 * Built on the CI runner with mingw-w64 (see ci/desktop.sh).
 */
#define WIN32_LEAN_AND_MEAN
#define UNICODE
#define _UNICODE
#include <windows.h>

#define BUF 32768

static wchar_t dir[BUF];
static wchar_t java[BUF];
static wchar_t cmd[BUF];

/* path separator and quote, kept as numbers so the source has no escapes */
static const wchar_t BS[] = {92, 0};
static const wchar_t QT[] = {34, 0};

static void fail(const wchar_t *text) {
    MessageBoxW(NULL, text, L"RayScope", MB_OK | MB_ICONERROR);
}

int WINAPI wWinMain(HINSTANCE inst, HINSTANCE prev, LPWSTR args, int show) {
    (void)inst;
    (void)prev;
    (void)args;
    (void)show;

    DWORD n = GetModuleFileNameW(NULL, dir, BUF);
    if (n == 0 || n >= BUF) {
        fail(L"Не удалось определить папку программы.");
        return 1;
    }
    wchar_t *cut = NULL;
    for (wchar_t *c = dir; *c; c++) {
        if (*c == 92 || *c == 47) cut = c;
    }
    if (cut) *cut = 0;

    lstrcpyW(java, dir);
    lstrcatW(java, BS);
    lstrcatW(java, L"runtime");
    lstrcatW(java, BS);
    lstrcatW(java, L"bin");
    lstrcatW(java, BS);
    lstrcatW(java, L"javaw.exe");
    if (GetFileAttributesW(java) == INVALID_FILE_ATTRIBUTES) {
        fail(L"Не найдена встроенная Java (папка runtime). Переустановите RayScope или распакуйте архив целиком.");
        return 1;
    }

    lstrcpyW(cmd, QT);
    lstrcatW(cmd, java);
    lstrcatW(cmd, QT);
    lstrcatW(cmd, L" -Xmx768m -Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Drayscope.home=");
    lstrcatW(cmd, QT);
    lstrcatW(cmd, dir);
    lstrcatW(cmd, QT);
    lstrcatW(cmd, L" -cp ");
    lstrcatW(cmd, QT);
    lstrcatW(cmd, dir);
    lstrcatW(cmd, BS);
    lstrcatW(cmd, L"lib");
    lstrcatW(cmd, BS);
    lstrcatW(cmd, L"*");
    lstrcatW(cmd, QT);
    lstrcatW(cmd, L" com.rayscope.desktop.MainKt");

    STARTUPINFOW si;
    PROCESS_INFORMATION pi;
    ZeroMemory(&si, sizeof(si));
    si.cb = sizeof(si);
    ZeroMemory(&pi, sizeof(pi));
    if (!CreateProcessW(java, cmd, NULL, NULL, FALSE, 0, NULL, dir, &si, &pi)) {
        fail(L"Не удалось запустить Java. Проверьте, что антивирус не удалил файлы из папки runtime.");
        return 1;
    }
    CloseHandle(pi.hThread);
    CloseHandle(pi.hProcess);
    return 0;
}
