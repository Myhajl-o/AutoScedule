package com.auto.schedule.reminder;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import com.auto.schedule.MainActivity;
import com.auto.schedule.R;
import com.auto.schedule.model.Massage;

/**
 * Показує нагадування про пару в системному лотку повідомлень.
 *
 * <p>Канал повідомлень створюється ліниво — під час першого надсилання.
 * Якщо користувач не надав дозвіл {@code POST_NOTIFICATIONS} (Android 13+),
 * нагадування просто не показується.</p>
 */
public class NotificationSender {

  /** Ідентифікатор каналу нагадувань. */
  private static final String CHANNEL_ID = "lesson_reminders";

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
      return;
    }

    Intent open = new Intent(context, MainActivity.class)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
    PendingIntent contentIntent = PendingIntent.getActivity(context, 0, open,
        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

    Notification notification = new Notification.Builder(context, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentTitle(context.getString(R.string.notification_title))
        .setContentText(msg.msg)
        .setStyle(new Notification.BigTextStyle().bigText(msg.msg))
        .setContentIntent(contentIntent)
        .setCategory(Notification.CATEGORY_REMINDER)
        .setAutoCancel(true)
        .build();

    manager.notify(notificationId(msg), notification);
  }

  /** Створює канал повідомлень (повторний виклик безпечний). */
  private void createChannel(NotificationManager manager) {
    NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
        context.getString(R.string.notification_channel_name),
        NotificationManager.IMPORTANCE_HIGH);
    channel.setDescription(context.getString(R.string.notification_channel_description));
    manager.createNotificationChannel(channel);
  }

  /** Стабільний id: повторне нагадування замінює попереднє, а не дублюється. */
  private int notificationId(Massage msg) {
    int hash = (msg.msg + "|" + msg.time).hashCode();
    return NOTIFICATION_ID_BASE + (hash & 0x7fffffff) % 100000;
  }
}
