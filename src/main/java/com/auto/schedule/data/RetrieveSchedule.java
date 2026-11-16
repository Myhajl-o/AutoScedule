package com.auto.schedule.data;

import com.auto.schedule.model.Schedule;

import java.time.LocalDate;

/**
 * Отримує розклад із сервера: бере HTML-сторінку через {@link GetDataSchedule}
 * і передає її на розбір у {@link CreateSchedule}.
 *
 * <p>Клас не залежить від конкретного способу отримання даних: джерело
 * передається параметром {@code getSched}, тому замість {@link GetStudySchedule}
 * можна підставити кеш або тестову заглушку.</p>
 *
 * <p>Мережевий запит блокує потік — викликайте {@link #retrieve(String,
 * GetDataSchedule)} з фонового потоку (наприклад, через {@code Executor}),
 * а не з UI-потоку.</p>
 */
public class RetrieveSchedule {

  /**
   * Завантажує розклад для вказаної групи.
   *
   * @param domen    адреса сервера розкладу (наприклад {@code https://asu.ifntung.edu.ua})
   * @param getSched джерело HTML-сторінки розкладу; не може бути {@code null}
   * @return готовий {@link Schedule} зі списком занять на один день
   * @throws IllegalArgumentException якщо домен або джерело не задані
   * @throws IllegalStateException    якщо сервер недоступний або HTML не вдалося розібрати
   */
  public Schedule retrieve(String domen, GetDataSchedule getSched) {
    return retrieve(domen, getSched, null);
  }

  /**
   * Завантажує розклад на конкретний день.
   *
   * <p>Потрібно для тихого оновлення ввечері: після занять треба отримати
   * саме наступний день, а не поточний.</p>
   *
   * @param domen    адреса сервера розкладу
   * @param getSched джерело HTML-сторінки розкладу; не може бути {@code null}
   * @param day      потрібний день або {@code null}, щоб узяти сьогодні
   * @return готовий {@link Schedule} зі списком занять за вказаний день
   * @throws IllegalArgumentException якщо домен або джерело не задані
   * @throws IllegalStateException    якщо сервер недоступний або HTML не вдалося розібрати
   */
  public Schedule retrieve(String domen, GetDataSchedule getSched, LocalDate day) {
    if (domen == null || domen.trim().isEmpty()) {
      throw new IllegalArgumentException("Домен не задано");
    }
    if (getSched == null) {
      throw new IllegalArgumentException("Джерело даних розкладу не задане");
    }

    // 1. Звертаємось до джерела і отримуємо HTML.
    String html = getSched.get(domen.trim());

    // 2. Розбираємо HTML у модель розкладу (заняття лише за один день).
    CreateSchedule creator = new CreateSchedule();
    return day == null ? creator.creating(html) : creator.creating(html, day);
  }
}
