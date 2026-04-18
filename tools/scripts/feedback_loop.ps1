
$combinations = @(
    @{ Version = 83; Subversion = "1"; Locale = 8 }, # GMS v83 (Most likely)
    @{ Version = 83; Subversion = "";  Locale = 8 }, # GMS v83 alternate
    @{ Version = 83; Subversion = "1"; Locale = 5 }, # EMS v83
    @{ Version = 83; Subversion = "1"; Locale = 2 }, # MSEA v83
    @{ Version = 62; Subversion = "1"; Locale = 8 }  # GMS v62 (Backup)
)

$clientPath = "C:\Nexon\MapleStory\HeavenMS-localhost-WINDOW.exe"
$packetCreatorPath = "src\main\java\tools\PacketCreator.java"
$serverConstantsPath = "src\main\java\constants\net\ServerConstants.java"

function Update-Source($v, $s, $l) {
    Write-Host "Updating source for v$v s$s l$l..."
    
    # Update Version in ServerConstants
    $constantsContent = Get-Content $serverConstantsPath
    $constantsContent = $constantsContent -replace 'public static final short VERSION = \d+;', "public static final short VERSION = $v;"
    $constantsContent | Set-Content $serverConstantsPath

    # Update getHello in PacketCreator
    $pcContent = Get-Content $packetCreatorPath
    # We'll use a regex to find and replace the whole getHello method body
    # This is slightly risky but let's try a targeted replace
    $newSub = if ($s -eq "") { 'p.writeShort(0);' } else { "p.writeString(`"$s`");" }
    
    # Read the file as a single string for easier multi-line regex
    $rawPc = [IO.File]::ReadAllText($packetCreatorPath)
    $pattern = 'public static Packet getHello\(short mapleVersion, InitializationVector sendIv, InitializationVector recvIv\) \{[\s\S]+?return p;\s+\}'
    $replacement = "public static Packet getHello(short mapleVersion, InitializationVector sendIv, InitializationVector recvIv) {
        OutPacket p = new ByteBufOutPacket();
        p.writeShortBE(0x0E);
        p.writeShort(mapleVersion);
        $newSub
        p.writeBytes(recvIv.getBytes());
        p.writeBytes(sendIv.getBytes());
        p.writeByte($l);
        return p;
    }"
    
    $rawPc = $rawPc -replace [regex]::Escape($rawPc.Substring($rawPc.IndexOf("public static Packet getHello("), $rawPc.IndexOf("return p;", $rawPc.IndexOf("public static Packet getHello(")) + 12 - $rawPc.IndexOf("public static Packet getHello("))), $replacement
    # Wait, the substring logic is hard. Let's just do a simpler regex if possible.
    # Actually, I'll use the 'replace' tool for precision in the main turn.
}

foreach ($c in $combinations) {
    Write-Host "`n>>> TESTING COMBINATION: Version=$($c.Version), Subversion='$($c.Subversion)', Locale=$($c.Locale)"
    
    # I'll perform the source updates and rebuilds via individual tool calls to ensure accuracy
    # This script will just be my log of what to do.
}
