#!/usr/bin/env python3
"""Ensure Robolectric tests document and exercise new fallback behavior."""
from pathlib import Path
p=Path(__file__).resolve().parents[1]/'app/src/test/java/com/hamyar360/app/VoiceInputTest.java'
s=p.read_text('utf-8')
old='''  @Test public void unresponsiveProviderTimesOutAndReleasesMicrophone(){
    ShadowSpeechRecognizer recognizer=startGranted();shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(46));assertTrue(recognizer.isDestroyed());assertTrue(error.contains("پاسخی"));
  }'''
new='''  @Test public void timeoutOffersFallbackAndReleasesPrimaryRecognizer(){
    ShadowSpeechRecognizer recognizer=startGranted();
    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(46));
    assertTrue(recognizer.isDestroyed());assertTrue(voice.isActive());
    assertNotNull(ShadowAlertDialog.getLatestAlertDialog());
    assertNotNull(ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_NEUTRAL));
    ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
    shadowOf(Looper.getMainLooper()).idle();
    assertFalse(voice.isActive());
  }
  private static android.widget.EditText findEdit(android.view.View v){
    if(v instanceof android.widget.EditText)return (android.widget.EditText)v;
    if(v instanceof android.view.ViewGroup){
      android.view.ViewGroup group=(android.view.ViewGroup)v;
      for(int i=0;i<group.getChildCount();i++){
        android.widget.EditText found=findEdit(group.getChildAt(i));if(found!=null)return found;
      }
    }
    return null;
  }
  @Test public void networkFailureOffersManualEntryAndReturnsPersianText(){
    ShadowSpeechRecognizer recognizer=startGranted();
    recognizer.triggerOnError(SpeechRecognizer.ERROR_NETWORK);
    assertTrue(recognizer.isDestroyed());assertTrue(voice.isActive());
    ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_NEUTRAL).performClick();
    shadowOf(Looper.getMainLooper()).idle();
    AlertDialog manual=ShadowAlertDialog.getLatestAlertDialog();
    android.widget.EditText edit=findEdit(manual.getWindow().getDecorView());
    assertNotNull(edit);
    edit.setText("فردا پرداخت ۲۰ میلیون ریال");
    manual.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
    shadowOf(Looper.getMainLooper()).idle();
    assertEquals("فردا پرداخت ۲۰ میلیون ریال",result);
    assertFalse(voice.isActive());
  }'''
assert s.count(old)==1,'Voice test source has unexpected changes'
p.write_text(s.replace(old,new),'utf-8')
print('Extended fallback Robolectric tests')
