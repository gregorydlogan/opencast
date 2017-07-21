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

package org.opencast.index.service.impl;

import static org.apache.commons.lang3.exception.ExceptionUtils.getStackTrace;
import static org.opencast.assetmanager.api.AssetManager.DEFAULT_OWNER;
import static org.opencast.assetmanager.api.fn.Enrichments.enrich;
import static org.opencast.metadata.dublincore.DublinCore.PROPERTY_IDENTIFIER;

import org.opencast.assetmanager.api.AssetManager;
import org.opencast.assetmanager.api.AssetManagerException;
import org.opencast.assetmanager.api.query.AQueryBuilder;
import org.opencast.assetmanager.api.query.AResult;
import org.opencast.assetmanager.api.query.Predicate;
import org.opencast.authorization.xacml.manager.api.AclService;
import org.opencast.authorization.xacml.manager.api.AclServiceFactory;
import org.opencast.capture.CaptureParameters;
import org.opencast.capture.admin.api.CaptureAgentStateService;
import org.opencast.event.comment.EventComment;
import org.opencast.event.comment.EventCommentException;
import org.opencast.event.comment.EventCommentParser;
import org.opencast.event.comment.EventCommentService;
import org.opencast.index.service.api.IndexService;
import org.opencast.index.service.catalog.adapter.DublinCoreMetadataUtil;
import org.opencast.index.service.catalog.adapter.MetadataList;
import org.opencast.index.service.catalog.adapter.MetadataUtils;
import org.opencast.index.service.catalog.adapter.events.CommonEventCatalogUIAdapter;
import org.opencast.index.service.catalog.adapter.series.CommonSeriesCatalogUIAdapter;
import org.opencast.index.service.exception.IndexServiceException;
import org.opencast.index.service.impl.index.AbstractSearchIndex;
import org.opencast.index.service.impl.index.event.Event;
import org.opencast.index.service.impl.index.event.EventHttpServletRequest;
import org.opencast.index.service.impl.index.event.EventSearchQuery;
import org.opencast.index.service.impl.index.group.Group;
import org.opencast.index.service.impl.index.group.GroupIndexSchema;
import org.opencast.index.service.impl.index.group.GroupSearchQuery;
import org.opencast.index.service.impl.index.series.Series;
import org.opencast.index.service.impl.index.series.SeriesSearchQuery;
import org.opencast.index.service.resources.list.query.GroupsListQuery;
import org.opencast.index.service.util.JSONUtils;
import org.opencast.index.service.util.RestUtils;
import org.opencast.ingest.api.IngestException;
import org.opencast.ingest.api.IngestService;
import org.opencast.matterhorn.search.SearchIndexException;
import org.opencast.matterhorn.search.SearchResult;
import org.opencast.matterhorn.search.SortCriterion;
import org.opencast.mediapackage.Catalog;
import org.opencast.mediapackage.EName;
import org.opencast.mediapackage.MediaPackage;
import org.opencast.mediapackage.MediaPackageElement;
import org.opencast.mediapackage.MediaPackageElement.Type;
import org.opencast.mediapackage.MediaPackageElementBuilderFactory;
import org.opencast.mediapackage.MediaPackageElementFlavor;
import org.opencast.mediapackage.MediaPackageElements;
import org.opencast.mediapackage.MediaPackageException;
import org.opencast.mediapackage.Track;
import org.opencast.mediapackage.identifier.Id;
import org.opencast.mediapackage.identifier.IdImpl;
import org.opencast.metadata.dublincore.DCMIPeriod;
import org.opencast.metadata.dublincore.DublinCore;
import org.opencast.metadata.dublincore.DublinCoreCatalog;
import org.opencast.metadata.dublincore.DublinCoreCatalogList;
import org.opencast.metadata.dublincore.DublinCoreUtil;
import org.opencast.metadata.dublincore.DublinCoreValue;
import org.opencast.metadata.dublincore.DublinCores;
import org.opencast.metadata.dublincore.EncodingSchemeUtils;
import org.opencast.metadata.dublincore.EventCatalogUIAdapter;
import org.opencast.metadata.dublincore.MetadataCollection;
import org.opencast.metadata.dublincore.MetadataField;
import org.opencast.metadata.dublincore.MetadataParsingException;
import org.opencast.metadata.dublincore.Precision;
import org.opencast.metadata.dublincore.SeriesCatalogUIAdapter;
import org.opencast.scheduler.api.SchedulerException;
import org.opencast.scheduler.api.SchedulerService;
import org.opencast.security.api.AccessControlList;
import org.opencast.security.api.AccessControlParser;
import org.opencast.security.api.AclScope;
import org.opencast.security.api.AuthorizationService;
import org.opencast.security.api.SecurityService;
import org.opencast.security.api.UnauthorizedException;
import org.opencast.security.api.User;
import org.opencast.security.api.UserDirectoryService;
import org.opencast.security.util.SecurityContext;
import org.opencast.series.api.SeriesException;
import org.opencast.series.api.SeriesQuery;
import org.opencast.series.api.SeriesService;
import org.opencast.userdirectory.JpaGroupRoleProvider;
import org.opencast.util.DateTimeSupport;
import org.opencast.util.NotFoundException;
import org.opencast.util.XmlNamespaceBinding;
import org.opencast.util.XmlNamespaceContext;
import org.opencast.util.data.Effect0;
import org.opencast.util.data.Tuple;
import org.opencast.workflow.api.WorkflowDatabaseException;
import org.opencast.workflow.api.WorkflowException;
import org.opencast.workflow.api.WorkflowInstance;
import org.opencast.workflow.api.WorkflowInstance.WorkflowState;
import org.opencast.workflow.api.WorkflowQuery;
import org.opencast.workflow.api.WorkflowService;
import org.opencast.workflow.api.WorkflowSet;
import org.opencast.workspace.api.Workspace;

import com.entwinemedia.fn.Fn2;
import com.entwinemedia.fn.Stream;
import com.entwinemedia.fn.data.Opt;

import net.fortuna.ical4j.model.DateTime;
import net.fortuna.ical4j.model.Period;
import net.fortuna.ical4j.model.parameter.Value;
import net.fortuna.ical4j.model.property.RRule;

import org.apache.commons.fileupload.FileItemIterator;
import org.apache.commons.fileupload.FileItemStream;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.fileupload.util.Streams;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.codehaus.jettison.json.JSONException;
import org.joda.time.DateTimeConstants;
import org.joda.time.DateTimeZone;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

public class IndexServiceImpl implements IndexService {

  private static final String WORKFLOW_CONFIG_PREFIX = "org.opencast.workflow.config.";

  public static final String THEME_PROPERTY_NAME = "theme";

  /** The logging facility */
  private static final Logger logger = LoggerFactory.getLogger(IndexServiceImpl.class);

  /** A parser for handling JSON documents inside the body of a request. **/
  private static final JSONParser parser = new JSONParser();

  private final List<EventCatalogUIAdapter> eventCatalogUIAdapters = new ArrayList<>();
  private final List<SeriesCatalogUIAdapter> seriesCatalogUIAdapters = new ArrayList<>();
  private EventCatalogUIAdapter eventCatalogUIAdapter;
  private SeriesCatalogUIAdapter seriesCatalogUIAdapter;

  private AclServiceFactory aclServiceFactory;
  private AuthorizationService authorizationService;
  private CaptureAgentStateService captureAgentStateService;
  private EventCommentService eventCommentService;
  private IngestService ingestService;
  private AssetManager assetManager;
  private SchedulerService schedulerService;
  private SecurityService securityService;
  private JpaGroupRoleProvider jpaGroupRoleProvider;
  private SeriesService seriesService;
  private UserDirectoryService userDirectoryService;
  private WorkflowService workflowService;
  private Workspace workspace;

  /** The single thread executor service */
  private ExecutorService executorService = Executors.newSingleThreadExecutor();

  /** OSGi DI. */
  public void setAclServiceFactory(AclServiceFactory aclServiceFactory) {
    this.aclServiceFactory = aclServiceFactory;
  }

  /** OSGi DI. */
  public void setAuthorizationService(AuthorizationService authorizationService) {
    this.authorizationService = authorizationService;
  }

  /** OSGi DI. */
  public void setCaptureAgentStateService(CaptureAgentStateService captureAgentStateService) {
    this.captureAgentStateService = captureAgentStateService;
  }

  /** OSGi callback for the event comment service. */
  public void setEventCommentService(EventCommentService eventCommentService) {
    this.eventCommentService = eventCommentService;
  }

  /** OSGi callback to add the event dublincore {@link EventCatalogUIAdapter} instance. */
  public void setCommonEventCatalogUIAdapter(CommonEventCatalogUIAdapter eventCatalogUIAdapter) {
    this.eventCatalogUIAdapter = eventCatalogUIAdapter;
  }

  /** OSGi callback to add the series dublincore {@link SeriesCatalogUIAdapter} instance. */
  public void setCommonSeriesCatalogUIAdapter(CommonSeriesCatalogUIAdapter seriesCatalogUIAdapter) {
    this.seriesCatalogUIAdapter = seriesCatalogUIAdapter;
  }

