#!/usr/bin/env node
/**
 * pixelate.mjs —— vanilla Minecraft PNG -> cobalt-ink pixel mask.
 *
 * The cobalt-grid frame is strictly two-colour: warm cream paper #F0EBDE and
 * electric cobalt ink #1F2BE0. This helper turns a Minecraft sprite (or a
 * larger GUI sheet) into a hard-edged cobalt ink mask: ink where the source is
 * dark, transparent where it is light. No anti-aliasing, no smoothing, no
 * second colour.
 *
 * Zero dependencies: PNG decode + encode use node:zlib only.
 *
 * CLI:
 *   node scripts/pixelate.mjs --in <png> --out <png> [--grid 16] [--threshold 0.5] [--invert]
 *
 *   --grid N        nearest-neighbour resample to N x N before thresholding (default 16)
 *   --threshold T   luminance 0..1 cut-off (default 0.5)
 *   --invert        flip which side becomes ink (light-on-dark sprites)
 *
 * Decoded input (non-interlaced only):
 *   colour type 0  grayscale           bit depth 1 / 2 / 4 / 8  (+ tRNS key)
 *   colour type 2  RGB                 bit depth 8              (+ tRNS key)
 *   colour type 3  palette             bit depth 1 / 2 / 4 / 8  (+ tRNS alpha)
 *   colour type 4  grayscale + alpha   bit depth 8
 *   colour type 6  RGBA                bit depth 8
 *   compression method 0; filter method 0; filter types 0-4 (all PNG filters)
 *
 * NOTE ON THE SOURCE LIBRARY. The user's vanilla texture library
 * (C:\Users\12501\Desktop\旧版MUT贴图\我的世界原版资源包\textures) has been
 * PNG-optimised: a census of its 3503 PNGs finds palette / 4-bit / grayscale
 * files far outnumbering plain 8-bit RGB(A). Requiring colour types 2 and 6
 * alone would reject most real sprites (e.g. item/copper_ingot.png and
 * mob_effect/strength.png are palette, gui/container/crafting_table.png is
 * 4-bit palette). Every format above is decoded exactly and losslessly, so the
 * mask is never approximated; the decode is where the accuracy lives.
 *
 * Anything outside the table above (interlaced / Adam7, 16-bit samples,
 * unknown colour types, corrupt chunks) fails with a clear error instead of
 * producing a wrong image.
 *
 * Output: 8-bit RGBA, non-interlaced, filter type 0 on every row.
 */

import { readFileSync, writeFileSync } from "node:fs";
import { inflateSync, deflateSync } from "node:zlib";

const INK_R = 0x1f;
const INK_G = 0x2b;
const INK_B = 0xe0;

const USAGE =
  "usage: node scripts/pixelate.mjs --in <png> --out <png> " +
  "[--grid 16] [--threshold 0.5] [--invert]";

const PNG_SIG = Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]);

function fail(message) {
  console.error("pixelate: " + message);
  process.exit(1);
}

function parseArgs(argv) {
  const opts = { input: null, output: null, grid: 16, threshold: 0.5, invert: false };
  for (let i = 0; i < argv.length; i++) {
    const arg = argv[i];
    if (arg === "--in") opts.input = argv[++i];
    else if (arg === "--out") opts.output = argv[++i];
    else if (arg === "--grid") opts.grid = Number(argv[++i]);
    else if (arg === "--threshold") opts.threshold = Number(argv[++i]);
    else if (arg === "--invert") opts.invert = true;
    else if (arg === "--help" || arg === "-h") {
      console.log(USAGE);
      process.exit(0);
    } else fail('unknown argument "' + arg + '"\n' + USAGE);
  }
  if (!opts.input) fail("missing --in <png>\n" + USAGE);
  if (!opts.output) fail("missing --out <png>\n" + USAGE);
  if (!Number.isInteger(opts.grid) || opts.grid < 1) {
    fail("--grid must be a positive integer (got " + opts.grid + ")");
  }
  if (!Number.isFinite(opts.threshold) || opts.threshold < 0 || opts.threshold > 1) {
    fail("--threshold must be a number in 0..1 (got " + opts.threshold + ")");
  }
  return opts;
}

