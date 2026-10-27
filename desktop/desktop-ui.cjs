/* Browser-only desktop enhancements; no Node.js access. */
(function(){
'use strict';
function init(){
 document.body.classList.add('hasel-desktop');
 const badge=document.querySelector('header .badge');if(badge)badge.textContent='دسکتاپ ۰.۹.۰ آزمایشی • مبنا ۰.۸.۲';
 const capture=document.querySelector('.capture');
 if(capture){const text=document.createElement('p');text.className='desktop-only-note';text.textContent='نسخه دسکتاپ: فایل‌های Excel/PDF از رایانه انتخاب می‌شوند. فرمان صوتی و OCR تصویر هنوز فعال نیستند. داده‌ها محلی و جدا از موبایل ذخیره می‌شوند.';capture.append(text)}
 const calendar=document.getElementById('calendarSyncChoose');
 if(calendar){calendar.disabled=true;calendar.title='همگام‌سازی خودکار مختص اندروید است';const p=document.createElement('button');p.className='secondary';p.type='button';p.textContent='ذخیره یادآورها در فایل تقویم (ICS)';p.addEventListener('click',()=>window.HamyarAndroid?.exportCalendarIcs?.());calendar.parentElement.append(p)}
 const camera=document.getElementById('camera');if(camera)camera.textContent='📎 انتخاب عکس';
 const editorCamera=document.getElementById('editorCamera');if(editorCamera)editorCamera.textContent='📎 افزودن تصویر';
 window.addEventListener('keydown',e=>{if(e.ctrlKey&&e.key.toLowerCase()==='s'){e.preventDefault();document.getElementById('exportBackup')?.click()}});
 window.addEventListener('hasel-native-result',e=>{try{window.onNativeResult?.(JSON.parse(e.detail))}catch(err){console.error('Native event rejected:',err)}});
}
if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',init);else init();
})();
