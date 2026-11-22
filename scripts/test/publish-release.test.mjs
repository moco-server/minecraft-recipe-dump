import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, mkdirSync, writeFileSync, readFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, delimiter } from 'node:path';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const script = fileURLToPath(new URL('../publish-release.sh', import.meta.url));
// 外部GitHub APIだけを置き換え、実際の公開scriptのコマンド・順序・失敗停止を検査する。
function run(t, { state = 'absent', uploadFails = false, version = '1.2.3', notesVersion = version, tag = `v${version}` } = {}) {
  const root = mkdtempSync(join(tmpdir(), 'recipe-publish-'));
  t.after(() => rmSync(root, { recursive: true, force: true }));
  mkdirSync(join(root, 'bin')); mkdirSync(join(root, 'assets'));
  writeFileSync(join(root, 'gradle.properties'), `mod_id=minecraft_recipe_dump\nmod_version=${version}\n`);
  writeFileSync(join(root, 'CHANGELOG.md'), `# Changes\n\n## ${notesVersion}\n\nRelease notes\n\n## 0.0.1\nEarlier\n`);
  for (const name of [`minecraft_recipe_dump-${version}.jar`, `recipe-dump-${version}.zip`]) writeFileSync(join(root, 'assets', name), 'artifact');
  writeFileSync(join(root, 'bin/gh'), `#!/usr/bin/env node
const fs = require('node:fs');
const args = process.argv.slice(2);
fs.appendFileSync('calls.jsonl', JSON.stringify(args) + '\\n');
if (args[1] === 'view') {
  if (process.env.RELEASE_STATE === 'absent') process.exit(1);
  console.log(JSON.stringify({ isDraft: process.env.RELEASE_STATE === 'draft' }));
} else if (args[1] === 'upload' && process.env.UPLOAD_FAILS === 'true') process.exit(1);
`, { mode: 0o755 });
  const result = spawnSync('bash', [script, tag, 'assets'], {
    cwd: root, encoding: 'utf8',
    env: { ...process.env, PATH: `${join(root, 'bin')}${delimiter}${process.env.PATH}`, RELEASE_STATE: state, UPLOAD_FAILS: String(uploadFails) },
  });
  const calls = result.status === 127 ? [] : (() => {
    try { return readFileSync(join(root, 'calls.jsonl'), 'utf8').trim().split('\n').map(JSON.parse); }
    catch { return []; }
  })();
  return { result, calls, root };
}
const linux = { skip: process.platform !== 'linux' };
test('uploads both assets before publishing a stable draft', linux, (t) => {
  const { result, calls, root } = run(t);
  assert.equal(result.status, 0, result.stderr);
  assert.deepEqual(calls, [
    ['release', 'view', 'v1.2.3', '--json', 'isDraft'],
    ['release', 'create', 'v1.2.3', '--verify-tag', '--draft', '--notes', 'Release notes', '--title', 'v1.2.3'],
    ['release', 'upload', 'v1.2.3', join(root, 'assets/minecraft_recipe_dump-1.2.3.jar'), join(root, 'assets/recipe-dump-1.2.3.zip'), '--clobber'],
    ['release', 'edit', 'v1.2.3', '--draft=false', '--prerelease=false', '--latest=true'],
  ]);
});
test('does not mark a prerelease as latest', linux, (t) => {
  const { result, calls } = run(t, { version: '1.2.3-rc.1' });
  assert.equal(result.status, 0, result.stderr);
  assert.ok(calls[1].includes('--prerelease'));
  assert.deepEqual(calls.at(-1), ['release', 'edit', 'v1.2.3-rc.1', '--draft=false', '--prerelease=true', '--latest=false']);
});
test('resumes an existing draft without creating it again', linux, (t) => {
  const { result, calls } = run(t, { state: 'draft' });
  assert.equal(result.status, 0, result.stderr);
  assert.deepEqual(calls.map((args) => args[1]), ['view', 'upload', 'edit']);
});
test('leaves the draft unpublished when upload fails', linux, (t) => {
  const { result, calls } = run(t, { uploadFails: true });
  assert.notEqual(result.status, 0);
  assert.deepEqual(calls.map((args) => args[1]), ['view', 'create', 'upload']);
});
test('does not overwrite a published release', linux, (t) => {
  const { result, calls } = run(t, { state: 'published' });
  assert.notEqual(result.status, 0);
  assert.deepEqual(calls.map((args) => args[1]), ['view']);
});
test('does not call GitHub when tag validation fails', linux, (t) => {
  const { result, calls } = run(t, { tag: 'v1.2.4' });
  assert.notEqual(result.status, 0);
  assert.deepEqual(calls, []);
});

test('does not create a draft if this version has no changelog entry', linux, (t) => {
  const { result, calls } = run(t, { notesVersion: '1.2.2' });
  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /Missing changelog entry/);
  assert.deepEqual(calls.map((args) => args[1]), ['view']);
});