/* ---------------------------------------------------------------- PNG read */

let crcTable = null;
function crc32(buf) {
  if (!crcTable) {
    crcTable = new Int32Array(256);
    for (let n = 0; n < 256; n++) {
      let c = n;
      for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
      crcTable[n] = c;
    }
  }
  let c = 0xffffffff;
  for (let i = 0; i < buf.length; i++) c = crcTable[(c ^ buf[i]) & 0xff] ^ (c >>> 8);
  return (c ^ 0xffffffff) >>> 0;
}

function readChunks(buf) {
  if (buf.length < 8 || !buf.subarray(0, 8).equals(PNG_SIG)) {
    fail("not a PNG file (bad signature)");
  }
  const chunks = [];
  let off = 8;
  while (off + 8 <= buf.length) {
    const length = buf.readUInt32BE(off);
    const type = buf.toString("latin1", off + 4, off + 8);
    const start = off + 8;
    const end = start + length;
    if (end + 4 > buf.length) fail("truncated PNG chunk " + type);
    const data = buf.subarray(start, end);
    const stored = buf.readUInt32BE(end);
    const actual = crc32(buf.subarray(off + 4, end));
    if (stored !== actual) fail("PNG chunk " + type + " failed its CRC check");
    chunks.push({ type, data });
    off = end + 4;
    if (type === "IEND") break;
  }
  return chunks;
}

function paeth(a, b, c) {
  const p = a + b - c;
  const pa = Math.abs(p - a);
  const pb = Math.abs(p - b);
  const pc = Math.abs(p - c);
  if (pa <= pb && pa <= pc) return a;
  return pb <= pc ? b : c;
}

/** Read one sample (colour component / palette index) from an unfiltered row. */
function readSample(row, x, channel, bitsPerPixel, bitDepth) {
  if (bitDepth === 8) return row[x * (bitsPerPixel >> 3) + channel];
  const bit = x * bitsPerPixel + channel * bitDepth;
  return (row[bit >> 3] >> (8 - bitDepth - (bit & 7))) & ((1 << bitDepth) - 1);
}

