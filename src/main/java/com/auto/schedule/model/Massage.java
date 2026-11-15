package com.auto.schedule.model;

import java.time.ZonedDateTime;

public class Massage {

  public String msg;
  public ZonedDateTime time;

  public Massage() {
    this(null, null);
  }

  public Massage(String msg, ZonedDateTime time) {
    this.msg = msg;
    this.time = time;
  }
}
