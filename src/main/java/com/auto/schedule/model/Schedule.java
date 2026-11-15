package com.auto.schedule.model;

import java.time.ZonedDateTime;
import java.util.List;

public class Schedule {

  public ZonedDateTime updateTime;
  public List<Lesson> sched;

  public Schedule() {
    this(null, null);
  }

  public Schedule(ZonedDateTime updateTime, List<Lesson> sched) {
    this.updateTime = updateTime;
    this.sched = sched;
  }

}
