param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]] $Command = @("cargo", "check")
)

$ErrorActionPreference = "Stop"

$cargoBin = Join-Path $env:USERPROFILE ".cargo\bin"
$llvmBin = "C:\Program Files\LLVM\bin"
$cmakeBin = "C:\Program Files\CMake\bin"
$vsDevCmd = "C:\Program Files (x86)\Microsoft Visual Studio\2022\BuildTools\Common7\Tools\VsDevCmd.bat"
$ninja = Get-ChildItem "$env:LOCALAPPDATA\Microsoft\WinGet\Packages" -Recurse -Filter ninja.exe |
    Select-Object -First 1

if (-not (Test-Path $vsDevCmd)) {
    throw "Visual Studio Build Tools developer command was not found: $vsDevCmd"
}

if (-not (Test-Path (Join-Path $llvmBin "libclang.dll"))) {
    throw "libclang.dll was not found. Install LLVM with: winget install LLVM.LLVM"
}

if (-not $ninja) {
    throw "ninja.exe was not found. Install Ninja with: winget install Ninja-build.Ninja"
}

$env:PATH = "$cargoBin;$llvmBin;$cmakeBin;$($ninja.DirectoryName);$env:PATH"
$env:LIBCLANG_PATH = $llvmBin
$env:CMAKE_GENERATOR = "Ninja"

$commandLine = $Command -join " "
if ($Command.Count -gt 0 -and $Command[0] -eq "cargo" -and -not ($Command -contains "--manifest-path")) {
    $commandLine = "cd /d `"$PSScriptRoot\..\engine`" && $commandLine"
}

cmd /c "`"$vsDevCmd`" -arch=x64 -host_arch=x64 && $commandLine"
exit $LASTEXITCODE
