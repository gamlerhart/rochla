$client = new-object System.Net.WebClient

iwr :key:rochla/api/machines/:key:id/cmd/current-user-setup.ps1 -UseBasicParsing | iex