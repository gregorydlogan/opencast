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

package org.opencast.index.service.impl.index.event;

import org.opencast.index.service.catalog.adapter.MetadataUtils;
import org.opencast.mediapackage.Publication;
import org.opencast.metadata.dublincore.DublinCore;
import org.opencast.metadata.dublincore.EventCatalogUIAdapter;
import org.opencast.metadata.dublincore.MetadataCollection;
import org.opencast.metadata.dublincore.MetadataField;
import org.opencast.util.DateTimeSupport;
import org.opencast.workflow.handler.distribution.EngagePublicationChannel;
import org.opencast.workflow.handler.distribution.InternalPublicationChannel;

import com.entwinemedia.fn.Fn;

import org.apache.commons.lang3.StringUtils;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;

public final class EventUtils {
  private static final int CREATED_BY_UI_ORDER = 14;

  public static final Map<String, String> PUBLICATION_CHANNELS = new HashMap<String, String>();

  static {
    PUBLICATION_CHANNELS.put(EngagePublicationChannel.CHANNEL_ID, "EVENTS.EVENTS.DETAILS.GENERAL.ENGAGE");
    PUBLICATION_CHANNELS.put("youtube", "EVENTS.EVENTS.DETAILS.GENERAL.YOUTUBE");
  }

  private EventUtils() {

  }

