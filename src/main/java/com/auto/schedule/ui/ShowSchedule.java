package com.auto.schedule.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.auto.schedule.R;
import com.auto.schedule.model.Lesson;
import com.auto.schedule.model.Schedule;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Показує розклад у повноекранному діалозі в термінальному стилі:
 * біле вікно з чорною рамкою і чорним текстом.
 *
 * <p>{@link Schedule} містить заняття лише за один день, тому вони просто
 * сортуються за часом початку. Для кожного заняття виводяться час, назва
 * (разом із типом і аудиторією) та викладач.</p>
 */
public class ShowSchedule {

  private static final DateTimeFormatter DATE_FORMAT =
      DateTimeFormatter.ofPattern("dd.MM.yyyy");

  private static final DateTimeFormatter TIME_FORMAT =
      DateTimeFormatter.ofPattern("HH:mm");

  private static final DateTimeFormatter STAMP_FORMAT =
      DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

  private final Activity activity;

  public ShowSchedule(Activity activity) {
    this.activity = activity;
  }

  /**
   * Відкриває діалог із розкладом.
   *
   * @param schedule розклад, який треба показати; {@code null} ігнорується
   */
  public void show(Schedule schedule) {
    if (schedule == null) {
      return;
    }

    View content = activity.getLayoutInflater().inflate(R.layout.dialog_schedule, null);
    TextView updated = content.findViewById(R.id.schedule_updated);
    LinearLayout container = content.findViewById(R.id.schedule_container);

    List<Lesson> lessons = new ArrayList<>();
    if (schedule.sched != null) {
      lessons.addAll(schedule.sched);
    }
    Collections.sort(lessons, BY_START);

    updated.setText(subtitle(schedule, lessons.size()));

    if (lessons.isEmpty()) {
      TextView empty = new TextView(activity);
      empty.setText(R.string.msg_no_lessons);
      empty.setTextColor(activity.getColor(R.color.text_dim_on_light));
      empty.setTypeface(updated.getTypeface());
      empty.setTextSize(16f);
      container.addView(empty);
    } else {
      LocalDate currentDay = null;
      for (Lesson lesson : lessons) {
        LocalDate day = lesson.startsAt == null ? null : lesson.startsAt.toLocalDate();
        if (day != null && !day.equals(currentDay)) {
          currentDay = day;
          container.addView(dayHeader(container, day));
        }
        container.addView(lessonRow(container, lesson));
      }
    }

    AlertDialog dialog = new AlertDialog.Builder(activity, R.style.AutoSchedule_DialogTheme)
        .setTitle(R.string.dialog_schedule_title)
        .setView(content)
        .setNegativeButton(R.string.action_close, null)
        .create();

    dialog.show();

    Window window = dialog.getWindow();
    if (window != null) {
      DisplayMetrics metrics = activity.getResources().getDisplayMetrics();
      window.setLayout((int) (metrics.widthPixels * 0.94f),
          (int) (metrics.heightPixels * 0.86f));
    }
  }

  // ------------------------------------------------------------------
  // Внутрішнє
  // ------------------------------------------------------------------

  private TextView dayHeader(LinearLayout parent, LocalDate day) {
    TextView header = (TextView) activity.getLayoutInflater()
        .inflate(R.layout.item_day_header, parent, false);
    header.setText(DATE_FORMAT.format(day));
    return header;
  }

  private View lessonRow(LinearLayout parent, Lesson lesson) {
    View row = activity.getLayoutInflater().inflate(R.layout.item_lesson, parent, false);

    TextView time = row.findViewById(R.id.lesson_time);
    TextView subject = row.findViewById(R.id.lesson_subject);
    TextView teacher = row.findViewById(R.id.lesson_teacher);

    time.setText(timeRange(lesson.startsAt, lesson.endsAt));
    subject.setText(lesson.subject == null ? "" : lesson.subject);

    if (lesson.teacher == null || lesson.teacher.trim().isEmpty()) {
      // Для розкладу викладача ім'я в клітинці не повторюється — показуємо групу.
      if (lesson.group == null || lesson.group.trim().isEmpty()) {
        teacher.setVisibility(View.GONE);
      } else {
        teacher.setText(lesson.group);
      }
    } else {
      teacher.setText(lesson.teacher);
    }
    return row;
  }

  private String timeRange(ZonedDateTime startsAt, ZonedDateTime endsAt) {
    String start = startsAt == null ? "--:--" : TIME_FORMAT.format(startsAt);
    String end = endsAt == null ? "--:--" : TIME_FORMAT.format(endsAt);
    return start + " - " + end;
  }

  private String subtitle(Schedule schedule, int count) {
    String lessons = count + " " + (count == 1 ? "lesson" : "lessons");
    if (schedule.updateTime == null) {
      return lessons;
    }
    return lessons + " | updated " + STAMP_FORMAT.format(schedule.updateTime);
  }

  private static final Comparator<Lesson> BY_START = new Comparator<Lesson>() {
    @Override
    public int compare(Lesson first, Lesson second) {
      if (first.startsAt == null && second.startsAt == null) {
        return 0;
      }
      if (first.startsAt == null) {
        return 1;
      }
      if (second.startsAt == null) {
        return -1;
      }
      return first.startsAt.compareTo(second.startsAt);
    }
  };
}
