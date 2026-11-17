package com.auto.schedule.reminder;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.BitmapFactory;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.os.Build;
import android.util.Log;

import com.auto.schedule.MainActivity;
import com.auto.schedule.R;
import com.auto.schedule.model.Massage;

/**
 * Показує нагадування про пару в системному лотку повідомлень.
 *
 * <p>Повідомлення термінові: канал має важливість
 * {@link NotificationManager#IMPORTANCE_HIGH}, тому Android показує його
 * поверх екрана (heads-up) зі звуком і вібрацією, а не просто складає
 * в лоток.</p>
 *
 * <p>Канал створюється ліниво — під час першого надсилання. Важливість
 * каналу не можна підвищити після створення, тому ідентифікатор
 * {@code lesson_reminders_alarm} відрізняється від старого
 * {@code lesson_reminders}; старий канал видаляється.</p>
 */
public class NotificationSender {

  private static final String TAG = "NotificationSender";

  /** Ідентифікатор термінового каналу нагадувань. */
  private static final String CHANNEL_ID = "lesson_reminders_alarm";

  /** Старий канал із нижчою важливістю — прибираємо, щоб не було дублів. */
  private static final String LEGACY_CHANNEL_ID = "lesson_reminders";

  /** Зсув, щоб id нагадувань не збігалися з іншими повідомленнями застосунку. */
  private static final int NOTIFICATION_ID_BASE = 1000;

  private final Context context;

  public NotificationSender(Context context) {
    this.context = context.getApplicationContext();
  }

  /**
   * Показує повідомлення.
   *
   * @param msg нагадування; {@code null} або без тексту ігнорується
   */
  public void send(Massage msg) {
    if (msg == null || msg.msg == null || msg.msg.trim().isEmpty()) {
      return;
    }

    NotificationManager manager = (NotificationManager)
        context.getSystemService(Context.NOTIFICATION_SERVICE);
    if (manager == null) {
      return;
    }

    createChannel(manager);

    if (Build.VERSION.SDK_INT >= 33
        && context.checkSelfPermission("android.permission.POST_NOTIFICATIONS")
            != PackageManager.PERMISSION_GRANTED) {
      Log.w(TAG, "Немає дозволу POST_NOTIFICATIONS — повідомлення не показано");
      return;
    }

    if (!manager.areNotificationsEnabled()) {
      Log.w(TAG, "Повідомлення вимкнені в налаштуваннях застосунку");
      return;
    }

    Intent open = new Intent(context, MainActivity.class)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
    PendingIntent contentIntent = PendingIntent.getActivity(context, 0, open,
        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

    Notification notification = new Notification.Builder(context, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_popup_reminder)
        .setLargeIcon(BitmapFactory.decodeResource(
            context.getResources(), R.drawable.ic_launcher))
        .setContentTitle(context.getString(R.string.notification_title))
        .setContentText(msg.msg)
        .setStyle(new Notification.BigTextStyle().bigText(msg.msg))
        .setContentIntent(contentIntent)
        .setCategory(Notification.CATEGORY_ALARM)
        .setPriority(Notification.PRIORITY_MAX)
        .setVisibility(Notification.VISIBILITY_PUBLIC)
        .setDefaults(Notification.DEFAULT_ALL)
        .setWhen(msg.time == null
            ? System.currentTimeMillis()
            : msg.time.toInstant().toEpochMilli())
        .setShowWhen(true)
        .setAutoCancel(true)
        .build();

    manager.notify(notificationId(msg), notification);
  }

  /** Створює терміновий канал повідомлень (повторний виклик безпечний). */
  private void createChannel(NotificationManager manager) {
    NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
        context.getString(R.string.notification_channel_name),
        NotificationManager.IMPORTANCE_HIGH);
    channel.setDescription(context.getString(R.string.notification_channel_description));

    // Сигнал тривоги: звук, вібрація, світлодіод і показ на екрані блокування.
    channel.enableLights(true);
    channel.setLightColor(0xFFFF0000);
    channel.enableVibration(true);
    channel.setVibrationPattern(new long[]{0, 400, 200, 400});
    channel.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
        new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build());
    channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
    channel.setShowBadge(true);

    manager.createNotificationChannel(channel);

    // Канал зі старою важливістю більше не потрібен.
    manager.deleteNotificationChannel(LEGACY_CHANNEL_ID);
  }

  /** Стабільний id: повторне нагадування замінює попереднє, а не дублюється. */
  private int notificationId(Massage msg) {
    int hash = (msg.msg + "|" + msg.time).hashCode();
    return NOTIFICATION_ID_BASE + (hash & 0x7fffffff) % 100000;
  }
}
