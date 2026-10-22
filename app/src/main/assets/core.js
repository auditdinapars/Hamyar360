(function(root){
'use strict';
const months=['فروردین','اردیبهشت','خرداد','تیر','مرداد','شهریور','مهر','آبان','آذر','دی','بهمن','اسفند'];
const norm=s=>String(s??'').replace(/[\u200e\u200f\u202a-\u202e\u2066-\u2069]/g,'').replace(/[۰-۹]/g,c=>'۰۱۲۳۴۵۶۷۸۹'.indexOf(c)).replace(/[٠-٩]/g,c=>'٠١٢٣٤٥٦٧٨٩'.indexOf(c)).replace(/ي/g,'ی').replace(/ك/g,'ک').replace(/[٬,]/g,'').trim();
const calendar=new Intl.DateTimeFormat('en-US-u-ca-persian-nu-latn',{year:'numeric',month:'numeric',day:'numeric',timeZone:'UTC'});
function parts(d){const p=Object.fromEntries(calendar.formatToParts(d).map(x=>[x.type,x.value]));return [+p.year,+p.month,+p.day];}
const dateKey=(y,m,d)=>`${y}/${String(m).padStart(2,'0')}/${String(d).padStart(2,'0')}`;
function today(){const d=new Date();return dateKey(...parts(new Date(Date.UTC(d.getFullYear(),d.getMonth(),d.getDate()))));}
const starts=new Map();
function yearStart(y){if(!Number.isInteger(y)||y<1300||y>1601)throw Error('سال نامعتبر');if(!starts.has(y)){let d=new Date(Date.UTC(y+621,2,18));for(let i=0;i<8;i++){const p=parts(d);if(p[0]===y&&p[1]===1&&p[2]===1){starts.set(y,d.getTime());break}d.setUTCDate(d.getUTCDate()+1)}if(!starts.has(y))throw Error('تقویم در دسترس نیست')}return starts.get(y)}
function monthDays(y,m){return m<7?31:m<12?30:Math.round((yearStart(y+1)-yearStart(y))/86400000)-336;}
function validDate(s){const a=norm(s).match(/^(\d{4})\/(\d{1,2})\/(\d{1,2})$/);if(!a)return false;const [y,m,d]=a.slice(1).map(Number);return y>=1300&&y<=1600&&m>=1&&m<=12&&d>=1&&d<=monthDays(y,m)}
function dateNorm(s){if(!validDate(s))return '';return dateKey(...norm(s).split('/').map(Number));}
function gregorian(s){const [y,m,d]=dateNorm(s).split('/').map(Number);return new Date(yearStart(y)+((m<=6?(m-1)*31:186+(m-7)*30)+d-1)*86400000)}
function addDays(s,n){return dateKey(...parts(new Date(gregorian(s).getTime()+n*86400000)));}
const small={'صفر':0,'یک':1,'یه':1,'دو':2,'سه':3,'چهار':4,'پنج':5,'شش':6,'هفت':7,'هشت':8,'نه':9,'ده':10,'یازده':11,'دوازده':12,'سیزده':13,'چهارده':14,'پانزده':15,'شانزده':16,'هفده':17,'هجده':18,'نوزده':19,'بیست':20,'سی':30,'چهل':40,'پنجاه':50,'شصت':60,'هفتاد':70,'هشتاد':80,'نود':90,'صد':100,'دویست':200,'سیصد':300,'چهارصد':400,'پانصد':500,'ششصد':600,'هفتصد':700,'هشتصد':800,'نهصد':900};
function numberWords(s){let total=0,group=0,found=false;for(const w of norm(s).split(/\s+/)){if(w==='و')continue;if(/^\d+(\.\d+)?$/.test(w)){group+=Number(w);found=true}else if(w in small){group+=small[w];found=true}else if(w==='هزار'){group=(group||1)*1000;found=true}else if(w==='میلیون'||w==='میلیارد'){total+=(group||1)*(w==='میلیون'?1e6:1e9);group=0;found=true}else return null}return found?total+group:null;}
function draft(text,target='tasks',base=today()){
 const s=norm(text).replace(/‌/g,' ');let date='',amount=null;
 const numeric=s.match(/\d{4}\/\d{1,2}\/\d{1,2}/);if(numeric)date=dateNorm(numeric[0]);
 if(!date){if(/پس فردا/.test(s))date=addDays(base,2);else if(/فردا/.test(s))date=addDays(base,1);else if(/امروز/.test(s))date=base;}
 if(!date){for(let i=0;i<months.length;i++){const pos=s.indexOf(months[i]);if(pos<0)continue;const before=s.slice(0,pos).trim().split(/\s+/);let day=null;for(let n=Math.min(5,before.length);n>=1;n--){const v=numberWords(before.slice(-n).join(' '));if(v>=1&&v<=31){day=v;break}}const year=s.slice(pos+months[i].length).match(/^\s*(1[3-6]\d{2})/);if(day){date=dateNorm(`${year?year[1]:base.slice(0,4)}/${i+1}/${day}`)}break;}}
 const unit=s.match(/تومان|ریال|RIAL|IRR/i);if(unit){const tokens=s.slice(0,unit.index).trim().split(/\s+/);for(let n=Math.min(15,tokens.length);n>=1;n--){const v=numberWords(tokens.slice(-n).join(' '));if(v!==null){const a=v*(unit[0]==='تومان'?10:1);if(Number.isSafeInteger(a)&&a>0)amount=a;break}}}
 if(amount===null){const labeled=s.match(/(?:مبلغ|جمع کل|جمع قابل پرداخت|Amount)\s*[:：]?\s*(\d{3,})/i);if(labeled&&Number.isSafeInteger(+labeled[1]))amount=+labeled[1];}
 let kind=target;if(!['tasks','pay','receive'].includes(kind))kind=amount!==null?(/دریافت|بگیرم|طلب|واریز.*به من/.test(s)?'receive':'pay'):'tasks';
 return {kind,title:text.trim().slice(0,180),date,amount,photo:''};
}
function validate(data){
 if(!data||!Array.isArray(data.tasks)||!Array.isArray(data.debts)||data.tasks.length+data.debts.length>10000)throw Error('فایل پشتیبان معتبر نیست');
 const ids=new Set();const clean=(x,debt)=>{if(!x||typeof x.id!=='string'||!x.id||ids.has(x.id)||typeof x.title!=='string'||!x.title.trim()||x.title.length>180||typeof x.done!=='boolean'||typeof x.date!=='string'||(x.date&&!validDate(x.date)))throw Error('رکورد نامعتبر در پشتیبان');ids.add(x.id);
 if(debt&&(!['pay','receive'].includes(x.type)||!Number.isSafeInteger(x.amount)||x.amount<=0||!x.date))throw Error('تعهد نامعتبر');
 if(x.photo&&(!(typeof x.photo==='string')||x.photo.length>2000000||!/^data:image\/(jpeg|png|webp);base64,[A-Za-z0-9+/=]+$/.test(x.photo)))throw Error('عکس نامعتبر');
 if(x.reminderAt && (!Number.isSafeInteger(x.reminderAt)||x.reminderAt<0))throw Error('یادآور نامعتبر');
 const r={reminderAt:x.reminderAt||0,id:x.id,title:x.title,date:x.date?dateNorm(x.date):'',done:x.done,photo:x.photo||'',createdAt:typeof x.createdAt==='string'?x.createdAt:'',completedAt:typeof x.completedAt==='string'?x.completedAt:''};if(debt){r.type=x.type;r.amount=x.amount}return r;};
 const inbox=(data.inbox||[]).map(x=>{if(!x||typeof x.id!=='string'||typeof x.text!=='string'||x.text.length>20000||typeof x.processed!=='boolean'||(x.photo&&!/^data:image\/(jpeg|png|webp);base64,[A-Za-z0-9+/=]+$/.test(x.photo))||(x.photo||'').length>2000000)throw Error('پیام ورودی نامعتبر');return {id:x.id,text:x.text,photo:x.photo||'',processed:x.processed,recordId:typeof x.recordId==='string'?x.recordId:'',receivedAt:typeof x.receivedAt==='string'?x.receivedAt:''};});
 if(inbox.length>1000||new Set(inbox.map(x=>x.id)).size!==inbox.length)throw Error('صندوق ورودی نامعتبر');
 return {version:2,tasks:data.tasks.map(x=>clean(x,false)),debts:data.debts.map(x=>clean(x,true)),inbox};
}
function records(db){return [...db.tasks.map(x=>({...x,kind:'tasks'})),...db.debts.map(x=>({...x,kind:x.type}))];}
function filter(db,{kind='all',status='all',from='',to='',query=''}={}){return records(db).filter(x=>(kind==='all'||x.kind===kind)&&(status==='all'||(status==='done'?x.done:status==='open'?!x.done:!x.done&&x.date&&x.date<today()))&&(!from||x.date&&x.date>=from)&&(!to||x.date&&x.date<=to)&&(!query||norm(x.title).includes(norm(query))));}
function summary(rows){return {total:rows.length,done:rows.filter(x=>x.done).length,open:rows.filter(x=>!x.done).length,receive:rows.filter(x=>x.kind==='receive'&&!x.done).reduce((a,x)=>a+BigInt(x.amount),0n).toString(),pay:rows.filter(x=>x.kind==='pay'&&!x.done).reduce((a,x)=>a+BigInt(x.amount),0n).toString()};}
function money(value){const s=norm(value);return /^\d+$/.test(s)?new Intl.NumberFormat('fa-IR').format(BigInt(s)):'';}
function period(mode,base=today()){const [y,m]=base.split('/').map(Number);if(mode==='day')return {from:base,to:base};if(mode==='week'){const from=addDays(base,-((gregorian(base).getUTCDay()+1)%7));return {from,to:addDays(from,6)};}if(mode==='month')return {from:dateKey(y,m,1),to:dateKey(y,m,monthDays(y,m))};return {from:'',to:''};}
function financial(rows){const sum=(kind,done)=>rows.filter(x=>x.kind===kind&&x.done===done).reduce((a,x)=>a+BigInt(x.amount),0n).toString();return {receiveDone:sum('receive',true),receiveOpen:sum('receive',false),payDone:sum('pay',true),payOpen:sum('pay',false)};}
function reminder(date,time){if(!validDate(date)||!/^\d{2}:\d{2}$/.test(time))return 0;const [h,m]=time.split(':').map(Number);if(h>23||m>59)return 0;const g=gregorian(date);return new Date(g.getUTCFullYear(),g.getUTCMonth(),g.getUTCDate(),h,m).getTime();}
function csv(rows){const cell=v=>'"'+String(v??'').replace(/^[=+@-]/,"'$&").replace(/"/g,'""')+'"';return '\uFEFF'+[['نوع','عنوان','سررسید شمسی','مبلغ ریال','وضعیت','زمان تکمیل'],...rows.map(x=>[x.kind==='tasks'?'کار':x.kind==='pay'?'پرداختنی':'دریافتنی',x.title,x.date,x.amount??'',x.done?'انجام‌شده':'انجام‌نشده',x.completedAt||''])].map(r=>r.map(cell).join(',')).join('\r\n');}
const api={money,period,financial,reminder,months,norm,parts,today,dateKey,monthDays,validDate,dateNorm,gregorian,addDays,numberWords,draft,validate,records,filter,summary,csv};
if(typeof module!=='undefined')module.exports=api;else root.HamyarCore=api;
})(typeof window!=='undefined'?window:globalThis);
