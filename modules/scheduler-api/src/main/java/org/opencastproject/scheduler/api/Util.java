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

import net.fortuna.ical4j.model.Period;
import net.fortuna.ical4j.model.TimeZoneRegistry;
import net.fortuna.ical4j.model.TimeZoneRegistryFactory;
import net.fortuna.ical4j.model.property.RRule;

import org.joda.time.DateTime;
import org.joda.time.DateTimeConstants;
import org.joda.time.DateTimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Calendar;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.TimeZone;

public final class Util {

  private static final Logger logger = LoggerFactory.getLogger(Util.class);

  private Util() {
  }

  /**
   * Giving a start time and end time with a recurrence rule and a timezone, all periods of the recurrence rule are
   * calculated taken daylight saving time into account.
   *
   *  NOTE: Do not modify this without making the same modifications to the copy of this method in IndexServiceImplTest
   *  I would have moved this to the scheduler-api bundle, but that would introduce a circular dependency :(
   *
   * @param startUtc
   *          the start date time  of the recurrence in UTC
   * @param endUtc
   *          the end date of the recurrence in UTC
   * @param duration
   *          the duration
   * @param rRule
   *          the recurrence rule
   * @param tz of the timeZone where this event will be scheduled, i.e. the timeZone of the capture agent
   * @return a list of scheduling periods
   */
  public static List<Period> calculatePeriods(Date startUtc, Date endUtc, long duration, RRule rRule, TimeZone tz) {
    List<Period> events = new LinkedList<>();
    TimeZoneRegistry registry = TimeZoneRegistryFactory.getInstance().createRegistry();

    logger.debug("Inbound start of recurrence {} and end of recurrence {}", startUtc, endUtc);
    DateTime startInTz = new DateTime(startUtc).toDateTime(DateTimeZone.forTimeZone(tz));
    DateTime endInTz = new DateTime(endUtc).toDateTime(DateTimeZone.forTimeZone(tz));

    net.fortuna.ical4j.model.DateTime periodStartTz = new net.fortuna.ical4j.model.DateTime(startInTz.toDate());
    net.fortuna.ical4j.model.DateTime periodEndTz = new net.fortuna.ical4j.model.DateTime(endInTz.toDate());

    Calendar endCalendarTz = Calendar.getInstance(tz);
    endCalendarTz.setTime(periodEndTz);

    Calendar calendarTz = Calendar.getInstance(tz);
    calendarTz.setTime(periodStartTz);

    calendarTz.set(Calendar.DAY_OF_MONTH, endCalendarTz.get(Calendar.DAY_OF_MONTH));
    calendarTz.set(Calendar.MONTH, endCalendarTz.get(Calendar.MONTH));
    calendarTz.set(Calendar.YEAR, endCalendarTz.get(Calendar.YEAR));
    periodEndTz.setTime(calendarTz.getTime().getTime() + duration);
    duration = duration % (DateTimeConstants.MILLIS_PER_DAY);

    logger.debug("1-Looking at recurrences for {} to {}, duration {}", periodStartTz.getTime(), periodEndTz.getTime(), duration);
    // Have to change the TimeZone to UTC for the rRule.getRecur() to work correctly in a non-global TimeZone
    periodStartTz.setTimeZone(registry.getTimeZone("UTC"));
    periodEndTz.setTimeZone(registry.getTimeZone("UTC"));

    for (Object date : rRule.getRecur().getDates(periodStartTz, periodEndTz, net.fortuna.ical4j.model.parameter.Value.DATE_TIME)) {
      Date d = (Date) date;
      net.fortuna.ical4j.model.DateTime datePeriod = new net.fortuna.ical4j.model.DateTime(d);
      Calendar cDate = Calendar.getInstance(registry.getTimeZone("UTC"));
      cDate.setTime(datePeriod);
      Calendar tzDate  = Calendar.getInstance(tz);
      tzDate.setTime(datePeriod);
      logger.debug("Looking at recurrence date {}, {}", d);
      // Adjust for DST regardless of end time DST
      if (tz.inDaylightTime(periodStartTz)) {
        if (!tz.inDaylightTime(d)) {
          logger.info("Adjusting " + d + " forward 1 hour");
          d.setTime(d.getTime() + tz.getDSTSavings());
        }
      } else if (tz.inDaylightTime(d)) {  // Otherwise only adjust special case 
        // Special case for first Sunday
        // Sunday DST day bug: https://github.com/ical4j/ical4j/issues/117
        logger.info("Adjusting " + d + " back 1 hour");
        d.setTime(d.getTime() - tz.getDSTSavings());
      }
      Calendar cal =  new Calendar.Builder().setTimeZone(tz).setInstant(d).build();
      cal.setTimeZone(tz);
      // update with the updated d
      cDate.setTime(d);
      Period p = new Period(new net.fortuna.ical4j.model.DateTime(cDate.getTimeInMillis()),
              new net.fortuna.ical4j.model.DateTime(cDate.getTimeInMillis() + duration));

      events.add(p);
      logger.trace("Adding date {} period '{}'", d, p.toString());
    }
    return events;
  }
}
