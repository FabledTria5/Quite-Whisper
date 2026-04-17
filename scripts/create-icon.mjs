import { execFileSync } from "node:child_process";
import { existsSync, mkdirSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = dirname(fileURLToPath(import.meta.url));
const repoRoot = resolve(__dirname, "..");

const source =
  process.argv[2] ?? resolve(repoRoot, "src-tauri/icons/icon-source.png");
const output = process.argv[3] ?? resolve(repoRoot, "src-tauri/icons/icon.ico");

if (!existsSync(source)) {
  throw new Error(`Icon source not found: ${source}`);
}

mkdirSync(dirname(output), { recursive: true });

const psScript = `
param(
  [Parameter(Mandatory = $true)][string] $Source,
  [Parameter(Mandatory = $true)][string] $Output
)

Add-Type -AssemblyName System.Drawing

function Convert-ToIconBitmap {
  param(
    [Parameter(Mandatory = $true)][System.Drawing.Image] $Image,
    [Parameter(Mandatory = $true)][int] $Size
  )

  $bitmap = [System.Drawing.Bitmap]::new($Size, $Size, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
  $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
  $graphics.Clear([System.Drawing.Color]::Transparent)
  $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
  $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
  $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
  $graphics.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality

  $scale = [Math]::Min($Size / $Image.Width, $Size / $Image.Height)
  $drawWidth = [int][Math]::Round($Image.Width * $scale)
  $drawHeight = [int][Math]::Round($Image.Height * $scale)
  $x = [int][Math]::Floor(($Size - $drawWidth) / 2)
  $y = [int][Math]::Floor(($Size - $drawHeight) / 2)
  $graphics.DrawImage($Image, $x, $y, $drawWidth, $drawHeight)
  $graphics.Dispose()

  $xor = [byte[]]::new($Size * $Size * 4)
  $offset = 0
  for ($row = $Size - 1; $row -ge 0; $row--) {
    for ($col = 0; $col -lt $Size; $col++) {
      $pixel = $bitmap.GetPixel($col, $row)
      $xor[$offset++] = $pixel.B
      $xor[$offset++] = $pixel.G
      $xor[$offset++] = $pixel.R
      $xor[$offset++] = $pixel.A
    }
  }
  $bitmap.Dispose()

  $andStride = [int]([Math]::Ceiling($Size / 32) * 4)
  $andMask = [byte[]]::new($andStride * $Size)

  $stream = [System.IO.MemoryStream]::new()
  $writer = [System.IO.BinaryWriter]::new($stream)
  $writer.Write([UInt32]40)
  $writer.Write([Int32]$Size)
  $writer.Write([Int32]($Size * 2))
  $writer.Write([UInt16]1)
  $writer.Write([UInt16]32)
  $writer.Write([UInt32]0)
  $writer.Write([UInt32]($xor.Length + $andMask.Length))
  $writer.Write([Int32]0)
  $writer.Write([Int32]0)
  $writer.Write([UInt32]0)
  $writer.Write([UInt32]0)
  $writer.Write($xor)
  $writer.Write($andMask)
  $writer.Flush()
  return ,$stream.ToArray()
}

$sizes = @(16, 24, 32, 48, 64, 128, 256)
$sourceImage = [System.Drawing.Image]::FromFile($Source)
$entries = @()
try {
  foreach ($size in $sizes) {
    $entries += [PSCustomObject]@{
      Size = $size
      Bytes = Convert-ToIconBitmap -Image $sourceImage -Size $size
    }
  }
} finally {
  $sourceImage.Dispose()
}

$file = [System.IO.File]::Create($Output)
$writer = [System.IO.BinaryWriter]::new($file)
try {
  $writer.Write([UInt16]0)
  $writer.Write([UInt16]1)
  $writer.Write([UInt16]$entries.Count)

  $imageOffset = 6 + ($entries.Count * 16)
  foreach ($entry in $entries) {
    $writer.Write([byte]($(if ($entry.Size -eq 256) { 0 } else { $entry.Size })))
    $writer.Write([byte]($(if ($entry.Size -eq 256) { 0 } else { $entry.Size })))
    $writer.Write([byte]0)
    $writer.Write([byte]0)
    $writer.Write([UInt16]1)
    $writer.Write([UInt16]32)
    $writer.Write([UInt32]$entry.Bytes.Length)
    $writer.Write([UInt32]$imageOffset)
    $imageOffset += $entry.Bytes.Length
  }

  foreach ($entry in $entries) {
    $writer.Write($entry.Bytes)
  }
} finally {
  $writer.Dispose()
  $file.Dispose()
}
`;

execFileSync(
  "powershell",
  [
    "-NoProfile",
    "-ExecutionPolicy",
    "Bypass",
    "-Command",
    `& { ${psScript} }`,
    "-Source",
    resolve(source),
    "-Output",
    resolve(output),
  ],
  { stdio: "inherit" },
);
