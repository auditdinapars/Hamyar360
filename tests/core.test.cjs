const {test}=require('node:test');
const assert=require('node:assert/strict');
const C=require('../app/src/main/assets/core.js');
test('Persian dates validate leap years and cross month/year boundaries',()=>{
 assert.equal(C.validDate('۱۴۰۳/۱۲/۳۰'),true);
 assert.equal(C.validDate('۱۴۰۴/۱۲/۳۰'),false);
 assert.equal(C.validDate('۱۴۰۵/۰۷/۳۱'),false);
 assert.equal(C.addDays('1403/12/30',1),'1404/01/01');
 assert.equal(C.addDays('1405/06/31',1),'1405/07/01');
 assert.equal(C.gregorian('1405/01/01').toISOString().slice(0,10),'2026-03-21');
 assert.equal(C.dateNorm('١٤٠٥/٨/٢'),'1405/08/02');
});
test('speech drafts preserve target and parse Persian amounts and relative dates',()=>{
 const d=C.draft('فردا بیست میلیون تومان پرداخت کنم','pay','1405/08/01');
 assert.equal(d.amount,200000000);assert.equal(d.date,'1405/08/02');assert.equal(d.kind,'pay');
 assert.equal(C.draft('پانزده آبان ۱۴۰۵ باید ۲۰ میلیون تومان دریافت کنم','receive').date,'1405/08/15');
 assert.equal(C.draft('پس فردا تماس با مشتری','tasks','1403/12/29').date,'1404/01/01');
 assert.equal(C.draft('جلسه با همکار','tasks').amount,null);
 assert.equal(C.numberWords('دو میلیون و سیصد هزار'),2300000);
});
const db={tasks:[{id:'t',title:'تماس',date:'',done:false}],debts:[{id:'p',title:'اجاره',date:'1405/08/01',done:false,type:'pay',amount:200},{id:'r',title:'طلب',date:'1405/08/02',done:true,type:'receive',amount:300}]};
test('old backups migrate; pay and receive reports stay separate',()=>{
 const d=C.validate(db);assert.equal(d.version,2);assert.equal(d.tasks[0].photo,'');
 assert.equal(C.filter(d,{kind:'pay'}).length,1);assert.equal(C.filter(d,{status:'done'})[0].id,'r');
 assert.equal(C.filter(d,{from:'1405/08/01',to:'1405/08/01'}).length,1);
 assert.deepEqual(C.summary(C.records(d)),{total:3,done:1,open:2,pay:'200',receive:'0'});
});
test('invalid imports and duplicate identifiers are rejected before replacing data',()=>{
 assert.throws(()=>C.validate({tasks:[{}],debts:[]}));
 assert.throws(()=>C.validate({...db,tasks:[...db.tasks,...db.tasks]}));
 assert.throws(()=>C.validate({...db,debts:[{...db.debts[0],amount:-1}]}));
 assert.throws(()=>C.validate({...db,tasks:[{...db.tasks[0],photo:'javascript:alert(1)'}]}));
});
test('CSV quotes titles and neutralizes formula injection',()=>{
 const text=C.csv([{kind:'tasks',title:'=SUM(1,2)',date:'',done:false}]);
 assert.ok(text.includes('"\'=SUM(1,2)"'));assert.ok(text.startsWith('\uFEFF'));
});

test('financial input groups Persian/Arabic digits without precision loss',()=>{
 assert.equal(C.money('١٢٣٤٥٦٧٨٩'),'۱۲۳٬۴۵۶٬۷۸۹');assert.equal(C.norm(C.money('9007199254740991')),'9007199254740991');assert.equal(C.money(''), '');
});
test('financial totals split paid/open and periods honor Persian boundaries',()=>{
 assert.deepEqual(C.financial(C.records(C.validate(db))),{receiveDone:'300',receiveOpen:'0',payDone:'0',payOpen:'200'});
 assert.deepEqual(C.period('month','1403/12/15'),{from:'1403/12/01',to:'1403/12/30'});
 const week=C.period('week','1405/01/01');assert.equal(week.from,'1405/01/01');assert.equal(week.to,'1405/01/07');
});
test('inbox backups retain processed linkage; reminders validate and migrate',()=>{
 const d=C.validate({...db,inbox:[{id:'m',text:'فردا مبلغ ۲۰۰۰۰ ریال پرداخت کنم',photo:'',processed:true,recordId:'p'}]});assert.equal(d.inbox[0].recordId,'p');assert.equal(d.tasks[0].reminderAt,0);
 assert.equal(C.draft(d.inbox[0].text,'auto','1405/08/01').amount,20000);
 assert.ok(C.reminder('1405/08/01','09:30')>0);assert.equal(C.reminder('1405/08/01','29:00'),0);
 assert.throws(()=>C.validate({...db,inbox:[{id:'x',text:'x',processed:false,photo:'javascript:x'}]}));
});

test('OCR document output parses dates and amounts despite bidirectional markers',()=>{
 const d=C.draft('رسید پرداخت مبلغ\n‎Invoice 1405/08/15‏\n‎Amount 250000 RIAL‏','auto');
 assert.equal(d.date,'1405/08/15');assert.equal(d.amount,250000);assert.equal(d.kind,'pay');
});
