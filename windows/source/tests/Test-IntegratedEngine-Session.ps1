param([Parameter(Mandatory=$true)][string]$ExePath)
$ErrorActionPreference='Stop'
$assembly=[Reflection.Assembly]::LoadFile((Resolve-Path -LiteralPath $ExePath).Path)
$type=$assembly.GetType('ZapretDesk.Engine',$true)
$temp=Join-Path ([IO.Path]::GetTempPath()) ('veyra-engine-test-'+[Guid]::NewGuid().ToString('N'))
[IO.Directory]::CreateDirectory($temp)>$null
$engine=[Activator]::CreateInstance($type,[object[]]@([string]$temp))
if($engine.Profiles.Count -ne 23){throw 'Profile count'}
if($engine.Config.Profile -ne 'general (VEYRA YOUTUBE 1).bat'){throw 'Default profile'}
foreach($p in $engine.Profiles){
 $args=$engine.Arguments($p.Id,$engine.Config)
 if($args -match '%[A-Za-z_][A-Za-z_0-9]*%|[\r\n]'){throw "Invalid arguments $($p.Id)"}
}
$new=$engine.Arguments('general (VEYRA YOUTUBE 1).bat',$engine.Config)
if($new -notmatch '--filter-tcp=443 --hostlist="[^"]*list-youtube-veyra.txt" --dpi-desync=fake,multidisorder --dpi-desync-split-pos=1,midsld --dpi-desync-repeats=11 --dpi-desync-fooling=badseq'){throw 'YouTube TCP mismatch'}
if($new -notmatch '--filter-udp=443 --hostlist="[^"]*list-youtube-veyra.txt" --dpi-desync=fake --dpi-desync-repeats=11'){throw 'YouTube QUIC mismatch'}
# Original Base rules must remain exactly intact after the two leading YouTube overrides.
$original=$engine.Arguments('general.bat',$engine.Config)
$baseRules=$original.Substring($original.IndexOf('--filter-udp=443'))
if(!$new.EndsWith($baseRules)){throw 'Original non-YouTube rules changed'}
# Existing settings and Telegram-related files are not migrated/reset on next startup.
[IO.File]::WriteAllText((Join-Path $temp 'telegram-port.txt'),'19635')
$engine.Config.Profile='general.bat';$engine.Save($engine.Config)
$again=[Activator]::CreateInstance($type,[object[]]@([string]$temp))
if($again.Config.Profile -ne 'general.bat'){throw 'Existing selection overwritten'}
if([IO.File]::ReadAllText((Join-Path $temp 'telegram-port.txt')) -ne '19635'){throw 'Port overwritten'}
Write-Output 'PASS: compiled EXE loaded; embedded engine extracted; all 23 profiles parsed; YouTube overrides match; Base rules preserved; saved selection/port retained. No winws/network/WPF execution.'

if($engine.Profiles[0].Name -ne 'Veyra Base'){throw 'Base display name'}
if($engine.Config.BaseRevision -ne 13){throw 'Base migration revision'}
$legacy=Join-Path $temp 'legacy';[IO.Directory]::CreateDirectory($legacy)>$null
[IO.File]::WriteAllText((Join-Path $legacy 'settings.json'),' {"Profile":"general.bat","GameMode":"off","Tcp":"1024-65535","Udp":"1024-65535"}')
[IO.File]::WriteAllText((Join-Path $legacy 'telegram-port.txt'),'19635')
$migrated=[Activator]::CreateInstance($type,[object[]]@([string]$legacy))
if($migrated.Config.Profile -ne 'general (VEYRA YOUTUBE 1).bat' -or $migrated.Config.BaseRevision -ne 13){throw 'Legacy migration failed'}
if([IO.File]::ReadAllText((Join-Path $legacy 'telegram-port.txt')) -ne '19635'){throw 'Legacy Telegram port changed'}
$migrated.Config.Profile='general.bat';$migrated.Save($migrated.Config)
$manual=[Activator]::CreateInstance($type,[object[]]@([string]$legacy))
if($manual.Config.Profile -ne 'general.bat'){throw 'Later manual choice lost'}
if($assembly.GetName().Version.ToString() -ne '1.3.0.0'){throw 'Assembly version'}
Write-Output 'PASS: 1.3 version, Veyra Base display, one-time migration, manual rollback and Telegram port preservation.'
