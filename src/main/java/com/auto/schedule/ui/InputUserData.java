package com.auto.schedule.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Typeface;
import android.os.Looper;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import com.auto.schedule.R;
import com.auto.schedule.data.Storage;
import com.auto.schedule.model.UserData;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Введення даних користувача (факультет, курс, група) через діалог.
 *
 * <p>Діалог показується в UI-потоці, а {@link #input()} повертає результат
 * синхронно, тому викликати його треба з фонового потоку. Якщо викликати
 * з UI-потоку, {@link #input()} кине {@link IllegalStateException}, щоб
 * застосунок не завис.</p>
 */
public class InputUserData implements Inputinfo<UserData> {

  /** Скільки чекати на відповідь користувача, перш ніж здатися. */
  private static final long WAIT_MINUTES = 5;

  private final Activity activity;

  public InputUserData(Activity activity) {
    this.activity = activity;
  }

  @Override
  public UserData input() {
    if (Looper.myLooper() == Looper.getMainLooper()) {
      throw new IllegalStateException(
          "InputUserData.input() не можна викликати з UI-потоку: діалог його заблокує");
    }

    final UserData[] result = new UserData[1];
    final CountDownLatch latch = new CountDownLatch(1);

    activity.runOnUiThread(new Runnable() {
      @Override
      public void run() {
        showDialog(result, latch);
      }
    });

    try {
      if (!latch.await(WAIT_MINUTES, TimeUnit.MINUTES)) {
        return null;
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return null;
    }
    return result[0];
  }

  private void showDialog(final UserData[] result, final CountDownLatch latch) {
    if (activity.isFinishing()) {
      latch.countDown();
      return;
    }

    View content = activity.getLayoutInflater().inflate(R.layout.dialog_input_user_data, null);
    final EditText faculty = content.findViewById(R.id.input_faculty);
    final EditText course = content.findViewById(R.id.input_course);
    final EditText group = content.findViewById(R.id.input_group);

    UserData current = Storage.loadUserData(activity);
    if (current != null) {
      faculty.setText(current.faculty);
      course.setText(String.valueOf(current.studyYear));
      group.setText(current.group);
    }

    final AlertDialog dialog = new AlertDialog.Builder(activity, R.style.AutoSchedule_DialogTheme)
        .setTitle(R.string.dialog_user_data_title)
        .setView(content)
        .setPositiveButton(R.string.action_save, null)
        .setNegativeButton(R.string.action_cancel, null)
        .create();

    dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
      @Override
      public void onDismiss(DialogInterface dismissed) {
        latch.countDown();
      }
    });

    dialog.setOnShowListener(new DialogInterface.OnShowListener() {
      @Override
      public void onShow(DialogInterface shown) {
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTypeface(Typeface.MONOSPACE);
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTypeface(Typeface.MONOSPACE);

        dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            .setOnClickListener(new View.OnClickListener() {
              @Override
              public void onClick(View view) {
                String facultyText = faculty.getText().toString().trim();
                String groupText = group.getText().toString().trim();
                int courseValue = parseCourse(course.getText().toString().trim());

                if (facultyText.isEmpty()) {
                  message("Faculty is required");
                  return;
                }
                if (courseValue < 1) {
                  message("Course must be a number from 1 to 6");
                  return;
                }
                if (groupText.isEmpty()) {
                  message("Group is required");
                  return;
                }

                result[0] = new UserData(facultyText, courseValue, groupText);
                dialog.dismiss();
              }
            });
      }
    });

    dialog.show();
  }

  private int parseCourse(String value) {
    try {
      int course = Integer.parseInt(value);
      return course >= 1 && course <= 6 ? course : -1;
    } catch (NumberFormatException e) {
      return -1;
    }
  }

  private void message(String text) {
    Toast.makeText(activity, text, Toast.LENGTH_SHORT).show();
  }
}
