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

package org.opencast.scheduler.impl;

import org.opencast.mediapackage.MediaPackage;
import org.opencast.metadata.dublincore.DublinCore;
import org.opencast.metadata.dublincore.DublinCoreCatalog;
import org.opencast.metadata.dublincore.DublinCoreCatalogList;
import org.opencast.security.api.UnauthorizedException;
import org.opencast.series.api.SeriesException;
import org.opencast.series.api.SeriesQuery;
import org.opencast.series.api.SeriesService;
import org.opencast.util.NotFoundException;

import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.DateTime;
import net.fortuna.ical4j.model.ParameterList;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.parameter.Cn;
import net.fortuna.ical4j.model.parameter.Encoding;
import net.fortuna.ical4j.model.parameter.FmtType;
import net.fortuna.ical4j.model.parameter.Value;
import net.fortuna.ical4j.model.parameter.XParameter;
import net.fortuna.ical4j.model.property.Attach;
import net.fortuna.ical4j.model.property.CalScale;
import net.fortuna.ical4j.model.property.Description;
import net.fortuna.ical4j.model.property.Location;
import net.fortuna.ical4j.model.property.Organizer;
import net.fortuna.ical4j.model.property.ProdId;
import net.fortuna.ical4j.model.property.RelatedTo;
import net.fortuna.ical4j.model.property.Uid;
import net.fortuna.ical4j.model.property.Version;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Create an iCalendar from the provided scheduled events.
 */
public class CalendarGenerator {

  /** Logging utility */
  private static final Logger logger = LoggerFactory.getLogger(CalendarGenerator.class);

  /** iCalendar */
  protected Calendar cal;

  /** Series service for Series DC retrieval */
  protected SeriesService seriesService;

  private final Map<String, DublinCoreCatalog> series = new HashMap<>();

  /**
   * Default constructor that creates a CalendarGenerator object
   *
   * @param seriesService
   *          the series service
   */
  public CalendarGenerator(SeriesService seriesService) {
    cal = new Calendar();
    cal.getProperties().add(new ProdId("Opencast Matterhorn Calendar File 0.5"));
    cal.getProperties().add(Version.VERSION_2_0);
    cal.getProperties().add(CalScale.GREGORIAN);
    this.seriesService = seriesService;
  }

  /**
   * gets the iCalendar creates by this object.
   *
   * @return the iCalendar
   */
  public Calendar getCalendar() {
    return cal;
  }

  /**
   * Sets an iCalender to work with
   *
   * @param cal
   *          the iCalendar to set
   */
  public void setCalendar(Calendar cal) {
    this.cal = cal;
  }

