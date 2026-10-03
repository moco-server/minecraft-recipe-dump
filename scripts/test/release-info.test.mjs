import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, mkdirSync, writeFileSync, rmSync, symlinkSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const script = fileURLToPath(new URL('../release-info.mjs', import.meta.url));
function fixture(t, version = '1.2.3') {
  const root = mkdtempSync(join(tmpdir(), 'recipe-release-'));
  t.after(() => rmSync(root, { recursive: true, force: true }));
  writeFileSync(join(root, 'gradle.properties'), `mod_id=minecraft_recipe_dump\nmod_version=${version}\n`);
  mkdirSync(join(root, 'assets'));
  for (const name of ['minecraft_recipe_dump-1.2.3.jar', 'recipe-dump-1.2.3.zip']) {
    writeFileSync(join(root, 'assets', name), 'artifact');
  }
  return root;
}
function run(root, tag, assets = false) {
  return spawnSync(process.execPath, [script, tag, ...(assets ? ['assets'] : [])], { cwd: root, encoding: 'utf8' });
}
test('accepts a matching stable version and identifies both release assets', (t) => {
  const root = fixture(t);
  const result = run(root, 'v1.2.3', true);
  assert.equal(result.status, 0, result.stderr);
  assert.deepEqual(JSON.parse(result.stdout), {
    version: '1.2.3', prerelease: false,
    assets: [join(root, 'assets/minecraft_recipe_dump-1.2.3.jar'), join(root, 'assets/recipe-dump-1.2.3.zip')],
  });
});
test('marks a prerelease with build metadata as a prerelease', (t) => {
  const root = fixture(t, '1.2.3-rc.1+build.7');
  const result = run(root, 'v1.2.3-rc.1+build.7');
  assert.equal(result.status, 0, result.stderr);
  assert.deepEqual(JSON.parse(result.stdout), { version: '1.2.3-rc.1+build.7', prerelease: true });
});
for (const tag of ['v1.2.4', '1.2.3']) {
  test(`rejects an invalid or mismatched tag: ${tag}`, (t) => {
    const result = run(fixture(t), tag);
    assert.notEqual(result.status, 0);
    assert.equal(result.stdout, '');
  });
}
for (const version of ['01.2.3', '1.2', '1.2.3/other', '1.2.3-01', '1.2.3+']) {
  test(`rejects invalid SemVer even when the tag matches mod_version: ${version}`, (t) => {
    const result = run(fixture(t, version), `v${version}`);
    assert.notEqual(result.status, 0);
    assert.equal(result.stdout, '');
  });
}
for (const fault of ['missing', 'extra', 'wrong-version', 'empty', 'directory']) {
  test(`rejects ${fault} release assets`, (t) => {
    const root = fixture(t); const jar = join(root, 'assets/minecraft_recipe_dump-1.2.3.jar');
    if (fault === 'extra') writeFileSync(join(root, 'assets/sources.jar'), 'source');
    else {
      rmSync(jar);
      if (fault === 'wrong-version') writeFileSync(join(root, 'assets/minecraft_recipe_dump-1.2.2.jar'), 'old');
      if (fault === 'empty') writeFileSync(jar, '');
      if (fault === 'directory') mkdirSync(jar);
    }
    const result = run(root, 'v1.2.3', true);
    assert.notEqual(result.status, 0);
    assert.equal(result.stdout, '');
  });
}
test('rejects symlink assets', { skip: process.platform === 'win32' }, (t) => {
  const root = fixture(t); const jar = join(root, 'assets/minecraft_recipe_dump-1.2.3.jar');
  rmSync(jar); symlinkSync(join(root, 'gradle.properties'), jar);
  assert.notEqual(run(root, 'v1.2.3', true).status, 0);
});
