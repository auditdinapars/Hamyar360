'use strict';
const {app,BrowserWindow,protocol,net,ipcMain,Notification,dialog}=require('electron');
const path=require('node:path'),fs=require('node:fs');
const {pathToFileURL}=require('node:url');
const {setupFiles}=require('./files.cjs');
const {createAuth}=require('./auth.cjs');
const {normalizeReminders,createIcs}=require('./desktop-core.cjs');

protocol.registerSchemesAsPrivileged([{scheme:'hasel',privileges:{standard:true,secure:true,supportFetchAPI:true,stream:true}}]);
const ROOT=path.join(__dirname,'web');
let win,auth,reminders=[],alreadyFired=new Set();
app.setName('Hasel 360 Desktop Beta');
app.setPath('userData',path.join(app.getPath('appData'),'Hasel360-Desktop-Beta'));
if(!app.requestSingleInstanceLock())app.quit();

function notifyClient(obj){if(win&&!win.isDestroyed())win.webContents.send('hasel:native',obj);}
function reportError(e){const text=String(e&&e.message||'خطای نامشخص').slice(0,300);notifyClient({type:'error',message:text});}
function check(event){
 if(!win||win.isDestroyed()||event.sender!==win.webContents||!event.senderFrame||!event.senderFrame.url.startsWith('hasel://app/'))throw Error('درخواست نامعتبر');
}
function tick(){
 if(!win||win.isDestroyed()||!Notification.isSupported())return;
 const now=Date.now();
 for(const item of reminders){
  const fingerprint=item.id+'|'+item.time;
  if(item.time<=now&&now-item.time<60000&&!alreadyFired.has(fingerprint)){
   alreadyFired.add(fingerprint);
   const title='یادآور حاصل ۳۶۰';
   try{new Notification({title,body:item.title.slice(0,180),silent:false}).show()}catch{}
  }
 }
 if(alreadyFired.size>20000)alreadyFired=new Set();
}
function startWindow(){
 win=new BrowserWindow({title:'حاصل ۳۶۰ — نسخه دسکتاپ آزمایشی',width:1440,height:900,minWidth:860,minHeight:610,show:false,backgroundColor:'#fff8f1',autoHideMenuBar:false,
  webPreferences:{preload:path.join(__dirname,'preload.cjs'),nodeIntegration:false,contextIsolation:true,sandbox:true,webSecurity:true,allowRunningInsecureContent:false,webviewTag:false}});
 win.once('ready-to-show',()=>win.show());
 win.webContents.setWindowOpenHandler(()=>({action:'deny'}));
 win.webContents.on('will-navigate',(event,url)=>{if(!url.startsWith('hasel://app/'))event.preventDefault()});
 win.webContents.on('will-attach-webview',e=>e.preventDefault());
 win.webContents.on('context-menu',(event,params)=>{
  if(params.isEditable){const {Menu}=require('electron');Menu.buildFromTemplate([{role:'undo'},{role:'redo'},{type:'separator'},{role:'cut'},{role:'copy'},{role:'paste'},{role:'selectAll'}]).popup({window:win})}
 });
 win.loadURL('hasel://app/index.html');
}
app.whenReady().then(async()=>{
 auth=createAuth(app.getPath('userData'));
 protocol.handle('hasel',request=>{
  try{
   const uri=new URL(request.url);
   if(uri.hostname!=='app')return new Response('Forbidden',{status:403});
   const segment=decodeURIComponent(uri.pathname).replace(/^\/+/,''),name=segment||'index.html';
   if(name.split('/').some(p=>p==='..')||name.includes('\\')||name.includes('\0'))return new Response('Forbidden',{status:403});
   const absolute=path.resolve(ROOT,name);
   if(!absolute.startsWith(ROOT+path.sep))return new Response('Forbidden',{status:403});
   if(!fs.existsSync(absolute)||!fs.statSync(absolute).isFile())return new Response('Not found',{status:404});
   return net.fetch(pathToFileURL(absolute).toString());
  }catch{return new Response('Bad request',{status:400})}
 });
 app.on('second-instance',()=>{if(win){win.show();win.focus()}});
 startWindow();
 const files=setupFiles(win,notifyClient);
 const actions={'hasel:save-text':'save-text','hasel:open-entity':'open-entity','hasel:save-entity':'save-entity','hasel:open-cash':'open-cash','hasel:cash-template':'cash-template','hasel:open-bank':'open-bank','hasel:open-photo':'open-photo'};
 for(const [key,op] of Object.entries(actions))ipcMain.handle(key,async(event,...args)=>{check(event);try{return await files.action(op,...args)}catch(e){reportError(e);return false}});
 ipcMain.on('hasel:ready',event=>{check(event)});
 ipcMain.on('hasel:reminders',(event,json)=>{check(event);try{reminders=normalizeReminders(json);tick()}catch(e){reportError(e)}});
 ipcMain.on('hasel:unsupported',(event,message)=>{check(event);notifyClient({type:'error',message:String(message).slice(0,300)})});
 ipcMain.on('hasel:auth',(event,method,...args)=>{try{check(event);event.returnValue=auth.act(method,...args)}catch(e){event.returnValue={ok:false,message:'خطا در کنترل دسترسی'}}});
 ipcMain.handle('hasel:calendar-ics',async event=>{
  check(event);
  try{const dest=await dialog.showSaveDialog(win,{title:'ذخیره یادآورها برای تقویم',defaultPath:'Hasel360-reminders.ics',filters:[{name:'Calendar iCalendar',extensions:['ics']}]});
   if(dest.canceled||!dest.filePath)return false;
   await require('node:fs/promises').writeFile(dest.filePath,createIcs(reminders),'utf8');
   notifyClient({type:'saved'});return true;
  }catch(e){reportError(e);return false}
 });
 setInterval(tick,30000).unref();
}).catch(err=>{dialog.showErrorBox('راه‌اندازی حاصل ۳۶۰ ناموفق بود',String(err.message||err));app.quit()});
app.on('window-all-closed',()=>{app.quit()});
