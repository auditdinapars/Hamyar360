package com.hamyar360.app;

import android.app.AlarmManager;
import android.content.Context;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.robolectric.Shadows.shadowOf;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class ReminderTest {
  @Test public void syncSchedulesThenCompletionCancels()throws Exception{
    Context c=RuntimeEnvironment.getApplication();AlarmManager a=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
    ReminderReceiver.sync(c,"[{\"id\":\"t1\",\"title\":\"تماس\",\"time\":"+(System.currentTimeMillis()+600000)+"}]");
    assertEquals(1,shadowOf(a).getScheduledAlarms().size());assertEquals(1,ReminderReceiver.saved(c).length());
    ReminderReceiver.sync(c,"[]");assertEquals(0,shadowOf(a).getScheduledAlarms().size());assertEquals(0,ReminderReceiver.saved(c).length());
  }
}
