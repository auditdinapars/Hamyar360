'use strict';
const {contextBridge,ipcRenderer}=require('electron');
const bridge=Object.freeze({
 ready:()=>ipcRenderer.send('hasel:ready'),
 syncReminders:json=>ipcRenderer.send('hasel:reminders',json),
 requestNotifications:()=>{},
 saveDocument:(text,mime,name)=>ipcRenderer.invoke('hasel:save-text',text,mime,name),
 pickEntityExcel:kind=>ipcRenderer.invoke('hasel:open-entity',kind),
 saveEntityExcel:(kind,rows,mode)=>ipcRenderer.invoke('hasel:save-entity',kind,rows,mode),
 pickCashExcel:()=>ipcRenderer.invoke('hasel:open-cash'),
 saveCashTemplate:()=>ipcRenderer.invoke('hasel:cash-template'),
 pickBankStatement:format=>ipcRenderer.invoke('hasel:open-bank',format),
 takePhoto:()=>ipcRenderer.invoke('hasel:open-photo'),
 startVoice:()=>ipcRenderer.send('hasel:unsupported','فرمان صوتی به سرویس گفتار دسکتاپ متصل نشده است؛ فعلاً از ورود متن استفاده کنید.'),
 readPhoto:()=>ipcRenderer.send('hasel:unsupported','OCR تصویر در نسخه اولیه دسکتاپ فعال نیست؛ متن تصویر را بررسی و دستی وارد کنید.'),
 requestCalendarSync:()=>ipcRenderer.send('hasel:unsupported','اتصال خودکار تقویم گوشی در دسکتاپ فعال نیست؛ از خروجی ICS استفاده کنید.'),
 selectCalendarSync:()=>ipcRenderer.send('hasel:unsupported','همگام‌سازی تقویم با حساب کاربری هنوز پشتیبانی نمی‌شود.'),
 exportCalendarIcs:()=>ipcRenderer.invoke('hasel:calendar-ics'),
 authStatus:()=>JSON.stringify(ipcRenderer.sendSync('hasel:auth','status')),
 authSetup:(password,confirmation)=>JSON.stringify(ipcRenderer.sendSync('hasel:auth','setup',password,confirmation)),
 authChange:(oldPassword,password,confirmation)=>JSON.stringify(ipcRenderer.sendSync('hasel:auth','change',oldPassword,password,confirmation)),
 authUnlock:password=>JSON.stringify(ipcRenderer.sendSync('hasel:auth','unlock',password)),
 authRecover:(recovery,password)=>JSON.stringify(ipcRenderer.sendSync('hasel:auth','recover',recovery,password)),
 ackInbox:()=>{}
});
contextBridge.exposeInMainWorld('HamyarAndroid',bridge);
ipcRenderer.on('hasel:native',(event,payload)=>{
 window.dispatchEvent(new CustomEvent('hasel-native-result',{detail:JSON.stringify(payload)}));
});
