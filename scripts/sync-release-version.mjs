import { readFileSync, writeFileSync } from 'node:fs';

const { version } = JSON.parse(readFileSync('package.json', 'utf8'));
const properties = readFileSync('gradle.properties', 'utf8');
const entries = [...properties.matchAll(/^mod_version=[^\r\n]+/gm)];
if (entries.length !== 1) throw new Error('Expected one mod_version entry in gradle.properties');
writeFileSync('gradle.properties', properties.replace(entries[0][0], `mod_version=${version}`));
