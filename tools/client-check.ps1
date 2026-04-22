$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot

$user = if ($args[0]) { $args[0] } else { "admin" }
$pass = if ($args[1]) { $args[1] } else { "admin" }

Push-Location $repoRoot
try {
    Write-Host "Compiling ClientSimulator..." -ForegroundColor Cyan
    & .\mvnw.cmd -q test-compile

    & .\mvnw.cmd -q dependency:build-classpath "-Dmdep.outputFile=target/clientcheck.cp" "-DincludeScope=test"
    $classpath = (Get-Content "target/clientcheck.cp" -Raw).Trim()

    Write-Host "Running Client Simulation for user '$user'..." -ForegroundColor Cyan
    # Include both main and test classes in classpath
    & java -cp "target/classes;target/test-classes;$classpath" tools.ClientSimulator 127.0.0.1 $user $pass
}
catch {
    Write-Host "Client simulation failed: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}
finally {
    Pop-Location
}
