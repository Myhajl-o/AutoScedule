package com.auto.schedule.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Typeface;
import android.os.Looper;
import android.view.View;
import android.widget.EditText;

import com.auto.schedule.R;
import com.auto.schedule.data.Storage;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Введення адреси сервера розкладу через діалог.
 *
 * <p>Як і {@link InputUserData}, {@link #input()} повертає результат
 * синхронно, тому його треба викликати з фонового потоку.</p>
 */
public class InputDomen implements Inputinfo<String> {

  private static final long WAIT_MINUTES = 5;

  private final Activity activity;

  public InputDomen(Activity activity) {
    this.activity = activity;
  }

  @Override
  public String input() {
    if (Looper.myLooper() == Looper.getMainLooper()) {
      throw new IllegalStateException(
          "InputDomen.input() не можна викликати з UI-потоку: діалог його заблокує");
    }

    final String[] result = new String[1];
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

  private void showDialog(final String[] result, final CountDownLatch latch) {
    if (activity.isFinishing()) {
      latch.countDown();
      return;
    }

    View content = activity.getLayoutInflater().inflate(R.layout.dialog_input_domen, null);
    final EditText url = content.findViewById(R.id.input_url);

    String current = Storage.loadDomen(activity);
    if (current != null) {
      url.setText(current);
    }

    final AlertDialog dialog = new AlertDialog.Builder(activity, R.style.AutoSchedule_DialogTheme)
        .setTitle(R.string.dialog_url_title)
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
                String value = url.getText().toString().trim();
                if (value.isEmpty()) {
                  return;
                }
                result[0] = value;
                dialog.dismiss();
              }
            });
      }
    });

    dialog.show();
  }
}
