import fs from 'node:fs';
const raw=fs.readFileSync('app/src/main/assets/verified-excerpts.js','utf8');
const data=new Function('const window={};\n'+raw+'\nreturn window.MASHHADAK_VERIFIED_EXCERPTS')();
const dir='build/verified-excerpts';fs.mkdirSync(dir,{recursive:true});
const pad=(n,k=2)=>String(n).padStart(k,'0');
const stamp=t=>{let ms=Math.round(Math.max(0,t)*1000);return pad(Math.floor(ms/3600000))+':'+pad(Math.floor(ms/60000)%60)+':'+pad(Math.floor(ms/1000)%60)+','+pad(ms%1000,3)};
for(let n=1;n<=4;n++){
 const lines=data[n]||[];
 const readable=['مقتطفات منتقاة من الحلقة '+n,'ملف غير كامل. التوقيت وقت التقاط الصورة وليس توقيت بداية ظهور الجملة.',''];
 const srt=[];
 for(let i=0;i<lines.length;i++){
  const p=lines[i];
  readable.push('['+pad(Math.floor(p.second/60))+':'+pad(p.second%60)+' تقريبًا] '+p.en,'المعنى: '+p.ar,'');
  const overlap=lines.slice(0,i).filter(x=>x.second===p.second).length;
  srt.push(String(i+1),stamp(p.second-1+overlap*2)+' --> '+stamp(p.second+1+overlap*2),p.en,p.ar,'');
 }
 fs.writeFileSync(dir+'/episode_'+n+'_sample.txt',readable.join('\n'));
 fs.writeFileSync(dir+'/episode_'+n+'_approximate.srt',srt.join('\n'));
}
fs.writeFileSync(dir+'/README_AR.txt','هذه مقتطفات حقيقية منتقاة من ترجمة الفيديوهات الأربع وليست سكربت كاملًا. توقيت SRT تقديري، ويحتاج تعديلًا للعرض المتزامن.');
console.log('Prepared preview captions for four episodes');
