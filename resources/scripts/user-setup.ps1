echo "Rochla is setting up your stuff =)"
echo "You are an admin here and can do what you want"
echo "The machine is live for 1 hour"

$client = new-object System.Net.WebClient

Set-ItemProperty -Path "HKCU:\Control Panel\Desktop" -Name Wallpaper -Value "c:\rochla\wallpaper.jpg"
rundll32.exe user32.dll, UpdatePerUserSystemParameters

function Set-Wallpaper
{
    param(
        [Parameter(Mandatory=$true)]
        $Path,

        [ValidateSet('Center', 'Stretch')]
        $Style = 'Stretch'
    )

    Add-Type @"
		using System;
		using System.Runtime.InteropServices;
		using Microsoft.Win32;
		namespace Wallpaper
		{
		public enum Style : int
		{
		Center, Stretch
		}
		public class Setter {
		public const int SetDesktopWallpaper = 20;
		public const int UpdateIniFile = 0x01;
		public const int SendWinIniChange = 0x02;
		[DllImport("user32.dll", SetLastError = true, CharSet = CharSet.Auto)]
		private static extern int SystemParametersInfo (int uAction, int uParam, string lpvParam, int fuWinIni);
		public static void SetWallpaper ( string path, Wallpaper.Style style ) {
		SystemParametersInfo( SetDesktopWallpaper, 0, path, UpdateIniFile | SendWinIniChange );
		RegistryKey key = Registry.CurrentUser.OpenSubKey("Control Panel\\Desktop", true);
		switch( style )
		{
		case Style.Stretch :
		key.SetValue(@"WallpaperStyle", "2") ;
		key.SetValue(@"TileWallpaper", "0") ;
		break;
		case Style.Center :
		key.SetValue(@"WallpaperStyle", "1") ;
		key.SetValue(@"TileWallpaper", "0") ;
		break;
		}
		key.Close();
		}
		}
		}
"@

    [Wallpaper.Setter]::SetWallpaper( $Path, $Style )
}

Set-Wallpaper -Path "c:\rochla\wallpaper.jpg"

