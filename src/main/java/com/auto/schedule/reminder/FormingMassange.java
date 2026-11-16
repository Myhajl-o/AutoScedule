package com.auto.schedule.reminder;

import com.auto.schedule.model.Lesson;
import com.auto.schedule.model.Massage;
import com.auto.schedule.model.Schedule;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Формує нагадування за 10 хвилин до початку кожної пари.
 *
 * <p>Бере лише заняття на сьогодні, час яких ще не минув. Якщо пара
 * починається менш ніж за 10 хвилин, нагадування не створюється — час
 * уже втрачено.</p>
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

      ZonedDateTime remindAt = lesson.startsAt.minusMinutes(REMIND_BEFORE_MINUTES);
      if (remindAt.isBefore(now)) {
        // До початку пари залишилось менше 10 хвилин (або вона вже минула).
        continue;
      }

      messages.add(new Massage(text(lesson), remindAt));
    }
    return messages;
  }

  /** Текст повідомлення: назва пари вже містить тип і аудиторію. */
  private String text(Lesson lesson) {
    String subject = lesson.subject == null || lesson.subject.trim().isEmpty()
        ? "Lesson"
        : lesson.subject;
    return "In " + REMIND_BEFORE_MINUTES + " min: " + subject
        + " at " + TIME_FORMAT.format(lesson.startsAt);
  }
}
