'use strict';
/*
 * Offline Persian + English OCR for image PDFs.
 * Native images are rendered page-by-page, never uploaded to a remote service.
 * OCR text remains UNTRUSTED until bank-reconcile checks every running balance.
 */
const path=require('node:path');
const fs=require('node:fs');
const MAX_PAGES=200,MAX_OCR_PAGES=120,MAX_PIXELS=8_000_000;
const MIN_TEXT_CHARS=100;
function normalizeDigits(s){
 return String(s||'').replace(/[۰-۹]/g,ch=>String(ch.charCodeAt(0)-0x06f0))
                     .replace(/[٠-٩]/g,ch=>String(ch.charCodeAt(0)-0x0660))
                     .replace(/\u066c/g,',').replace(/\u066b/g,'.');
}
function needsOcr(s){
 const str=String(s||'').trim();
 // Header-only pages containing account metadata may still have scanned transaction rows.
 const numerals=(str.match(/[0-9۰-۹٠-٩]/g)||[]).length;
 return str.length<MIN_TEXT_CHARS||numerals<6;
}
function localLanguageDir(){
 const p=path.join(__dirname,'ocr-langs');
 for(const name of ['eng','fas']){
  const file=path.join(p,name+'.traineddata');
  if(!fs.existsSync(file)||fs.statSync(file).size<100_000)throw Error('مدل OCR آفلاین '+name+' در بسته نصب وجود ندارد');
 }
 return p;
}
async function makeWorker(){
 const {createWorker}=require('tesseract.js');
 const opts={langPath:localLanguageDir(),gzip:false,cacheMethod:'none'};
 // In Node, tesseract.js itself locates its worker and WASM core in node_modules.
 return createWorker(['fas','eng'],1,opts);
}
function makeCanvas(){
 const {createCanvas}=require('@napi-rs/canvas');
 return createCanvas;
}
function makePdfjs(){
 return require('pdfjs-dist/legacy/build/pdf.js');
}
async function renderPng(page){
 const viewport=page.getViewport({scale:2.1});
 let width=Math.max(1,Math.ceil(viewport.width)),height=Math.max(1,Math.ceil(viewport.height));
 const scale=Math.min(1,Math.sqrt(MAX_PIXELS/(width*height)));
 const view=scale<1?page.getViewport({scale:2.1*scale}):viewport;
 width=Math.ceil(view.width);height=Math.ceil(view.height);
 if(width*height>MAX_PIXELS+5000)throw Error('صفحه PDF بیش از ظرفیت مجاز OCR است');
 const canvas=makeCanvas()(width,height);
 const ctx=canvas.getContext('2d');
 ctx.fillStyle='#ffffff';ctx.fillRect(0,0,width,height);
 await page.render({canvasContext:ctx,viewport:view,background:'rgb(255,255,255)'}).promise;
 const png=canvas.toBuffer('image/png');
 if(png.length>20*1024*1024)throw Error('تصویر صفحه PDF بیش از ظرفیت ایمن است');
 return png;
}
async function extractPdfWithOcr(pdfBytes,{onProgress=()=>{},createWorker=makeWorker}={}){
 const buffer=Buffer.isBuffer(pdfBytes)?pdfBytes:Buffer.from(pdfBytes);
 if(buffer.length<10||buffer.length>40*1024*1024||buffer.subarray(0,5).toString('ascii')!=='%PDF-')throw Error('فایل PDF نامعتبر یا بیش از ۴۰ مگابایت است');
 const pdfjs=makePdfjs();
 const task=pdfjs.getDocument({data:new Uint8Array(buffer),useSystemFonts:true,disableFontFace:true,isEvalSupported:false});
 let document,worker=null;
 const pages=[],details=[];
 try{
  document=await task.promise;
  if(document.numPages<1||document.numPages>MAX_PAGES)throw Error('تعداد صفحات PDF خارج از ظرفیت ایمن است');
  for(let p=1;p<=document.numPages;p++){
   const page=await document.getPage(p);
   const content=await page.getTextContent();
   const textual=(content.items||[]).map(x=>x.str+(x.hasEOL?'\n':' ')).join('').trim();
   let text=textual,confidence=null,usedOcr=false;
   if(needsOcr(textual)){
    if(details.filter(x=>x.usedOcr).length>=MAX_OCR_PAGES)throw Error('تعداد صفحات اسکن بیش از ظرفیت OCR است؛ فایل را به چند بخش تقسیم کنید');
    if(!worker)worker=await createWorker();
    onProgress({page:p,total:document.numPages,state:'recognizing'});
    const image=await renderPng(page);
    const outcome=await worker.recognize(image);
    text=String(outcome?.data?.text||'').trim();
    confidence=Number(outcome?.data?.confidence)||0;
    usedOcr=true;
    if(text.length<8||confidence<20)throw Error('OCR صفحه '+p+' به متن قابل اتکا نرسید؛ کیفیت اسکن را بررسی کنید');
   }
   pages.push(normalizeDigits(text));
   details.push({page:p,usedOcr,confidence,characters:text.length});
   if(typeof page.cleanup==='function')page.cleanup();
   onProgress({page:p,total:document.numPages,state:'complete'});
  }
  const ocrPages=details.filter(x=>x.usedOcr);
  return {
   text:pages.join('\n\n'),
   ocr:{
    used:ocrPages.length>0,
    pages:ocrPages.length,
    pageCount:document.numPages,
    lowConfidencePages:ocrPages.filter(x=>x.confidence<70).map(x=>x.page),
    averageConfidence:ocrPages.length?Math.round(ocrPages.reduce((v,x)=>v+x.confidence,0)/ocrPages.length):null,
    reviewRequired:ocrPages.length>0,
    offline:true
   }
  };
 }finally{
  try{if(worker)await worker.terminate()}catch{}
  try{if(document)await document.destroy()}catch{}
  try{await task.destroy()}catch{}
 }
}
module.exports={extractPdfWithOcr,normalizeDigits,needsOcr,localLanguageDir,MAX_PAGES,MAX_OCR_PAGES};
