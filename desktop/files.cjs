'use strict';
const fs=require('node:fs/promises');
const path=require('node:path');
const {dialog,nativeImage}=require('electron');
const {ALLOWED_KINDS,sanitizeName}=require('./desktop-core.cjs');
const ExcelJS=require('exceljs');
const MAX_XLSX=40*1024*1024,MAX_PDF=40*1024*1024,MAX_ROWS=20000,MAX_COLS=32;
const CASH_HEADERS=['date_shamsi','account_code','category_code','unit_code','amount_rial','description','document_ref'];
async function choose(win,filters){
 const r=await dialog.showOpenDialog(win,{title:'انتخاب فایل حاصل ۳۶۰',properties:['openFile'],filters});
 return r.canceled||!r.filePaths.length?null:r.filePaths[0];
}
async function guardFile(file,max,exts){
 if(!exts.includes(path.extname(file).toLowerCase()))throw Error('پسوند فایل مورد تأیید نیست');
 const stat=await fs.stat(file);if(!stat.isFile()||stat.size<=0||stat.size>max)throw Error('فایل خالی یا بیش از ظرفیت ایمن است');
 return fs.readFile(file);
}
function cellText(cell){
 if(cell.type===ExcelJS.ValueType.Formula||cell.value&&typeof cell.value==='object'&&('formula' in cell.value||'sharedFormula' in cell.value))throw Error('فرمول Excel مجاز نیست؛ ابتدا Paste Values کنید');
 const v=cell.value;
 if(v===null||v===undefined)return '';
 if(v instanceof Date)return [v.getUTCFullYear(),String(v.getUTCMonth()+1).padStart(2,'0'),String(v.getUTCDate()).padStart(2,'0')].join('/');
 if(typeof v==='object'){if(Array.isArray(v.richText))return v.richText.map(x=>x.text).join('');if(v.text!==undefined)return String(v.text);return '';}
 return String(v);
}
async function loadXlsx(file,{bank=false}={}){
 const data=await guardFile(file,MAX_XLSX,['.xlsx']);
 const book=new ExcelJS.Workbook();await book.xlsx.load(data);
 let chosen=null;
 for(const sheet of book.worksheets){
  if(sheet.rowCount>MAX_ROWS+1||sheet.columnCount>MAX_COLS)throw Error('فایل بزرگ است؛ آن را برحسب دوره تقسیم کنید (حداکثر ۲۰هزار ردیف و ۳۲ ستون در هر برگه)');
  let rows=[];for(let n=1;n<=sheet.rowCount;n++){const row=sheet.getRow(n);const cells=[];for(let c=1;c<=Math.min(MAX_COLS,Math.max(sheet.columnCount,row.cellCount));c++){let val=cellText(row.getCell(c));if(val.length>1000)throw Error('متن سلول بیش از حد مجاز است');cells.push(val)}rows.push(cells)}
  if(!rows.some(x=>x.some(v=>v.trim())))continue;
  const score=rows.slice(0,30).reduce((n,r)=>n+(r.some(c=>/تاریخ|date/i.test(c))?2:0)+(r.some(c=>/برداشت|بدهکار|debit/i.test(c))?2:0)+(r.some(c=>/واریز|بستانکار|credit/i.test(c))?2:0),0);
  if(!chosen||bank&&score>chosen.score)chosen={rows,score};
  if(!bank)break;
 }
 if(!chosen)throw Error('برگه اطلاعاتی قابل خواندن نیست');
 return chosen.rows;
}
async function writeXlsx(win,rows,title,filename){
 if(!Array.isArray(rows)||rows.length===0||rows.length>MAX_ROWS+1)throw Error('تعداد ردیف‌های Excel معتبر نیست');
 const workbook=new ExcelJS.Workbook();workbook.creator='Hasel 360 Desktop';const sheet=workbook.addWorksheet('Hasel360',{views:[{rightToLeft:true}]});
 for(const row of rows){if(!Array.isArray(row)||row.length>MAX_COLS)throw Error('تعداد ستون‌ها معتبر نیست');const values=row.map(v=>{const s=String(v??'');if(s.length>1200)throw Error('متن طولانی');return s});sheet.addRow(values);}
 sheet.getRow(1).font={bold:true};sheet.columns.forEach(col=>{col.width=24;col.numFmt='@'});
 const dest=await dialog.showSaveDialog(win,{title,defaultPath:sanitizeName(filename),filters:[{name:'Excel',extensions:['xlsx']}]});
 if(dest.canceled||!dest.filePath)return false;
 await workbook.xlsx.writeFile(dest.filePath);
 return true;
}
async function pdfText(file){
 const data=await guardFile(file,MAX_PDF,['.pdf']);
 const pdfParse=require('pdf-parse');
 const pdf=await pdfParse(data,{max:200});
 if(pdf.numpages>200||String(pdf.text||'').length>6000000)throw Error('PDF بزرگ‌تر از ظرفیت ایمن است');
 if(!String(pdf.text||'').trim())throw Error('این PDF اسکن تصویری است؛ استخراج متنی مطمئن ممکن نیست');
 return String(pdf.text);
}
function setupFiles(win,send){
 async function action(method,...args){
  if(method==='save-text'){
   const [text,mime,name]=args;
   if(typeof text!=='string'||Buffer.byteLength(text,'utf8')>60*1024*1024||!['text/csv','application/json','text/plain'].includes(mime))throw Error('فرمت یا حجم خروجی معتبر نیست');
   const ext=mime==='text/csv'?'csv':mime==='application/json'?'json':'txt';
   const dest=await dialog.showSaveDialog(win,{title:'ذخیره گزارش یا پشتیبان',defaultPath:sanitizeName(name||'hasel360.'+ext),filters:[{name:ext.toUpperCase(),extensions:[ext]}]});
   if(!dest.canceled&&dest.filePath){await fs.writeFile(dest.filePath,text,'utf8');send({type:'saved'})}
  }
  else if(method==='open-entity'){
   const kind=args[0];if(!ALLOWED_KINDS.has(kind))throw Error('نوع اطلاعات نامعتبر است');
   const file=await choose(win,[{name:'Excel',extensions:['xlsx']}]);if(file)send({type:'entityExcel',kind,rows:await loadXlsx(file)});
  }
  else if(method==='open-cash'){
   const file=await choose(win,[{name:'Excel',extensions:['xlsx']}]);if(file)send({type:'cashExcel',rows:await loadXlsx(file),name:path.basename(file)});
  }
  else if(method==='open-bank'){
   const format=args[0];if(format!=='pdf'&&format!=='xlsx')throw Error('فرمت صورت‌حساب نامعتبر است');
   const file=await choose(win,[{name:format.toUpperCase(),extensions:[format]}]);if(file)send(format==='pdf'?{type:'bankStatement',format,text:await pdfText(file),name:path.basename(file)}:{type:'bankStatement',format,rows:await loadXlsx(file,{bank:true}),name:path.basename(file)});
  }
  else if(method==='save-entity'){
   const [kind,json,mode]=args;if(!ALLOWED_KINDS.has(kind)||typeof json!=='string'||json.length>15000000)throw Error('اطلاعات خروجی نامعتبر است');
   const rows=JSON.parse(json);if(await writeXlsx(win,rows,'خروجی Excel حاصل ۳۶۰','Hasel360-'+kind+'-'+sanitizeName(mode)+'.xlsx'))send({type:'saved'});
  }
  else if(method==='cash-template'){
   if(await writeXlsx(win,[CASH_HEADERS], 'قالب Excel گردش نقدی','Hasel360-cash-template.xlsx'))send({type:'saved'});
  }
  else if(method==='open-photo'){
   const file=await choose(win,[{name:'تصویر',extensions:['jpg','jpeg','png','webp']}]);if(!file)return;
   const raw=await guardFile(file,25*1024*1024,['.jpg','.jpeg','.png','.webp']);
   let picture=nativeImage.createFromBuffer(raw);if(picture.isEmpty())throw Error('عکس معتبر نیست');
   const size=picture.getSize(),factor=Math.min(1,1600/Math.max(size.width,size.height));
   if(factor<1)picture=picture.resize({width:Math.max(1,Math.floor(size.width*factor)),height:Math.max(1,Math.floor(size.height*factor))});
   const encoded=picture.toJPEG(75);if(encoded.length>1400000)throw Error('حجم عکس حتی پس از کاهش اندازه زیاد است');
   send({type:'photo',data:'data:image/jpeg;base64,'+encoded.toString('base64')});
  }
  else throw Error('عملیات فایل پشتیبانی نمی‌شود');
 }
 return {action};
}
module.exports={setupFiles,loadXlsx,pdfText,cellText,MAX_ROWS,MAX_COLS};