  /** OSGi callback to add {@link EventCatalogUIAdapter} instance. */
  public void addCatalogUIAdapter(EventCatalogUIAdapter catalogUIAdapter) {
    eventCatalogUIAdapters.add(catalogUIAdapter);
  }

  /** OSGi callback to remove {@link EventCatalogUIAdapter} instance. */
  public void removeCatalogUIAdapter(EventCatalogUIAdapter catalogUIAdapter) {
    eventCatalogUIAdapters.remove(catalogUIAdapter);
  }

  /** OSGi callback to add {@link SeriesCatalogUIAdapter} instance. */
  public void addCatalogUIAdapter(SeriesCatalogUIAdapter catalogUIAdapter) {
    seriesCatalogUIAdapters.add(catalogUIAdapter);
  }

  /** OSGi callback to remove {@link SeriesCatalogUIAdapter} instance. */
  public void removeCatalogUIAdapter(SeriesCatalogUIAdapter catalogUIAdapter) {
    seriesCatalogUIAdapters.remove(catalogUIAdapter);
  }

  /** OSGi DI. */
  public void setIngestService(IngestService ingestService) {
    this.ingestService = ingestService;
  }

  /** OSGi DI. */
  public void setAssetManager(AssetManager assetManager) {
    this.assetManager = assetManager;
  }

  /** OSGi DI. */
  public void setSchedulerService(SchedulerService schedulerService) {
    this.schedulerService = schedulerService;
  }

  /** OSGi DI. */
  public void setSecurityService(SecurityService securityService) {
    this.securityService = securityService;
  }

  /** OSGi DI. */
  public void setSeriesService(SeriesService seriesService) {
    this.seriesService = seriesService;
  }

  /** OSGi DI. */
  public void setWorkflowService(WorkflowService workflowService) {
    this.workflowService = workflowService;
  }

  /** OSGi DI. */
  public void setWorkspace(Workspace workspace) {
    this.workspace = workspace;
  }

  /** OSGi DI. */
  public void setUserDirectoryService(UserDirectoryService userDirectoryService) {
    this.userDirectoryService = userDirectoryService;
  }

  /** OSGi DI. */
  public void setGroupRoleProvider(JpaGroupRoleProvider jpaGroupRoleProvider) {
    this.jpaGroupRoleProvider = jpaGroupRoleProvider;
  }

  public AclService getAclService() {
    return aclServiceFactory.serviceFor(securityService.getOrganization());
  }

  private static final Fn2<EventCatalogUIAdapter, String, Boolean> eventOrganizationFilter = new Fn2<EventCatalogUIAdapter, String, Boolean>() {
    @Override
    public Boolean apply(EventCatalogUIAdapter catalogUIAdapter, String organization) {
      return organization.equals(catalogUIAdapter.getOrganization());
    }
  };

  private static final Fn2<SeriesCatalogUIAdapter, String, Boolean> seriesOrganizationFilter = new Fn2<SeriesCatalogUIAdapter, String, Boolean>() {
    @Override
    public Boolean apply(SeriesCatalogUIAdapter catalogUIAdapter, String organization) {
      return catalogUIAdapter.getOrganization().equals(organization);
    }
  };

  public List<EventCatalogUIAdapter> getEventCatalogUIAdapters(String organization) {
    return Stream.$(eventCatalogUIAdapters).filter(eventOrganizationFilter._2(organization)).toList();
  }

  /**
   * @param organization
   *          The organization to filter the results with.
   * @return A {@link List} of {@link SeriesCatalogUIAdapter} that provide the metadata to the front end.
   */
  public List<SeriesCatalogUIAdapter> getSeriesCatalogUIAdapters(String organization) {
    return Stream.$(seriesCatalogUIAdapters).filter(seriesOrganizationFilter._2(organization)).toList();
  }

  @Override
  public List<EventCatalogUIAdapter> getEventCatalogUIAdapters() {
    return new ArrayList<>(getEventCatalogUIAdapters(securityService.getOrganization().getId()));
  }

  @Override
  public List<SeriesCatalogUIAdapter> getSeriesCatalogUIAdapters() {
    return new LinkedList<>(getSeriesCatalogUIAdapters(securityService.getOrganization().getId()));
  }

  @Override
  public EventCatalogUIAdapter getCommonEventCatalogUIAdapter() {
    return eventCatalogUIAdapter;
  }

  @Override
  public SeriesCatalogUIAdapter getCommonSeriesCatalogUIAdapter() {
    return seriesCatalogUIAdapter;
  }

  @Override
  public String createEvent(HttpServletRequest request) throws IndexServiceException {
    JSONObject metadataJson = null;
    MediaPackage mp = null;
    try {
      if (ServletFileUpload.isMultipartContent(request)) {
        mp = ingestService.createMediaPackage();

        for (FileItemIterator iter = new ServletFileUpload().getItemIterator(request); iter.hasNext();) {
          FileItemStream item = iter.next();
          String fieldName = item.getFieldName();
          if (item.isFormField()) {
            if ("metadata".equals(fieldName)) {
              String metadata = Streams.asString(item.openStream());
              try {
                metadataJson = (JSONObject) parser.parse(metadata);
              } catch (Exception e) {
                logger.warn("Unable to parse metadata {}", metadata);
                throw new IllegalArgumentException("Unable to parse metadata");
              }
            }
          } else {
            if ("presenter".equals(item.getFieldName())) {
              mp = ingestService.addTrack(item.openStream(), item.getName(), MediaPackageElements.PRESENTER_SOURCE, mp);
            } else if ("presentation".equals(item.getFieldName())) {
              mp = ingestService.addTrack(item.openStream(), item.getName(), MediaPackageElements.PRESENTATION_SOURCE,
                      mp);
            } else if ("audio".equals(item.getFieldName())) {
              mp = ingestService.addTrack(item.openStream(), item.getName(),
                      new MediaPackageElementFlavor("presenter-audio", "source"), mp);
            } else {
              logger.warn("Unknown field name found {}", item.getFieldName());
            }
          }
        }
      } else {
        throw new IllegalArgumentException("No multipart content");
      }

      // MH-10834 If there is only an audio track, change the flavor from presenter-audio/source to presenter/source.
      if (mp.getTracks().length == 1
              && mp.getTracks()[0].getFlavor().equals(new MediaPackageElementFlavor("presenter-audio", "source"))) {
        Track audioTrack = mp.getTracks()[0];
        mp.remove(audioTrack);
        audioTrack.setFlavor(MediaPackageElements.PRESENTER_SOURCE);
        mp.add(audioTrack);
      }

      return createEvent(metadataJson, mp);
    } catch (Exception e) {
      logger.error("Unable to create event: {}", getStackTrace(e));
      throw new IndexServiceException(e.getMessage());
    }
  }

  /**
   * Get the type of the source that is creating the event.
   *
   * @param source
   *          The source of the event e.g. upload, single scheduled, multi scheduled
   * @return The type of the source
   * @throws IllegalArgumentException
   *           Thrown if unable to get the source from the json object.
   */
  private SourceType getSourceType(JSONObject source) {
    SourceType type;
    try {
      type = SourceType.valueOf((String) source.get("type"));
    } catch (Exception e) {
      logger.error("Unknown source type '{}'", source.get("type"));
      throw new IllegalArgumentException("Unknown source type");
    }
    return type;
  }

  /**
   * Get the access control list from a JSON representation
   *
   * @param metadataJson
   *          The {@link JSONObject} that has the access json
   * @return An {@link AccessControlList}
   * @throws IllegalArgumentException
   *           Thrown if unable to parse the access control list
   */
  private AccessControlList getAccessControlList(JSONObject metadataJson) {
    AccessControlList acl = new AccessControlList();
    JSONObject accessJson = (JSONObject) metadataJson.get("access");
    if (accessJson != null) {
      try {
        acl = AccessControlParser.parseAcl(accessJson.toJSONString());
      } catch (Exception e) {
        logger.warn("Unable to parse access control list: {}", accessJson.toJSONString());
        throw new IllegalArgumentException("Unable to parse access control list!");
      }
    }
    return acl;
  }

  @Override
  public String createEvent(JSONObject metadataJson, MediaPackage mp) throws ParseException, IOException,
          MediaPackageException, IngestException, NotFoundException, SchedulerException, UnauthorizedException {
    if (metadataJson == null)
      throw new IllegalArgumentException("No metadata set");

    JSONObject source = (JSONObject) metadataJson.get("source");
    if (source == null)
      throw new IllegalArgumentException("No source field in metadata");

    JSONObject processing = (JSONObject) metadataJson.get("processing");
    if (processing == null)
      throw new IllegalArgumentException("No processing field in metadata");

    JSONArray allEventMetadataJson = (JSONArray) metadataJson.get("metadata");
    if (allEventMetadataJson == null)
      throw new IllegalArgumentException("No metadata field in metadata");

    AccessControlList acl = getAccessControlList(metadataJson);

    MetadataList metadataList = getMetadataListWithAllEventCatalogUIAdapters();
    try {
      metadataList.fromJSON(allEventMetadataJson.toJSONString());
    } catch (MetadataParsingException e) {
      logger.warn("Unable to parse event metadata {}", allEventMetadataJson.toJSONString());
      throw new IllegalArgumentException("Unable to parse metadata set");
    }

    EventHttpServletRequest eventHttpServletRequest = new EventHttpServletRequest();
    eventHttpServletRequest.setAcl(acl);
    eventHttpServletRequest.setMetadataList(metadataList);
    eventHttpServletRequest.setMediaPackage(mp);
    eventHttpServletRequest.setProcessing(processing);
    eventHttpServletRequest.setSource(source);

    return createEvent(eventHttpServletRequest);
  }

