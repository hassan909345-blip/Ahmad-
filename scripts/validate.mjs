import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const html = readFileSync('app/src/main/assets/index.html', 'utf8');
const supplementary = readFileSync('app/src/main/assets/telecom-data.js', 'utf8');
const stories = readFileSync('app/src/main/assets/stories-data.js', 'utf8');
const importer = readFileSync('app/src/main/assets/importer.js', 'utf8');
const episodeVideos = readFileSync('app/src/main/assets/series-episodes.js', 'utf8');
const fourWorkbooks=readFileSync('app/src/main/assets/four-episode-workbooks.js','utf8');
const availableVideos = new Function('const window = {};\n' + episodeVideos + '\nreturn window.MASHHADAK_EPISODES;')();
assert.equal(availableVideos.length, 30, 'Expected 30 linked Extra English episodes');
assert.equal(new Set(availableVideos.map(x=>x.youtubeId)).size, 30, 'Video IDs must be unique');
assert.deepEqual(availableVideos.filter(x=>x.driveId).map(x=>x.number), [1,2,3,4], 'Google Drive originals must be connected for episodes one to four');
for(const ep of availableVideos.filter(x=>x.driveId))assert.ok(/^[A-Za-z0-9_-]{10,80}$/.test(ep.driveId));
for (const entry of availableVideos) {
 assert.ok(/^[a-zA-Z0-9_-]{11}$/.test(entry.youtubeId), 'Bad YouTube ID: '+entry.number);
 assert.equal(entry.exercises.length, 3, 'Every episode should have three original teaching expressions');
}

new Function('const window={};\n' + supplementary);
new Function('const window={};\n' + stories);
new Function('const window={};\n' + importer);
new Function('const window={};\n' + episodeVideos);
new Function('const window={};\n' + fourWorkbooks);
const scripts = [...html.matchAll(/<script(?:\s[^>]*)?>([\s\S]*?)<\/script>/g)].map(x => x[1]).filter(x => x.trim().length > 0);
assert.ok(scripts.length >= 2, 'Expected course data and app scripts');
for (const [index, script] of scripts.entries()) {
  try {
    new Function(script);
  } catch (error) {
    throw new Error('JavaScript syntax error in script ' + (index + 1) + ': ' + error.message);
  }
}
const info = new Function('const window={};\n' + supplementary + '\n' + stories + '\n' + fourWorkbooks + '\n' + scripts[0] + '\nreturn { total: ALL_PHRASES.length, lessons: LESSONS.length, phrases: ALL_PHRASES, telecom: TELECOM_LESSONS.length, extra: window.TELECOM_EXTRA.length, tracks: CAREER_TRACKS, ids: LESSONS.map(l=>l.id), stories: window.ETKALLEM_STORIES.length, workbooks: FOUR_EPISODE_WORKBOOKS, base: targetTotal };')();
assert.equal(info.base, 3000, 'Expected exactly 3000 base phrases');
assert.equal(info.total, 3160, 'Expected base phrases plus 160 original episode exercises');
assert.equal(info.workbooks.length, 4, 'Expected four Drive episode workbooks');
for(const [i,book] of info.workbooks.entries()) {
 assert.equal(book.episode, i+1);
 assert.equal(book.scenes.length, 8, 'Expected 8 scenes per episode');
 assert.equal(book.scenes.reduce((n,s)=>n+s.lines.length,0),40,'Expected 40 phrases per episode');
 assert.ok(info.ids.includes('extra_ep'+(i+1)), 'Missing full episode lesson');
}
assert.ok(info.telecom >= 12, 'Expected network design categories');
assert.equal(info.extra, 6, 'Expected six extra telecom network design lessons');
assert.equal(info.stories, 10, 'Expected ten original role-play episodes');
assert.equal(info.tracks.length, 4, 'Expected four career tracks');
for(const track of info.tracks) for(const id of track.ids) assert.ok(info.ids.includes(id), 'Missing career module: ' + id);
assert.ok(info.lessons >= 40, 'Expected plenty of real-world lessons');
const keys = info.phrases.map(p => p.id);
assert.equal(new Set(keys).size, keys.length, 'Expected unique phrase IDs');
const unique = new Set(info.phrases.map(p => p.en.toLowerCase()));
assert.ok(unique.size >= 2950, 'Expected diverse phrases');
console.log('Verified:', info.total, 'phrases including four episode workbooks;', info.lessons, 'lessons;', info.telecom + info.extra, 'telecom modules; 4 professional tracks');

const p = new Function('const window={};\n' + importer + '\nreturn window.EtkallemImporter.parse;')();
const srt = p('1\n00:00:01,000 --> 00:00:03,000\nA: Hello there!\n\n2\n00:00:03,000 --> 00:00:06,000\nB: How are you?', 'episode.srt');
assert.equal(srt.turns.length, 2);
assert.equal(srt.turns[0].en, 'Hello there!');
assert.equal(srt.turns[1].start, 3);
const vtt = p('WEBVTT\n\n00:00:02.100 --> 00:00:04.500\nGood morning!', 'episode.vtt');
assert.equal(vtt.turns.length, 1);
const txt = p('A: Hello! | أهلاً!\nB: Welcome! | مرحباً!', 'episode.txt');
assert.equal(txt.turns.length, 2);
assert.equal(txt.turns[0].ar, 'أهلاً!');
console.log('Subtitle import tests passed: SRT / VTT / TXT');
console.log('Thirty linked external videos and all original study exercises checked.');
