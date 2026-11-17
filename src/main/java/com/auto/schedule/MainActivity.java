package com.auto.schedule;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.auto.schedule.data.GetStudySchedule;
import com.auto.schedule.data.RetrieveSchedule;
import com.auto.schedule.data.Storage;
import com.auto.schedule.model.Schedule;
import com.auto.schedule.model.Student;
import com.auto.schedule.model.UserData;
import com.auto.schedule.reminder.ReminderReceiver;
import com.auto.schedule.ui.InputDomen;
import com.auto.schedule.ui.InputUserData;
import com.auto.schedule.ui.ShowSchedule;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Головний екран застосунку: п'ять кнопок у термінальному стилі та
 * рядок стану із поточними даними.
 *
 * <p>Кнопки: показати розклад, ввести дані користувача, ввести адресу
 * сервера, оновити розклад, вийти.</p>
 */
public class MainActivity extends Activity {

  private static final DateTimeFormatter STAMP_FORMAT =
      DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

  /** Дата в повідомленні про оновлення. */
  private static final DateTimeFormatter DATE_FORMAT =
      DateTimeFormatter.ofPattern("dd.MM.yyyy");

  /** Код запиту дозволу на повідомлення (Android 13+). */
  private static final int REQUEST_NOTIFICATIONS = 1;

  /** Адреса сервера розкладу. */
  private String domen;

  /** Дані користувача, для якого завантажується розклад. */
  private UserData data;

  /** Останній завантажений розклад. */
  private Schedule schedule;

  private TextView status;
  private Button updateButton;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    status = findViewById(R.id.status);
    updateButton = findViewById(R.id.btn_update_schedule);

    findViewById(R.id.btn_show_schedule).setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        if (schedule == null) {
          toast("No schedule yet - press Update Schedule");
          return;
        }
        new ShowSchedule(MainActivity.this).show(schedule);
      }
    });

    findViewById(R.id.btn_input_user_data).setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        inputUserData();
      }
    });

    findViewById(R.id.btn_input_url).setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        inputDomen();
      }
    });

    updateButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        updateSchedule();
      }
    });

    findViewById(R.id.btn_exit).setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        finishAffinity();
      }
    });

    loader();
    draving();

    ReminderReceiver.scheduleDaily(this);
    requestNotificationPermission();
    warnIfNotificationsDisabled();
  }

  // ------------------------------------------------------------------
  // Дані
  // ------------------------------------------------------------------

  /** Підтягує збережені дані з локального сховища. */
  private void loader() {
    domen = Storage.loadDomen(this);
    data = Student.fromValues(Storage.loadUserData(this));
    schedule = Storage.loadSchedule(this);
  }

  /** Перемальовує рядок стану під кнопками. */
  private void draving() {
    StringBuilder text = new StringBuilder();
    text.append("Server: ").append(isEmpty(domen) ? "not set" : domen).append('\n');

    if (data == null) {
      text.append("User data: not set");
    } else {
      text.append(data.describe());
    }

    text.append('\n');
    if (schedule == null) {
      text.append("Schedule: not loaded");
    } else {
      int count = schedule.sched == null ? 0 : schedule.sched.size();
      text.append("Schedule: ").append(count)
          .append(count == 1 ? " lesson" : " lessons");
      if (schedule.updateTime != null) {
        text.append(", updated ").append(STAMP_FORMAT.format(schedule.updateTime));
      }
    }

    status.setText(text.toString());
  }

  // ------------------------------------------------------------------
  // Дії кнопок
  // ------------------------------------------------------------------

  private void inputUserData() {
    new Thread(new Runnable() {
      @Override
      public void run() {
        final UserData entered = new InputUserData(MainActivity.this).input();
        runOnUiThread(new Runnable() {
          @Override
          public void run() {
            if (entered == null) {
              return;
            }
            data = entered;
            Storage.saveUserData(MainActivity.this, entered.toValues());
            draving();
            toast("User data saved");
          }
        });
      }
    }, "input-user-data").start();
  }

  private void inputDomen() {
    new Thread(new Runnable() {
      @Override
      public void run() {
        final String entered = new InputDomen(MainActivity.this).input();
        runOnUiThread(new Runnable() {
          @Override
          public void run() {
            if (entered == null) {
              return;
            }
            domen = entered;
            Storage.saveDomen(MainActivity.this, entered);
            draving();
            toast("Server URL saved");
          }
        });
      }
    }, "input-domen").start();
  }

  private void updateSchedule() {
    if (isEmpty(domen)) {
      toast("Enter the server URL first");
      return;
    }
    if (data == null) {
      toast("Enter user data first");
      return;
    }

    final String requestDomen = domen;
    final UserData requestData = data;
    // Після закінчення пар кнопка теж підтягує наступний день.
    final LocalDate targetDay = ReminderReceiver.targetDay(schedule);

    updateButton.setEnabled(false);
    status.setText("Loading schedule...");

    new Thread(new Runnable() {
      @Override
      public void run() {
        try {
          RetrieveSchedule retriever = new RetrieveSchedule();
          GetStudySchedule source = new GetStudySchedule(requestData);
          final Schedule fetched = targetDay.equals(LocalDate.now())
              ? retriever.retrieve(requestDomen, source)
              : retriever.retrieve(requestDomen, source, targetDay);
          runOnUiThread(new Runnable() {
            @Override
            public void run() {
              schedule = fetched;
              Storage.saveSchedule(MainActivity.this, schedule);
              ReminderReceiver.scheduleReminders(MainActivity.this, schedule);
              updateButton.setEnabled(true);
              draving();
              int count = schedule.sched == null ? 0 : schedule.sched.size();
              String day = targetDay.equals(LocalDate.now())
                  ? ""
                  : " for " + DATE_FORMAT.format(targetDay);
              toast("Schedule updated" + day + ": " + count
                  + (count == 1 ? " lesson" : " lessons"));
            }
          });
        } catch (final RuntimeException e) {
          runOnUiThread(new Runnable() {
            @Override
            public void run() {
              updateButton.setEnabled(true);
              draving();
              toast("Update failed: " + e.getMessage());
            }
          });
        }
      }
    }, "update-schedule").start();
  }

  // ------------------------------------------------------------------
  // Дрібне
  // ------------------------------------------------------------------

  private boolean isEmpty(String value) {
    return value == null || value.trim().isEmpty();
  }

  /** Android 13+ вимагає явного дозволу на показ повідомлень. */
  private void requestNotificationPermission() {
    if (Build.VERSION.SDK_INT >= 33
        && checkSelfPermission("android.permission.POST_NOTIFICATIONS")
            != PackageManager.PERMISSION_GRANTED) {
      requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},
          REQUEST_NOTIFICATIONS);
    }
  }

  /** На Android 12 і нижче дозвіл видається в налаштуваннях, а не запитом. */
  private void warnIfNotificationsDisabled() {
    if (Build.VERSION.SDK_INT >= 33) {
      return;
    }
    NotificationManager manager = (NotificationManager)
        getSystemService(NOTIFICATION_SERVICE);
    if (manager != null && !manager.areNotificationsEnabled()) {
      toast("Notifications are disabled: reminders will not be shown");
    }
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, String[] permissions,
      int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    if (requestCode == REQUEST_NOTIFICATIONS
        && (grantResults.length == 0
            || grantResults[0] != PackageManager.PERMISSION_GRANTED)) {
      toast("Notifications are disabled: reminders will not be shown");
    }
  }

  private void toast(String text) {
    Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
  }
}