  @Override
  public String createEvent(EventHttpServletRequest eventHttpServletRequest) throws ParseException, IOException,
          MediaPackageException, IngestException, NotFoundException, SchedulerException, UnauthorizedException {
    // Preconditions
    if (eventHttpServletRequest.getAcl().isNone()) {
      throw new IllegalArgumentException("No access control list available to create new event.");
    }
    if (eventHttpServletRequest.getMediaPackage().isNone()) {
      throw new IllegalArgumentException("No mediapackage available to create new event.");
    }
    if (eventHttpServletRequest.getMetadataList().isNone()) {
      throw new IllegalArgumentException("No metadata list available to create new event.");
    }
    if (eventHttpServletRequest.getProcessing().isNone()) {
      throw new IllegalArgumentException("No processing metadata available to create new event.");
    }
    if (eventHttpServletRequest.getSource().isNone()) {
      throw new IllegalArgumentException("No source field metadata available to create new event.");
    }

    // Get Workflow
    String workflowTemplate = (String) eventHttpServletRequest.getProcessing().get().get("workflow");
    if (workflowTemplate == null)
      throw new IllegalArgumentException("No workflow template in metadata");

    // Get Type of Source
    SourceType type = getSourceType(eventHttpServletRequest.getSource().get());

    MetadataCollection eventMetadata = eventHttpServletRequest.getMetadataList().get()
            .getMetadataByAdapter(eventCatalogUIAdapter).get();

    JSONObject sourceMetadata = (JSONObject) eventHttpServletRequest.getSource().get().get("metadata");
    if (sourceMetadata != null
            && (type.equals(SourceType.SCHEDULE_SINGLE) || type.equals(SourceType.SCHEDULE_MULTIPLE))) {
      try {
        MetadataField<?> current = eventMetadata.getOutputFields().get("location");
        eventMetadata.updateStringField(current, (String) sourceMetadata.get("device"));
      } catch (Exception e) {
        logger.warn("Unable to parse device {}", sourceMetadata.get("device"));
        throw new IllegalArgumentException("Unable to parse device");
      }
    }

    MetadataField<?> created = eventMetadata.getOutputFields().get(DublinCore.PROPERTY_CREATED.getLocalName());
    if (created == null || !created.isUpdated() || created.getValue().isNone()) {
      eventMetadata.removeField(created);
      MetadataField<String> newCreated = MetadataUtils.copyMetadataField(created);
      newCreated.setValue(EncodingSchemeUtils.encodeDate(new Date(), Precision.Second).getValue());
      eventMetadata.addField(newCreated);
    }

    // Get presenter usernames for use as technical presenters
    Set<String> presenterUsernames = new HashSet<>();
    Opt<Set<String>> technicalPresenters = updatePresenters(eventMetadata);
    if (technicalPresenters.isSome()) {
      presenterUsernames = technicalPresenters.get();
    }

    eventHttpServletRequest.getMetadataList().get().add(eventCatalogUIAdapter, eventMetadata);
    updateMediaPackageMetadata(eventHttpServletRequest.getMediaPackage().get(),
            eventHttpServletRequest.getMetadataList().get());

    DublinCoreCatalog dc = getDublinCoreCatalog(eventHttpServletRequest);
    String captureAgentId = null;
    TimeZone tz = null;
    org.joda.time.DateTime start = null;
    org.joda.time.DateTime end = null;
    long duration = 0L;
    Properties caProperties = new Properties();
    RRule rRule = null;
    if (sourceMetadata != null
            && (type.equals(SourceType.SCHEDULE_SINGLE) || type.equals(SourceType.SCHEDULE_MULTIPLE))) {
      Properties configuration;
      try {
        captureAgentId = (String) sourceMetadata.get("device");
        configuration = captureAgentStateService.getAgentConfiguration((String) sourceMetadata.get("device"));
      } catch (Exception e) {
        logger.warn("Unable to parse device {}: because: {}", sourceMetadata.get("device"), getStackTrace(e));
        throw new IllegalArgumentException("Unable to parse device");
      }

      String durationString = (String) sourceMetadata.get("duration");
      if (StringUtils.isBlank(durationString))
        throw new IllegalArgumentException("No duration in source metadata");

      // Create timezone based on CA's reported TZ.
      String agentTimeZone = configuration.getProperty("capture.device.timezone");
      if (StringUtils.isNotBlank(agentTimeZone)) {
        tz = TimeZone.getTimeZone(agentTimeZone);
        dc.set(DublinCores.OC_PROPERTY_AGENT_TIMEZONE, tz.getID());
      } else { // No timezone was present, assume the serve's local timezone.
        tz = TimeZone.getDefault();
        logger.debug(
                "The field 'capture.device.timezone' has not been set in the agent configuration. The default server timezone will be used.");
      }

      org.joda.time.DateTime now = new org.joda.time.DateTime(DateTimeZone.UTC);
      start = now.withMillis(DateTimeSupport.fromUTC((String) sourceMetadata.get("start")));
      end = now.withMillis(DateTimeSupport.fromUTC((String) sourceMetadata.get("end")));
      duration = Long.parseLong(durationString);
      DublinCoreValue period = EncodingSchemeUtils
              .encodePeriod(new DCMIPeriod(start.toDate(), start.plus(duration).toDate()), Precision.Second);
      String inputs = (String) sourceMetadata.get("inputs");

      caProperties.putAll(configuration);
      dc.set(DublinCore.PROPERTY_TEMPORAL, period);
      caProperties.put(CaptureParameters.CAPTURE_DEVICE_NAMES, inputs);
    }

    if (type.equals(SourceType.SCHEDULE_MULTIPLE)) {
      rRule = new RRule((String) sourceMetadata.get("rrule"));
    }

    Map<String, String> configuration = new HashMap<>();
    if (eventHttpServletRequest.getProcessing().get().get("configuration") != null) {
      configuration = new HashMap<>((JSONObject) eventHttpServletRequest.getProcessing().get().get("configuration"));
    }
    for (Entry<String, String> entry : configuration.entrySet()) {
      caProperties.put(WORKFLOW_CONFIG_PREFIX.concat(entry.getKey()), entry.getValue());
    }
    caProperties.put(CaptureParameters.INGEST_WORKFLOW_DEFINITION, workflowTemplate);

    eventHttpServletRequest.setMediaPackage(authorizationService.setAcl(eventHttpServletRequest.getMediaPackage().get(),
            AclScope.Episode, eventHttpServletRequest.getAcl().get()).getA());

    MediaPackage mediaPackage;
    switch (type) {
      case UPLOAD:
      case UPLOAD_LATER:
        eventHttpServletRequest
                .setMediaPackage(updateDublincCoreCatalog(eventHttpServletRequest.getMediaPackage().get(), dc));
        configuration.put("workflowDefinitionId", workflowTemplate);
        WorkflowInstance ingest = ingestService.ingest(eventHttpServletRequest.getMediaPackage().get(),
                workflowTemplate, configuration);
        return eventHttpServletRequest.getMediaPackage().get().getIdentifier().compact();
      case SCHEDULE_SINGLE:
        mediaPackage = updateDublincCoreCatalog(eventHttpServletRequest.getMediaPackage().get(), dc);
        eventHttpServletRequest.setMediaPackage(mediaPackage);
        try {
          schedulerService.addEvent(start.toDate(), start.plus(duration).toDate(), captureAgentId, presenterUsernames,
                  mediaPackage, configuration, (Map) caProperties, Opt.<Boolean> none(), Opt.<String> none(),
                  SchedulerService.ORIGIN);
        } finally {
          for (MediaPackageElement mediaPackageElement : mediaPackage.getElements()) {
            try {
              workspace.delete(mediaPackage.getIdentifier().toString(), mediaPackageElement.getIdentifier());
            } catch (NotFoundException | IOException e) {
              logger.warn("Failed to delete media package element", e);
            }
          }
        }
        return mediaPackage.getIdentifier().compact();
      case SCHEDULE_MULTIPLE:
        List<Period> periods = calculatePeriods(start.toDate(), end.toDate(), duration, rRule, tz);
        int i = 1;
        int length = Integer.toString(periods.size()).length();
        List<String> ids = new ArrayList<>();
        String initialTitle = dc.getFirst(DublinCore.PROPERTY_TITLE);
        for (Period period : periods) {
          Date startDate = new Date(period.getStart().getTime());
          Date endDate = new Date(period.getEnd().getTime());
          Id id = new IdImpl(UUID.randomUUID().toString());

          // Set the new media package identifier
          eventHttpServletRequest.getMediaPackage().get().setIdentifier(id);

          // Update dublincore title and temporal
          String newTitle = initialTitle + String.format(" %0" + length + "d", i++);
          dc.set(DublinCore.PROPERTY_TITLE, newTitle);
          DublinCoreValue eventTime = EncodingSchemeUtils.encodePeriod(new DCMIPeriod(startDate, endDate),
                  Precision.Second);
          dc.set(DublinCore.PROPERTY_TEMPORAL, eventTime);
          mediaPackage = updateDublincCoreCatalog(eventHttpServletRequest.getMediaPackage().get(), dc);
          mediaPackage.setTitle(newTitle);

          try {
            schedulerService.addEvent(startDate, endDate, captureAgentId, presenterUsernames, mediaPackage,
                    configuration, (Map) caProperties, Opt.none(), Opt.none(), SchedulerService.ORIGIN);
          } finally {
            for (MediaPackageElement mediaPackageElement : mediaPackage.getElements()) {
              try {
                workspace.delete(mediaPackage.getIdentifier().toString(), mediaPackageElement.getIdentifier());
              } catch (NotFoundException | IOException e) {
                logger.warn("Failed to delete media package element", e);
              }
            }
          }
          ids.add(id.compact());
        }
        return StringUtils.join(ids, ",");
      default:
        logger.warn("Unknown source type {}", type);
        throw new IllegalArgumentException("Unknown source type");
    }
  }

