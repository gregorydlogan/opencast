/**
 * Licensed to The Apereo Foundation under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 *
 * The Apereo Foundation licenses this file to you under the Educational
 * Community License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License
 * at:
 *
 *   http://opensource.org/licenses/ecl2.txt
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations under
 * the License.
 *
 */
package org.opencastproject.scheduler.api;

import static org.junit.Assert.assertEquals;

import net.fortuna.ical4j.model.Period;
import net.fortuna.ical4j.model.property.RRule;

import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

public class UtilTests {
  private static final Logger logger = LoggerFactory.getLogger(UtilTests.class);
  /**
   * Tests for the method calculatePeriods
   */
  private final TimeZone utc = TimeZone.getTimeZone("UTC");
  private final TimeZone jst = TimeZone.getTimeZone("JST"); // Japan Standard Time (UTC +9)
  private final TimeZone pst = TimeZone.getTimeZone("PST"); // Alaska Standard Time (UTC -8)
  private final TimeZone cet = TimeZone.getTimeZone("CET"); // Alaska Standard Time (UTC +2)

  @Before
  public void setUp() {

  }

  @Test
  public void calculateDaysChange() throws ParseException {
    Calendar start;
    Calendar end;
    long durationMillis;
    String days;
    List<Period> periods;

    // JST - does not observe DST at all
    start = Calendar.getInstance(jst);
    start.set(2016, 2, 25, 22, 0);
    end = Calendar.getInstance(jst);
    end.set(2016, 2, 29, start.get(Calendar.HOUR_OF_DAY), 5);
    durationMillis = (end.get(Calendar.MINUTE) - start.get(Calendar.MINUTE)) * 60 * 1000;
    days = "MO,TU,FR,SA,SU"; // --> Still the same day when switch to UTC (22-9)
    periods = generatePeriods(jst, start, end, days, durationMillis);
    assertEquals(5, periods.size());

    // PST - Switches on 2016-03-13 -> this does not go over a DST switchover
    start = Calendar.getInstance(pst);
    start.set(2016, 2, 25, 22, 0);
    end = Calendar.getInstance(pst);
    end.set(2016, 2, 29, start.get(Calendar.HOUR_OF_DAY), 5);
    durationMillis = (end.get(Calendar.MINUTE) - start.get(Calendar.MINUTE)) * 60 * 1000;
    days = "MO,TU,WE,SA,SU"; // --> A day after when switching to UTC (22+8)
    periods = generatePeriods(pst, start, end, days, durationMillis);
    assertEquals(5, periods.size());

    // CET - Switches on 2016-03-27 -> this goes over a DST switchover
    start = Calendar.getInstance(cet);
    start.set(2016, Calendar.MARCH, 24, 12, 5);
    end = Calendar.getInstance(cet);
    end.set(2016, Calendar.MARCH, 30, start.get(Calendar.HOUR_OF_DAY), 10);
    durationMillis = (end.get(Calendar.MINUTE) - start.get(Calendar.MINUTE)) * 60 * 1000;
    days = "MO,TH,FR,SA,SU"; // --> A day before when switch to UCT (0-2)
    periods = generatePeriods(cet, start, end, days, durationMillis);
    assertEquals(5, periods.size());
  }

  @Test
  public void calculateDSTSpringForwardChange() throws ParseException {
    Calendar start;
    Calendar end;
    long durationMillis;
    String days;

    // CET->CEST test (March 25 is CET->CEST)
    TimeZone cetCest = TimeZone.getTimeZone("Europe/Berlin");

    //On Sunday, March 27, 2:00 am CET->CEST
    start = Calendar.getInstance(cetCest);
    start.set(2016, Calendar.MARCH, 18, 0, 5);
    end = Calendar.getInstance(cetCest);
    end.set(2016, Calendar.APRIL, 7, start.get(Calendar.HOUR_OF_DAY), 10);
    durationMillis = (end.get(Calendar.MINUTE) - start.get(Calendar.MINUTE)) * 60 * 1000;
    days = "MO,TU,WE,TH,FR,SA,SU";
    doDSTChangeOverTest(cetCest, start, end, days, durationMillis);
  }

  @Test
  public void calculateDSTFallBackChange() throws ParseException {
    Calendar start;
    Calendar end;
    long durationMillis;
    String days;
    TimeZone cetCest = TimeZone.getTimeZone("Europe/Berlin");

    //On Sunday, October 30, 3:00 am, CEST->CET
    start = Calendar.getInstance(cetCest);
    start.set(2016, Calendar.OCTOBER, 20, 0, 5);
    end = Calendar.getInstance(cetCest);
    end.set(2016, Calendar.NOVEMBER, 8, start.get(Calendar.HOUR_OF_DAY), 10);
    durationMillis = (end.get(Calendar.MINUTE) - start.get(Calendar.MINUTE)) * 60 * 1000;
    days = "MO,TU,WE,TH,FR,SA,SU";
    doDSTChangeOverTest(cetCest, start, end, days, durationMillis);
  }

  /**
   * Call with fallback and spring forward scenarios
   * 
   * @param tz
   * @param start
   * @param end
   * @param days
   * @param durationMillis
   * @throws ParseException 
   */
  private void doDSTChangeOverTest(TimeZone tz, Calendar start,
          Calendar end, String days, long durationMillis) throws ParseException  {
    SimpleDateFormat simpleDateFormat = new SimpleDateFormat("EE MMM dd HH:mm:ss zzz yyyy");
    simpleDateFormat.setTimeZone(tz);

    List<Period> periods = generatePeriods(cet, start, end, days, durationMillis);
    for (Period p : periods) {
      logger.debug(p.toString());
    }
    assertEquals("Incorrect number of scheduled events!", 20, periods.size());
    for (Period d : periods) {
      Calendar instance = Calendar.getInstance(tz);
      long time = d.getStart().getTime();
      instance.setTime(new Date(time));
      logger.debug("Instance {}, calendar hour {}, calendar min {}, zone {}",
              simpleDateFormat.format(instance.getTime()),
              instance.get(Calendar.HOUR_OF_DAY),
              instance.get(Calendar.MINUTE),
              instance.getTimeZone().getID());
      assertEquals("Incorrect start hour", 0, instance.get(Calendar.HOUR_OF_DAY));
      logger.info("Correct start time " + instance.getTime());
    }
  }

  private List<Period> generatePeriods(TimeZone tz, Calendar start, Calendar end, String days, Long duration)
          throws ParseException {
    Calendar utcDate = Calendar.getInstance(utc);
    start.setTimeZone(tz);
    end.setTimeZone(tz);
    utcDate.setTime(start.getTime());
    RRule rRule = new RRule(generateRule(days, utcDate.get(Calendar.HOUR_OF_DAY), utcDate.get(Calendar.MINUTE)));
    return Util.calculatePeriods(start.getTime(), end.getTime(), duration, rRule, tz);
  }

  private String generateRule(String days, int hour, int minute) {
    return String.format("FREQ=WEEKLY;BYDAY=%s;BYHOUR=%d;BYMINUTE=%d", days, hour, minute);
  }
}
