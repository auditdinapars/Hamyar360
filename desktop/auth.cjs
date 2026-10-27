'use strict';
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto');
function createAuth(userDir){
 const target=path.join(userDir,'local-access.json');
 let state={};
 try{state=JSON.parse(fs.readFileSync(target,'utf8'))}catch{}
 const save=()=>{const tmp=target+'.tmp';fs.mkdirSync(userDir,{recursive:true});fs.writeFileSync(tmp,JSON.stringify(state),{mode:0o600});fs.renameSync(tmp,target)};
 const hash=(s,salt)=>crypto.scryptSync(String(s),Buffer.from(salt,'hex'),32,{N:16384,r:8,p:1}).toString('hex');
 const verify=(secret,salt,digest)=>{if(!digest||!salt)return false;const a=Buffer.from(hash(secret,salt),'hex'),b=Buffer.from(digest,'hex');return a.length===b.length&&crypto.timingSafeEqual(a,b)};
 let failures=0,blockedUntil=0;
 function status(){return {enabled:!!state.passHash,delaySec:Math.max(0,Math.ceil((blockedUntil-Date.now())/1000))}}
 function valid(p){return typeof p==='string'&&p.length>=8&&p.length<=128}
 function set(p){state.passSalt=crypto.randomBytes(16).toString('hex');state.passHash=hash(p,state.passSalt);const recovery=crypto.randomBytes(18).toString('hex').toUpperCase();state.recoverySalt=crypto.randomBytes(16).toString('hex');state.recoveryHash=hash(recovery,state.recoverySalt);save();failures=0;blockedUntil=0;return {ok:true,recovery}}
 function guard(){if(blockedUntil>Date.now())return {ok:false,message:'تلاش بیش از حد؛ '+Math.ceil((blockedUntil-Date.now())/1000)+' ثانیه دیگر اقدام کنید.'};return null}
 function bad(){failures++;if(failures>=5){blockedUntil=Date.now()+30000;failures=0}return {ok:false,message:'رمز یا کد بازیابی نامعتبر است.'}}
 function act(method,...args){
  if(method==='status')return status();const locked=guard();if(locked)return locked;
  if(method==='setup'){if(state.passHash)return {ok:false,message:'رمز قبلاً تعریف شده است'};if(args[0]!==args[1]||!valid(args[0]))return {ok:false,message:'رمز حداقل ۸ کاراکتر و حداکثر ۱۲۸ کاراکتر باشد'};return set(args[0])}
  if(method==='unlock'){if(!state.passHash)return {ok:true};if(!verify(args[0],state.passSalt,state.passHash))return bad();failures=0;return {ok:true}}
  if(method==='change'){if(!state.passHash||!verify(args[0],state.passSalt,state.passHash))return bad();if(args[1]!==args[2]||!valid(args[1]))return {ok:false,message:'رمز جدید نامعتبر یا تکرار آن متفاوت است'};return set(args[1])}
  if(method==='recover'){if(!state.recoveryHash||!verify(String(args[0]||'').trim().toUpperCase(),state.recoverySalt,state.recoveryHash))return bad();if(!valid(args[1]))return {ok:false,message:'رمز جدید کوتاه است'};return set(args[1])}
  return {ok:false,message:'عملیات پشتیبانی نمی‌شود'}
 }
 return {act,status};
}
module.exports={createAuth};