  /**
   * Get the {@link DublinCoreCatalog} from an {@link EventHttpServletRequest}.
   *
   * @param eventHttpServletRequest
   *          The request to extract the {@link DublinCoreCatalog} from.
   * @return The {@link DublinCoreCatalog}
   */
  private DublinCoreCatalog getDublinCoreCatalog(EventHttpServletRequest eventHttpServletRequest) {
    DublinCoreCatalog dc;
    Opt<DublinCoreCatalog> dcOpt = DublinCoreUtil.loadEpisodeDublinCore(workspace,
            eventHttpServletRequest.getMediaPackage().get());
    if (dcOpt.isSome()) {
      dc = dcOpt.get();
      // make sure to bind the OC_PROPERTY namespace
      dc.addBindings(XmlNamespaceContext
              .mk(XmlNamespaceBinding.mk(DublinCores.OC_PROPERTY_NS_PREFIX, DublinCores.OC_PROPERTY_NS_URI)));
    } else {
      dc = DublinCores.mkOpencastEpisode().getCatalog();
    }
    return dc;
  }

  /**
   * Update the presenters field in the event {@link MetadataCollection} to have friendly names loaded by the
   * {@link UserDirectoryService} and return the usernames of the presenters.
   *
   * @param eventMetadata
   *          The {@link MetadataCollection} to update the presenters (creator field) with full names.
   * @return If the presenters (creator) field has been updated, the set of user names, if any, of the presenters. None
   *         if it wasn't updated.
   */
  private Opt<Set<String>> updatePresenters(MetadataCollection eventMetadata) {
    MetadataField<?> presentersMetadataField = eventMetadata.getOutputFields()
            .get(DublinCore.PROPERTY_CREATOR.getLocalName());
    if (presentersMetadataField.isUpdated()) {
      Set<String> presenterUsernames = new HashSet<>();
      Tuple<List<String>, Set<String>> updatedPresenters = getTechnicalPresenters(eventMetadata);
      presenterUsernames = updatedPresenters.getB();
      eventMetadata.removeField(presentersMetadataField);
      MetadataField<Iterable<String>> newPresentersMetadataField = MetadataUtils
              .copyMetadataField(presentersMetadataField);
      newPresentersMetadataField.setValue(updatedPresenters.getA());
      eventMetadata.addField(newPresentersMetadataField);
      return Opt.some(presenterUsernames);
    } else {
      return Opt.none();
    }
  }

  private MediaPackage updateDublincCoreCatalog(MediaPackage mp, DublinCoreCatalog dc)
          throws IOException, MediaPackageException, IngestException {
    try (InputStream inputStream = IOUtils.toInputStream(dc.toXmlString(), "UTF-8")) {
      // Update dublincore catalog
      Catalog[] catalogs = mp.getCatalogs(MediaPackageElements.EPISODE);
      if (catalogs.length > 0) {
        Catalog catalog = catalogs[0];
        URI uri = workspace.put(mp.getIdentifier().toString(), catalog.getIdentifier(), "dublincore.xml", inputStream);
        catalog.setURI(uri);
        // setting the URI to a new source so the checksum will most like be invalid
        catalog.setChecksum(null);
      } else {
        mp = ingestService.addCatalog(inputStream, "dublincore.xml", MediaPackageElements.EPISODE, mp);
      }
    }
    return mp;
  }

  /**
   * Giving a start time and end time with a recurrence rule and a timezone, all periods of the recurrence rule are
   * calculated taken daylight saving time into account.
   *
   *
   * @param start
   *          the start date time
   * @param end
   *          the end date
   * @param duration
   *          the duration
   * @param rRule
   *          the recurrence rule
   * @param tz
   * @return a list of scheduling periods
   */
  protected List<Period> calculatePeriods(Date start, Date end, long duration, RRule rRule, TimeZone tz) {
    final TimeZone utc = TimeZone.getTimeZone("UTC");
    TimeZone.setDefault(tz);
    DateTime seed = new DateTime(start);
    DateTime period = new DateTime();

    Calendar endCalendar = Calendar.getInstance(utc);
    endCalendar.setTime(end);
    Calendar calendar = Calendar.getInstance(utc);
    calendar.setTime(seed);
    calendar.set(Calendar.DAY_OF_MONTH, endCalendar.get(Calendar.DAY_OF_MONTH));
    calendar.set(Calendar.MONTH, endCalendar.get(Calendar.MONTH));
    calendar.set(Calendar.YEAR, endCalendar.get(Calendar.YEAR));
    period.setTime(calendar.getTime().getTime() + duration);
    duration = duration % (DateTimeConstants.MILLIS_PER_DAY);

    List<Period> periods = new ArrayList<>();

    TimeZone.setDefault(utc);
    for (Object date : rRule.getRecur().getDates(seed, period, Value.DATE_TIME)) {
      Date d = (Date) date;
      Calendar cDate = Calendar.getInstance(utc);

      // Adjust for DST, if start of event
      if (tz.inDaylightTime(seed)) { // Event starts in DST
        if (!tz.inDaylightTime(d)) { // Date not in DST?
          d.setTime(d.getTime() + tz.getDSTSavings()); // Adjust for Fall back one hour
        }
      } else { // Event doesn't start in DST
        if (tz.inDaylightTime(d)) {
          d.setTime(d.getTime() - tz.getDSTSavings()); // Adjust for Spring forward one hour
        }
      }
      cDate.setTime(d);

      periods.add(new Period(new DateTime(cDate.getTime()), new DateTime(cDate.getTimeInMillis() + duration)));
    }

    TimeZone.setDefault(null);
    return periods;
  }

  @Override
  public MetadataList updateCommonEventMetadata(String id, String metadataJSON, AbstractSearchIndex index)
          throws IllegalArgumentException, IndexServiceException, SearchIndexException, NotFoundException,
          UnauthorizedException {
    MetadataList metadataList;
    try {
      metadataList = getMetadataListWithCommonEventCatalogUIAdapter();
      metadataList.fromJSON(metadataJSON);
    } catch (Exception e) {
      logger.warn("Not able to parse the event metadata {}: {}", metadataJSON, getStackTrace(e));
      throw new IllegalArgumentException("Not able to parse the event metadata " + metadataJSON, e);
    }
    return updateEventMetadata(id, metadataList, index);
  }

  @Override
  public MetadataList updateAllEventMetadata(String id, String metadataJSON, AbstractSearchIndex index)
          throws IllegalArgumentException, IndexServiceException, NotFoundException, SearchIndexException,
          UnauthorizedException {
    MetadataList metadataList;
    try {
      metadataList = getMetadataListWithAllEventCatalogUIAdapters();
      metadataList.fromJSON(metadataJSON);
    } catch (Exception e) {
      logger.warn("Not able to parse the event metadata {}: {}", metadataJSON, getStackTrace(e));
      throw new IllegalArgumentException("Not able to parse the event metadata " + metadataJSON, e);
    }
    return updateEventMetadata(id, metadataList, index);
  }