  /**
   * Adds an SchedulerEvent as a new entry to this iCalendar
   *
   * @param mp
   *          {@link MediaPackage} of event
   * @param agentId
   *          the agent identifier
   * @param start
   *          the start date
   * @param end
   *          the end date
   * @param captureAgentMetadata
   *          properties for capture agent metadata
   *
   * @return true if the event could be added.
   */
  public boolean addEvent(MediaPackage mp, DublinCoreCatalog catalog, String agentId, Date start, Date end,
          String captureAgentMetadata) {
    String eventId = mp.getIdentifier().compact();

    logger.debug("Creating iCaleandar VEvent from scheduled event '{}'", eventId);

    DateTime startDate = new DateTime(start);
    DateTime endDate = new DateTime(end);
    Date marginEndDate = new org.joda.time.DateTime(endDate.getTime()).plusHours(1).toDate();
    if (marginEndDate.before(new Date())) {
      logger.debug("Event has already passed more than an hour, skipping!");
      return false;
    }
    startDate.setUtc(true);
    endDate.setUtc(true);
    String seriesID = null;

    VEvent event = new VEvent(startDate, endDate, catalog.getFirst(DublinCore.PROPERTY_TITLE));
    try {
      ParameterList pl = new ParameterList();
      if (StringUtils.isNotEmpty(catalog.getFirst(DublinCore.PROPERTY_CREATOR))) {
        pl.add(new Cn(catalog.getFirst(DublinCore.PROPERTY_CREATOR)));
      }
      event.getProperties().add(new Uid(eventId));

      // TODO Organizer should be URI (email-address?) created fake address
      if (StringUtils.isNotEmpty(catalog.getFirst(DublinCore.PROPERTY_CREATOR))) {
        URI organizer = new URI("mailto", catalog.getFirst(DublinCore.PROPERTY_CREATOR) + "@matterhorn.opencast", null);
        event.getProperties().add(new Organizer(pl, organizer));
      }
      if (StringUtils.isNotEmpty(catalog.getFirst(DublinCore.PROPERTY_DESCRIPTION))) {
        event.getProperties().add(new Description(catalog.getFirst(DublinCore.PROPERTY_DESCRIPTION)));
      }
      event.getProperties().add(new Location(agentId));
      if (StringUtils.isNotEmpty(catalog.getFirst(DublinCore.PROPERTY_IS_PART_OF))) {
        seriesID = catalog.getFirst(DublinCore.PROPERTY_IS_PART_OF);
        event.getProperties().add(new RelatedTo(seriesID));
      }

      ParameterList dcParameters = new ParameterList();
      dcParameters.add(new FmtType("application/xml"));
      dcParameters.add(Value.BINARY);
      dcParameters.add(Encoding.BASE64);
      dcParameters.add(new XParameter("X-APPLE-FILENAME", "episode.xml"));
      Attach metadataAttachment = new Attach(dcParameters, catalog.toXmlString().getBytes("UTF-8"));
      event.getProperties().add(metadataAttachment);

      String seriesDC = getSeriesDublinCoreAsString(seriesID);
      if (seriesDC != null) {
        logger.debug("Attaching series {} information to event {}", seriesID, eventId);
        ParameterList sDcParameters = new ParameterList();
        sDcParameters.add(new FmtType("application/xml"));
        sDcParameters.add(Value.BINARY);
        sDcParameters.add(Encoding.BASE64);
        sDcParameters.add(new XParameter("X-APPLE-FILENAME", "series.xml"));
        Attach seriesAttachment = new Attach(sDcParameters, seriesDC.getBytes("UTF-8"));
        event.getProperties().add(seriesAttachment);
      } else {
        logger.debug("No series provided for event {}.", eventId);
      }

      ParameterList caParameters = new ParameterList();
      caParameters.add(new FmtType("application/text"));
      caParameters.add(Value.BINARY);
      caParameters.add(Encoding.BASE64);
      caParameters.add(new XParameter("X-APPLE-FILENAME", "org.opencast.capture.agent.properties"));
      Attach agentsAttachment = new Attach(caParameters, captureAgentMetadata.getBytes("UTF-8"));
      event.getProperties().add(agentsAttachment);

    } catch (Exception e) {
      logger.error("Unable to add event '{}' to recording calendar: {}", eventId, ExceptionUtils.getStackTrace(e));
      return false;
    }

    cal.getComponents().add(event);

    logger.debug("new VEvent = {} ", event.toString());
    return true;
  }

  /**
   * Returns series DC associated with this event or null if {@link SeriesService} is not available or does not contain
   * entry for series with specified ID.
   *
   * @param seriesID
   *          {@link DublinCoreCatalog} to be retrieved
   * @return DC serialized to string or null
   * @throws UnauthorizedException
   *           if the current user is not allowed to view this series
   * @throws NotFoundException
   *           if the series cannot be found
   */
  private String getSeriesDublinCoreAsString(String seriesID) throws UnauthorizedException, NotFoundException {
    if (StringUtils.isBlank(seriesID))
      return null;
    if (seriesService == null) {
      logger.warn("No SeriesService available");
      return null;
    }

    if (series.isEmpty()) {
      try {
        DublinCoreCatalogList seriesCatalogs = seriesService.getSeries(new SeriesQuery().setCount(Integer.MAX_VALUE));
        for (DublinCoreCatalog dc : seriesCatalogs.getCatalogList()) {
          series.put(dc.getFirst(DublinCore.PROPERTY_IDENTIFIER), dc);
        }
      } catch (SeriesException e) {
        logger.error("Error loading DublinCoreCatalog for series '{}': {}", seriesID, ExceptionUtils.getStackTrace(e));
        return null;
      }
    }

    DublinCoreCatalog seriesDC = series.get(seriesID);
    if (seriesDC == null) {
      try {
        seriesDC = seriesService.getSeries(seriesID);
      } catch (SeriesException e) {
        logger.error("Error loading DublinCoreCatalog for series '{}': {}", seriesID, ExceptionUtils.getStackTrace(e));
        return null;
      }
    }

    try {
      return seriesDC.toXmlString();
    } catch (IOException e) {
      logger.error("Error serializing DublinCoreCatalog of series '{}': {}", seriesID, ExceptionUtils.getStackTrace(e));
      return null;
    }
  }

}
