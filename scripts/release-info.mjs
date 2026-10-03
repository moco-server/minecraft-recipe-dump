import { readFileSync, readdirSync, lstatSync } from 'node:fs';
import { join, resolve } from 'node:path';

try {
  const [tag, directory, ...extra] = process.argv.slice(2);
  if (!tag || extra.length) throw new Error('Usage: node scripts/release-info.mjs <tag> [asset-directory]');
  const properties = readFileSync('gradle.properties', 'utf8');
  const version = properties.match(/^mod_version=(.+)$/m)?.[1].trim();
  const modId = properties.match(/^mod_id=(.+)$/m)?.[1].trim();
  const number = '(0|[1-9][0-9]*)';
  const identifier = '(?:0|[1-9][0-9]*|[0-9]*[A-Za-z-][0-9A-Za-z-]*)';
  const semver = new RegExp(`^${number}\\.${number}\\.${number}(?:-(${identifier}(?:\\.${identifier})*))?(?:\\+[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*)?$`);
  const match = version?.match(semver);
  if (!match || tag !== `v${version}`) throw new Error(`Release tag must equal v<mod_version> with a SemVer version (mod_version=${version}, tag=${tag})`);
  if (!modId || !/^[a-z][a-z0-9_]*$/.test(modId)) throw new Error('Invalid mod_id');
  const info = { version, prerelease: Boolean(match[4]) };
  if (directory) {
    const names = [`${modId}-${version}.jar`, `recipe-dump-${version}.zip`];
    const actual = readdirSync(directory).sort();
    if (JSON.stringify(actual) !== JSON.stringify([...names].sort())) throw new Error(`Expected only ${names.join(', ')}; found ${actual.join(', ')}`);
    info.assets = names.map((name) => {
      const file = resolve(join(directory, name));
      const stat = lstatSync(file);
      if (!stat.isFile() || stat.isSymbolicLink() || stat.size === 0) throw new Error(`Release asset must be a nonempty regular file: ${name}`);
      return file;
    });
  }
  console.log(JSON.stringify(info));
} catch (error) {
  console.error(error.message);
  process.exitCode = 1;
}
