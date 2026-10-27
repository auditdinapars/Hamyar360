'use strict';
const path=require('node:path');
const ALLOWED_KINDS=new Set(['accounts','categories','units','tasks','receivables','payables']);
function sanitizeName(s){const base=path.basename(String(s||'hasel360-export')).replace(/[<>:"/\\|?*\x00-\x1f]/g,'_').slice(0,96);return base||'hasel360-export'}
function normalizeReminders(data){
 const parsed=typeof data==='string'?JSON.parse(data):data;
 if(!Array.isArray(parsed)||parsed.length>10000)throw Error('یادآورهای نامعتبر');
 return parsed.map(x=>{if(!x||typeof x.id!=='string'||x.id.length>150||typeof x.title!=='string'||x.title.length>350||!Number.isSafeInteger(x.time)||x.time<0)throw Error('یادآور نامعتبر');return {id:x.id,title:x.title,time:x.time}});
}
function escapeIcs(s){return String(s).replace(/\\/g,'\\\\').replace(/\n/g,'\\n').replace(/;/g,'\\;').replace(/,/g,'\\,').replace(/[\r]/g,'')}
function icsUtc(millis){return new Date(millis).toISOString().replace(/[-:]/g,'').replace(/\.\d{3}Z$/,'Z')}
function createIcs(reminders,now=Date.now()){
 const lines=['BEGIN:VCALENDAR','VERSION:2.0','PRODID:-//Hasel360//Offline Desktop//FA','CALSCALE:GREGORIAN','METHOD:PUBLISH'];
 for(const x of reminders.filter(x=>x.time>=now)){
  const start=x.time,end=start+15*60*1000,uid=Buffer.from(x.id).toString('hex').slice(0,120)+'@hasel360.local';
  lines.push('BEGIN:VEVENT','UID:'+uid,'DTSTAMP:'+icsUtc(now),'DTSTART:'+icsUtc(start),'DTEND:'+icsUtc(end),'SUMMARY:'+escapeIcs('یادآور حاصل ۳۶۰'),'DESCRIPTION:'+escapeIcs(x.title),'BEGIN:VALARM','ACTION:DISPLAY','DESCRIPTION:'+escapeIcs('یادآور حاصل ۳۶۰'),'TRIGGER:-PT0M','END:VALARM','END:VEVENT');
 }
 lines.push('END:VCALENDAR');
 return lines.join('\r\n')+'\r\n';
}
module.exports={ALLOWED_KINDS,sanitizeName,normalizeReminders,createIcs};
