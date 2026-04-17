import { mkdirSync, writeFileSync } from "node:fs";

const size = 32;
const pixels = Buffer.alloc(size * size * 4);

for (let y = 0; y < size; y += 1) {
  for (let x = 0; x < size; x += 1) {
    const offset = (y * size + x) * 4;
    const inside = x >= 4 && x < 28 && y >= 4 && y < 28;
    pixels[offset] = inside ? 79 : 0;
    pixels[offset + 1] = inside ? 114 : 0;
    pixels[offset + 2] = inside ? 40 : 0;
    pixels[offset + 3] = inside ? 255 : 0;
  }
}

const bitmapHeaderSize = 40;
const xorSize = pixels.length;
const andMaskSize = size * Math.ceil(size / 32) * 4;
const imageSize = bitmapHeaderSize + xorSize + andMaskSize;
const icon = Buffer.alloc(6 + 16 + imageSize);

let offset = 0;
icon.writeUInt16LE(0, offset);
offset += 2;
icon.writeUInt16LE(1, offset);
offset += 2;
icon.writeUInt16LE(1, offset);
offset += 2;

icon[offset++] = size;
icon[offset++] = size;
icon[offset++] = 0;
icon[offset++] = 0;
icon.writeUInt16LE(1, offset);
offset += 2;
icon.writeUInt16LE(32, offset);
offset += 2;
icon.writeUInt32LE(imageSize, offset);
offset += 4;
icon.writeUInt32LE(22, offset);
offset += 4;

icon.writeUInt32LE(bitmapHeaderSize, offset);
offset += 4;
icon.writeInt32LE(size, offset);
offset += 4;
icon.writeInt32LE(size * 2, offset);
offset += 4;
icon.writeUInt16LE(1, offset);
offset += 2;
icon.writeUInt16LE(32, offset);
offset += 2;
icon.writeUInt32LE(0, offset);
offset += 4;
icon.writeUInt32LE(xorSize + andMaskSize, offset);
offset += 4;
icon.writeInt32LE(0, offset);
offset += 4;
icon.writeInt32LE(0, offset);
offset += 4;
icon.writeUInt32LE(0, offset);
offset += 4;
icon.writeUInt32LE(0, offset);
offset += 4;

for (let y = size - 1; y >= 0; y -= 1) {
  pixels.copy(icon, offset, y * size * 4, (y + 1) * size * 4);
  offset += size * 4;
}

mkdirSync("src-tauri/icons", { recursive: true });
writeFileSync("src-tauri/icons/icon.ico", icon);
