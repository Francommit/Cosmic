
function Test-Configuration($version, $subversion, $locale) {
    Write-Host "Testing Config: Version=$version, Subversion=$subversion, Locale=$locale"
    
    # 1. Update PacketCreator.java (this is a simplified placeholder, I'll do this via tool calls)
    # 2. Rebuild and Restart
    # 3. Launch client
    $clientPath = "C:\Nexon\MapleStory\HeavenMS-localhost-WINDOW.exe"
    $process = Start-Process -FilePath $clientPath -PassThru -ErrorAction SilentlyContinue
    
    if ($process) {
        Write-Host "Client started with PID: $($process.Id)"
        Start-Sleep -Seconds 10
        if ($process.HasExited) {
            Write-Host "Client crashed immediately with exit code: $($process.ExitCode)"
            return $false
        } else {
            Write-Host "Client is still running after 10 seconds. This might be it!"
            # We can't easily see if it reached login, but staying open is a good sign
            Stop-Process -Id $process.Id -Force
            return $true
        }
    } else {
        Write-Host "Failed to start client process."
        return $false
    }
}
