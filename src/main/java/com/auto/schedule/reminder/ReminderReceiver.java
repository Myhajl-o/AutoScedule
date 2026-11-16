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
import com.auto.schedule.model.Lesson;
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
 * Планувальник нагадувань і оновлень розкладу.
 *
 * <p>Має два незалежні сценарії:</p>
 * <ul>
 *   <li><b>О 7:30</b> — щоденна перевірка. Розклад завантажується завжди,
 *       незалежно від того, чи він уже є у сховищі, і для кожної пари
 *       створюється нагадування за 10 хвилин до початку.</li>
 *   <li><b>Після останньої пари</b> — тихе оновлення. Розклад
 *       перезавантажується на наступний день, щоб увечері було видно
 *       заняття на завтра. Нагадування при цьому <b>не</b> створюються.</li>
 * </ul>
 *
 * <p>Якщо на сьогодні пар немає (вихідний), тихе оновлення виконується
 * о 16:00. Якщо оновити розклад о 7:30 не вдалося, нагадування не
 * створюються — щоб не показувати застарілі дані.</p>
 *
 * <p>Після перезавантаження пристрою щоденний будильник відновлюється,
 * а перевірка виконується одразу, якщо зараз уже після 7:30.</p>
 */
public class ReminderReceiver extends BroadcastReceiver {

  private static final String TAG = "ReminderReceiver";

  /** Щоденна перевірка розкладу (7:30). */
  public static final String ACTION_DAILY_CHECK = "com.auto.schedule.action.DAILY_CHECK";

  /** Показ конкретного нагадування (надсилається AlarmManager-ом). */
  public static final String ACTION_SHOW_REMINDER = "com.auto.schedule.action.SHOW_REMINDER";

  /** Тиже оновлення розкладу на наступний день після занять. */
  public static final String ACTION_REFRESH_AFTER_LESSONS =
      "com.auto.schedule.action.REFRESH_AFTER_LESSONS";

  public static final String EXTRA_MESSAGE = "message";
  public static final String EXTRA_TIME = "time";

  /** Час щоденної перевірки. */
  private static final int CHECK_HOUR = 7;
  private static final int CHECK_MINUTE = 30;

  /** Час тихого оновлення, якщо на сьогодні пар немає. */
  private static final int NO_LESSONS_HOUR = 16;

  /** Пауза після останньої пари: сервер має встигнути оновити розклад. */
  private static final int AFTER_LESSONS_DELAY_MINUTES = 5;

  /** Код запиту щоденного будильника (має бути стабільним для скасування). */
  private static final int REQUEST_DAILY = 1;

