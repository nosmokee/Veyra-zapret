$ErrorActionPreference='Stop'
$exe=Join-Path $PSScriptRoot '../../Veyra.exe'
if(!(Test-Path $exe)){$exe=Join-Path $PSScriptRoot '../bin/Release/Veyra.exe'}
$a=[Reflection.Assembly]::LoadFile(([IO.Path]::GetFullPath($exe)))
function New-Obj($n){[Activator]::CreateInstance($a.GetType('ZapretDesk.'+$n,$true))}
function Assert($b,$m){if(!$b){throw $m}}
$palette=$a.GetType('ZapretDesk.Palette');$map=$palette.GetMethod('Map');$lum=$palette.GetMethod('Luminance')
function Color($role,$theme){$map.Invoke($null,[object[]]@($role,$theme))}
function Ratio($one,$two){$x=$lum.Invoke($null,[object[]]@($one));$y=$lum.Invoke($null,[object[]]@($two));([Math]::Max($x,$y)+.05)/([Math]::Min($x,$y)+.05)}
$ids=$a.GetType('ZapretDesk.Themes').GetField('Ids').GetValue($null);$count=0
foreach($mode in @('light','dark','system')){
 $fingerprints=@();$surfaces=@();foreach($id in $ids){$look=New-Obj Appearance;$look.Mode=$mode;$look.Background='space';$look.Accent='#123456';$look.SelectTheme($id);Assert ($look.Accent -eq '' -and $look.Theme -eq $id -and $look.Background -eq 'space') 'Preset clears custom accent only';$look.Normalize();$key=$look.Colors
 $bg=Color '#18120E' $key;$card=Color '#281E17' $key;$text=Color '#F5EBE0' $key;$muted=Color '#C4B3A3' $key;$accent=Color '#FF9D52' $key;$foreground=Color '#332012' $key
 Assert ((Ratio $text $card) -ge 4.5) "Text contrast $id/$mode";Assert ((Ratio $muted $card) -ge 4.5) "Muted contrast $id/$mode";Assert ((Ratio $foreground $accent) -ge 4.5) "Button contrast $id/$mode"
 $fingerprints+=($bg+$card+$accent);$surfaces+=$card
 $look.Background='amber';Assert ((Color '#281E17' $look.Colors) -eq $card) 'Background must not override theme palette'
 [string]$path=(Join-Path ([IO.Path]::GetTempPath()) ('veyra-theme-'+[Guid]::NewGuid().ToString('N')+'.json'));$j=$a.GetType('ZapretDesk.Json');$null=$j.GetMethod('Write').MakeGenericMethod($look.GetType()).Invoke($null,[object[]]@($path,$look));$saved=$j.GetMethod('Read').MakeGenericMethod($look.GetType()).Invoke($null,[object[]]@($path));$saved.Normalize();Assert ($saved.Colors -eq $look.Colors) 'Theme roundtrip';Remove-Item -LiteralPath $path;$count++
 };Assert (@($fingerprints|Select-Object -Unique).Count -eq 9) 'Nine distinct themes';Assert (@($surfaces|Select-Object -Unique).Count -eq 9) 'Nine distinct card palettes'
}
foreach($hex in @('#FFFFFF','#000000','#808080','#FF0000','#00FF00','#0000FF','#FFFF00')){$fg=$palette.GetMethod('Contrast').Invoke($null,[object[]]@($hex));Assert ((Ratio $fg $hex) -ge 4.5) 'Custom contrast'}
$src=[IO.File]::ReadAllText((Join-Path $PSScriptRoot '../ZapretDesk.cs'));Assert ($src.Contains('look.SelectTheme(id.Substring(6))')) 'Click handler uses preset selection';Assert ($src.Contains('https://desktop.telegram.org/') -and $src.Contains('https://github.com/telegramdesktop/tdesktop/releases/latest')) 'Official download URLs'
Write-Host "PASS: $count theme/mode cases; nine distinct surfaces and accents; custom accent reset; unchanged background; persistence; >=4.5 text/button contrast; official links."

$look=New-Obj Appearance;$look.Mode='system';Assert ($look.Colors -notmatch ':system:') 'System mode resolves in render stamp';Write-Host 'PASS: resolved system light/dark is part of the render key.'
$build=$a.GetType('ZapretDesk.Scenes').GetMethod('Build');$hitTest=$a.GetType('ZapretDesk.ChromePolicy').GetMethod('HitTest')
foreach($w in @(390,1280)){foreach($id in $ids){$m=New-Obj Model;$m.Tab='personal';$m.Look.Background='space';$m.Look.Accent='#123456';$scene=$build.Invoke($null,[object[]]@($m,[single]$w,[single]840));$hit=@($scene.Hits|Where-Object Id -eq ('theme:'+$id))[0];$scroll=[single][Math]::Max(0,$hit.Rect.Y-($scene.HeaderHeight+80));$target=$hitTest.Invoke($null,[object[]]@($scene,[single]($hit.Rect.X+$hit.Rect.W/2),[single]($hit.Rect.Y-$scroll+25),$scroll,$false));Assert ($target.Id -eq ('theme:'+$id)) "Theme hit test $id/$w";$m.Look.SelectTheme($target.Id.Substring(6));Assert ($m.Look.Theme -eq $id -and $m.Look.Accent -eq '' -and $m.Look.Background -eq 'space') 'Theme click semantics'}}
Write-Host 'PASS: 18 theme hit tests at mobile/desktop widths including scrolled positions; preset applies without changing background.'
