import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const html = readFileSync('app/src/main/assets/index.html', 'utf8');
const supplementary = readFileSync('app/src/main/assets/telecom-data.js', 'utf8');
new Function('const window={};\n' + supplementary);
const scripts = [...html.matchAll(/<script(?:\s[^>]*)?>([\s\S]*?)<\/script>/g)].map(x => x[1]).filter(x => x.trim().length > 0);
assert.ok(scripts.length >= 2, 'Expected course data and app scripts');
for (const [index, script] of scripts.entries()) {
  try {
    new Function(script);
  } catch (error) {
    throw new Error('JavaScript syntax error in script ' + (index + 1) + ': ' + error.message);
  }
}
const info = new Function('const window={};\n' + supplementary + '\n' + scripts[0] + '\nreturn { total: ALL_PHRASES.length, lessons: LESSONS.length, phrases: ALL_PHRASES, telecom: TELECOM_LESSONS.length, extra: window.TELECOM_EXTRA.length, tracks: CAREER_TRACKS, ids: LESSONS.map(l=>l.id) };')();
assert.equal(info.total, 3000, 'Expected exactly 3000 lessons phrases');
assert.ok(info.telecom >= 12, 'Expected network design categories');
assert.equal(info.extra, 6, 'Expected six extra telecom network design lessons');
assert.equal(info.tracks.length, 4, 'Expected four career tracks');
for(const track of info.tracks) for(const id of track.ids) assert.ok(info.ids.includes(id), 'Missing career module: ' + id);
assert.ok(info.lessons >= 40, 'Expected plenty of real-world lessons');
const keys = info.phrases.map(p => p.id);
assert.equal(new Set(keys).size, keys.length, 'Expected unique phrase IDs');
const unique = new Set(info.phrases.map(p => p.en.toLowerCase()));
assert.ok(unique.size >= 2950, 'Expected diverse phrases');
console.log('Verified:', info.total, 'phrases;', info.lessons, 'lessons;', info.telecom + info.extra, 'telecom modules; 4 professional tracks');
