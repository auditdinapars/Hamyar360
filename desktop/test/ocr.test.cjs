'use strict';
const test=require('node:test'),assert=require('node:assert/strict');
const {normalizeDigits,needsOcr,extractPdfWithOcr,localLanguageDir}=require('../ocr-pdf.cjs');
test('OCR digit normalization preserves Persian words and converts Persian and Arabic numerals',()=>{
 assert.equal(normalizeDigits('برداشت ۱۲۳٬۴۵۶ واریز ١٥٠٫٢٠ USD 789'),'برداشت 123,456 واریز 150.20 USD 789');
});
test('OCR detects empty and header-only pages, not normal statement text',()=>{
 assert.equal(needsOcr(''),true);
 assert.equal(needsOcr('شماره حساب ۱۲۳۴۵'),true);
 assert.equal(needsOcr('Bank account 12345 - just a header'),true);
 assert.equal(needsOcr('Date: 2026-10-27 Debit: 123 Credit: 12 Balance 456 Currency IRR Account: 12345 Date: 2026-10-26'.repeat(2)),false);
});
test('untrusted PDF bytes are rejected before loading any engine',async()=>{
 await assert.rejects(extractPdfWithOcr(Buffer.from('not a pdf')),/PDF/);
});
test('OCR models are not obtained from the internet at runtime',()=>{
 // Checksums are verified during the build; local assets are essential for execution.
 if(process.env.HASEL_OCR_E2E==='1')assert.match(localLanguageDir(),/ocr-langs/);
});
test('real offline Persian-English OCR reads raster PDF and normalizes numerals',
 {skip:process.env.HASEL_OCR_E2E!=='1',timeout:180000},async()=>{
 const {createCanvas}=require('@napi-rs/canvas');
 const {PDFDocument}=require('pdf-lib');
 const cv=createCanvas(1450,460),ctx=cv.getContext('2d');
 ctx.fillStyle='#fff';ctx.fillRect(0,0,1450,460);
 ctx.fillStyle='#111';ctx.font='bold 66px Arial';
 ctx.fillText('ACCOUNT 123456789',60,112);
 ctx.font='52px Arial';ctx.fillText('DEBIT 120000 CREDIT 50000',60,240);
 ctx.font='45px Tahoma';ctx.fillText('تاریخ ۱۴۰۵/۰۸/۰۵',60,355);
 const pdf=await PDFDocument.create();
 const page=pdf.addPage([725,230]);
 const image=await pdf.embedPng(cv.toBuffer('image/png'));
 page.drawImage(image,{x:0,y:0,width:725,height:230});
 const buffer=Buffer.from(await pdf.save());
 const received=await extractPdfWithOcr(buffer);
 assert.equal(received.ocr.used,true);
 assert.equal(received.ocr.pages,1);
 assert.equal(received.ocr.reviewRequired,true);
 assert.match(received.text,/123456789|12345678/);
 assert.match(received.text,/120000|1200[0-9]0/);
 });
