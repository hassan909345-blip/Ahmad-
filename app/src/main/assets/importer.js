/* Client-only subtitle/transcript reader; does not upload imported media or dialogue. */
(function(){
'use strict';
function clean(s) {
 return String(s||'').replace(/<[^>]*>/g,' ').replace(/&nbsp;/gi,' ').replace(/&amp;/gi,'&').replace(/&lt;/gi,'<').replace(/&gt;/gi,'>')
 .replace(/\{\\an\d+\}/g,' ').replace(/\s+/g,' ').trim();
}
const timeRE=/(?:(\d{1,2}):)?(\d{2}):(\d{2})[,.](\d{1,3})\s*-->\s*(?:(\d{1,2}):)?(\d{2}):(\d{2})[,.](\d{1,3})/;
function seconds(h,m,s,ms){return (+h||0)*3600+(+m||0)*60+(+s||0)+(+ms||0)/Math.pow(10,String(ms||'0').length);}
function normalizeSpeaker(line, fallback){
 let text=clean(line), speaker=fallback;
 const match=text.match(/^(?:-\s*)?([A-Za-z][A-Za-z\s]{0,24}):\s+(.+)$/);
 if(match){speaker=match[1];text=match[2];}
 return {speaker,text};
}
function parse(content,filename='محادثة مستوردة'){
 if(typeof content!=='string'||content.length>5e6)throw new Error('الملف كبير جدًا: الحد الأقصى 5 ميجابايت لكل ملف.');
 let raw=content.replace(/^\uFEFF/,'').replace(/\r\n?/g,'\n').trim();
 if(!raw)throw new Error('الملف فارغ.');
 const lines=[];
 const timed=timeRE.test(raw);
 if(timed){
   for(const part of raw.split(/\n\s*\n/)){
     const rows=part.split('\n');const i=rows.findIndex(x=>timeRE.test(x));if(i<0)continue;
     const times=rows[i].match(timeRE);
     let cue=rows.slice(i+1).filter(x=>x.trim()&&!/^NOTE\s/i.test(x)).map(x=>x.trim()).join(' ');
     cue=cue.replace(/^[-–]\s+/,'').trim();
     if(!cue)continue;
     const fields=normalizeSpeaker(cue,lines.length%2?'B':'A');
     if(!fields.text)continue;
     lines.push({
       speaker:fields.speaker,en:fields.text,ar:'',
       start:seconds(times[1],times[2],times[3],times[4]),
       end:seconds(times[5],times[6],times[7],times[8])
     });
     if(lines.length>=2200)break;
   }
 }else{
   for(const row of raw.split('\n')){
     const value=row.trim();if(!value||/^WEBVTT$/i.test(value))continue;
     let divider=value.indexOf('|');if(divider<0)divider=value.indexOf('\t');
     const english=divider>=0?value.slice(0,divider):value;
     const arabic=divider>=0?value.slice(divider+1):'';
     const speaker=normalizeSpeaker(english,lines.length%2?'B':'A');
     if(!speaker.text)continue;
     lines.push({speaker:speaker.speaker,en:speaker.text,ar:clean(arabic),start:null,end:null});
     if(lines.length>=2200)break;
   }
 }
 if(!lines.length)throw new Error('لم أجد جملًا قابلة للقراءة. استخدم SRT أو VTT أو TXT.');
 const name=String(filename).split(/[\\/]/).pop().replace(/\.(srt|vtt|txt)$/i,'').trim().slice(0,85)||'محادثة مستوردة';
 return {name,kind:timed?'subtitles':'transcript',turns:lines};
}
window.EtkallemImporter={parse,clean};
})();