  @Override
  public void removeCatalogByFlavor(Event event, MediaPackageElementFlavor flavor)
          throws IndexServiceException, NotFoundException, UnauthorizedException {
    MediaPackage mediaPackage = getEventMediapackage(event);
    Catalog[] catalogs = mediaPackage.getCatalogs(flavor);
    if (catalogs.length == 0) {
      throw new NotFoundException(String.format("Cannot find a catalog with flavor '%s' for event with id '%s'.",
              flavor.toString(), event.getIdentifier()));
    }
    for (Catalog catalog : catalogs) {
      mediaPackage.remove(catalog);
    }
    switch (getEventSource(event)) {
      case WORKFLOW:
        Opt<WorkflowInstance> workflowInstance = getCurrentWorkflowInstance(event.getIdentifier());
        if (workflowInstance.isNone()) {
          logger.error("No workflow instance for event {} found!", event.getIdentifier());
          throw new IndexServiceException("No workflow instance found for event " + event.getIdentifier());
        }
        try {
          WorkflowInstance instance = workflowInstance.get();
          instance.setMediaPackage(mediaPackage);
          updateWorkflowInstance(instance);
        } catch (WorkflowException e) {
          logger.error("Unable to remove catalog with flavor {} by updating workflow event {} because {}",
                  new Object[] { flavor, event.getIdentifier(), getStackTrace(e) });
          throw new IndexServiceException("Unable to update workflow event " + event.getIdentifier());
        }
        break;
      case ARCHIVE:
        assetManager.takeSnapshot(DEFAULT_OWNER, mediaPackage);
        break;
      case SCHEDULE:
        try {
          schedulerService.updateEvent(event.getIdentifier(), Opt.<Date> none(), Opt.<Date> none(), Opt.<String> none(),
                  Opt.<Set<String>> none(), Opt.some(mediaPackage), Opt.<Map<String, String>> none(),
                  Opt.<Map<String, String>> none(), Opt.<Opt<Boolean>> none(), SchedulerService.ORIGIN);
        } catch (SchedulerException e) {
          logger.error("Unable to remove catalog with flavor {} by updating scheduled event {} because {}",
                  new Object[] { flavor, event.getIdentifier(), getStackTrace(e) });
          throw new IndexServiceException("Unable to update scheduled event " + event.getIdentifier());
        }
        break;
      default:
        throw new IndexServiceException(
                String.format("Unable to handle event source type '%s'", getEventSource(event)));
    }
  }

  @Override
  public void removeCatalogByFlavor(Series series, MediaPackageElementFlavor flavor)
          throws NotFoundException, IndexServiceException {
    if (series == null) {
      throw new IllegalArgumentException("The series cannot be null.");
    }
    if (flavor == null) {
      throw new IllegalArgumentException("The flavor cannot be null.");
    }
    boolean found = false;
    try {
      found = seriesService.deleteSeriesElement(series.getIdentifier(), flavor.getType());
    } catch (SeriesException e) {
      throw new IndexServiceException(String.format("Unable to delete catalog from series '%s' with type '%s'",
              series.getIdentifier(), flavor.getType()), e);
    }

    if (!found) {
      throw new NotFoundException(String.format("Unable to find a catalog for series '%s' with flavor '%s'",
              series.getIdentifier(), flavor));
    }
  }

  @Override
  public MetadataList updateEventMetadata(String id, MetadataList metadataList, AbstractSearchIndex index)
          throws IndexServiceException, SearchIndexException, NotFoundException, UnauthorizedException {
    Opt<Event> optEvent = getEvent(id, index);
    if (optEvent.isNone())
      throw new NotFoundException("Cannot find an event with id " + id);

    Event event = optEvent.get();
    MediaPackage mediaPackage = getEventMediapackage(event);
    Opt<Set<String>> presenters = Opt.none();
    Opt<MetadataCollection> eventCatalog = metadataList.getMetadataByAdapter(getCommonEventCatalogUIAdapter());
    if (eventCatalog.isSome()) {
      presenters = updatePresenters(eventCatalog.get());
    }
    updateMediaPackageMetadata(mediaPackage, metadataList);
    switch (getEventSource(event)) {
      case WORKFLOW:
        Opt<WorkflowInstance> workflowInstance = getCurrentWorkflowInstance(event.getIdentifier());
        if (workflowInstance.isNone()) {
          logger.error("No workflow instance for event {} found!", event.getIdentifier());
          throw new IndexServiceException("No workflow instance found for event " + event.getIdentifier());
        }
        try {
          WorkflowInstance instance = workflowInstance.get();
          instance.setMediaPackage(mediaPackage);
          updateWorkflowInstance(instance);
        } catch (WorkflowException e) {
          logger.error("Unable to update workflow event {} with metadata {} because {}",
                  new Object[] { id, RestUtils.getJsonStringSilent(metadataList.toJSON()), getStackTrace(e) });
          throw new IndexServiceException("Unable to update workflow event " + id);
        }
        break;
      case ARCHIVE:
        assetManager.takeSnapshot(DEFAULT_OWNER, mediaPackage);
        break;
      case SCHEDULE:
        try {
          schedulerService.updateEvent(id, Opt.<Date> none(), Opt.<Date> none(), Opt.<String> none(), presenters,
                  Opt.some(mediaPackage), Opt.<Map<String, String>> none(), Opt.<Map<String, String>> none(),
                  Opt.<Opt<Boolean>> none(), SchedulerService.ORIGIN);
        } catch (SchedulerException e) {
          logger.error("Unable to update scheduled event {} with metadata {} because {}",
                  new Object[] { id, RestUtils.getJsonStringSilent(metadataList.toJSON()), getStackTrace(e) });
          throw new IndexServiceException("Unable to update scheduled event " + id);
        }
        break;
      default:
        logger.error("Unkown event source!");
    }
    return metadataList;
  }

  /**
   * Processes the combined usernames and free text entries of the presenters (creator) field into a list of presenters
   * using the full names of the users if available and adds the usernames to a set of technical presenters.
   *
   * @param eventMetadata
   *          The metadata list that has the presenter (creator) field to pull the list of presenters from.
   * @return A {@link Tuple} with a list of friendly presenter names and a set of user names if available for the
   *         presenters.
   */
  protected Tuple<List<String>, Set<String>> getTechnicalPresenters(MetadataCollection eventMetadata) {
    MetadataField<?> presentersMetadataField = eventMetadata.getOutputFields()
            .get(DublinCore.PROPERTY_CREATOR.getLocalName());
    List<String> presenters = new ArrayList<>();
    Set<String> technicalPresenters = new HashSet<>();
    for (String presenter : MetadataUtils.getIterableStringMetadata(presentersMetadataField)) {
      User user = userDirectoryService.loadUser(presenter);
      if (user == null) {
        presenters.add(presenter);
      } else {
        String fullname = StringUtils.isNotBlank(user.getName()) ? user.getName() : user.getUsername();
        presenters.add(fullname);
        technicalPresenters.add(user.getUsername());
      }
    }
    return Tuple.tuple(presenters, technicalPresenters);
  }

  @Override
  public AccessControlList updateEventAcl(String id, AccessControlList acl, AbstractSearchIndex index)
          throws IllegalArgumentException, IndexServiceException, SearchIndexException, NotFoundException,
          UnauthorizedException {
    Opt<Event> optEvent = getEvent(id, index);
    if (optEvent.isNone())
      throw new NotFoundException("Cannot find an event with id " + id);

    Event event = optEvent.get();
    MediaPackage mediaPackage = getEventMediapackage(event);
    switch (getEventSource(event)) {
      case WORKFLOW:
        // Not updating the acl as the workflow might have already passed the point of distribution.
        throw new IllegalArgumentException("Unable to update the ACL of this event as it is currently processing.");
      case ARCHIVE:
        mediaPackage = authorizationService.setAcl(mediaPackage, AclScope.Episode, acl).getA();
        assetManager.takeSnapshot(DEFAULT_OWNER, mediaPackage);
        return acl;
      case SCHEDULE:
        mediaPackage = authorizationService.setAcl(mediaPackage, AclScope.Episode, acl).getA();
        try {
          schedulerService.updateEvent(id, Opt.<Date> none(), Opt.<Date> none(), Opt.<String> none(),
                  Opt.<Set<String>> none(), Opt.some(mediaPackage), Opt.<Map<String, String>> none(),
                  Opt.<Map<String, String>> none(), Opt.<Opt<Boolean>> none(), SchedulerService.ORIGIN);
        } catch (SchedulerException e) {
          throw new IndexServiceException("Unable to update the acl for the scheduled event", e);
        }
        return acl;
      default:
        logger.error("Unknown event source '{}' unable to update ACL!", getEventSource(event));
        throw new IndexServiceException(
                String.format("Unable to update the ACL as '{}' is an unknown event source.", getEventSource(event)));
    }
  }

