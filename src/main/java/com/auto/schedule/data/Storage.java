package com.auto.schedule.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.auto.schedule.model.Lesson;
import com.auto.schedule.model.Schedule;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Локальне сховище застосунку.
 *
 * <p>Клас не має власних полів: усі дані лежать у {@link SharedPreferences}
 * (ключі {@code domen}, {@code userData}, {@code schedule}). Дані користувача
 * зберігаються як впорядкований список рядків
 * {@code [факультет, курс, група]}, розклад — як JSON.</p>
 *
 * <p>{@link Context} передається в кожен метод — саме тому клас не тримає
 * посилання на {@code Context} і не створює витоків пам'яті.</p>
 */
public final class Storage {

  private static final String PREFS_NAME = "auto_schedule";

  private static final String KEY_DOMEN = "domen";
  private static final String KEY_USER_DATA = "userData";
  private static final String KEY_SCHEDULE = "schedule";

  /** Кількість полів у списку даних користувача. */
  private static final int USER_DATA_SIZE = 3;

  private Storage() {
    // утилітарний клас: лише статичні методи
  }

  // ------------------------------------------------------------------
  // Домен (адреса сервера з розкладом)
  // ------------------------------------------------------------------

  public static void saveDomen(Context context, String domen) {
    prefs(context).edit().putString(KEY_DOMEN, domen).apply();
  }

  /** @return збережений домен або {@code null}, якщо його ще не задано */
  public static String loadDomen(Context context) {
    return prefs(context).getString(KEY_DOMEN, null);
  }

  // ------------------------------------------------------------------
  // Дані користувача: [факультет, курс, група]
  // ------------------------------------------------------------------

  /**
   * Зберігає дані користувача.
   *
   * @param data список {@code [факультет, курс, група]}; {@code null} очищає запис
   */
  public static void saveUserData(Context context, List<String> data) {
    if (data == null) {
      prefs(context).edit().remove(KEY_USER_DATA).apply();
      return;
    }

    prefs(context).edit()
        .putString(KEY_USER_DATA, new JSONArray(data).toString())
        .apply();
  }

  /**
   * @return збережені дані користувача у вигляді {@code [факультет, курс, група]}
   *         або {@code null}, якщо їх ще немає
   */
  public static List<String> loadUserData(Context context) {
    String raw = prefs(context).getString(KEY_USER_DATA, null);
    if (raw == null) {
      return null;
    }

    try {
      JSONArray json = new JSONArray(raw);
      if (json.length() < USER_DATA_SIZE) {
        return null;
      }
      List<String> data = new ArrayList<>(USER_DATA_SIZE);
      for (int i = 0; i < USER_DATA_SIZE; i++) {
        data.add(json.isNull(i) ? null : json.optString(i));
      }
      return data;
    } catch (JSONException | RuntimeException e) {
      // пошкоджений запис трактуємо як відсутній
      return null;
    }
  }

  // ------------------------------------------------------------------
  // Розклад
  // ------------------------------------------------------------------

  public static void saveSchedule(Context context, Schedule schedule) {
    if (schedule == null) {
      prefs(context).edit().remove(KEY_SCHEDULE).apply();
      return;
    }

    prefs(context).edit()
        .putString(KEY_SCHEDULE, scheduleToJson(schedule).toString())
        .apply();
  }

  /** @return збережений розклад або {@code null}, якщо його ще немає */
  public static Schedule loadSchedule(Context context) {
    String raw = prefs(context).getString(KEY_SCHEDULE, null);
    if (raw == null) {
      return null;
    }

    try {
      return scheduleFromJson(new JSONObject(raw));
    } catch (JSONException | RuntimeException e) {
      return null;
    }
  }

  // ------------------------------------------------------------------
  // Внутрішнє: доступ до SharedPreferences
  // ------------------------------------------------------------------

  private static SharedPreferences prefs(Context context) {
    return context.getApplicationContext()
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
  }

  // ------------------------------------------------------------------
  // Внутрішнє: Schedule <-> JSON
  // ------------------------------------------------------------------

  private static JSONObject scheduleToJson(Schedule schedule) {
    JSONObject json = new JSONObject();
    try {
      json.put("updateTime", timeToString(schedule.updateTime));

      JSONArray lessons = new JSONArray();
      if (schedule.sched != null) {
        for (Lesson lesson : schedule.sched) {
          lessons.put(lessonToJson(lesson));
        }
      }
      json.put("sched", lessons);
    } catch (JSONException e) {
      throw new IllegalStateException("Не вдалося серіалізувати Schedule", e);
    }
    return json;
  }

  private static Schedule scheduleFromJson(JSONObject json) {
    ZonedDateTime updateTime = timeFromString(
        json.isNull("updateTime") ? null : json.optString("updateTime"));

    JSONArray lessons = json.optJSONArray("sched");
    List<Lesson> sched = new ArrayList<>();
    if (lessons != null) {
      for (int i = 0; i < lessons.length(); i++) {
        JSONObject lesson = lessons.optJSONObject(i);
        if (lesson != null) {
          sched.add(lessonFromJson(lesson));
        }
      }
    }

    return new Schedule(updateTime, sched);
  }

  // ------------------------------------------------------------------
  // Внутрішнє: Lesson <-> JSON
  // ------------------------------------------------------------------

  private static JSONObject lessonToJson(Lesson lesson) {
    JSONObject json = new JSONObject();
    try {
      json.put("id", lesson.id);
      json.put("subject", lesson.subject);
      json.put("startsAt", timeToString(lesson.startsAt));
      json.put("endsAt", timeToString(lesson.endsAt));
      json.put("group", lesson.group);
      json.put("teacher", lesson.teacher);
    } catch (JSONException e) {
      throw new IllegalStateException("Не вдалося серіалізувати Lesson", e);
    }
    return json;
  }

  private static Lesson lessonFromJson(JSONObject json) {
    return new Lesson(
        json.isNull("id") ? null : json.optString("id"),
        json.isNull("subject") ? null : json.optString("subject"),
        timeFromString(json.isNull("startsAt") ? null : json.optString("startsAt")),
        timeFromString(json.isNull("endsAt") ? null : json.optString("endsAt")),
        json.isNull("group") ? null : json.optString("group"),
        json.isNull("teacher") ? null : json.optString("teacher"));
  }

  // ------------------------------------------------------------------
  // Внутрішнє: ZonedDateTime <-> ISO-8601
  // ------------------------------------------------------------------

  private static String timeToString(ZonedDateTime time) {
    return time == null ? null : time.toString();
  }

  private static ZonedDateTime timeFromString(String value) {
    if (value == null || value.trim().isEmpty()) {
      return null;
    }
    return ZonedDateTime.parse(value);
  }
}
