package com.auto.schedule.data;

import com.auto.schedule.model.UserData;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Виконує HTTP-запит до сервера розкладу та повертає HTML-сторінку.
 *
 * <p>Сервер «ПС-Розклад v.5.4» приймає звичайну HTML-форму
 * ({@code POST /timetable.cgi?n=700}) з полями {@code faculty}, {@code teacher},
 * {@code course}, {@code group}, {@code sdate}, {@code edate}, {@code n}.
 * Сторінка віддається у кодуванні <b>windows-1251</b>, тому і параметри
 * форми, і відповідь кодуються/декодуються саме в ньому.</p>
 *
 * <p>{@link UserData#faculty} може містити як числовий id факультету
 * ({@code "1015"}), так і його назву — назва автоматично зіставляється зі
 * списком {@code <select name="faculty">} на сторінці форми.</p>
 *
 * <p>Мережеві виклики не можна робити в головному потоці Android — викликайте
 * {@code get(...)} з фонового потоку.</p>
 */
public class GetDataSchedule {

  /** Кодування сервера (у html: {@code <meta charset="windows-1251">}). */
  private static final String SERVER_CHARSET = "windows-1251";

  /** Шлях сторінки з формою та результатом (n=700 — «Розклад занять»). */
  private static final String TIMETABLE_PATH = "/timetable.cgi?n=700";

  /** Код сторінки розкладу в параметрі {@code n}. */
  private static final String TIMETABLE_PAGE = "700";

  private static final int TIMEOUT_MS = 20000;

  /** Пункт списку факультетів у формі: {@code <option value="1015">Назва</option>}. */
  private static final Pattern FACULTY_OPTION = Pattern.compile(
      "<option\\s+value=\"(\\d+)\"[^>]*>(.*?)</option>",
      Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

  /**
   * Надсилає дані користувача на сервер і повертає HTML-сторінку розкладу.
   *
   * @param data  факультет, курс і група, які вводить користувач
   * @param domen адреса сервера, наприклад {@code https://asu.ifntung.edu.ua}
   * @return HTML-сторінка у вигляді рядка (вже декодована у Unicode)
   * @throws IllegalArgumentException якщо домен або дані не задані
   * @throws IllegalStateException    якщо сервер недоступний або повернув помилку
   */
  public String get(UserData data, String domen) {
    if (data == null) {
      throw new IllegalArgumentException("Дані користувача не задані");
    }

    String url = buildUrl(domen);
    byte[] payload = buildBody(data, url).getBytes(Charset.forName(SERVER_CHARSET));

    HttpURLConnection connection = null;
    try {
      connection = (HttpURLConnection) new URL(url).openConnection();
      connection.setRequestMethod("POST");
      connection.setConnectTimeout(TIMEOUT_MS);
      connection.setReadTimeout(TIMEOUT_MS);
      connection.setDoOutput(true);
      connection.setDoInput(true);
      connection.setUseCaches(false);
      connection.setRequestProperty("Content-Type",
          "application/x-www-form-urlencoded; charset=" + SERVER_CHARSET);
      connection.setRequestProperty("Accept", "text/html");
      connection.setRequestProperty("Accept-Charset", SERVER_CHARSET);
      connection.setFixedLengthStreamingMode(payload.length);

      try (OutputStream out = connection.getOutputStream()) {
        out.write(payload);
        out.flush();
      }

      int status = connection.getResponseCode();
      if (status != HttpURLConnection.HTTP_OK) {
        throw new IllegalStateException("Сервер повернув HTTP " + status + " (" + url + ")");
      }

      return readResponse(connection);
    } catch (IOException e) {
      throw new IllegalStateException("Не вдалося отримати розклад з " + url, e);
    } finally {
      if (connection != null) {
        connection.disconnect();
      }
    }
  }

  // ------------------------------------------------------------------
  // Внутрішнє
  // ------------------------------------------------------------------

  /**
   * Нормалізує домен до повної адреси сторінки розкладу.
   * Приймає {@code asu.ifntung.edu.ua}, {@code http://host}, {@code https://host/}
   * або вже готове посилання {@code .../timetable.cgi?n=700}.
   */
  private String buildUrl(String domen) {
    if (domen == null || domen.trim().isEmpty()) {
      throw new IllegalArgumentException("Домен не задано");
    }

    String base = domen.trim();
    if (!base.startsWith("http://") && !base.startsWith("https://")) {
      base = "http://" + base;
    }
    while (base.endsWith("/")) {
      base = base.substring(0, base.length() - 1);
    }

    if (base.contains("timetable.cgi")) {
      return base + (base.contains("?") ? "&" : "?") + "n=" + TIMETABLE_PAGE;
    }
    return base + TIMETABLE_PATH;
  }

  /** Тіло POST-запиту у форматі HTML-форми (application/x-www-form-urlencoded). */
  private String buildBody(UserData data, String url) {
    StringBuilder body = new StringBuilder();
    appendField(body, "faculty", resolveFaculty(url, data.faculty));
    appendField(body, "teacher", "");
    appendField(body, "course", String.valueOf(data.studyYear));
    appendField(body, "group", data.group);
    appendField(body, "sdate", "");
    appendField(body, "edate", "");
    appendField(body, "n", TIMETABLE_PAGE);
    return body.toString();
  }

  private void appendField(StringBuilder body, String name, String value) {
    if (body.length() > 0) {
      body.append('&');
    }
    body.append(name).append('=').append(urlEncode(value));
  }

  /** Кодує значення у windows-1251 (кирилиця в назві групи). */
  private String urlEncode(String value) {
    if (value == null) {
      return "";
    }
    try {
      return URLEncoder.encode(value, SERVER_CHARSET);
    } catch (UnsupportedEncodingException e) {
      throw new IllegalStateException("Кодування " + SERVER_CHARSET + " недоступне", e);
    }
  }

  /**
   * Перетворює назву факультету на його числовий id, який очікує форма сервера.
   *
   * <p>{@link UserData#faculty} може містити як готовий id ({@code "1015"}),
   * так і назву ({@code "Факультет інформаційних технологій"}). Для назви
   * завантажуємо сторінку з формою (той самий {@code n=700}) і шукаємо
   * відповідний пункт {@code <select name="faculty">}. Якщо зіставити не
   * вдалося — надсилаємо введене значення як є, щоб не ламати запит.</p>
   */
  private String resolveFaculty(String url, String faculty) {
    if (faculty == null) {
      return "";
    }

    String value = faculty.trim();
    if (value.isEmpty() || value.matches("\\d+")) {
      return value;
    }

    String formPage = getFormPage(url);
    if (formPage == null) {
      return value;
    }

    String needle = value.toLowerCase();
    Matcher matcher = FACULTY_OPTION.matcher(formPage);
    while (matcher.find()) {
      String label = cleanText(matcher.group(2)).toLowerCase();
      if (label.equals(needle) || label.startsWith(needle)) {
        return matcher.group(1);
      }
    }
    return value;
  }

  /** GET-запит сторінки з формою; {@code null}, якщо її не вдалося отримати. */
  private String getFormPage(String url) {
    HttpURLConnection connection = null;
    try {
      connection = (HttpURLConnection) new URL(url).openConnection();
      connection.setRequestMethod("GET");
      connection.setConnectTimeout(TIMEOUT_MS);
      connection.setReadTimeout(TIMEOUT_MS);
      connection.setUseCaches(false);
      connection.setRequestProperty("Accept", "text/html");
      connection.setRequestProperty("Accept-Charset", SERVER_CHARSET);

      if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
        return null;
      }
      return readResponse(connection);
    } catch (IOException e) {
      return null;
    } finally {
      if (connection != null) {
        connection.disconnect();
      }
    }
  }

  /** Прибирає теги та HTML-сутності з підпису {@code <option>}. */
  private String cleanText(String value) {
    if (value == null) {
      return "";
    }
    String text = value.replaceAll("<[^>]*>", " ")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'");
    return text.replaceAll("\\s+", " ").trim();
  }

  /** Читає тіло відповіді, поважаючи charset із Content-Type (fallback — windows-1251). */  private String readResponse(HttpURLConnection connection) throws IOException {
    Charset charset = resolveCharset(connection.getContentType());

    try (InputStream in = connection.getInputStream()) {
      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      byte[] chunk = new byte[8192];
      int read;
      while ((read = in.read(chunk)) != -1) {
        buffer.write(chunk, 0, read);
      }
      return new String(buffer.toByteArray(), charset);
    }
  }

  private Charset resolveCharset(String contentType) {
    if (contentType != null) {
      int index = contentType.toLowerCase().indexOf("charset=");
      if (index >= 0) {
        String name = contentType.substring(index + "charset=".length()).trim();
        int semicolon = name.indexOf(';');
        if (semicolon >= 0) {
          name = name.substring(0, semicolon);
        }
        name = name.replace("\"", "").trim();
        try {
          return Charset.forName(name);
        } catch (RuntimeException ignored) {
          // невідоме кодування — беремо кодування сервера
        }
      }
    }
    return StandardCharsets.UTF_8.name().equals(SERVER_CHARSET)
        ? StandardCharsets.UTF_8
        : Charset.forName(SERVER_CHARSET);
  }
}