  @Override
  public SearchResult<Group> getGroups(String filter, Opt<Integer> optLimit, Opt<Integer> optOffset,
          Opt<String> optSort, AbstractSearchIndex index) throws SearchIndexException {
    GroupSearchQuery query = new GroupSearchQuery(securityService.getOrganization().getId(), securityService.getUser());

    // Parse the filters
    if (StringUtils.isNotBlank(filter)) {
      for (String f : filter.split(",")) {
        String[] filterTuple = f.split(":");
        if (filterTuple.length < 2) {
          logger.info("No value for filter {} in filters list: {}", filterTuple[0], filter);
          continue;
        }

        String name = filterTuple[0];
        String value = filterTuple[1];

        if (GroupsListQuery.FILTER_NAME_NAME.equals(name))
          query.withName(value);
      }
    }

    if (optSort.isSome()) {
      Set<SortCriterion> sortCriteria = RestUtils.parseSortQueryParameter(optSort.get());
      for (SortCriterion criterion : sortCriteria) {
        switch (criterion.getFieldName()) {
          case GroupIndexSchema.NAME:
            query.sortByName(criterion.getOrder());
            break;
          case GroupIndexSchema.DESCRIPTION:
            query.sortByDescription(criterion.getOrder());
            break;
          case GroupIndexSchema.ROLE:
            query.sortByRole(criterion.getOrder());
            break;
          case GroupIndexSchema.MEMBERS:
            query.sortByMembers(criterion.getOrder());
            break;
          case GroupIndexSchema.ROLES:
            query.sortByRoles(criterion.getOrder());
            break;
          default:
            throw new WebApplicationException(Status.BAD_REQUEST);
        }
      }
    }

    if (optLimit.isSome())
      query.withLimit(optLimit.get());
    if (optOffset.isSome())
      query.withOffset(optOffset.get());

    return index.getByQuery(query);
  }

  @Override
  public Opt<Group> getGroup(String id, AbstractSearchIndex index) throws SearchIndexException {
    SearchResult<Group> result = index
            .getByQuery(new GroupSearchQuery(securityService.getOrganization().getId(), securityService.getUser())
                    .withIdentifier(id));

    // If the results list if empty, we return already a response.
    if (result.getPageSize() == 0) {
      logger.debug("Didn't find event with id {}", id);
      return Opt.none();
    }
    return Opt.some(result.getItems()[0].getSource());
  }

  @Override
  public Response removeGroup(String id) throws NotFoundException {
    return jpaGroupRoleProvider.removeGroup(id);
  }

  @Override
  public Response updateGroup(String id, String name, String description, String roles, String members)
          throws NotFoundException {
    return jpaGroupRoleProvider.updateGroup(id, name, description, roles, members);
  }

  @Override
  public Response createGroup(String name, String description, String roles, String members) {
    if (StringUtils.isEmpty(roles))
      roles = "";
    if (StringUtils.isEmpty(members))
      members = "";
    return jpaGroupRoleProvider.createGroup(name, description, roles, members);
  }

  /**
   * Get a single event
   *
   * @param id
   *          the mediapackage id
   * @return an event or none if not found wrapped in an option
   * @throws SearchIndexException
   */
  @Override
  public Opt<Event> getEvent(String id, AbstractSearchIndex index) throws SearchIndexException {
    SearchResult<Event> result = index
            .getByQuery(new EventSearchQuery(securityService.getOrganization().getId(), securityService.getUser())
                    .withIdentifier(id));
    // If the results list if empty, we return already a response.
    if (result.getPageSize() == 0) {
      logger.debug("Didn't find event with id {}", id);
      return Opt.none();
    }
    return Opt.some(result.getItems()[0].getSource());
  }

  @Override
  public boolean removeEvent(String id) throws NotFoundException, UnauthorizedException {
    boolean unauthorizedScheduler = false;
    boolean notFoundScheduler = false;
    boolean removedScheduler = true;
    try {
      schedulerService.removeEvent(id);
    } catch (NotFoundException e) {
      notFoundScheduler = true;
    } catch (UnauthorizedException e) {
      unauthorizedScheduler = true;
    } catch (SchedulerException e) {
      removedScheduler = false;
      logger.error("Unable to remove the event '{}' from scheduler service: {}", id, getStackTrace(e));
    }

    boolean unauthorizedWorkflow = false;
    boolean notFoundWorkflow = false;
    boolean removedWorkflow = true;
    try {
      WorkflowQuery workflowQuery = new WorkflowQuery().withMediaPackage(id);
      WorkflowSet workflowSet = workflowService.getWorkflowInstances(workflowQuery);
      if (workflowSet.size() == 0)
        notFoundWorkflow = true;
      for (WorkflowInstance instance : workflowSet.getItems()) {
        workflowService.stop(instance.getId());
        workflowService.remove(instance.getId());
      }
    } catch (NotFoundException e) {
      notFoundWorkflow = true;
    } catch (UnauthorizedException e) {
      unauthorizedWorkflow = true;
    } catch (WorkflowDatabaseException e) {
      removedWorkflow = false;
      logger.error("Unable to remove the event '{}' because removing workflow failed: {}", id, getStackTrace(e));
    } catch (WorkflowException e) {
      removedWorkflow = false;
      logger.error("Unable to remove the event '{}' because removing workflow failed: {}", id, getStackTrace(e));
    }

    boolean unauthorizedArchive = false;
    boolean notFoundArchive = false;
    boolean removedArchive = true;
    try {
      final AQueryBuilder q = assetManager.createQuery();
      final Predicate p = q.organizationId().eq(securityService.getOrganization().getId()).and(q.mediaPackageId(id));
      final AResult r = q.select(q.nothing()).where(p).run();
      if (r.getSize() > 0) {
        q.delete(DEFAULT_OWNER, q.snapshot()).where(p).run();
      } else {
        notFoundArchive = true;
      }
    } catch (AssetManagerException e) {
      if (e.getCause() instanceof UnauthorizedException) {
        unauthorizedArchive = true;
      } else if (e.getCause() instanceof NotFoundException) {
        notFoundArchive = true;
      } else {
        removedArchive = false;
        logger.error("Unable to remove the event '{}' from the archive: {}", id, getStackTrace(e));
      }
    }

    if (notFoundScheduler && notFoundWorkflow && notFoundArchive)
      throw new NotFoundException("Event id " + id + " not found.");

    if (unauthorizedScheduler || unauthorizedWorkflow || unauthorizedArchive)
      throw new UnauthorizedException("Not authorized to remove event id " + id);

    try {
      eventCommentService.deleteComments(id);
    } catch (EventCommentException e) {
      logger.error("Unable to remove comments for event '{}': {}", id, getStackTrace(e));
    }

    return removedScheduler && removedWorkflow && removedArchive;
  }

  @Override
  public void updateWorkflowInstance(WorkflowInstance workflowInstance)
          throws WorkflowException, UnauthorizedException {
    // Only update the workflow if the instance is in a working state
    if (WorkflowInstance.WorkflowState.FAILED.equals(workflowInstance.getState())
            || WorkflowInstance.WorkflowState.FAILING.equals(workflowInstance.getState())
            || WorkflowInstance.WorkflowState.STOPPED.equals(workflowInstance.getState())
            || WorkflowInstance.WorkflowState.SUCCEEDED.equals(workflowInstance.getState())) {
      logger.info("Skip updating {} workflow mediapackage {} with updated comments catalog",
              workflowInstance.getState(), workflowInstance.getMediaPackage().getIdentifier().toString());
      return;
    }
    workflowService.update(workflowInstance);
  }

  @Override
  public MediaPackage getEventMediapackage(Event event) throws IndexServiceException {
    switch (getEventSource(event)) {
      case WORKFLOW:
        Opt<WorkflowInstance> currentWorkflowInstance = getCurrentWorkflowInstance(event.getIdentifier());
        if (currentWorkflowInstance.isNone()) {
          logger.error("No workflow instance for event {} found!", event.getIdentifier());
          throw new IndexServiceException("No workflow instance found for event " + event.getIdentifier());
        }
        return currentWorkflowInstance.get().getMediaPackage();
      case ARCHIVE:
        final AQueryBuilder q = assetManager.createQuery();
        final AResult r = q.select(q.snapshot())
                .where(q.mediaPackageId(event.getIdentifier()).and(q.version().isLatest())).run();
        if (r.getSize() > 0) {
          logger.debug("Found event in archive with id {}", event.getIdentifier());
          return enrich(r).getSnapshots().head2().getMediaPackage();
        }
        logger.error("No event with id {} found from archive!", event.getIdentifier());
        throw new IndexServiceException("No archived event found with id " + event.getIdentifier());
      case SCHEDULE:
        try {
          MediaPackage mediaPackage = schedulerService.getMediaPackage(event.getIdentifier());
          logger.debug("Found event in scheduler with id {}", event.getIdentifier());
          return mediaPackage;
        } catch (NotFoundException e) {
          logger.error("No scheduled event with id {} found!", event.getIdentifier());
          throw new IndexServiceException(e.getMessage(), e);
        } catch (UnauthorizedException e) {
          logger.error("Unauthorized to get event with id {} from scheduler because {}", event.getIdentifier(),
                  getStackTrace(e));
          throw new IndexServiceException(e.getMessage(), e);
        } catch (SchedulerException e) {
          logger.error("Unable to get event with id {} from scheduler because {}", event.getIdentifier(),
                  getStackTrace(e));
          throw new IndexServiceException(e.getMessage(), e);
        }
      default:
        throw new IllegalStateException("Unknown event type!");
    }
  }

  /**
   * Determines in a very basic way what kind of source the event is
   *
   * @param event
   *          the event
   * @return the source type
   */
  @Override
  public Source getEventSource(Event event) {
    if (event.getWorkflowId() != null && isWorkflowActive(event.getWorkflowState()))
      return Source.WORKFLOW;

    if (event.getSchedulingStatus() != null && !event.hasRecordingStarted())
      return Source.SCHEDULE;

    if (event.getArchiveVersion() != null)
      return Source.ARCHIVE;

    if (event.getWorkflowId() != null)
      return Source.WORKFLOW;

    return Source.SCHEDULE;
  }