  /** Код запиту тихого оновлення після занять. */
  private static final int REQUEST_AFTER_LESSONS = 2;

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
      runAsync(new Runnable() {
        @Override
        public void run() {
          dailyCheck();
        }
      });
      return;
    }

    if (ACTION_REFRESH_AFTER_LESSONS.equals(action)) {
      runAsync(new Runnable() {
        @Override
        public void run() {
          silentRefresh();
        }
      });
      return;
    }

    if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {
      scheduleDaily(this.context);
      if (!LocalTime.now().isBefore(LocalTime.of(CHECK_HOUR, CHECK_MINUTE))) {
        // Після перезавантаження вже після 7:30 — наздоганяємо сьогоднішній стан.
        runAsync(new Runnable() {
          @Override
          public void run() {
            bootCheck();
          }
        });
      }
    }
  }

  // ------------------------------------------------------------------
  // Щоденна перевірка (7:30)
  // ------------------------------------------------------------------

  /** Виконує мережеву роботу у фоні, бо onReceive має обмежений час. */
  private void runAsync(final Runnable task) {
    final PendingResult pendingResult = goAsync();
    new Thread(new Runnable() {
      @Override
      public void run() {
        try {
          task.run();
        } finally {
          pendingResult.finish();
        }
      }
    }, "reminder-worker").start();
  }

  /**
   * Щоденна перевірка: сховище → завантаження свіжого розкладу →
   * нагадування за 10 хвилин до кожної пари.
   *
   * <p>Розклад завантажується завжди, навіть якщо сьогодні вже
   * оновлювався.</p>
   */
  private void dailyCheck() {
    loader();
    if (isEmpty(domen) || data == null) {
      Log.w(TAG, "Не задано адресу сервера або дані користувача");
      return;
    }

    Schedule fetched = updatorSchedule();
    if (fetched == null) {
      // Без свіжого розкладу нагадування не складаємо.
      Log.w(TAG, "Розклад не оновлено — нагадування не плануються");
      return;
    }
    schedule = fetched;

    scheduleReminders(context, schedule);

    // Час тихого оновлення вже минув (наприклад, пристрій був вимкнений) —
    // одразу підтягуємо наступний день.
    if (afterLessonsMillis(schedule) <= System.currentTimeMillis()) {
      silentRefresh();
    }
  }

  /**
   * Стан після перезавантаження: якщо пари вже закінчилися — тихе
   * оновлення на наступний день, інакше звичайна щоденна перевірка.
   */
  private void bootCheck() {
    loader();
    if (isEmpty(domen) || data == null) {
      return;
    }

    if (needsSilentRefresh()) {
      silentRefresh();
    } else {
      dailyCheck();
    }
  }

  /** @return {@code true}, якщо сьогоднішні пари вже закінчилися */
  private boolean needsSilentRefresh() {
    ZonedDateTime now = ZonedDateTime.now();
    ZonedDateTime lastEnd = lastLessonEndToday(schedule, now.toLocalDate());
    if (lastEnd != null) {
      return now.isAfter(lastEnd);
    }
    return now.toLocalTime().isAfter(LocalTime.of(NO_LESSONS_HOUR, 0));
  }

  // ------------------------------------------------------------------
  // Тихе оновлення після занять
  // ------------------------------------------------------------------

  /**
   * Завантажує розклад на наступний день і зберігає його.
   *
   * <p>Нагадування не створюються: оновлення потрібне лише для того, щоб
   * увечері було видно заняття на завтра.</p>
   */
  private void silentRefresh() {
    loader();
    if (isEmpty(domen) || data == null) {
      Log.w(TAG, "Тихе оновлення: не задано адресу сервера або дані користувача");
      return;
    }

    LocalDate nextDay = LocalDate.now().plusDays(1);
    Schedule fetched = fetch(nextDay);
    if (fetched == null) {
      Log.w(TAG, "Тихе оновлення не вдалося");
      return;
    }

    int count = fetched.sched == null ? 0 : fetched.sched.size();
    Log.i(TAG, "Тихе оновлення: " + nextDay + " — занять " + count);
  }

  // ------------------------------------------------------------------
  // Сховище та сервер
  // ------------------------------------------------------------------

  /** Підтягує дані зі сховища. */
  private void loader() {
    domen = Storage.loadDomen(context);
    data = Student.fromValues(Storage.loadUserData(context));
    schedule = Storage.loadSchedule(context);
  }

  /**
   * Завантажує розклад на сьогодні (з запасним варіантом найближчого дня).
   *
   * @return свіжий розклад або {@code null}, якщо оновлення не вдалося
   */
  private Schedule updatorSchedule() {
    return fetch(null);
  }

  /**
   * Завантажує розклад і зберігає його у сховищі.
   *
   * @param day конкретний день або {@code null} для сьогодні
   * @return свіжий розклад або {@code null}, якщо оновлення не вдалося
   */
  private Schedule fetch(LocalDate day) {
    if (isEmpty(domen) || data == null) {
      return null;
    }

    try {
      RetrieveSchedule retriever = new RetrieveSchedule();
      GetStudySchedule source = new GetStudySchedule(data);
      Schedule fetched = day == null
          ? retriever.retrieve(domen, source)
          : retriever.retrieve(domen, source, day);
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
   * Формує й планує нагадування для розкладу та ставить тихе оновлення
   * після останньої пари.
   *
   * <p>Використовується і щоденною перевіркою, і ручним оновленням
   * розкладу в {@code MainActivity}.</p>
   */
  public static void scheduleReminders(Context context, Schedule schedule) {
    List<Massage> messages = new FormingMassange().forming(schedule);
    new ReminderFactory(context).run(messages);
    scheduleAfterLessons(context, schedule);
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
  // Будильники
  // ------------------------------------------------------------------

  /**
   * Планує тихе оновлення після останньої пари (або о 16:00, якщо пар
   * сьогодні немає).
   *
   * <p>Попередній будильник скасовується, тому повторний виклик не
   * створює дублікатів.</p>
   */
  public static void scheduleAfterLessons(Context context, Schedule schedule) {
    Context app = context.getApplicationContext();
    AlarmManager alarmManager = (AlarmManager) app.getSystemService(Context.ALARM_SERVICE);
    if (alarmManager == null) {
      return;
    }

    PendingIntent pendingIntent = afterLessonsIntent(app);
    alarmManager.cancel(pendingIntent);

    long triggerAt = afterLessonsMillis(schedule);
    if (triggerAt <= System.currentTimeMillis()) {
      return;
    }

    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
    Log.i(TAG, "Тихе оновлення заплановано на " + triggerAt);
  }

  /**
   * @return момент, коли всі пари на сьогодні вже закінчилися
   *         (або 16:00, якщо пар немає)
   */
  private static long afterLessonsMillis(Schedule schedule) {
    ZonedDateTime now = ZonedDateTime.now();
    ZonedDateTime lastEnd = lastLessonEndToday(schedule, now.toLocalDate());

    ZonedDateTime at = lastEnd != null
        ? lastEnd.plusMinutes(AFTER_LESSONS_DELAY_MINUTES)
        : now.toLocalDate().atTime(NO_LESSONS_HOUR, 0).atZone(now.getZone());
    return at.toInstant().toEpochMilli();
  }

  /** @return час закінчення останньої пари на вказаний день або {@code null} */
  private static ZonedDateTime lastLessonEndToday(Schedule schedule, LocalDate day) {
    if (schedule == null || schedule.sched == null) {
      return null;
    }

    ZonedDateTime last = null;
    for (Lesson lesson : schedule.sched) {
      if (lesson == null || lesson.endsAt == null) {
        continue;
      }
      if (!day.equals(lesson.endsAt.toLocalDate())) {
        continue;
      }
      if (last == null || lesson.endsAt.isAfter(last)) {
        last = lesson.endsAt;
      }
    }
    return last;
  }

  private static PendingIntent afterLessonsIntent(Context app) {
    Intent intent = new Intent(app, ReminderReceiver.class)
        .setAction(ACTION_REFRESH_AFTER_LESSONS);
    return PendingIntent.getBroadcast(app, REQUEST_AFTER_LESSONS, intent,
        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
  }

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
