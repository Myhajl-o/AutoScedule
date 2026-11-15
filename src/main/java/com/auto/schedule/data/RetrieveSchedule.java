package com.auto.schedule.data;

import com.auto.schedule.model.Schedule;
import com.auto.schedule.model.UserData;

/**
 * Отримує розклад із сервера: надсилає дані користувача на домен,
 * приймає HTML-сторінку і передає її на розбір у {@link CreateSchedule}.
 *
 * <p>Мережевий запит блокує потік — викликайте {@link #retrieve(String, UserData)}
 * з фонового потоку (наприклад, через {@code Executor}), а не з UI-потоку.</p>
 */
public class RetrieveSchedule {

  /** Домен, з яким працює екземпляр (заповнюється під час {@link #retrieve}). */
  private String domen;

  public RetrieveSchedule() {
  }

  public RetrieveSchedule(String domen) {
    this.domen = domen;
  }

  /**
   * Завантажує розклад для вказаної групи.
   *
   * @param domen адреса сервера розкладу (наприклад {@code https://asu.ifntung.edu.ua})
   * @param data  факультет, курс і група користувача
   * @return готовий {@link Schedule} зі списком занять
   * @throws IllegalArgumentException якщо домен або дані не задані
   * @throws IllegalStateException    якщо сервер недоступний або HTML не вдалося розібрати
   */
  public Schedule retrieve(String domen, UserData data) {
    if (domen == null || domen.trim().isEmpty()) {
      throw new IllegalArgumentException("Домен не задано");
    }
    if (data == null) {
      throw new IllegalArgumentException("Дані користувача не задані");
    }

    this.domen = domen.trim();

    // 1. Звертаємось до сервера і отримуємо HTML.
    String html = new GetDataSchedule().get(data, this.domen);

    // 2. Розбираємо HTML у модель розкладу.
    return new CreateSchedule().creating(html);
  }

  /** Домен, з яким працює цей екземпляр (може бути {@code null}, якщо ще не заданий). */
  public String getDomen() {
    return domen;
  }
}
