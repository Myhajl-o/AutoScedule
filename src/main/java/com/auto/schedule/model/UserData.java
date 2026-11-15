package com.auto.schedule.model;

import java.util.List;
import java.util.Map;

/**
 * Базові дані користувача, для якого запитується розклад.
 *
 * <p>Клас абстрактний, бо розклад можна запитувати для різних типів
 * користувачів. Спільним для всіх є факультет, а решта полів і спосіб
 * формування запиту визначаються в нащадках (зараз — {@link Student}).</p>
 *
 * <p>Завдяки абстрактним методам новий тип користувача (наприклад,
 * викладач) додається окремим класом-нащадком, а транспортний шар
 * ({@code GetStudySchedule}, {@code RetrieveSchedule}, {@code ShowSchedule})
 * не потрібно змінювати.</p>
 */
public abstract class UserData {

  /** Факультет — спільне поле для всіх типів користувачів. */
  public String faculty;

  protected UserData(String faculty) {
    this.faculty = faculty;
  }

  /**
   * Поля HTML-форми, які цей тип користувача надсилає серверу розкладу.
   *
   * <p>Ключ — ім'я поля форми, значення — його значення. Порядок полів
   * зберігається, тому доцільно повертати {@link java.util.LinkedHashMap}.</p>
   *
   * @return поля форми; ніколи не {@code null}
   */
  public abstract Map<String, String> scheduleParams();

  /**
   * Подання даних для збереження у локальному сховищі.
   *
   * <p>Нащадок, який створює цей список, уміє й відновити з нього об'єкт
   * (наприклад, {@link Student#fromValues(List)}).</p>
   *
   * @return список рядків із полями цього типу користувача
   */
  public abstract List<String> toValues();

  /**
   * Опис даних для рядка стану головного екрана.
   *
   * @return текст без завершального переводу рядка
   */
  public abstract String describe();
}
