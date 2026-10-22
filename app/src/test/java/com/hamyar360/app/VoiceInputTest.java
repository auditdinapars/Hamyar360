package com.hamyar360.app;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;
import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.os.Bundle;
import android.os.Looper;
import android.provider.Settings;
import android.speech.RecognitionService;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowActivity;
import org.robolectric.shadows.ShadowAlertDialog;
import org.robolectric.shadows.ShadowSpeechRecognizer;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
@LooperMode(LooperMode.Mode.PAUSED)
public class VoiceInputTest {
  private ActivityController<Activity> controller;
  private Activity activity;
  private VoiceInput voice;
  private String result, error;
  private int finished;
  @Before public void setup(){
    controller=Robolectric.buildActivity(Activity.class).setup();activity=controller.get();
    activity.getPreferences(Activity.MODE_PRIVATE).edit().clear().commit();
    shadowOf(activity.getApplication()).denyPermissions(Manifest.permission.RECORD_AUDIO);
    voice=new VoiceInput(activity,new VoiceInput.Listener(){
      public void result(String text){result=text;}
      public void error(String text){error=text;}
      public void finished(){finished++;}
    });
  }
  @After public void teardown(){voice.destroy();controller.pause().stop().destroy();}
  private ShadowSpeechRecognizer startGranted(){
    shadowOf(activity.getApplication()).grantPermissions(Manifest.permission.RECORD_AUDIO);
    ResolveInfo info=new ResolveInfo();info.serviceInfo=new ServiceInfo();info.serviceInfo.packageName="test.speech";info.serviceInfo.name="test.speech.Service";
    shadowOf(activity.getPackageManager()).addResolveInfoForIntent(new Intent(RecognitionService.SERVICE_INTERFACE),info);
    voice.start();shadowOf(Looper.getMainLooper()).idle();
    assertNotNull(ShadowSpeechRecognizer.getLatestSpeechRecognizer());
    return shadowOf(ShadowSpeechRecognizer.getLatestSpeechRecognizer());
  }
  @Test public void firstTapRequestsMicrophoneBeforeListening(){
    voice.start();ShadowActivity.PermissionsRequest request=shadowOf(activity).getLastRequestedPermission();
    assertNotNull(request);assertArrayEquals(new String[]{Manifest.permission.RECORD_AUDIO},request.requestedPermissions);
    assertEquals(VoiceInput.MICROPHONE_PERMISSION,request.requestCode);assertNull(ShadowSpeechRecognizer.getLatestSpeechRecognizer());
  }
  @Test public void denialOffersOwnAppSettingsWithoutStartingRecording(){
    voice.start();voice.onPermissionResult(VoiceInput.MICROPHONE_PERMISSION,new int[]{PackageManager.PERMISSION_DENIED});
    assertNull(ShadowSpeechRecognizer.getLatestSpeechRecognizer());assertTrue(finished>0);assertTrue(error.contains("همیار"));
    while(shadowOf(activity).getNextStartedActivity()!=null){} // Discard the system permission-request intent.
    ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick();
    shadowOf(Looper.getMainLooper()).idle();
    Intent intent=shadowOf(activity).getNextStartedActivity();assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,intent.getAction());
    assertEquals("package:"+activity.getPackageName(),intent.getDataString());
  }
  @Test public void recognitionUsesPersianAndDeliversTextThenReleasesMicrophone(){
    ShadowSpeechRecognizer recognizer=startGranted();assertEquals("fa-IR",recognizer.getLastRecognizerIntent().getStringExtra(RecognizerIntent.EXTRA_LANGUAGE));
    Bundle data=new Bundle();data.putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION,new ArrayList<>(Arrays.asList("فردا تماس بگیر")));recognizer.triggerOnResults(data);
    assertEquals("فردا تماس بگیر",result);assertTrue(recognizer.isDestroyed());assertTrue(finished>0);
  }
  @Test public void providerPermissionErrorIsNotMistakenForHamyarPermission(){
    ShadowSpeechRecognizer recognizer=startGranted();recognizer.triggerOnError(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS);
    assertTrue(error.contains("همیار مجوز میکروفن دارد"));assertTrue(recognizer.isDestroyed());
    ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick();
    shadowOf(Looper.getMainLooper()).idle();
    assertEquals(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS,shadowOf(activity).getNextStartedActivity().getAction());
  }
  @Test public void backgroundingCancelsRecognitionAndIgnoresLateResults(){
    ShadowSpeechRecognizer recognizer=startGranted();voice.onStop();assertTrue(recognizer.isDestroyed());
    Bundle data=new Bundle();data.putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION,new ArrayList<>(Arrays.asList("late")));recognizer.triggerOnResults(data);assertNull(result);
  }
  @Test public void unresponsiveProviderTimesOutAndReleasesMicrophone(){
    ShadowSpeechRecognizer recognizer=startGranted();shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(46));assertTrue(recognizer.isDestroyed());assertTrue(error.contains("پاسخی"));
  }
}

