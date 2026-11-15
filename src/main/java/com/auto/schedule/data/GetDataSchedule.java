package com.auto.schedule.data;

/**
 * Джерело HTML-сторінки розкладу.
 *
 * <p>Інтерфейс відв'язує {@link RetrieveSchedule} від конкретного способу
 * отримання даних: зараз це {@link GetStudySchedule} (HTTP-запит до сервера
 * «ПС-Розклад»), але можна підставити будь-яку іншу реалізацію — наприклад,
 * читання з кешу або тестову заглушку.</p>
 */
public interface GetDataSchedule {

  /**
   * Повертає HTML-сторінку з розкладом.
   *
   * @param domen адреса сервера з розкладом
   * @return HTML-сторінка у вигляді рядка
   */
  String get(String domen);
}
