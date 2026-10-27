/* Browser-only desktop enhancements; no Node.js access. */
(function(){
'use strict';
function init(){
 document.body.classList.add('hasel-desktop');
 const badge=document.querySelector('header .badge');if(badge)badge.textContent='دسکتاپ ۰.۹.۱ آزمایشی • OCR آفلاین فارسی و انگلیسی';
 const capture=document.querySelector('.capture');
 if(capture){const text=document.createElement('p');text.className='desktop-only-note';text.textContent='نسخه دسکتاپ: فایل‌های Excel/PDF از رایانه انتخاب می‌شوند. فرمان صوتی و OCR عکس جداگانه هنوز فعال نیستند؛ OCR صفحات PDF بانکی فراهم شده است. داده‌ها محلی و جدا از موبایل ذخیره می‌شوند.';capture.append(text)}
 const calendar=document.getElementById('calendarSyncChoose');
 if(calendar){calendar.disabled=true;calendar.title='همگام‌سازی خودکار مختص اندروید است';const p=document.createElement('button');p.className='secondary';p.type='button';p.textContent='ذخیره یادآورها در فایل تقویم (ICS)';p.addEventListener('click',()=>window.HamyarAndroid?.exportCalendarIcs?.());calendar.parentElement.append(p)}
 const camera=document.getElementById('camera');if(camera)camera.textContent='📎 انتخاب عکس';
 const editorCamera=document.getElementById('editorCamera');if(editorCamera)editorCamera.textContent='📎 افزودن تصویر';
 window.addEventListener('keydown',e=>{if(e.ctrlKey&&e.key.toLowerCase()==='s'){e.preventDefault();document.getElementById('exportBackup')?.click()}});
 function reviewOcrBank(data){
   let popup=document.getElementById('desktopOcrReview');
   if(!popup){
    popup=document.createElement('dialog');popup.id='desktopOcrReview';popup.dir='rtl';popup.style.cssText='width:min(1050px,96vw);max-height:96vh;padding:20px;border:1px solid #dbc5ad;border-radius:12px;box-shadow:0 12px 45px #0004;';
    const h=document.createElement('h3');h.textContent='بازبینی ضروری متن تشخیص‌داده‌شده از PDF';popup.append(h);
    const desc=document.createElement('p');desc.id='desktopOcrSummary';popup.append(desc);
    const text=document.createElement('textarea');text.id='desktopOcrText';text.spellcheck=false;text.dir='auto';text.style.cssText='display:block;width:100%;box-sizing:border-box;height:55vh;white-space:pre;direction:rtl;font-family:monospace;line-height:1.7;margin:10px 0;padding:12px;';popup.append(text);
    const line=document.createElement('div');line.style.cssText='display:flex;justify-content:flex-end;gap:12px;flex-wrap:wrap;';
    const close=document.createElement('button');close.type='button';close.className='secondary';close.textContent='لغو ورود فایل';close.onclick=()=>popup.close();line.append(close);
    const ok=document.createElement('button');ok.id='desktopOcrApprove';ok.type='button';ok.textContent='متن را بررسی کردم؛ تحلیل صورتحساب';line.append(ok);popup.append(line);document.body.append(popup);
   }
   popup.querySelector('#desktopOcrSummary').textContent=
    'تعداد صفحات OCR: '+data.ocr.pages+' از '+data.ocr.pageCount+
    ' | میانگین اطمینان: '+data.ocr.averageConfidence+'٪'+
    (data.ocr.lowConfidencePages.length?' | صفحات کم‌اطمینان: '+data.ocr.lowConfidencePages.join('، '):'')+
    ' — متن و تمام مبالغ و تاریخ‌ها را با PDF اصلی تطبیق دهید. پس از تأیید متن نیز کنترل مانده و تأیید نهایی جداگانه لازم است.';
   popup.querySelector('#desktopOcrText').value=String(data.text||'').slice(0,6000000);
   popup.querySelector('#desktopOcrApprove').onclick=()=>{
    const corrected=popup.querySelector('#desktopOcrText').value;
    if(corrected.trim().length<10){window.alert('متن تشخیص داده‌شده کافی نیست؛ اسکن واضح‌تری انتخاب کنید.');return;}
    popup.close();
    try{window.onNativeResult?.({...data,text:corrected,ocr:{...data.ocr,userReviewed:true}})}catch(error){console.error('Bank OCR review rejected',error);}
   };
   popup.showModal();
 }
 window.addEventListener('hasel-native-result',e=>{
  try{
   const obj=JSON.parse(e.detail);
   if(obj.type==='ocrProgress'){
    const status=document.getElementById('bankImportStatus');
    if(status)status.textContent='در حال تشخیص متن PDF، صفحه '+obj.page+' از '+obj.total+' — پردازش آفلاین';
    return;
   }
   if(obj.type==='bankStatement'&&obj.ocr?.used){reviewOcrBank(obj);return;}
   window.onNativeResult?.(obj);
  }catch(err){console.error('Native event rejected:',err)}
 });
}
if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',init);else init();
})();
