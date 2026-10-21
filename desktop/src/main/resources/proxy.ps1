param(
    [int]$Enable = 0,
    [string]$Server = "127.0.0.1:10809",
    [string]$Bypass = "<local>"
)
# RayScope: switches the Windows (WinINet) system proxy and notifies running apps.
$ErrorActionPreference = "Stop"
$key = "HKCU:\Software\Microsoft\Windows\CurrentVersion\Internet Settings"
Set-ItemProperty -Path $key -Name ProxyEnable -Value $Enable -Type DWord
if ($Enable -eq 1) {
    Set-ItemProperty -Path $key -Name ProxyServer -Value $Server
    Set-ItemProperty -Path $key -Name ProxyOverride -Value $Bypass
}
$sig = '[DllImport("wininet.dll", SetLastError = true)] public static extern bool InternetSetOption(System.IntPtr hInternet, int dwOption, System.IntPtr lpBuffer, int dwBufferLength);'
$type = Add-Type -MemberDefinition $sig -Name "WinInetProxy" -Namespace "RayScope" -PassThru
[void]$type::InternetSetOption([System.IntPtr]::Zero, 39, [System.IntPtr]::Zero, 0)
[void]$type::InternetSetOption([System.IntPtr]::Zero, 37, [System.IntPtr]::Zero, 0)
exit 0
