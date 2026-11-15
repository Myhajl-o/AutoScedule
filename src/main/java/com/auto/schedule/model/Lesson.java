package com.auto.schedule.model;

import java.time.ZonedDateTime;

public class Lesson {
  public String id;
  public String subject;
  public ZonedDateTime startsAt;
  public ZonedDateTime endsAt;
  public String group;
  public String teacher;

  public Lesson() {
    this(null, null, null, null, null, null);
  }

  public Lesson(String id, String subject,
      ZonedDateTime startsAt, ZonedDateTime endsAt,
      String group, String teacher) {

    this.id = id;
    this.subject = subject;
    this.startsAt = startsAt;
    this.endsAt = endsAt;
    this.group = group;
    this.teacher = teacher;

  }

}
