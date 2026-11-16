package com.auto.schedule.reminder;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import com.auto.schedule.model.Massage;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Планує нагадування на конкретний час через {@link AlarmManager}.
 *
 * <p>Використовуються звичайні (неточні) будильники
 * {@link AlarmManager#setAndAllowWhileIdle}: вони працюють і в режимі Doze,
 * але Android може доставити їх із невеликою затримкою.</p>
 *
 * <p>Перед новим плануванням усі раніше створені нагадування скасовуються,
 * тому повторний виклик не створює дублікатів.</p>
 */
public class ReminderFactory {

  private static final String PREFS_NAME = "auto_schedule_reminders";
  private static final String KEY_CODES = "scheduled_codes";

  private final Context context;

  public ReminderFactory(Context context) {
    this.context = context.getApplicationContext();
  }

  /**
   * Планує всі нагадування зі списку.
   *
   * @param messages нагадування з часом; {@code null} ігнорується
   */
  public void run(List<Massage> messages) {
    AlarmManager alarmManager = (AlarmManager)
        context.getSystemService(Context.ALARM_SERVICE);
    if (alarmManager == null) {
      return;
    }

    cancelScheduled(alarmManager);

    Set<String> codes = new HashSet<>();
    long now = System.currentTimeMillis();

    if (messages != null) {
      for (Massage msg : messages) {
        if (msg == null || msg.time == null) {
          continue;
        }

        long triggerAt = msg.time.toInstant().toEpochMilli();
        if (triggerAt <= now) {
          continue;
        }

        int requestCode = requestCode(msg);
        PendingIntent pendingIntent = pendingIntent(requestCode);
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
        codes.add(String.valueOf(requestCode));
      }
    }

    saveCodes(codes);
  }

  /** Скасовує нагадування, заплановані попереднім викликом {@link #run}. */
  private void cancelScheduled(AlarmManager alarmManager) {
    for (String code : loadCodes()) {
      try {
        alarmManager.cancel(pendingIntent(Integer.parseInt(code)));
      } catch (NumberFormatException ignored) {
        // пошкоджений запис у налаштуваннях просто пропускаємо
      }
    }
    prefs().edit().remove(KEY_CODES).apply();
  }

  private PendingIntent pendingIntent(int requestCode) {
    Intent intent = new Intent(context, ReminderReceiver.class)
        .setAction(ReminderReceiver.ACTION_SHOW_REMINDER);
    return PendingIntent.getBroadcast(context, requestCode, intent,
        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
  }

  /** Стабільний код запиту для пари «текст + час». */
  private int requestCode(Massage msg) {
    int hash = (msg.msg + "|" + msg.time).hashCode();
    return hash & 0x7fffffff;
  }

  private Set<String> loadCodes() {
    return prefs().getStringSet(KEY_CODES, Collections.<String>emptySet());
  }

  private void saveCodes(Set<String> codes) {
    prefs().edit().putStringSet(KEY_CODES, codes).apply();
  }

  private SharedPreferences prefs() {
    return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
  }
}
