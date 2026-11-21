#!/usr/bin/env node
/**
 * Regenerates the signed-distance glyph sets the offline style uses for map text.
 *
 * The style renders text through `asset://glyphs/{fontstack}/{range}.pbf`, so every
 * label glyph must be baked into the APK ahead of time. Two stacks are built here:
 *
 *   AtlasRegular = Noto Sans Regular  (Latin, digits, punctuation)
 *                + Vazirmatn Regular (Persian/Arabic shaping forms)
 *   AtlasBold    = the bold pair
 *
 * Vazirmatn is the reference Persian UI face: closed counters, even stroke weight
 * and a proper set of Arabic presentation forms, which is what keeps street and
 * place names readable at 10-12 px on a map. Latin stays with Noto Sans because it
 * has the wider glyph set map labels need (accents, symbols, transliterations).
 *
 * Persian is only correct on MapLibre Native through glyph PBFs. With `font-faces`
 * every Arabic-script label is shaped left-to-right and laid out reversed; the glyph
 * path applies the usual BiDi reordering, so names read joined and in order.
 *
 * Usage (needs the `fontnik` module):
 *
 *     npm install --prefix tools fontnik
 *     node tools/generate_glyphs.js
 *
 * Output: app/src/main/assets/glyphs/Atlas{Regular,Bold}/<start>-<end>.pbf
 */

const fontnik = require('fontnik');
const fs = require('fs');
const path = require('path');

const ROOT = path.resolve(__dirname, '..');
const ASSETS = path.join(ROOT, 'app', 'src', 'main', 'assets');
const OUT = path.join(ASSETS, 'glyphs');

const STACKS = {
  AtlasRegular: [
    path.join(ASSETS, 'fonts', 'NotoSans-Regular.ttf'),
    path.join(__dirname, 'fonts', 'vazirmatn', 'Vazirmatn-Regular.ttf'),
  ],
  AtlasBold: [
    path.join(ASSETS, 'fonts', 'NotoSans-Bold.ttf'),
    path.join(__dirname, 'fonts', 'vazirmatn', 'Vazirmatn-Bold.ttf'),
  ],
};

// Latin/basic, Cyrillic-Greek, Arabic, Arabic Supplement, Arabic Extended-A,
// Arabic Presentation Forms A and B (what MapLibre's Arabic shaper asks for).
const RANGES = [
  [0, 255], [256, 511], [512, 767], [768, 1023], [1536, 1791], [1792, 2047],
  [2048, 2303], [8192, 8447], [64256, 64511], [64512, 64767], [64768, 65023],
  [65024, 65279],
];

function range(fontPath, start, end) {
  return new Promise((resolve, reject) => {
    const font = fs.readFileSync(fontPath);
    fontnik.range({ font, start, end }, (err, buf) => (err ? reject(err) : resolve(buf)));
  });
}

function composite(buffers) {
  return new Promise((resolve, reject) => {
    fontnik.composite(buffers, (err, buf) => (err ? reject(err) : resolve(buf)));
  });
}

async function main() {
  let written = 0;
  for (const [stack, fonts] of Object.entries(STACKS)) {
    const dir = path.join(OUT, stack);
    fs.mkdirSync(dir, { recursive: true });
    for (const [start, end] of RANGES) {
      const parts = [];
      for (const font of fonts) parts.push(await range(font, start, end));
      const merged = await composite(parts);
      fs.writeFileSync(path.join(dir, `${start}-${end}.pbf`), merged);
      written += 1;
    }
    console.log(`${stack}: ${RANGES.length} ranges from ${fonts.map((f) => path.basename(f)).join(' + ')}`);
  }
  console.log(`wrote ${written} glyph ranges to ${path.relative(ROOT, OUT)}`);
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
