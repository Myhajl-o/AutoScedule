package com.auto.schedule.ui;

/**
 * Джерело введення від користувача.
 *
 * @param <T> тип даних, які повертає {@link #input()}
 */
public interface Inputinfo<T> {

  /**
   * Показує користувачу форму введення і повертає результат.
   *
   * @return введені дані або {@code null}, якщо користувач скасував введення
   */
  T input();
}