/** Decode a supported PNG into a flat RGBA Uint8Array. */
function decodePng(buf) {
  const chunks = readChunks(buf);
  const ihdr = chunks.find((c) => c.type === "IHDR");
  if (!ihdr) fail("missing IHDR chunk");
  if (ihdr.data.length !== 13) fail("IHDR chunk has the wrong length");

  const width = ihdr.data.readUInt32BE(0);
  const height = ihdr.data.readUInt32BE(4);
  const bitDepth = ihdr.data[8];
  const colorType = ihdr.data[9];
  const compression = ihdr.data[10];
  const filterMethod = ihdr.data[11];
  const interlace = ihdr.data[12];

  if (width === 0 || height === 0) fail("image has a zero dimension");
  if (compression !== 0) fail("unsupported compression method " + compression + " (only 0)");
  if (filterMethod !== 0) fail("unsupported filter method " + filterMethod + " (only 0)");
  if (interlace !== 0) fail("interlaced (Adam7) PNG is not supported");
  if (bitDepth === 16) fail("16-bit PNG is not supported (Minecraft sprites are 1/2/4/8-bit)");
  if (bitDepth !== 1 && bitDepth !== 2 && bitDepth !== 4 && bitDepth !== 8) {
    fail("unsupported bit depth " + bitDepth);
  }
  if (colorType === 3) {
    if (!chunks.some((c) => c.type === "PLTE")) fail("palette PNG (colour type 3) without a PLTE chunk");
  } else if (colorType === 0) {
    // grayscale: 1/2/4/8
  } else if (colorType === 2 || colorType === 4 || colorType === 6) {
    if (bitDepth !== 8) {
      fail("colour type " + colorType + " is only supported at bit depth 8 (got " + bitDepth + ")");
    }
  } else {
    fail("unsupported colour type " + colorType + " (supported: 0, 2, 3, 4, 6)");
  }

  const channels = colorType === 0 ? 1 : colorType === 2 ? 3 : colorType === 3 ? 1 : colorType === 4 ? 2 : 4;
  const bitsPerPixel = channels * bitDepth;
  const bpp = Math.max(1, Math.ceil(bitsPerPixel / 8));
  const stride = Math.ceil((width * bitsPerPixel) / 8);

  const palette = colorType === 3 ? chunks.find((c) => c.type === "PLTE").data : null;
  if (palette && palette.length % 3 !== 0) fail("PLTE chunk length is not a multiple of 3");

  let paletteAlpha = null;
  let grayKey = null;
  let rgbKey = null;
  const trns = chunks.find((c) => c.type === "tRNS");
  if (trns) {
    if (colorType === 3) {
      paletteAlpha = trns.data;
    } else if (colorType === 0) {
      if (trns.data.length !== 2) fail("grayscale tRNS chunk must be 2 bytes");
      grayKey = trns.data.readUInt16BE(0);
    } else if (colorType === 2) {
      if (trns.data.length !== 6) fail("RGB tRNS chunk must be 6 bytes");
      rgbKey = [trns.data.readUInt16BE(0), trns.data.readUInt16BE(2), trns.data.readUInt16BE(4)];
    } else {
      fail("tRNS chunk is not valid on colour type " + colorType);
    }
  }

  const idatParts = chunks.filter((c) => c.type === "IDAT").map((c) => c.data);
  if (idatParts.length === 0) fail("missing IDAT chunk");
  const idat = Buffer.concat(idatParts);

  let raw;
  try {
    raw = inflateSync(idat);
  } catch (err) {
    fail("zlib inflate failed: " + err.message);
  }

  const expected = height * (stride + 1);
  if (raw.length !== expected) {
    fail(
      "decompressed size mismatch: got " + raw.length + " bytes, expected " + expected +
        " (colour type " + colorType + ", bit depth " + bitDepth + ")"
    );
  }

  const rgba = new Uint8Array(width * height * 4);
  const row = Buffer.alloc(stride);
  let prev = Buffer.alloc(stride);
  let pos = 0;

  for (let y = 0; y < height; y++) {
    const filter = raw[pos++];
    if (filter > 4) fail("unknown filter type " + filter + " on row " + y);
    const line = raw.subarray(pos, pos + stride);
    pos += stride;

    for (let x = 0; x < stride; x++) {
      const a = x >= bpp ? row[x - bpp] : 0;
      const b = prev[x];
      const c = x >= bpp ? prev[x - bpp] : 0;
      const cur = line[x];
      let value;
      if (filter === 0) value = cur;
      else if (filter === 1) value = cur + a;
      else if (filter === 2) value = cur + b;
      else if (filter === 3) value = cur + ((a + b) >> 1);
      else value = cur + paeth(a, b, c);
      row[x] = value & 0xff;
    }

    for (let x = 0; x < width; x++) {
      const d = (y * width + x) * 4;
      let r, g, b, alpha = 255;

      if (colorType === 3) {
        const index = readSample(row, x, 0, bitsPerPixel, bitDepth);
        if (index * 3 + 2 >= palette.length) {
          fail("palette index " + index + " out of range on row " + y);
        }
        r = palette[index * 3];
        g = palette[index * 3 + 1];
        b = palette[index * 3 + 2];
        if (paletteAlpha && index < paletteAlpha.length) alpha = paletteAlpha[index];
      } else if (colorType === 0) {
        const sample = readSample(row, x, 0, bitsPerPixel, bitDepth);
        const value = bitDepth === 8 ? sample : Math.round((sample * 255) / ((1 << bitDepth) - 1));
        r = g = b = value;
        if (grayKey !== null && sample === grayKey) alpha = 0;
      } else if (colorType === 6) {
        const s = x * 4;
        r = row[s];
        g = row[s + 1];
        b = row[s + 2];
        alpha = row[s + 3];
      } else if (colorType === 4) {
        const s = x * 2;
        r = g = b = row[s];
        alpha = row[s + 1];
      } else {
        const s = x * 3;
        r = row[s];
        g = row[s + 1];
        b = row[s + 2];
        if (rgbKey && r === rgbKey[0] && g === rgbKey[1] && b === rgbKey[2]) alpha = 0;
      }

      rgba[d] = r;
      rgba[d + 1] = g;
      rgba[d + 2] = b;
      rgba[d + 3] = alpha;
    }
    prev = Buffer.from(row);
  }

  return { width, height, rgba };
}

