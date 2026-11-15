package com.auto.schedule.data;

import com.auto.schedule.model.Lesson;
import com.auto.schedule.model.Schedule;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Перетворює HTML-сторінку розкладу (ПС-Розклад v.5.4) у модель {@link Schedule}.
 *
 * <p>Розбір розділений на два кроки, як у діаграмі класів:</p>
 * <ol>
 *   <li>{@link #parsing(String)} — витягує з HTML пласкі записи: один рядок
 *       на непорожню клітинку таблиці (дата, день тижня, номер пари, час
 *       початку/кінця та HTML вмісту клітинки);</li>
 *   <li>{@link #forming(List)} — з кожного запису будує об'єкти {@link Lesson}
 *       (в одній клітинці сервера може бути дві пари для різних підгруп).</li>
 * </ol>
 *
 * <p>{@link #creating(String)} просто з'єднує обидва кроки і повертає готовий
 * {@link Schedule} з часом оновлення {@code now}. У розкладі залишаються
 * заняття лише за один день — сьогоднішній, а якщо на сьогодні їх немає
 * (наприклад, у неділю), то за перший день, який є у відповіді сервера.</p>
 */
public class CreateSchedule {

  /** Розділювач полів у записі, який повертає {@link #parsing(String)}. */
  private static final char FIELD_SEPARATOR = '\t';

  private static final DateTimeFormatter DATE_FORMAT =
      DateTimeFormatter.ofPattern("dd.MM.yyyy");

  private static final DateTimeFormatter TIME_FORMAT =
      DateTimeFormatter.ofPattern("H:mm");

  /**
   * Один прохід по HTML: або заголовок дня ({@code <h4>16.11.2026 ...</h4>}),
   * або рядок таблиці з парою ({@code <tr><td>1</td><td>08:00<br>09:20</td>...}).
   */
  private static final Pattern DAY_OR_ROW = Pattern.compile(
      "<h4>\\s*(\\d{2}\\.\\d{2}\\.\\d{4})\\s*<small>(.*?)</small>\\s*</h4>"
          + "|<tr>\\s*<td>\\s*(\\d+)\\s*</td>"
          + "\\s*<td>\\s*(\\d{1,2}:\\d{2})\\s*<br\\s*/?>\\s*(\\d{1,2}:\\d{2})\\s*</td>"
          + "\\s*<td[^>]*class=\"[^\"]*\\brozklad-cell-text\\b[^\"]*\"[^>]*>(.*?)</td>\\s*</tr>",
      Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

  private static final Pattern P_NAME = span("p_name");
  private static final Pattern P_TYPE = span("p_type_name");
  private static final Pattern P_TEACHER = span("t_name");
  private static final Pattern P_ROOM = span("room_name");
  private static final Pattern P_GROUP = span("gr2_name");

  /** Заголовок дня у відповіді сервера — ознака, що прийшов саме розклад. */
  private static final Pattern DAY_HEADER = Pattern.compile(
      "<h4>\\s*\\d{2}\\.\\d{2}\\.\\d{4}\\s*<small>",
      Pattern.CASE_INSENSITIVE);

  /**
   * Розбирає HTML у список записів вигляду
   * {@code дата \t день \t номер \t початок \t кінець \t HTML клітинки}.
   *
   * @param data HTML-сторінка розкладу
   * @return записи в порядку появи на сторінці; порожні клітинки пропускаються
   */
  public List<String> parsing(String data) {
    List<String> records = new ArrayList<>();
    if (data == null || data.trim().isEmpty()) {
      return records;
    }

    String date = null;
    String dayName = "";
    Matcher matcher = DAY_OR_ROW.matcher(data);
    while (matcher.find()) {
      if (matcher.group(1) != null) {
        // Заголовок дня: далі всі рядки до наступного заголовка належать йому.
        date = matcher.group(1);
        dayName = clean(matcher.group(2));
        continue;
      }

      if (date == null) {
        continue;
      }

      String cell = matcher.group(6);
      if (cell == null || !P_NAME.matcher(cell).find()) {
        // Порожня пара («вікно») — сервер віддає клітинку з одним пробілом.
        continue;
      }

      records.add(record(date, dayName, matcher.group(3), matcher.group(4),
          matcher.group(5), cell));
    }
    return records;
  }

  /**
   * Будує заняття з записів, отриманих у {@link #parsing(String)}.
   *
   * @param param записи у форматі {@code дата \t день \t номер \t початок \t кінець \t HTML}
   * @return список занять; ніколи не {@code null}
   */
  public List<Lesson> forming(List<String> param) {
    List<Lesson> lessons = new ArrayList<>();
    if (param == null) {
      return lessons;
    }

    ZoneId zone = ZoneId.systemDefault();
    for (String raw : param) {
      if (raw == null || raw.isEmpty()) {
        continue;
      }

      String[] fields = raw.split(String.valueOf(FIELD_SEPARATOR), 6);
      if (fields.length < 6) {
        continue;
      }

      LocalDate day = parseDate(fields[0]);
      if (day == null) {
        continue;
      }

      ZonedDateTime startsAt = at(day, fields[3], zone);
      ZonedDateTime endsAt = at(day, fields[4], zone);
      List<String> blocks = splitCell(fields[5]);

      for (int i = 0; i < blocks.size(); i++) {
        String block = blocks.get(i);
        String subject = spanText(P_NAME, block);
        if (subject == null || subject.isEmpty()) {
          continue;
        }

        String type = spanText(P_TYPE, block);
        String room = spanText(P_ROOM, block);
        String teacher = spanText(P_TEACHER, block);
        String group = spanText(P_GROUP, block);
        boolean remote = block.toLowerCase().contains("remote_work");

        String id = day + "#" + fields[2] + "#" + (i + 1);
        lessons.add(new Lesson(id, composeSubject(subject, type, room, remote),
            startsAt, endsAt, group, teacher));
      }
    }
    return lessons;
  }

  /**
   * Повний цикл: HTML → {@link Schedule} з заняттями за один день.
   *
   * <p>Сервер віддає розклад на весь тиждень, але {@link Schedule} за
   * моделлю містить лише один день. Тому після розбору залишаємо заняття
   * за сьогодні, а якщо сьогодні у розкладі немає — за найближчий день,
   * який повернув сервер.</p>
   *
   * @param data HTML-сторінка розкладу
   * @return розклад із часом оновлення {@code now} та заняттями за один день
   * @throws IllegalArgumentException якщо HTML порожній
   * @throws IllegalStateException    якщо сервер повернув сторінку без розкладу
   *                                  (зазвичай — неправильні факультет, курс або група)
   */
  public Schedule creating(String data) {
    if (data == null || data.trim().isEmpty()) {
      throw new IllegalArgumentException("Сервер повернув порожню сторінку розкладу");
    }

    List<Lesson> lessons = forming(parsing(data));
    if (lessons.isEmpty() && !DAY_HEADER.matcher(data).find()) {
      // Сервер повернув форму введення замість розкладу.
      throw new IllegalStateException(
          "Сервер не повернув розклад — перевірте параметри запиту");
    }
    return new Schedule(ZonedDateTime.now(), selectDay(lessons));
  }

  /**
   * Залишає заняття лише за один день.
   *
   * <p>Спочатку пробуємо сьогоднішню дату. Якщо на сьогодні занять немає
   * (вихідний, святковий день або розклад починається з наступного
   * тижня), беремо найперший день, який є у відповіді сервера.</p>
   *
   * @param lessons усі заняття тижня
   * @return заняття за вибраний день; ніколи не {@code null}
   */
  private List<Lesson> selectDay(List<Lesson> lessons) {
    List<Lesson> selected = new ArrayList<>();
    if (lessons == null || lessons.isEmpty()) {
      return selected;
    }

    LocalDate today = LocalDate.now();
    for (Lesson lesson : lessons) {
      if (lesson.startsAt != null && today.equals(lesson.startsAt.toLocalDate())) {
        selected.add(lesson);
      }
    }
    if (!selected.isEmpty()) {
      return selected;
    }

    // Сьогодні у розкладі немає — беремо найперший день, який є.
    LocalDate firstDay = null;
    for (Lesson lesson : lessons) {
      if (lesson.startsAt == null) {
        continue;
      }
      LocalDate day = lesson.startsAt.toLocalDate();
      if (firstDay == null || day.isBefore(firstDay)) {
        firstDay = day;
      }
    }
    if (firstDay == null) {
      return selected;
    }

    for (Lesson lesson : lessons) {
      if (lesson.startsAt != null && firstDay.equals(lesson.startsAt.toLocalDate())) {
        selected.add(lesson);
      }
    }
    return selected;
  }

  // ------------------------------------------------------------------
  // Внутрішнє: розбір HTML
  // ------------------------------------------------------------------

  /** Патерн для {@code <span class="... className ...">значення</span>}. */
  private static Pattern span(String className) {
    return Pattern.compile(
        "<span[^>]*class=\"[^\"]*\\b" + className + "\\b[^\"]*\"[^>]*>(.*?)</span>",
        Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
  }

  /**
   * Ділить вміст клітинки на окремі заняття. Сервер розділяє пари в одній
   * клітинці повторним {@code <span class="p_name">}, тому ріжемо саме по них.
   * Префікс до першої назви (іконка та «дистанційно») приєднуємо до першого
   * заняття, щоб не втратити позначку дистанційної форми.
   */
  private List<String> splitCell(String cell) {
    List<String> blocks = new ArrayList<>();
    List<Integer> starts = new ArrayList<>();

    Matcher matcher = P_NAME.matcher(cell);
    while (matcher.find()) {
      starts.add(matcher.start());
    }
    if (starts.isEmpty()) {
      return blocks;
    }

    for (int i = 0; i < starts.size(); i++) {
      int from = i == 0 ? 0 : starts.get(i);
      int to = i + 1 < starts.size() ? starts.get(i + 1) : cell.length();
      blocks.add(cell.substring(from, to));
    }
    return blocks;
  }

  private String spanText(Pattern pattern, String html) {
    Matcher matcher = pattern.matcher(html);
    if (!matcher.find()) {
      return null;
    }
    String value = clean(matcher.group(1));
    return value.isEmpty() ? null : value;
  }

  /**
   * {@link Lesson} не має окремих полів для типу заняття та аудиторії,
   * тому дописуємо їх у назву: {@code "Вища математика (Пр, 4.312.ауд.)"}.
   */
  private String composeSubject(String subject, String type, String room, boolean remote) {
    List<String> details = new ArrayList<>(2);
    if (type != null) {
      details.add(type);
    }
    if (room != null) {
      details.add(room);
    } else if (remote) {
      details.add("дистанційно");
    }

    if (details.isEmpty()) {
      return subject;
    }
    return subject + " (" + String.join(", ", details) + ")";
  }

  /** Прибирає теги, HTML-сутності та зайві пробіли. */
  private String clean(String value) {
    if (value == null) {
      return "";
    }
    String text = value.replaceAll("<[^>]*>", " ");
    text = text.replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&apos;", "'");
    return text.replaceAll("\\s+", " ").trim();
  }

  private String record(String date, String dayName, String number,
      String start, String end, String cell) {
    String safeCell = cell.replace('\t', ' ').replace('\r', ' ').replace('\n', ' ');
    return date + FIELD_SEPARATOR + dayName + FIELD_SEPARATOR + number
        + FIELD_SEPARATOR + start + FIELD_SEPARATOR + end
        + FIELD_SEPARATOR + safeCell;
  }

  private LocalDate parseDate(String value) {
    try {
      return LocalDate.parse(value.trim(), DATE_FORMAT);
    } catch (RuntimeException e) {
      return null;
    }
  }

  private ZonedDateTime at(LocalDate day, String time, ZoneId zone) {
    if (time == null || time.trim().isEmpty()) {
      return null;
    }
    try {
      return day.atTime(LocalTime.parse(time.trim(), TIME_FORMAT)).atZone(zone);
    } catch (RuntimeException e) {
      return null;
    }
  }
}
