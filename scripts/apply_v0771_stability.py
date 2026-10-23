#!/usr/bin/env python3
"""Non-destructive patch from exact v0.7.7 source; run after all v077 overlays."""
from pathlib import Path
import hashlib
root=Path(__file__).resolve().parents[1]
assets=root/'app/src/main/assets'
expected={
 'app/src/main/assets/app.js':'43ec0aa711fb6b2fe72524393bdb0666149fd5e14084efd57c622a4b13090cc8',
 'app/src/main/assets/cash-ui.js':'a48913493341b0cd1d56702d25aa2e0c12da16cd2d31fa878aa806219cc95364',
 'app/src/main/assets/index.html':'860a4b2ccd9848a277d2034e7253b5a28866c98dda4bd682f716289712953353',
 'app/src/main/java/com/hamyar360/app/VoiceInput.java':'22f74a79fc0cf2828a90820b68b67a0b42c3a1435ef8dce82ecac15b9827ef12',
 'app/src/main/java/com/hamyar360/app/MainActivity.java':'16db524574828ab93d603c4bf1d04162463534072fece389198bbecbd49ee30d',
 'app/build.gradle':'a7cdf30e0d01b5ed8bfa9aae18abd7d2ce928babb29e235def080f555532a8a7',
}
for rel,sha in expected.items():
 p=root/rel
 assert hashlib.sha256(p.read_bytes()).hexdigest()==sha,f'Stability patch: unexpected original source {rel}'
updates={}
def replace(rel,old,new):
 if rel not in updates:updates[rel]=(root/rel).read_text('utf-8')
 text=updates[rel]
 assert text.count(old)==1,(rel,old[:60],text.count(old))
 updates[rel]=text.replace(old,new)
