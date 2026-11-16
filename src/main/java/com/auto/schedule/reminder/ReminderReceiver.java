package com.auto.schedule.reminder;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.auto.schedule.data.GetStudySchedule;
import com.auto.schedule.data.RetrieveSchedule;
import com.auto.schedule.data.Storage;
import com.auto.schedule.model.Massage;
import com.auto.schedule.model.Schedule;
import com.auto.schedule.model.Student;
import com.auto.schedule.model.UserData;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * Щоденний планувальник нагадувань.
 *
 * <p>О 7:30 отримує повідомлення від {@link AlarmManager}, підтягує дані зі
 * {@link Storage}, перевіряє, чи розклад оновлено сьогодні, за потреби
 * викликає {@link #updatorSchedule()} і планує нагадування за 10 хвилин до
 * кожної пари.</p>
 *
 * <p>Якщо оновити розклад не вдалося, нагадування не створюються — щоб не
 * показувати застарілі дані. Після перезавантаження пристрою щоденний
 * будильник відновлюється, а якщо перезавантаження сталося після 7:30,
 * одразу виконується й сьогоднішня перевірка.</p>
 */
public class ReminderReceiver extends BroadcastReceiver {

  private static final String TAG = "ReminderReceiver";

  /** Щоденна перевірка розкладу. */
  public static final String ACTION_DAILY_CHECK = "com.auto.schedule.action.DAILY_CHECK";

  /** Показ конкретного нагадування (надсилається AlarmManager-ом). */
  public static final String ACTION_SHOW_REMINDER = "com.auto.schedule.action.SHOW_REMINDER";

  public static final String EXTRA_MESSAGE = "message";
  public static final String EXTRA_TIME = "time";

  /** Час щоденної перевірки. */
  private static final int CHECK_HOUR = 7;
  private static final int CHECK_MINUTE = 30;

  /** Код запиту щоденного будильника (має бути стабільним для скасування). */
  private static final int REQUEST_DAILY = 1;

  /**
   * Нагадування, яке спрацювало із запізненням більше ніж на 30 хвилин,
   * вважаємо застарілим (наприклад, пристрій був вимкнений).
   */
  private static final long STALE_REMINDER_MS = 30L * 60L * 1000L;

  /** Контекст поточного виклику {@link #onReceive}. */
  private Context context;

  /** Адреса сервера, дані користувача й розклад зі сховища. */
  private String domen;
  private UserData data;
  private Schedule schedule;

  @Override
  public void onReceive(Context context, Intent intent) {
    this.context = context.getApplicationContext();

    String action = intent == null ? null : intent.getAction();

    if (ACTION_SHOW_REMINDER.equals(action)) {
      showReminder(intent);
      return;
    }

    if (ACTION_DAILY_CHECK.equals(action)) {
      // Наступний будильник ставимо одразу, навіть якщо перевірка впаде.
      scheduleDaily(this.context);
      runDailyCheckAsync();
      return;
    }

    if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {
      scheduleDaily(this.context);
      if (!LocalTime.now().isBefore(LocalTime.of(CHECK_HOUR, CHECK_MINUTE))) {
        // Після перезавантаження вже після 7:30 — наздоганяємо сьогоднішні пари.
        runDailyCheckAsync();
      }
    }
  }

  // ------------------------------------------------------------------
  // Щоденна перевірка
  // ------------------------------------------------------------------

  /** Виконує мережеву роботу у фоні, бо onReceive має обмежений час. */
  private void runDailyCheckAsync() {
    final PendingResult pendingResult = goAsync();
    new Thread(new Runnable() {
      @Override
      public void run() {
        try {
          dailyCheck();
        } finally {
          pendingResult.finish();
        }
      }
    }, "reminder-daily-check").start();
  }

  /** Повний цикл: сховище → перевірка оновлення → планування нагадувань. */
  private void dailyCheck() {
    loader();
    if (isEmpty(domen) || data == null) {
      Log.w(TAG, "Не задано адресу сервера або дані користувача");
      return;
    }

    if (!isUpdatedToday()) {
      Schedule fetched = updatorSchedule();
      if (fetched == null) {
        // За рішенням користувача: без свіжого розкладу нагадування не складаємо.
        Log.w(TAG, "Розклад не оновлено — нагадування не плануються");
        return;
      }
      schedule = fetched;
    }

    if (schedule == null) {
      return;
    }
    scheduleReminders(context, schedule);
  }

  /** Підтягує дані зі сховища. */
  private void loader() {
    domen = Storage.loadDomen(context);
    data = Student.fromValues(Storage.loadUserData(context));
    schedule = Storage.loadSchedule(context);
  }

  /** @return {@code true}, якщо розклад уже завантажували сьогодні */
  private boolean isUpdatedToday() {
    return schedule != null
        && schedule.updateTime != null
        && LocalDate.now().equals(schedule.updateTime.toLocalDate());
  }

  /**
   * Оновлює розклад із сервера й зберігає його.
   *
   * @return свіжий розклад або {@code null}, якщо оновлення не вдалося
   */
  private Schedule updatorSchedule() {
    if (isEmpty(domen) || data == null) {
      return null;
    }

    try {
      Schedule fetched = new RetrieveSchedule()
          .retrieve(domen, new GetStudySchedule(data));
      Storage.saveSchedule(context, fetched);
      return fetched;
    } catch (RuntimeException e) {
      Log.e(TAG, "Не вдалося оновити розклад", e);
      return null;
    }
  }

  // ------------------------------------------------------------------
  // Нагадування
  // ------------------------------------------------------------------

  /**
   * Формує й планує нагадування для розкладу.
   *
   * <p>Використовується і щоденною перевіркою, і ручним оновленням
   * розкладу в {@code MainActivity}.</p>
   */
  public static void scheduleReminders(Context context, Schedule schedule) {
    List<Massage> messages = new FormingMassange().forming(schedule);
    new ReminderFactory(context).run(messages);
  }

  /** Показує нагадування, яке спрацювало. */
  private void showReminder(Intent intent) {
    String message = intent.getStringExtra(EXTRA_MESSAGE);
    long time = intent.getLongExtra(EXTRA_TIME, 0L);
    if (message == null || message.trim().isEmpty()) {
      return;
    }

    long now = System.currentTimeMillis();
    if (time > 0 && now - time > STALE_REMINDER_MS) {
      return;
    }

    ZonedDateTime when = time > 0
        ? Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault())
        : ZonedDateTime.now();
    new NotificationSender(context).send(new Massage(message, when));
  }

  // ------------------------------------------------------------------
  // Щоденний будильник
  // ------------------------------------------------------------------

  /**
   * Ставить наступний щоденний будильник на 7:30.
   *
   * <p>Викликається з {@code MainActivity}, після спрацювання будильника
   * та після перезавантаження пристрою.</p>
   */
  public static void scheduleDaily(Context context) {
    Context app = context.getApplicationContext();
    AlarmManager alarmManager = (AlarmManager) app.getSystemService(Context.ALARM_SERVICE);
    if (alarmManager == null) {
      return;
    }

    Intent intent = new Intent(app, ReminderReceiver.class)
        .setAction(ACTION_DAILY_CHECK);
    PendingIntent pendingIntent = PendingIntent.getBroadcast(app, REQUEST_DAILY, intent,
        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

    alarmManager.cancel(pendingIntent);
    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextDailyCheckMillis(),
        pendingIntent);
  }

  /** @return найближчі 7:30 у поточному часовому поясі */
  private static long nextDailyCheckMillis() {
    ZonedDateTime now = ZonedDateTime.now();
    ZonedDateTime next = now.withHour(CHECK_HOUR)
        .withMinute(CHECK_MINUTE)
        .withSecond(0)
        .withNano(0);
    if (!next.isAfter(now)) {
      next = next.plusDays(1);
    }
    return next.toInstant().toEpochMilli();
  }

  private static boolean isEmpty(String value) {
    return value == null || value.trim().isEmpty();
  }
}
