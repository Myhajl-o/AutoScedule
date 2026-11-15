package com.auto.schedule.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Студент: розклад запитується за факультетом, курсом і групою.
 */
public class Student extends UserData {

  public String studyYear;
  public String group;

  public Student(String faculty, String studyYear, String group) {
    super(faculty);
    this.studyYear = studyYear;
    this.group = group;
  }

  /**
   * Відновлює студента зі списку {@code [факультет, курс, група]}.
   *
   * @param values збережені дані
   * @return студент або {@code null}, якщо список відсутній чи пошкоджений
   */
  public static Student fromValues(List<String> values) {
    if (values == null || values.size() < 3) {
      return null;
    }

    String faculty = values.get(0);
    String studyYear = values.get(1);
    String group = values.get(2);
    if (faculty == null || studyYear == null || group == null) {
      return null;
    }
    return new Student(faculty, studyYear, group);
  }

  @Override
  public Map<String, String> scheduleParams() {
    Map<String, String> params = new LinkedHashMap<>();
    params.put("teacher", "");
    params.put("course", studyYear == null ? "" : studyYear);
    params.put("group", group == null ? "" : group);
    return params;
  }

  @Override
  public List<String> toValues() {
    List<String> values = new ArrayList<>(3);
    values.add(faculty);
    values.add(studyYear);
    values.add(group);
    return values;
  }

  @Override
  public String describe() {
    return "Faculty: " + faculty + "\nGroup: " + group
        + " (" + studyYear + " course)";
  }
}
