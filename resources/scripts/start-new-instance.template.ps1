# Make IE useable
function Disable-InternetExplorerESC {
    $AdminKey = "HKLM:\SOFTWARE\Microsoft\Active Setup\Installed Components\{A509B1A7-37EF-4b3f-8CFC-4F3A74704073}"
    $UserKey = "HKLM:\SOFTWARE\Microsoft\Active Setup\Installed Components\{A509B1A8-37EF-4b3f-8CFC-4F3A74704073}"
    Set-ItemProperty -Path $AdminKey -Name "IsInstalled" -Value 0 -Force
    Set-ItemProperty -Path $UserKey -Name "IsInstalled" -Value 0 -Force
    Stop-Process -Name Explorer -Force
    Write-Host "IE Enhanced Security Configuration (ESC) has been disabled." -ForegroundColor Green
}
Disable-InternetExplorerESC

# Allow remote desktop without password
Set-ItemProperty -Path "HKLM:\SYSTEM\CurrentControlSet\Control\Lsa" -Name "LimitBlankPasswordUse" -Value 00000000

# Remove AWS shortcuds
rm C:\Users\Default\Desktop\*

# Setup a user
$UserName = ":key:user"
$UserPwd = ":key:password"
$Computer = [ADSI]"WinNT://$Env:COMPUTERNAME,Computer"

$LocalAdmin = $Computer.Create("User", $UserName)
$LocalAdmin.SetPassword($UserPwd)
$LocalAdmin.SetInfo()
$LocalAdmin.UserFlags = 64 + 65536 # ADS_UF_PASSWD_CANT_CHANGE + ADS_UF_DONT_EXPIRE_PASSWD
$LocalAdmin.SetInfo()
$LocalAdmin.FullName = $UserName
$LocalAdmin.SetInfo()

NET LOCALGROUP "Administrators" $UserName /add


## Downloads & installs
mkdir C:\rochla
$client = new-object System.Net.WebClient

iwr https://chocolatey.org/install.ps1 -UseBasicParsing | iex
$client.DownloadFile(":key:downloads/PsExec.exe", "C:\rochla\PsExec.exe")
$client.DownloadFile(":key:downloads/wallpaper.jpg", "C:\rochla\wallpaper.jpg")
$client.DownloadFile("http://start.turbo.net/install", "C:\rochla\turbo-installer.exe")
$client.DownloadFile(":key:rochla/api/machines/:key:id/cmd/user-init.ps1", "C:\rochla\user-init.ps1")


choco install -y firefox
C:\rochla\turbo-installer.exe --all-users
echo "powershell -executionpolicy bypass -File C:\rochla\user-init.ps1" | Out-File "C:\Users\Default\AppData\Roaming\Microsoft\Windows\Start Menu\Programs\Startup\user-init.bat" -Encoding ASCII

# Warmup user
&"C:\rochla\PsExec.exe" -u :key:user -p :key:password C:\Windows\system32\timeout -t 0

#Final, callback into to tell this instance is ready
$client.DownloadString(":key:rochla/api/machines/:key:id/signal/setup-completed")
