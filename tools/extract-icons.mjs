// Gera nekkora-icons.txt a partir do conjunto de ícones da nekkora-lib (web),
// para que Swing e Web usem exatamente os mesmos desenhos.
//   node tools/extract-icons.mjs ../Nekkora-lib/src/components/Icon/icons.tsx tools/extra-icons.txt > <saida>
import { readFileSync } from 'node:fs';

const [src, extra] = process.argv.slice(2);
const text = readFileSync(src, 'utf8');
const attrs = (tag) => Object.fromEntries([...tag.matchAll(/([a-zA-Z-]+)="([^"]*)"/g)].map((m) => [m[1], m[2]]));
const n = (v) => Number(v ?? 0);

function circle(cx, cy, rx, ry = rx) {
  return `M${cx - rx} ${cy}A${rx} ${ry} 0 1 0 ${cx + rx} ${cy}A${rx} ${ry} 0 1 0 ${cx - rx} ${cy}Z`;
}
function rect(x, y, w, h, r) {
  if (!r) return `M${x} ${y}H${x + w}V${y + h}H${x}Z`;
  return `M${x + r} ${y}H${x + w - r}A${r} ${r} 0 0 1 ${x + w} ${y + r}V${y + h - r}A${r} ${r} 0 0 1 ${x + w - r} ${y + h}H${x + r}A${r} ${r} 0 0 1 ${x} ${y + h - r}V${y + r}A${r} ${r} 0 0 1 ${x + r} ${y}Z`;
}

const out = ['# Nekkora icons — grade 24x24. D = camada duotone (preenchida, translúcida) · S = traço · F = preenchimento sólido', ''];
const blockRe = /^  '?([a-z-]+)'?: (?:\(\n([\s\S]*?)\n  \)|(<[^\n]*\/>)),$/gm;
for (const [, name, multi, single] of text.matchAll(blockRe)) {
  const body = multi ?? single;
  out.push(`@${name}`);
  for (const [tag, kind] of body.matchAll(/<(Duo|path|circle|rect|ellipse)\b[^>]*\/>/g)) {
    const a = attrs(tag);
    const solid = a.fill === 'currentColor';
    let d;
    if (kind === 'Duo' || kind === 'path') d = a.d;
    else if (kind === 'circle') d = circle(n(a.cx), n(a.cy), n(a.r));
    else if (kind === 'ellipse') d = circle(n(a.cx), n(a.cy), n(a.rx), n(a.ry));
    else d = rect(n(a.x), n(a.y), n(a.width), n(a.height), n(a.rx));
    out.push(`${kind === 'Duo' ? 'D' : solid ? 'F' : 'S'} ${d}`);
  }
  out.push('');
}
if (extra) out.push(readFileSync(extra, 'utf8').trim(), '');
process.stdout.write(out.join('\n'));