sync="""/* An in-process event bus; no information is transmitted. */
(function(root){
 'use strict';
 const subscribers=new Set();let revision=0;
 function subscribe(listener){
  if(typeof listener!=='function')throw new TypeError('Subscriber must be a function');
  subscribers.add(listener);return ()=>subscribers.delete(listener);
 }
 function publish(scope){
  if(!['legacy','cash','combined'].includes(scope))throw new Error('Unknown change scope');
  const change=Object.freeze({scope,revision:++revision});
  for(const fn of [...subscribers]){
   try{fn(change)}catch(error){console.error('Data refresh listener failed',error)}
  }
  return change;
 }
 root.HaselDataSync=Object.freeze({subscribe,publish,get revision(){return revision}});
})(typeof window!=='undefined'?window:globalThis);
"""
updates['app/src/main/assets/data-sync.js']=sync
replace('app/src/main/assets/index.html','<script src="app.js"></script>','<script src="data-sync.js"></script><script src="app.js"></script>')
replace('app/src/main/assets/app.js','db=next;render();syncReminders();return true',"db=next;render();syncReminders();window.HaselDataSync?.publish('legacy');return true")
replace('app/src/main/assets/app.js',"$('calendarPreference').value=C.calendarMode();","window.HaselDataSync?.subscribe(change=>{if(change.scope==='cash'&&ready)render()});\n$('calendarPreference').value=C.calendarMode();")
replace('app/src/main/assets/app.js',"window.HaselCashUI.replace(valid.cash);render();syncReminders();$('settlementDialog').close()","window.HaselCashUI.replace(valid.cash);render();syncReminders();window.HaselDataSync?.publish('combined');$('settlementDialog').close()")
replace('app/src/main/assets/app.js',"window.HaselCashUI?.replace(cash);render();syncReminders();notice('بازیابی همه اطلاعات انجام شد')","window.HaselCashUI?.replace(cash);render();syncReminders();window.HaselDataSync?.publish('combined');notice('بازیابی همه اطلاعات انجام شد')")
rel='app/src/main/assets/cash-ui.js'
old="let state=K.initial(),write,notice=()=>{},busy=false,ready=false,selectedVersion='';"
new=old+"\nfunction clearMatrix(){const head=$('cashMatrixHead'),body=$('cashMatrixBody');head.replaceChildren();body.replaceChildren();delete body.dataset.versionId;delete body.dataset.month;$('cashSaveMatrix').disabled=true;}\n"
replace(rel,old,new)
replace(rel,"state=checked;render();if(success.includes('دوره بودجه')","state=checked;clearMatrix();render();root.HaselDataSync?.publish('cash');if(success.includes('دوره بودجه')")
replace(rel,"$('cashMatrixBody').replaceChildren();for(const c of cats)","$('cashMatrixBody').replaceChildren();$('cashMatrixBody').dataset.versionId=version;$('cashMatrixBody').dataset.month=m;$('cashSaveMatrix').disabled=false;for(const c of cats)")
replace(rel,"notice('ماتریس بودجه آماده ورود یا اصلاح است')}catch(err){notice(err.message)}}","notice('ماتریس بودجه آماده ورود یا اصلاح است')}catch(err){clearMatrix();notice(err.message)}}")
replace(rel,"nextMonthPeriod();render()}\n$('cashCategoryForm')","nextMonthPeriod();clearMatrix();render()}\n$('cashCategoryForm')")
replace(rel,"$('cashDrawMatrix').onclick=matrix;$('cashSaveMatrix').onclick=()=>{try{","$('cashLineVersion').addEventListener('change',()=>clearMatrix());$('cashLineMonth').addEventListener('input',()=>clearMatrix());\n$('cashDrawMatrix').onclick=matrix;$('cashSaveMatrix').onclick=()=>{try{")
replace(rel,"const inputs=[...$('cashMatrixBody').querySelectorAll('input.cash-matrix-cell')];if(!inputs.length)","const body=$('cashMatrixBody'),version=val('cashLineVersion'),m=K.month(val('cashLineMonth'));if(!body.dataset.versionId||body.dataset.versionId!==version||body.dataset.month!==m||!state.budgetVersions.some(v=>v.id===version&&v.status==='draft')){clearMatrix();throw Error('ماتریس قدیمی است؛ نسخه و ماه را دوباره نمایش دهید')}const inputs=[...body.querySelectorAll('input.cash-matrix-cell')];if(!inputs.length)")
replace(rel,"state=valid;render();notice('ورود گروهی Excel ذخیره شد');return true","state=valid;clearMatrix();render();root.HaselDataSync?.publish('cash');notice('ورود گروهی Excel ذخیره شد');return true")
replace(rel,"replace:next=>{state=K.validate(next);selectedVersion='';render()}","replace:next=>{state=K.validate(next);selectedVersion='';clearMatrix();render()}")
replace(rel,"root.HaselCashUI={init,render,previewExcel","root.HaselDataSync?.subscribe(change=>{if(change.scope==='legacy'&&ready)report()});\nroot.HaselCashUI={init,render,previewExcel")
replace('app/build.gradle',"versionCode 14; versionName '0.7.7'","versionCode 15; versionName '0.7.7.1'")
replace('app/build.gradle',"applicationIdSuffix '.beta'","applicationIdSuffix '.stability'")
v='app/src/main/java/com/hamyar360/app/VoiceInput.java'
replace(v,'import android.speech.SpeechRecognizer;','import android.speech.SpeechRecognizer;\nimport android.widget.EditText;')
replace(v,'static final int MICROPHONE_PERMISSION=915;','static final int MICROPHONE_PERMISSION=915, ALTERNATIVE_RECOGNIZER_REQUEST=922;')
replace(v,'private boolean active, destroyed;','private boolean active, destroyed, externalPending;')
replace(v,'private final Runnable timeout=()->fail("پاسخی از سرویس گفتار نرسید؛ اتصال اینترنت و سرویس گفتار گوشی را بررسی کنید.");','private final Runnable timeout=()->offerFallback("پاسخی از سرویس اصلی گفتار نرسید؛ می‌توانید از روش جایگزین استفاده کنید.");')
replace(v,'if(!SpeechRecognizer.isRecognitionAvailable(activity)){fail("سرویس تشخیص گفتار روی گوشی فعال نیست؛ یک سرویس سازگار با فارسی را نصب یا فعال کنید.");return;}','if(!SpeechRecognizer.isRecognitionAvailable(activity)){offerFallback("سرویس اصلی تشخیص گفتار روی گوشی فعال نیست.");return;}')
replace(v,'else fail(message(code));}','else offerFallback(message(code));}')
replace(v,'else fail("گفتار تشخیص داده نشد؛ دوباره فرمان صوتی را بزنید.");}','else offerFallback("گفتار تشخیص داده نشد؛ روش دیگری را انتخاب کنید.");}')
replace(v,'catch(Exception ex){fail("شروع تشخیص گفتار ممکن نشد؛ سرویس گفتار گوشی را بررسی کنید.");}','catch(Exception ex){offerFallback("شروع سرویس اصلی گفتار ممکن نشد.");}')
extra=r'''  // Fallback is opt-in. Manual entry works fully offline.
  private void offerFallback(String reason){
    if(!active||destroyed)return;
    ++generation;handler.removeCallbacks(timeout);handler.removeCallbacks(retry);
    if(recognizer!=null){SpeechRecognizer old=recognizer;recognizer=null;
      try{old.cancel();}catch(Exception ignored){}old.destroy();}
    if(dialog!=null){dialog.dismiss();dialog=null;}
    dialog=new AlertDialog.Builder(activity).setTitle("روش جایگزین فرمان صوتی")
      .setMessage(reason+"\nمی‌توانید از برنامه تشخیص گفتار دیگری استفاده کنید یا متن را دستی وارد کنید. پردازش گفتار توسط برنامه دیگر ممکن است نیازمند اینترنت باشد.")
      .setPositiveButton("سرویس گفتار جایگزین",(d,w)->launchAlternative())
      .setNeutralButton("ورود دستی متن",(d,w)->manualEntry())
      .setNegativeButton("انصراف",(d,w)->finish()).setOnCancelListener(d->finish()).show();
  }
  private void launchAlternative(){
    if(!active||destroyed)return;
    try{
      Intent intent=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
      intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
      intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE,"fa-IR");
      intent.putExtra(RecognizerIntent.EXTRA_PROMPT,"فرمان خود را به فارسی بگویید");
      intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,1);
      externalPending=true;
      activity.startActivityForResult(intent,ALTERNATIVE_RECOGNIZER_REQUEST);
    }catch(Exception ex){externalPending=false;manualEntry();}
  }
  boolean onFallbackResult(int requestCode,int resultCode,Intent data){
    if(requestCode!=ALTERNATIVE_RECOGNIZER_REQUEST)return false;
    if(!externalPending||!active||destroyed)return true;
    externalPending=false;
    ArrayList<String> matches=resultCode==Activity.RESULT_OK&&data!=null
      ?data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS):null;
    String text=matches!=null&&!matches.isEmpty()?matches.get(0):"";
    if(text!=null&&!text.trim().isEmpty()){finish();listener.result(text.trim());}
    else if(resultCode==Activity.RESULT_CANCELED)finish();
    else manualEntry();
    return true;
  }
  private void manualEntry(){
    if(!active||destroyed)return;
    EditText input=new EditText(activity);input.setHint("شرح، تاریخ و مبلغ را به فارسی وارد کنید");
    input.setMinLines(2);input.setMaxLines(5);
    dialog=new AlertDialog.Builder(activity).setTitle("فرمان به‌صورت متن")
      .setMessage("متن را وارد و سپس پیش‌نویس ساخته‌شده را بررسی کنید.")
      .setView(input)
      .setPositiveButton("درج متن",(d,w)->{
        String value=input.getText().toString().trim();
        finish();if(!value.isEmpty())listener.result(value);
        else listener.error("متنی وارد نشد؛ ثبت دستی از فرم برنامه همچنان در دسترس است.");
      }).setNegativeButton("انصراف",(d,w)->finish())
      .setOnCancelListener(d->finish()).show();
  }
'''
replace(v,'  private String message(int code){',extra+'  private String message(int code){')
replace(v,'active=false;++generation;handler.removeCallbacks(timeout);handler.removeCallbacks(retry);','active=false;externalPending=false;++generation;handler.removeCallbacks(timeout);handler.removeCallbacks(retry);')
replace('app/src/main/java/com/hamyar360/app/MainActivity.java','    super.onActivityResult(request,result,data);','    super.onActivityResult(request,result,data);\n    if(voiceInput!=null&&voiceInput.onFallbackResult(request,result,data))return;')
# Confirm the entire old version is as expected before writing any modification.
for rel,text in updates.items():
 p=root/rel;p.parent.mkdir(parents=True,exist_ok=True)
 p.write_text(text,'utf-8')
 print('Patched',rel,hashlib.sha256(p.read_bytes()).hexdigest())
print('Patch applied without touching stored user data')
