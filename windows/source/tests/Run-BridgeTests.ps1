$ErrorActionPreference='Stop'
# Windows PowerShell 5.1/.NET Framework 4.8. Loopback tests, no accounts.
Add-Type -Path @((Join-Path $PSScriptRoot '../TelegramBridge.cs'),(Join-Path $PSScriptRoot '../TelegramTransport.cs'),(Join-Path $PSScriptRoot 'BridgeTests.cs')) -ReferencedAssemblies System.dll,System.Core.dll,System.Security.dll -IgnoreWarnings
[ZapretDesk.BridgeTests46]::Run().GetAwaiter().GetResult()
