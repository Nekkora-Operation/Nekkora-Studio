// Gera IconName.java (enum com categoria) a partir de icons.tsx + extra-icons.txt.
import { readFileSync } from 'node:fs';
const [src, extra] = process.argv.slice(2);
const cats = { 'Navegação': 'NAVIGATION', 'Ações': 'ACTIONS', 'Serviços': 'SERVICES', 'Marcas': 'BRANDS', 'Setas': 'ARROWS', 'Status': 'STATUS', 'Tema': 'THEME', 'Objetos': 'OBJECTS' };
const extraCats = { pause: 'ACTIONS', stop: 'ACTIONS', save: 'ACTIONS', terminal: 'OBJECTS', sliders: 'ACTIONS', 'panel-left': 'LAYOUT', 'panel-right': 'LAYOUT', list: 'LAYOUT', grid: 'LAYOUT', history: 'OBJECTS', tag: 'OBJECTS', sort: 'ARROWS', 'folder-open': 'OBJECTS', 'wifi-off': 'STATUS', key: 'OBJECTS', command: 'ACTIONS', braces: 'OBJECTS', 'lock-open': 'OBJECTS', 'circle-dashed': 'STATUS', 'minus-circle': 'STATUS', 'pause-circle': 'STATUS', image: 'OBJECTS' };
let cat = 'NAVIGATION';
const entries = [];
for (const line of readFileSync(src, 'utf8').split('\n')) {
  const h = line.match(/^  \/\/ -+ (.+)$/);
  if (h) cat = cats[h[1].trim()] ?? cat;
  const m = line.match(/^  '?([a-z-]+)'?: (?:\($|<)/);
  if (m) entries.push([m[1], cat]);
}
for (const line of readFileSync(extra, 'utf8').split('\n')) {
  const m = line.match(/^@([a-z-]+)$/);
  if (m) entries.push([m[1], extraCats[m[1]] ?? 'OBJECTS']);
}
const body = entries.map(([n, c]) => `    ${n.toUpperCase().replace(/-/g, '_')}("${n}", IconCategory.${c})`).join(',\n');
process.stdout.write(`package com.nekkora.ui.icon;

/**
 * Ícones Nekkora (mesmo desenho da nekkora-lib web). Gerado por {@code tools/gen-icon-enum.mjs}.
 */
public enum IconName {
${body};

    private final String id;
    private final IconCategory category;

    IconName(String id, IconCategory category) {
        this.id = id;
        this.category = category;
    }

    /** Nome do ícone no conjunto (ex.: {@code chevron-down}). */
    public String id() {
        return id;
    }

    public IconCategory category() {
        return category;
    }
}
`);
