$ErrorActionPreference = 'Stop'
# Extract the production records and recommendation methods; no WPF/winws/network needed.
$source = Get-Content (Join-Path $PSScriptRoot '../ZapretDesk.cs') -Raw
$records = [regex]::Match($source, '(?s)public sealed class Profile \{.*?(?=public static class Json)').Value
$methods = [regex]::Match($source, '(?s)public static bool CoreHttpsPassed\(.*?(?= public static async Task<List<Check>> ProbeTelegramWeb)').Value
if (!$records -or !$methods) { throw 'Production method extraction failed' }
Add-Type -TypeDefinition ("using System; using System.Linq; using System.Collections.Generic; namespace RecommendationRegression {" + $records + "public static class Engine {" + $methods + "}}")
function Result($names, $ms) {
    $r = New-Object RecommendationRegression.Result
    foreach ($name in $names) {
        $c = New-Object RecommendationRegression.Check
        $c.Name = $name; $c.Ok = $true; $c.Ms = $ms
        $r.Checks.Add($c)
    }
    return $r
}
function Assert($condition, $message) { if (!$condition) { throw $message } }
$bad = Result @('Discord', 'Discord API', 'Google', 'GitHub') 1
$good = Result @('YouTube', 'Discord', 'Discord API') 100
$all = Result @('YouTube', 'Discord', 'Discord API', 'Google', 'GitHub', 'ChatGPT') 200
Assert ($null -eq [RecommendationRegression.Engine]::Recommend([RecommendationRegression.Result[]]@($bad))) '4/6 without YouTube must not be recommended'
Assert ([object]::ReferenceEquals($good, [RecommendationRegression.Engine]::Recommend([RecommendationRegression.Result[]]@($bad, $good)))) 'Target-service checks must outrank unrelated 4/6'
Assert ([object]::ReferenceEquals($all, [RecommendationRegression.Engine]::Recommend([RecommendationRegression.Result[]]@($good, $all)))) 'Total successes still rank eligible candidates'
$fast = Result @('YouTube', 'Discord', 'Discord API') 1
Assert ([object]::ReferenceEquals($fast, [RecommendationRegression.Engine]::Recommend([RecommendationRegression.Result[]]@($good, $fast)))) 'Latency breaks ties'
$all.Error = 'winws exited'
Assert ($null -eq [RecommendationRegression.Engine]::Recommend([RecommendationRegression.Result[]]@($all))) 'Process errors disqualify candidate'
$noDiscord = Result @('YouTube', 'Discord API', 'Google', 'GitHub', 'ChatGPT') 1
Assert ($null -eq [RecommendationRegression.Engine]::Recommend([RecommendationRegression.Result[]]@($noDiscord))) 'Discord page failure must not be masked'
Write-Output 'PASS: 6 recommendation regression cases; no network or video verification.'