/* --------------------------------------------------------------- resample */

/** Nearest-neighbour resample a flat RGBA buffer to grid x grid. */
function resampleNearest(rgba, width, height, grid) {
  const out = new Uint8Array(grid * grid * 4);
  for (let y = 0; y < grid; y++) {
    const sy = Math.min(height - 1, Math.floor((y * height) / grid));
    for (let x = 0; x < grid; x++) {
      const sx = Math.min(width - 1, Math.floor((x * width) / grid));
      const s = (sy * width + sx) * 4;
      const d = (y * grid + x) * 4;
      out[d] = rgba[s];
      out[d + 1] = rgba[s + 1];
      out[d + 2] = rgba[s + 2];
      out[d + 3] = rgba[s + 3];
    }
  }
  return out;
}

/* ------------------------------------------------------------- ink mask */

function luminance(r, g, b) {
  // Rec. 709 relative luminance, normalised to 0..1.
  return (0.2126 * r + 0.7152 * g + 0.0722 * b) / 255;
}

function toInkMask(rgba, count, threshold, invert) {
  const out = new Uint8Array(count * 4);
  let ink = 0;
  for (let i = 0; i < count; i++) {
    const o = i * 4;
    const alpha = rgba[o + 3];
    let isInk = false;
    if (alpha !== 0) {
      const lum = luminance(rgba[o], rgba[o + 1], rgba[o + 2]);
      isInk = invert ? lum >= threshold : lum < threshold;
    }
    if (isInk) {
      out[o] = INK_R;
      out[o + 1] = INK_G;
      out[o + 2] = INK_B;
      out[o + 3] = 255;
      ink++;
    }
  }
  return { out, ink };
}

/* ---------------------------------------------------------------- PNG write */

function pngChunk(type, data) {
  const length = Buffer.alloc(4);
  length.writeUInt32BE(data.length, 0);
  const typeBuf = Buffer.from(type, "latin1");
  const crc = Buffer.alloc(4);
  crc.writeUInt32BE(crc32(Buffer.concat([typeBuf, data])), 0);
  return Buffer.concat([length, typeBuf, data, crc]);
}

function encodePng(rgba, size) {
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(size, 0);
  ihdr.writeUInt32BE(size, 4);
  ihdr[8] = 8; // bit depth
  ihdr[9] = 6; // colour type: RGBA
  ihdr[10] = 0; // compression
  ihdr[11] = 0; // filter method
  ihdr[12] = 0; // interlace: none

  const stride = size * 4;
  const raw = Buffer.alloc((stride + 1) * size);
  for (let y = 0; y < size; y++) {
    const rowStart = y * (stride + 1);
    raw[rowStart] = 0; // filter type 0 on every row
    for (let i = 0; i < stride; i++) raw[rowStart + 1 + i] = rgba[y * stride + i];
  }

  return Buffer.concat([
    PNG_SIG,
    pngChunk("IHDR", ihdr),
    pngChunk("IDAT", deflateSync(raw, { level: 9 })),
    pngChunk("IEND", Buffer.alloc(0)),
  ]);
}

/* -------------------------------------------------------------------- main */

function main() {
  const opts = parseArgs(process.argv.slice(2));

  let input;
  try {
    input = readFileSync(opts.input);
  } catch (err) {
    fail("cannot read " + opts.input + ": " + err.message);
  }

  const { width, height, rgba } = decodePng(input);
  const maskSource = resampleNearest(rgba, width, height, opts.grid);
  const { out, ink } = toInkMask(maskSource, opts.grid * opts.grid, opts.threshold, opts.invert);

  const png = encodePng(out, opts.grid);
  try {
    writeFileSync(opts.output, png);
  } catch (err) {
    fail("cannot write " + opts.output + ": " + err.message);
  }

  const coverage = (ink / (opts.grid * opts.grid)) * 100;
  console.log(
    opts.input + " " + width + "x" + height + " -> " + opts.output + " " + opts.grid + "x" + opts.grid +
      " | ink " + ink + "px | coverage " + coverage.toFixed(1) + "%"
  );
}

main();
