'use strict';
const test=require('node:test'),assert=require('node:assert/strict');
const {normalizeReminders,createIcs,sanitizeName,ALLOWED_KINDS}=require('../desktop-core.cjs');
const {createAuth}=require('../auth.cjs');
const fs=require('node:fs'),os=require('node:os'),path=require('node:path');
test('desktop accepts supported import kinds only',()=>{assert.equal(ALLOWED_KINDS.has('accounts'),true);assert.equal(ALLOWED_KINDS.has('../secrets'),false)});
test('path names are reduced to a base file',()=>{const s=sanitizeName('../../a/bad:name.csv');assert(!s.includes('/'));assert(!s.includes(':'));});
test('reminders reject malformed items',()=>{assert.throws(()=>normalizeReminders('[{"id":"x","title":"X","time":"hello"}]'));assert.equal(normalizeReminders([{id:'1',title:'دوست',time:1700000000000}]).length,1)});
test('ICS is stable and excludes obsolete reminders',()=>{
 const arr=[{id:'1',title:'پرداخت',time:1700000000000},{id:'2',title:'وصول',time:1800000000000}];
 const ics=createIcs(arr,1750000000000);assert.equal((ics.match(/BEGIN:VEVENT/g)||[]).length,1);assert.match(ics,/SUMMARY:یادآور حاصل ۳۶۰/);assert(!ics.includes('پرداخت'));assert.match(ics,/UID:/)
});
test('local login requires password and supports recovery without sharing it',()=>{
 const d=fs.mkdtempSync(path.join(os.tmpdir(),'hasel-auth-'));try{
 const auth=createAuth(d);assert.equal(auth.status().enabled,false);
 assert.equal(auth.act('setup','short','short').ok,false);
 const created=auth.act('setup','A strong pass 2026','A strong pass 2026');
 assert.equal(created.ok,true);assert.equal(auth.act('unlock','wrong').ok,false);
 assert.equal(auth.act('unlock','A strong pass 2026').ok,true);
 assert.equal(auth.act('recover',created.recovery,'New strong pass').ok,true);
 assert.equal(auth.act('unlock','A strong pass 2026').ok,false);
 assert.equal(auth.act('unlock','New strong pass').ok,true);
 const state=fs.readFileSync(path.join(d,'local-access.json'),'utf8');assert(!state.includes('New strong pass'));
 }finally{fs.rmSync(d,{recursive:true,force:true})}
});