  /**
   * Loads the metadata for the given event
   *
   * @param event
   *          the source {@link Event}
   * @return a {@link MetadataCollection} instance with all the event metadata
   */
  @SuppressWarnings("unchecked")
  public static MetadataCollection getEventMetadata(Event event, EventCatalogUIAdapter eventCatalogUIAdapter)
          throws Exception {
    MetadataCollection metadata = eventCatalogUIAdapter.getRawFields();

    MetadataField<?> title = metadata.getOutputFields().get(DublinCore.PROPERTY_TITLE.getLocalName());
    metadata.removeField(title);
    MetadataField<String> newTitle = MetadataUtils.copyMetadataField(title);
    newTitle.setValue(event.getTitle());
    metadata.addField(newTitle);

    MetadataField<?> subject = metadata.getOutputFields().get(DublinCore.PROPERTY_SUBJECT.getLocalName());
    metadata.removeField(subject);
    MetadataField<String> newSubject = MetadataUtils.copyMetadataField(subject);
    newSubject.setValue(event.getSubject());
    metadata.addField(newSubject);

    MetadataField<?> description = metadata.getOutputFields().get(DublinCore.PROPERTY_DESCRIPTION.getLocalName());
    metadata.removeField(description);
    MetadataField<String> newDescription = MetadataUtils.copyMetadataField(description);
    newDescription.setValue(event.getDescription());
    metadata.addField(newDescription);

    MetadataField<?> language = metadata.getOutputFields().get(DublinCore.PROPERTY_LANGUAGE.getLocalName());
    metadata.removeField(language);
    MetadataField<String> newLanguage = MetadataUtils.copyMetadataField(language);
    newLanguage.setValue(event.getLanguage());
    metadata.addField(newLanguage);

    MetadataField<?> rightsHolder = metadata.getOutputFields().get(DublinCore.PROPERTY_RIGHTS_HOLDER.getLocalName());
    metadata.removeField(rightsHolder);
    MetadataField<String> newRightsHolder = MetadataUtils.copyMetadataField(rightsHolder);
    newRightsHolder.setValue(event.getRights());
    metadata.addField(newRightsHolder);

    MetadataField<?> license = metadata.getOutputFields().get(DublinCore.PROPERTY_LICENSE.getLocalName());
    metadata.removeField(license);
    MetadataField<String> newLicense = MetadataUtils.copyMetadataField(license);
    newLicense.setValue(event.getLicense());
    metadata.addField(newLicense);

    MetadataField<?> series = metadata.getOutputFields().get(DublinCore.PROPERTY_IS_PART_OF.getLocalName());
    metadata.removeField(series);
    MetadataField<String> newSeries = MetadataUtils.copyMetadataField(series);
    newSeries.setValue(event.getSeriesId());
    metadata.addField(newSeries);

    MetadataField<?> presenters = metadata.getOutputFields().get(DublinCore.PROPERTY_CREATOR.getLocalName());
    metadata.removeField(presenters);
    MetadataField<String> newPresenters = MetadataUtils.copyMetadataField(presenters);
    newPresenters.setValue(StringUtils.join(event.getPresenters(), ", "));
    metadata.addField(newPresenters);

    MetadataField<?> contributors = metadata.getOutputFields().get(DublinCore.PROPERTY_CONTRIBUTOR.getLocalName());
    metadata.removeField(contributors);
    MetadataField<String> newContributors = MetadataUtils.copyMetadataField(contributors);
    newContributors.setValue(StringUtils.join(event.getContributors(), ", "));
    metadata.addField(newContributors);

    String recordingStartDate = event.getRecordingStartDate();
    if (StringUtils.isNotBlank(recordingStartDate)) {
      Date startDateTime = new Date(DateTimeSupport.fromUTC(recordingStartDate));

      MetadataField<?> startDate = metadata.getOutputFields().get("startDate");
      metadata.removeField(startDate);
      MetadataField<String> newStartDate = MetadataUtils.copyMetadataField(startDate);
      SimpleDateFormat sdf = new SimpleDateFormat(startDate.getPattern().get());
      sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
      newStartDate.setValue(sdf.format(startDateTime));
      metadata.addField(newStartDate);

      MetadataField<?> startTime = metadata.getOutputFields().get("startTime");
      metadata.removeField(startTime);
      MetadataField<String> newStartTime = MetadataUtils.copyMetadataField(startTime);
      sdf = new SimpleDateFormat(startTime.getPattern().get());
      sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
      newStartTime.setValue(sdf.format(startDateTime));
      metadata.addField(newStartTime);
    }

    if (event.getDuration() != null) {
      MetadataField<?> duration = metadata.getOutputFields().get("duration");
      metadata.removeField(duration);
      MetadataField<String> newDuration = MetadataUtils.copyMetadataField(duration);
      newDuration.setValue(event.getDuration().toString());
      metadata.addField(newDuration);
    }

    MetadataField<?> agent = metadata.getOutputFields().get("location");
    metadata.removeField(agent);
    MetadataField<String> newAgent = MetadataUtils.copyMetadataField(agent);
    newAgent.setValue(event.getLocation());
    metadata.addField(newAgent);

    MetadataField<?> source = metadata.getOutputFields().get(DublinCore.PROPERTY_SOURCE.getLocalName());
    metadata.removeField(source);
    MetadataField<String> newSource = MetadataUtils.copyMetadataField(source);
    newSource.setValue(event.getSource());
    metadata.addField(newSource);

    String createdDate = event.getCreated();
    if (StringUtils.isNotBlank(createdDate)) {
      MetadataField<?> created = metadata.getOutputFields().get(DublinCore.PROPERTY_CREATED.getLocalName());
      metadata.removeField(created);
      MetadataField<Date> newCreated = MetadataUtils.copyMetadataField(created);
      newCreated.setValue(new Date(DateTimeSupport.fromUTC(createdDate)));
      metadata.addField(newCreated);
    }

    MetadataField<?> uid = metadata.getOutputFields().get(DublinCore.PROPERTY_IDENTIFIER.getLocalName());
    metadata.removeField(uid);
    MetadataField<String> newUID = MetadataUtils.copyMetadataField(uid);
    newUID.setValue(event.getIdentifier());
    metadata.addField(newUID);

    return metadata;
  }

  /**
   * A filter to remove all internal channel publications.
   */
  public static final Fn<Publication, Boolean> internalChannelFilter = new Fn<Publication, Boolean>() {
    @Override
    public Boolean apply(Publication a) {
      if (InternalPublicationChannel.CHANNEL_ID.equals(a.getChannel()))
        return false;
      return true;
    }
  };
}