  @Override
  public Opt<WorkflowInstance> getCurrentWorkflowInstance(String mpId) throws IndexServiceException {
    WorkflowQuery query = new WorkflowQuery().withMediaPackage(mpId);
    WorkflowSet workflowInstances;
    try {
      workflowInstances = workflowService.getWorkflowInstances(query);
      if (workflowInstances.size() == 0) {
        logger.info("No workflow instance found for mediapackage {}.", mpId);
        return Opt.none();
      }
    } catch (WorkflowDatabaseException e) {
      logger.error("Unable to get workflows for event {} because {}", mpId, getStackTrace(e));
      throw new IndexServiceException("Unable to get current workflow for event " + mpId);
    }
    // Get the newest workflow instance
    // TODO This presuppose knowledge of the Database implementation and should be fixed sooner or later!
    WorkflowInstance workflowInstance = workflowInstances.getItems()[0];
    for (WorkflowInstance instance : workflowInstances.getItems()) {
      if (instance.getId() > workflowInstance.getId())
        workflowInstance = instance;
    }
    return Opt.some(workflowInstance);
  }

  private void updateMediaPackageMetadata(MediaPackage mp, MetadataList metadataList) {
    List<EventCatalogUIAdapter> catalogUIAdapters = getEventCatalogUIAdapters();
    if (catalogUIAdapters.size() > 0) {
      for (EventCatalogUIAdapter catalogUIAdapter : catalogUIAdapters) {
        Opt<MetadataCollection> metadata = metadataList.getMetadataByAdapter(catalogUIAdapter);
        if (metadata.isSome() && metadata.get().isUpdated()) {
          catalogUIAdapter.storeFields(mp, metadata.get());
        }
      }
    }
  }

  @Override
  public String createSeries(MetadataList metadataList, Map<String, String> options, Opt<AccessControlList> optAcl,
          Opt<Long> optThemeId) throws IndexServiceException {
    DublinCoreCatalog dc = DublinCores.mkOpencastSeries().getCatalog();
    dc.set(PROPERTY_IDENTIFIER, UUID.randomUUID().toString());
    dc.set(DublinCore.PROPERTY_CREATED, EncodingSchemeUtils.encodeDate(new Date(), Precision.Second));
    for (Entry<String, String> entry : options.entrySet()) {
      dc.set(new EName(DublinCores.OC_PROPERTY_NS_URI, entry.getKey()), entry.getValue());
    }

    Opt<MetadataCollection> seriesMetadata = metadataList.getMetadataByFlavor(MediaPackageElements.SERIES.toString());
    if (seriesMetadata.isSome()) {
      DublinCoreMetadataUtil.updateDublincoreCatalog(dc, seriesMetadata.get());
    }

    AccessControlList acl;
    if (optAcl.isSome()) {
      acl = optAcl.get();
    } else {
      acl = new AccessControlList();
    }

    String seriesId;
    try {
      DublinCoreCatalog createdSeries = seriesService.updateSeries(dc);
      seriesId = createdSeries.getFirst(PROPERTY_IDENTIFIER);
      seriesService.updateAccessControl(seriesId, acl);
      for (Long id : optThemeId)
        seriesService.updateSeriesProperty(seriesId, THEME_PROPERTY_NAME, Long.toString(id));
    } catch (Exception e) {
      logger.error("Unable to create new series: {}", getStackTrace(e));
      throw new IndexServiceException("Unable to create new series");
    }

    updateSeriesMetadata(seriesId, metadataList);

    return seriesId;
  }

  @Override
  public String createSeries(String metadata)
          throws IllegalArgumentException, IndexServiceException, UnauthorizedException {
    JSONObject metadataJson = null;
    try {
      metadataJson = (JSONObject) new JSONParser().parse(metadata);
    } catch (Exception e) {
      logger.warn("Unable to parse metadata {}", metadata);
      throw new IllegalArgumentException("Unable to parse metadata" + metadata);
    }

    if (metadataJson == null)
      throw new IllegalArgumentException("No metadata set to create series");

    JSONArray seriesMetadataJson = (JSONArray) metadataJson.get("metadata");
    if (seriesMetadataJson == null)
      throw new IllegalArgumentException("No metadata field in metadata");

    JSONObject options = (JSONObject) metadataJson.get("options");
    if (options == null)
      throw new IllegalArgumentException("No options field in metadata");

    Opt<Long> themeId = Opt.none();
    Long theme = (Long) metadataJson.get("theme");
    if (theme != null) {
      themeId = Opt.some(theme);
    }

    Map<String, String> optionsMap;
    try {
      optionsMap = JSONUtils.toMap(new org.codehaus.jettison.json.JSONObject(options.toJSONString()));
    } catch (JSONException e) {
      logger.warn("Unable to parse options to map: {}", getStackTrace(e));
      throw new IllegalArgumentException("Unable to parse options to map");
    }

    DublinCoreCatalog dc = DublinCores.mkOpencastSeries().getCatalog();
    dc.set(PROPERTY_IDENTIFIER, UUID.randomUUID().toString());
    dc.set(DublinCore.PROPERTY_CREATED, EncodingSchemeUtils.encodeDate(new Date(), Precision.Second));
    for (Entry<String, String> entry : optionsMap.entrySet()) {
      dc.set(new EName(DublinCores.OC_PROPERTY_NS_URI, entry.getKey()), entry.getValue());
    }

    MetadataList metadataList;
    try {
      metadataList = getMetadataListWithAllSeriesCatalogUIAdapters();
      metadataList.fromJSON(seriesMetadataJson.toJSONString());
    } catch (Exception e) {
      logger.warn("Not able to parse the series metadata {}: {}", seriesMetadataJson, getStackTrace(e));
      throw new IllegalArgumentException("Not able to parse the series metadata");
    }

    Opt<MetadataCollection> seriesMetadata = metadataList.getMetadataByFlavor(MediaPackageElements.SERIES.toString());
    if (seriesMetadata.isSome()) {
      DublinCoreMetadataUtil.updateDublincoreCatalog(dc, seriesMetadata.get());
    }

    AccessControlList acl = getAccessControlList(metadataJson);

    String seriesId;
    try {
      DublinCoreCatalog createdSeries = seriesService.updateSeries(dc);
      seriesId = createdSeries.getFirst(PROPERTY_IDENTIFIER);
      seriesService.updateAccessControl(seriesId, acl);
      for (Long id : themeId)
        seriesService.updateSeriesProperty(seriesId, THEME_PROPERTY_NAME, Long.toString(id));
    } catch (Exception e) {
      logger.error("Unable to create new series: {}", getStackTrace(e));
      throw new IndexServiceException("Unable to create new series");
    }

    updateSeriesMetadata(seriesId, metadataList);

    return seriesId;
  }

  @Override
  public Opt<Series> getSeries(String seriesId, AbstractSearchIndex searchIndex) throws SearchIndexException {
    SearchResult<Series> result = searchIndex
            .getByQuery(new SeriesSearchQuery(securityService.getOrganization().getId(), securityService.getUser())
                    .withIdentifier(seriesId));
    // If the results list if empty, we return already a response.
    if (result.getPageSize() == 0) {
      logger.debug("Didn't find series with id {}", seriesId);
      return Opt.none();
    }
    return Opt.some(result.getItems()[0].getSource());
  }

  @Override
  public void removeSeries(String id) throws NotFoundException, SeriesException, UnauthorizedException {
    SeriesQuery seriesQuery = new SeriesQuery();
    seriesQuery.setSeriesId(id);
    DublinCoreCatalogList dublinCoreCatalogList = seriesService.getSeries(seriesQuery);
    if (dublinCoreCatalogList.size() == 0) {
      throw new NotFoundException();
    }
    seriesService.deleteSeries(id);
  }

  @Override
  public MetadataList updateCommonSeriesMetadata(String id, String metadataJSON, AbstractSearchIndex index)
          throws IllegalArgumentException, IndexServiceException, NotFoundException, UnauthorizedException {
    MetadataList metadataList = getMetadataListWithCommonSeriesCatalogUIAdapters();
    return updateSeriesMetadata(id, metadataJSON, index, metadataList);
  }

  @Override
  public MetadataList updateAllSeriesMetadata(String id, String metadataJSON, AbstractSearchIndex index)
          throws IllegalArgumentException, IndexServiceException, NotFoundException, UnauthorizedException {
    MetadataList metadataList = getMetadataListWithAllSeriesCatalogUIAdapters();
    return updateSeriesMetadata(id, metadataJSON, index, metadataList);
  }

  @Override
  public MetadataList updateAllSeriesMetadata(String id, MetadataList metadataList, AbstractSearchIndex index)
          throws IndexServiceException, NotFoundException, UnauthorizedException {
    checkSeriesExists(id, index);
    updateSeriesMetadata(id, metadataList);
    return metadataList;
  }

