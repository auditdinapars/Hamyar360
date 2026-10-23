const {test}=require('node:test');
const assert=require('node:assert/strict');
const vm=require('node:vm');
const fs=require('node:fs');
const C=require('../app/src/main/assets/cash-core.js');
function syncBus(){const context={console};context.window=context;vm.runInNewContext(fs.readFileSync(require('node:path').join(__dirname,'../app/src/main/assets/data-sync.js'),'utf8'),context);return context.HaselDataSync}
test('shared notification bus updates dashboards only after publish',()=>{
 const bus=syncBus(),received=[],stop=bus.subscribe(x=>received.push(x.scope));
 bus.publish('cash');bus.publish('legacy');bus.publish('combined');stop();bus.publish('cash');
 assert.deepEqual(received,['cash','legacy','combined']);assert.equal(bus.revision,4);
 assert.throws(()=>bus.publish('invalid'),/Unknown/);
});
test('failed subscriber cannot stop a second renderer from updating',()=>{
 const errors=[],ctx={console:{error:(...a)=>errors.push(a)}};ctx.window=ctx;
 vm.runInNewContext(fs.readFileSync(require('node:path').join(__dirname,'../app/src/main/assets/data-sync.js'),'utf8'),ctx);
 const seen=[];ctx.HaselDataSync.subscribe(()=>{throw Error('chart crashed')});ctx.HaselDataSync.subscribe(x=>seen.push(x.scope));
 ctx.HaselDataSync.publish('cash');assert.deepEqual(seen,['cash']);assert.equal(errors.length,1);
});
test('budget deletion removes matrix records and preserves independent periods and audit',()=>{
 let s=C.initial();
 s=C.addVersion(s,{label:'بودجه اول',year:'1405',startDate:'1405/08/01',endDate:'1405/08/30'});
 s=C.addVersion(s,{label:'بودجه دوم',year:'1405',startDate:'1405/09/01',endDate:'1405/09/30'});
 const a=s.budgetVersions[0].id,b=s.budgetVersions[1].id;
 s=C.setLine(s,{versionId:a,month:'1405/08',categoryId:'supplier',unitId:C.GENERAL,amount:'1000'});
 s=C.setLine(s,{versionId:b,month:'1405/09',categoryId:'supplier',unitId:C.GENERAL,amount:'2000'});
 const original=structuredClone(s);
 s=C.deleteMany(s,'budgetVersions',[a],'اصلاح داده آزمایشی');
 assert.equal(s.budgetVersions.length,1);assert.equal(s.budgetVersions[0].id,b);
 assert.equal(s.budgetLines.length,1);assert.equal(s.budgetLines[0].amount,'2000');
 assert.equal(s.audit.filter(e=>e.entity==='budgetVersion'&&e.action==='delete').length,1);
 assert.equal(original.budgetLines.length,2);
 assert.throws(()=>C.setLine(s,{versionId:a,month:'1405/08',categoryId:'supplier',unitId:C.GENERAL,amount:'5'}),/پیش‌نویس/);
});