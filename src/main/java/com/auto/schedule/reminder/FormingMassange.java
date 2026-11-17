package com.auto.schedule.reminder;

import com.auto.schedule.model.Lesson;
import com.auto.schedule.model.Massage;
import com.auto.schedule.model.Schedule;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Формує нагадування за 10 хвилин до початку кожної пари.
 *
 * <p>Бере лише заняття на сьогодні, які ще не почалися. Якщо до початку
 * пари залишилося менш ніж 10 хвилин, нагадування створюється на
 * поточний момент — щоб воно прийшло одразу, а не загубилося.</p>
 */
public class FormingMassange {

  /** За скільки хвилин до початку пари нагадувати. */
  public static final int REMIND_BEFORE_MINUTES = 10;

  /** Час, який показується в тексті нагадування. */
  private static final DateTimeFormatter TIME_FORMAT =
      DateTimeFormatter.ofPattern("HH:mm");

  /**
   * Створює список нагадувань для розкладу.
   *
   * @param sched розклад на один день; {@code null} ігнорується
   * @return нагадування, відсортовані за часом; ніколи не {@code null}
   */
  public List<Massage> forming(Schedule sched) {
    List<Massage> messages = new ArrayList<>();
    if (sched == null || sched.sched == null) {
      return messages;
    }

    ZonedDateTime now = ZonedDateTime.now();
    LocalDate today = now.toLocalDate();

    for (Lesson lesson : sched.sched) {
      if (lesson == null || lesson.startsAt == null) {
        continue;
      }
      if (!today.equals(lesson.startsAt.toLocalDate())) {
        // Розклад може містити наступний день, якщо сьогодні пар немає.
        continue;
      }

      if (!lesson.startsAt.isAfter(now)) {
        // Пара вже почалася — нагадувати нема про що.
        continue;
      }

      ZonedDateTime remindAt = lesson.startsAt.minusMinutes(REMIND_BEFORE_MINUTES);
      boolean immediately = remindAt.isBefore(now);
      if (immediately) {
        // До початку менш ніж 10 хвилин — нагадуємо одразу.
        remindAt = now;
      }

      int minutesBefore = immediately
          ? minutesUntil(now, lesson.startsAt)
          : REMIND_BEFORE_MINUTES;
      messages.add(new Massage(text(lesson, minutesBefore), remindAt));
    }
    return messages;
  }

  /** @return скільки хвилин залишилося до початку, округлено вгору (мінімум 1) */
  private static int minutesUntil(ZonedDateTime now, ZonedDateTime startsAt) {
    long seconds = Duration.between(now, startsAt).getSeconds();
    return (int) Math.max(1, (seconds + 59) / 60);
  }

  /** Текст повідомлення: назва пари вже містить тип і аудиторію. */
  private String text(Lesson lesson, int minutesBefore) {
    String subject = lesson.subject == null || lesson.subject.trim().isEmpty()
        ? "Lesson"
        : lesson.subject;

    StringBuilder text = new StringBuilder()
        .append("In ").append(minutesBefore).append(" min: ")
        .append(subject)
        .append(" at ").append(TIME_FORMAT.format(lesson.startsAt));

    // Сервер не друкує групу, якщо пара саме для групи студента.
    if (lesson.group != null && !lesson.group.trim().isEmpty()) {
      text.append('\n').append("Group: ").append(lesson.group.trim());
    }
    return text.toString();
  }
}