  @Override
  public void updateCommentCatalog(final Event event, final List<EventComment> comments) throws Exception {
    final SecurityContext securityContext = new SecurityContext(securityService, securityService.getOrganization(),
            securityService.getUser());
    executorService.execute(new Runnable() {
      @Override
      public void run() {
        securityContext.runInContext(new Effect0() {
          @Override
          protected void run() {
            try {
              MediaPackage mediaPackage = getEventMediapackage(event);
              updateMediaPackageCommentCatalog(mediaPackage, comments);
              switch (getEventSource(event)) {
                case WORKFLOW:
                  logger.info("Update workflow mediapacakge {} with updated comments catalog.", event.getIdentifier());
                  Opt<WorkflowInstance> workflowInstance = getCurrentWorkflowInstance(event.getIdentifier());
                  if (workflowInstance.isNone()) {
                    logger.error("No workflow instance for event {} found!", event.getIdentifier());
                    throw new IndexServiceException("No workflow instance found for event " + event.getIdentifier());
                  }
                  WorkflowInstance instance = workflowInstance.get();
                  instance.setMediaPackage(mediaPackage);
                  updateWorkflowInstance(instance);
                  break;
                case ARCHIVE:
                  logger.info("Update archive mediapacakge {} with updated comments catalog.", event.getIdentifier());
                  assetManager.takeSnapshot(DEFAULT_OWNER, mediaPackage);
                  break;
                case SCHEDULE:
                  logger.info("Update scheduled mediapacakge {} with updated comments catalog.", event.getIdentifier());
                  schedulerService.updateEvent(event.getIdentifier(), Opt.<Date> none(), Opt.<Date> none(),
                          Opt.<String> none(), Opt.<Set<String>> none(), Opt.some(mediaPackage),
                          Opt.<Map<String, String>> none(), Opt.<Map<String, String>> none(), Opt.<Opt<Boolean>> none(),
                          SchedulerService.ORIGIN);
                  break;
                default:
                  logger.error("Unkown event source {}!", event.getSource().toString());
              }
            } catch (Exception e) {
              logger.error("Unable to update event {} comment catalog: {}", event.getIdentifier(), getStackTrace(e));
            }
          }
        });
      }
    });
  }

  private void updateMediaPackageCommentCatalog(MediaPackage mediaPackage, List<EventComment> comments)
          throws EventCommentException, IOException {
    // Get the comments catalog
    Catalog[] commentCatalogs = mediaPackage.getCatalogs(MediaPackageElements.COMMENTS);
    Catalog c = null;
    if (commentCatalogs.length == 1)
      c = commentCatalogs[0];

    if (comments.size() > 0) {
      // If no comments catalog found, create a new one
      if (c == null) {
        c = (Catalog) MediaPackageElementBuilderFactory.newInstance().newElementBuilder().newElement(Type.Catalog,
                MediaPackageElements.COMMENTS);
        c.setIdentifier(UUID.randomUUID().toString());
        mediaPackage.add(c);
      }

      // Update comments catalog
      InputStream in = null;
      try {
        String commentCatalog = EventCommentParser.getAsXml(comments);
        in = IOUtils.toInputStream(commentCatalog, "UTF-8");
        URI uri = workspace.put(mediaPackage.getIdentifier().toString(), c.getIdentifier(), "comments.xml", in);
        c.setURI(uri);
        // setting the URI to a new source so the checksum will most like be invalid
        c.setChecksum(null);
      } finally {
        IOUtils.closeQuietly(in);
      }
    } else {
      // Remove comments catalog
      if (c != null) {
        mediaPackage.remove(c);
        try {
          workspace.delete(c.getURI());
        } catch (NotFoundException e) {
          logger.warn("Comments catalog {} not found to delete!", c.getURI());
        }
      }
    }
  }

  @Override
  public void changeOptOutStatus(String eventId, boolean optout, AbstractSearchIndex index)
          throws NotFoundException, SchedulerException, SearchIndexException, UnauthorizedException {
    Opt<Event> optEvent = getEvent(eventId, index);
    if (optEvent.isNone())
      throw new NotFoundException("Cannot find an event with id " + eventId);

    schedulerService.updateEvent(eventId, Opt.<Date> none(), Opt.<Date> none(), Opt.<String> none(),
            Opt.<Set<String>> none(), Opt.<MediaPackage> none(), Opt.<Map<String, String>> none(),
            Opt.<Map<String, String>> none(), Opt.some(Opt.some(optout)), SchedulerService.ORIGIN);
    logger.debug("Setting event {} to opt out status of {}", eventId, optout);
  }

  /**
   * Checks to see if a given series exists.
   *
   * @param seriesID
   *          The id of the series.
   * @param index
   *          The index to check for the particular series.
   * @throws NotFoundException
   *           Thrown if unable to find the series.
   * @throws IndexServiceException
   *           Thrown if unable to access the index to get the series.
   */
  private void checkSeriesExists(String seriesID, AbstractSearchIndex index)
          throws NotFoundException, IndexServiceException {
    try {
      Opt<Series> optSeries = getSeries(seriesID, index);
      if (optSeries.isNone())
        throw new NotFoundException("Cannot find a series with id " + seriesID);
    } catch (SearchIndexException e) {
      logger.error("Unable to get a series with id {} because: {}", seriesID, getStackTrace(e));
      throw new IndexServiceException("Cannot use search service to find Series");
    }
  }

  private MetadataList updateSeriesMetadata(String seriesID, String metadataJSON, AbstractSearchIndex index,
          MetadataList metadataList)
                  throws IllegalArgumentException, IndexServiceException, NotFoundException, UnauthorizedException {
    checkSeriesExists(seriesID, index);
    try {
      metadataList.fromJSON(metadataJSON);
    } catch (Exception e) {
      logger.warn("Not able to parse the event metadata {}: {}", metadataJSON, getStackTrace(e));
      throw new IllegalArgumentException("Not able to parse the event metadata");
    }

    updateSeriesMetadata(seriesID, metadataList);
    return metadataList;
  }

  /**
   * @return A {@link MetadataList} with only the common SeriesCatalogUIAdapter's empty {@link MetadataCollection}
   *         available
   */
  private MetadataList getMetadataListWithCommonSeriesCatalogUIAdapters() {
    MetadataList metadataList = new MetadataList();
    metadataList.add(seriesCatalogUIAdapter.getFlavor(), seriesCatalogUIAdapter.getUITitle(),
            seriesCatalogUIAdapter.getRawFields());
    return metadataList;
  }

  /**
   * @return A {@link MetadataList} with all of the available CatalogUIAdapters empty {@link MetadataCollection}
   *         available
   */
  @Override
  public MetadataList getMetadataListWithAllSeriesCatalogUIAdapters() {
    MetadataList metadataList = new MetadataList();
    for (SeriesCatalogUIAdapter adapter : getSeriesCatalogUIAdapters()) {
      metadataList.add(adapter.getFlavor(), adapter.getUITitle(), adapter.getRawFields());
    }
    return metadataList;
  }

  private MetadataList getMetadataListWithCommonEventCatalogUIAdapter() {
    MetadataList metadataList = new MetadataList();
    metadataList.add(eventCatalogUIAdapter, eventCatalogUIAdapter.getRawFields());
    return metadataList;
  }

  @Override
  public MetadataList getMetadataListWithAllEventCatalogUIAdapters() {
    MetadataList metadataList = new MetadataList();
    for (EventCatalogUIAdapter catalogUIAdapter : getEventCatalogUIAdapters()) {
      metadataList.add(catalogUIAdapter, catalogUIAdapter.getRawFields());
    }
    return metadataList;
  }

  /**
   * Checks the list of metadata for updated fields and stores/updates them in the respective metadata catalog.
   *
   * @param seriesId
   *          The series identifier
   * @param metadataList
   *          The metadata list
   */
  private void updateSeriesMetadata(String seriesId, MetadataList metadataList) {
    for (SeriesCatalogUIAdapter adapter : seriesCatalogUIAdapters) {
      Opt<MetadataCollection> metadata = metadataList.getMetadataByFlavor(adapter.getFlavor());
      if (metadata.isSome() && metadata.get().isUpdated()) {
        adapter.storeFields(seriesId, metadata.get());
      }
    }
  }

  public boolean isWorkflowActive(String workflowState) {
    return WorkflowState.INSTANTIATED.toString().equals(workflowState)
            || WorkflowState.RUNNING.toString().equals(workflowState)
            || WorkflowState.PAUSED.toString().equals(workflowState);
  }

  @Override
  public boolean hasActiveTransaction(String eventId)
          throws NotFoundException, UnauthorizedException, IndexServiceException {
    try {
      return schedulerService.hasActiveTransaction(eventId);
    } catch (SchedulerException e) {
      logger.error("Unable to get active transaction for scheduled event {} because {}", eventId, getStackTrace(e));
      throw new IndexServiceException("Unable to get active transaction for scheduled event " + eventId);
    } catch (NotFoundException e) {
      logger.trace("The event was not found by the scheduler so it can't be in an active transaction.");
      return false;
    }
  }

}
