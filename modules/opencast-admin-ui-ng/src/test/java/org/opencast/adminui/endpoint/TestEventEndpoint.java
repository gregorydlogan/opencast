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

package org.opencast.adminui.endpoint;

import static org.easymock.EasyMock.anyString;
import static org.easymock.EasyMock.expect;
import static org.opencast.capture.CaptureParameters.INGEST_WORKFLOW_DEFINITION;
import static org.opencast.index.service.util.CatalogAdapterUtil.getCatalogProperties;
import static org.opencast.util.DateTimeSupport.fromUTC;
import static org.opencast.util.IoSupport.withResource;
import static org.opencast.util.PropertiesUtil.toDictionary;
import static org.opencast.util.data.Collections.map;
import static org.opencast.util.data.Option.none;
import static org.opencast.util.data.Option.some;
import static org.opencast.util.data.Tuple.tuple;

import org.opencast.adminui.endpoint.AbstractEventEndpointTest.TestEnv;
import org.opencast.adminui.impl.AdminUIConfiguration;
import org.opencast.adminui.impl.index.AdminUISearchIndex;
import org.opencast.authorization.xacml.manager.api.AclService;
import org.opencast.authorization.xacml.manager.api.EpisodeACLTransition;
import org.opencast.authorization.xacml.manager.api.ManagedAcl;
import org.opencast.authorization.xacml.manager.api.SeriesACLTransition;
import org.opencast.authorization.xacml.manager.api.TransitionQuery;
import org.opencast.authorization.xacml.manager.api.TransitionResult;
import org.opencast.authorization.xacml.manager.impl.ManagedAclImpl;
import org.opencast.authorization.xacml.manager.impl.TransitionResultImpl;
import org.opencast.capture.CaptureParameters;
import org.opencast.capture.admin.api.Agent;
import org.opencast.capture.admin.api.CaptureAgentStateService;
import org.opencast.event.comment.EventComment;
import org.opencast.event.comment.EventCommentReply;
import org.opencast.event.comment.EventCommentService;
import org.opencast.fun.juc.Immutables;
import org.opencast.index.service.api.IndexService;
import org.opencast.index.service.api.IndexService.Source;
import org.opencast.index.service.catalog.adapter.MetadataList;
import org.opencast.index.service.catalog.adapter.events.CommonEventCatalogUIAdapter;
import org.opencast.index.service.impl.index.AbstractSearchIndex;
import org.opencast.index.service.impl.index.event.Event;
import org.opencast.index.service.impl.index.event.EventSearchQuery;
import org.opencast.index.service.impl.index.series.Series;
import org.opencast.index.service.resources.list.api.ListProvidersService;
import org.opencast.index.service.resources.list.api.ResourceListQuery;
import org.opencast.job.api.Incident;
import org.opencast.job.api.Incident.Severity;
import org.opencast.job.api.IncidentImpl;
import org.opencast.job.api.IncidentTree;
import org.opencast.job.api.IncidentTreeImpl;
import org.opencast.job.api.Job;
import org.opencast.job.api.JobImpl;
import org.opencast.matterhorn.search.SearchResultItem;
import org.opencast.matterhorn.search.impl.SearchResultImpl;
import org.opencast.matterhorn.search.impl.SearchResultItemImpl;
import org.opencast.mediapackage.Attachment;
import org.opencast.mediapackage.MediaPackage;
import org.opencast.mediapackage.MediaPackageBuilderFactory;
import org.opencast.mediapackage.MediaPackageBuilderImpl;
import org.opencast.mediapackage.Publication;
import org.opencast.mediapackage.PublicationImpl;
import org.opencast.mediapackage.Track;
import org.opencast.mediapackage.attachment.AttachmentImpl;
import org.opencast.mediapackage.identifier.IdImpl;
import org.opencast.mediapackage.track.AbstractStreamImpl;
import org.opencast.message.broker.api.MessageReceiver;
import org.opencast.message.broker.api.MessageSender;
import org.opencast.metadata.dublincore.DublinCoreCatalog;
import org.opencast.metadata.dublincore.DublinCores;
import org.opencast.metadata.dublincore.EventCatalogUIAdapter;
import org.opencast.metadata.dublincore.MetadataCollection;
import org.opencast.metadata.dublincore.StaticMetadataServiceDublinCoreImpl;
import org.opencast.scheduler.api.Recording;
import org.opencast.scheduler.api.SchedulerService;
import org.opencast.scheduler.api.SchedulerService.ReviewStatus;
import org.opencast.scheduler.api.TechnicalMetadata;
import org.opencast.scheduler.api.TechnicalMetadataImpl;
import org.opencast.security.api.AccessControlEntry;
import org.opencast.security.api.AccessControlList;
import org.opencast.security.api.AclScope;
import org.opencast.security.api.AuthorizationService;
import org.opencast.security.api.DefaultOrganization;
import org.opencast.security.api.JaxbOrganization;
import org.opencast.security.api.JaxbRole;
import org.opencast.security.api.JaxbUser;
import org.opencast.security.api.Organization;
import org.opencast.security.api.OrganizationDirectoryService;
import org.opencast.security.api.Permissions;
import org.opencast.security.api.SecurityService;
import org.opencast.security.api.User;
import org.opencast.security.urlsigning.service.UrlSigningService;
import org.opencast.series.impl.SeriesServiceDatabaseException;
import org.opencast.series.impl.SeriesServiceImpl;
import org.opencast.series.impl.solr.SeriesServiceSolrIndex;
import org.opencast.serviceregistry.api.IncidentService;
import org.opencast.serviceregistry.api.Incidents;
import org.opencast.serviceregistry.api.ServiceRegistry;
import org.opencast.util.DateTimeSupport;
import org.opencast.util.MimeType;
import org.opencast.util.NotFoundException;
import org.opencast.util.PropertiesUtil;
import org.opencast.util.data.Function;
import org.opencast.util.data.Option;
import org.opencast.util.data.Tuple;
import org.opencast.workflow.api.ConfiguredWorkflowRef;
import org.opencast.workflow.api.WorkflowDefinition;
import org.opencast.workflow.api.WorkflowDefinitionImpl;
import org.opencast.workflow.api.WorkflowInstanceImpl;
import org.opencast.workflow.api.WorkflowOperationDefinitionImpl;
import org.opencast.workflow.api.WorkflowQuery;
import org.opencast.workflow.api.WorkflowService;
import org.opencast.workflow.api.WorkflowSet;
import org.opencast.workflow.api.WorkflowSetImpl;
import org.opencast.workspace.api.Workspace;

import com.entwinemedia.fn.data.Opt;

import net.fortuna.ical4j.model.property.RRule;

import org.easymock.Capture;
import org.easymock.EasyMock;
import org.easymock.IAnswer;
import org.joda.time.DateTime;
import org.junit.Ignore;
import org.osgi.service.cm.ConfigurationException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TimeZone;

import javax.ws.rs.Path;

@Path("/")
@Ignore
public class TestEventEndpoint extends AbstractEventEndpoint {
  private final TestEnv env;

  private final MediaPackageBuilderImpl mpBuilder = new MediaPackageBuilderImpl();

  private final JaxbOrganization defaultOrganization = new DefaultOrganization();

  private final JaxbOrganization opencastOrganization = new JaxbOrganization("opencast.org");

  private static final String PREVIEW_SUBTYPE = "preview";

  /** A user with permissions. */
  private final User userWithPermissions = new JaxbUser("sample", null, "WithPermissions", "with@permissions.com",
          "test", defaultOrganization,
          new HashSet<>(Arrays.asList(new JaxbRole("ROLE_STUDENT", defaultOrganization, "test"),
                  new JaxbRole("ROLE_OTHERSTUDENT", defaultOrganization, "test"),
                  new JaxbRole(defaultOrganization.getAnonymousRole(), defaultOrganization, "test"))));

  /** A user without permissions. */
  private final User userWithoutPermissions = new JaxbUser("sample", null, "WithoutPermissions",
          "without@permissions.com", "test", opencastOrganization,
          new HashSet<>(Arrays.asList(new JaxbRole("ROLE_NOTHING", opencastOrganization, "test"))));

  private final User defaultUser = userWithPermissions;

  /** Answers with a constant response. */
  public static class Responder<A> implements IAnswer<A> {
    private A response;

    Responder(A response) {
      this.response = response;
    }

    public void setResponse(A response) {
      this.response = response;
    }

    @Override
    public A answer() throws Throwable {
      return response;
    }
  }

  public TestEventEndpoint() throws Exception {
    env = AbstractEventEndpointTest.testEnv();

    // security service
    final SecurityService securityService = EasyMock.createNiceMock(SecurityService.class);
    Responder<User> userResponder = new Responder<>(defaultUser);
    Responder<JaxbOrganization> organizationResponder = new Responder<>(defaultOrganization);
    EasyMock.expect(securityService.getUser()).andAnswer(userResponder).anyTimes();
    EasyMock.expect(securityService.getOrganization()).andAnswer(organizationResponder).anyTimes();
    EasyMock.replay(securityService);
    env.setSecurityService(securityService);

    UrlSigningService urlSigningService = EasyMock.createNiceMock(UrlSigningService.class);
    EasyMock.expect(urlSigningService.accepts(EasyMock.anyString())).andReturn(false).anyTimes();
    EasyMock.replay(urlSigningService);
    env.setUrlSigningService(urlSigningService);

    // Preview subtype
    AdminUIConfiguration adminUIConfiguration = new AdminUIConfiguration();
    Hashtable<String, String> dictionary = new Hashtable<>();
    dictionary.put(AdminUIConfiguration.OPT_PREVIEW_SUBTYPE, PREVIEW_SUBTYPE);
    adminUIConfiguration.updated(dictionary);
    env.setAdminUIConfiguration(adminUIConfiguration);

    // acl
    final String anonymousRole = securityService.getOrganization().getAnonymousRole();
    final AccessControlList acl = new AccessControlList(
            new AccessControlEntry(anonymousRole, Permissions.Action.READ.toString(), true));
    /* The authorization service */
    final AuthorizationService authorizationService = EasyMock.createNiceMock(AuthorizationService.class);
    EasyMock.expect(authorizationService.getActiveAcl((MediaPackage) EasyMock.anyObject()))
            .andReturn(new Tuple<>(acl, AclScope.Episode)).anyTimes();
    EasyMock.expect(
            authorizationService.hasPermission((MediaPackage) EasyMock.anyObject(), (String) EasyMock.anyObject()))
            .andReturn(true).anyTimes();

    EasyMock.expect(authorizationService.setAcl(EasyMock.anyObject(MediaPackage.class),
            EasyMock.anyObject(AclScope.class), EasyMock.anyObject(AccessControlList.class)))
            .andReturn(new Tuple<MediaPackage, Attachment>(loadMpFromResource("jobs_mediapackage1"),
                    new AttachmentImpl()));

    EasyMock.replay(authorizationService);

    env.setAuthorizationService(authorizationService);

    List<ManagedAcl> managedAcls = new ArrayList<>();
    ManagedAcl managedAcl1 = new ManagedAclImpl(43L, "Public", defaultOrganization.getId(), acl);
    managedAcls.add(managedAcl1);
    managedAcls.add(new ManagedAclImpl(44L, "Private", defaultOrganization.getId(), acl));

    Date transitionDate = new Date(DateTimeSupport.fromUTC("2014-06-05T15:00:00Z"));
    TransitionResult transitionResult = getTransitionResult(managedAcl1, transitionDate);

    AclService aclService = EasyMock.createNiceMock(AclService.class);
    EasyMock.expect(aclService.getAcls()).andReturn(managedAcls).anyTimes();
    EasyMock.expect(aclService.applyAclToEpisode(EasyMock.anyString(), EasyMock.anyObject(AccessControlList.class),
            EasyMock.anyObject(Option.class))).andReturn(true).anyTimes();
    EasyMock.expect(aclService.getTransitions(EasyMock.anyObject(TransitionQuery.class))).andReturn(transitionResult)
            .anyTimes();
    EasyMock.replay(aclService);

    env.setAclService(aclService);

    // service registry
    final ServiceRegistry serviceRegistry = EasyMock.createNiceMock(ServiceRegistry.class);
    JobImpl job = new JobImpl(12L);
    Date dateCreated = new Date(DateTimeSupport.fromUTC("2014-06-05T15:00:00Z"));
    Date dateCompleted = new Date(DateTimeSupport.fromUTC("2014-06-05T16:00:00Z"));
    job.setDateCreated(dateCreated);
    job.setDateCompleted(dateCompleted);
    EasyMock.expect(serviceRegistry.getJob(EasyMock.anyLong())).andReturn(job).anyTimes();
    EasyMock.expect(serviceRegistry.createJob((String) EasyMock.anyObject(), (String) EasyMock.anyObject(),
            (List<String>) EasyMock.anyObject(), (String) EasyMock.anyObject(), EasyMock.anyBoolean()))
            .andReturn(new JobImpl()).anyTimes();
    EasyMock.expect(serviceRegistry.updateJob((Job) EasyMock.anyObject())).andReturn(new JobImpl()).anyTimes();
    EasyMock.expect(serviceRegistry.getJobs((String) EasyMock.anyObject(), (Job.Status) EasyMock.anyObject()))
            .andReturn(new ArrayList<Job>()).anyTimes();
    EasyMock.replay(serviceRegistry);

    // Org directory
    OrganizationDirectoryService orgDirectory = EasyMock.createNiceMock(OrganizationDirectoryService.class);
    EasyMock.expect(orgDirectory.getOrganization((String) EasyMock.anyObject())).andReturn(defaultOrganization)
            .anyTimes();
    EasyMock.replay(orgDirectory);

    // workspace
    Workspace workspace = EasyMock.createNiceMock(Workspace.class);
    EasyMock.expect(workspace.get((URI) EasyMock.anyObject())).andAnswer(new IAnswer<File>() {
      @Override
      public File answer() throws Throwable {
        final URI uri = (URI) EasyMock.getCurrentArguments()[0];
        if ("file".equals(uri.getScheme())) {
          return new File(uri);
        } else if (uri.toString().startsWith("http://episodes/10.0000/1/publish-catalog-1/1/")) {
          return new File(getClass().getResource("/dublincore.xml").toURI());
        } else {
          return new File(getClass().getResource("/" + uri.toString()).toURI());
        }
      }
    }).anyTimes();
    EasyMock.replay(workspace);

    // workflow service
    final WorkflowSetImpl workflowSet = new WorkflowSetImpl();

    WorkflowDefinition wfD = new WorkflowDefinitionImpl();
    wfD.setTitle("Full");
    wfD.setId("full");
    WorkflowOperationDefinitionImpl wfDOp1 = new WorkflowOperationDefinitionImpl("ingest", "Ingest", "error", false);
    WorkflowOperationDefinitionImpl wfDOp2 = new WorkflowOperationDefinitionImpl("archive", "Archive", "error", true);
    wfD.add(wfDOp1);
    wfD.add(wfDOp2);

    WorkflowDefinitionImpl wfD2 = new WorkflowDefinitionImpl();
    wfD2.setTitle("Full HTML5");
    wfD2.setId("full-html5");
    wfD2.addTag("quick_actions");
    wfD2.addTag("test");
    wfD2.setDescription("Test description");
    wfD2.setConfigurationPanel("<h2>Test</h2>");

    MediaPackage mp1 = loadMpFromResource("jobs_mediapackage1");
    // the id is set dynamic - lets force an id so we can get a consistent json respons
    Track mp1Track = mp1.getTrack("publish-track-1");
    ((AbstractStreamImpl) mp1Track.getStreams()[0]).setIdentifier("fortesting");

    final WorkflowInstanceImpl workflowInstanceImpl1 = new WorkflowInstanceImpl(wfD, mp1, 2L, null, null,
            new HashMap<String, String>());
    final WorkflowInstanceImpl workflowInstanceImpl2 = new WorkflowInstanceImpl(wfD,
            loadMpFromResource("jobs_mediapackage2"), 2L, null, null, new HashMap<String, String>());
    final WorkflowInstanceImpl workflowInstanceImpl3 = new WorkflowInstanceImpl(wfD,
            loadMpFromResource("jobs_mediapackage3"), 2L, null, null, new HashMap<String, String>());

    workflowInstanceImpl1.setId(1);
    workflowInstanceImpl2.setId(2);
    workflowInstanceImpl3.setId(3);
    workflowInstanceImpl1.getOperations().get(0).setId(4L);
    workflowInstanceImpl1.getOperations().get(1).setId(5L);

    workflowSet.addItem(workflowInstanceImpl1);
    workflowSet.addItem(workflowInstanceImpl2);
    workflowSet.addItem(workflowInstanceImpl3);

    workflowSet.setTotalCount(3);

    WorkflowService workflowService = EasyMock.createNiceMock(WorkflowService.class);
    EasyMock.expect(workflowService.getWorkflowDefinitionById(EasyMock.anyString())).andReturn(wfD).anyTimes();
    EasyMock.expect(workflowService.getWorkflowById(EasyMock.anyLong())).andReturn(workflowInstanceImpl1).anyTimes();
    EasyMock.expect(workflowService.getWorkflowInstances(EasyMock.anyObject(WorkflowQuery.class)))
            .andAnswer(new IAnswer<WorkflowSet>() {
              @Override
              public WorkflowSet answer() throws Throwable {
                WorkflowQuery query = (WorkflowQuery) EasyMock.getCurrentArguments()[0];
                if (query.getId() != null && Long.parseLong(query.getId()) == 9999L) {
                  return new WorkflowSetImpl();
                } else {
                  return workflowSet;
                }
              }
            }).anyTimes();
    EasyMock.expect(workflowService.listAvailableWorkflowDefinitions()).andReturn(Arrays.asList(wfD, wfD2));
    EasyMock.replay(workflowService);
    env.setWorkflowService(workflowService);

    // series service
    final SeriesServiceImpl seriesService = new SeriesServiceImpl();
    final SeriesServiceSolrIndex seriesServiceSolrIndex = new SeriesServiceSolrIndex() {
      private final Map<String, String> idMap = map(tuple("series-a", "/series-dublincore-a.xml"),
              tuple("series-b", "/series-dublincore-b.xml"), tuple("series-c", "/series-dublincore-c.xml"),
              tuple("foobar-series", "/series-dublincore.xml"));

      @Override
      public DublinCoreCatalog getDublinCore(String seriesId) throws SeriesServiceDatabaseException, NotFoundException {
        String file = idMap.get(seriesId);
        if (file != null) {
          return withResource(getClass().getResourceAsStream(file), new Function<InputStream, DublinCoreCatalog>() {
            @Override
            public DublinCoreCatalog apply(InputStream is) {
              return DublinCores.read(is);
            }
          });
        }
        throw new Error("Mock error");
      }

      @Override
      public AccessControlList getAccessControl(String seriesID)
              throws NotFoundException, SeriesServiceDatabaseException {
        return acl;
      }

    };
    seriesService.setIndex(seriesServiceSolrIndex);

    StaticMetadataServiceDublinCoreImpl metadataSvcs = new StaticMetadataServiceDublinCoreImpl();
    metadataSvcs.setWorkspace(workspace);

    // Org directory
    MessageSender messageSender = EasyMock.createNiceMock(MessageSender.class);
    EasyMock.replay(messageSender);

    MessageReceiver messageReceiver = EasyMock.createNiceMock(MessageReceiver.class);
    EasyMock.replay(messageReceiver);

    final Date now = DateTime.parse("2014-06-05T09:15:56Z").toDate();
    EventComment comment = EventComment.create(Option.some(65L), "abc123", "mh_default_org", "Comment 1",
            userWithPermissions, "Sick", true, now, now);
    EventComment comment2 = EventComment.create(Option.some(65L), "abc123", "mh_default_org", "Comment 2",
            userWithPermissions, "Defect", false, now, now);
    EventCommentReply reply = EventCommentReply.create(Option.some(78L), "Cant reproduce", userWithoutPermissions, now,
            now);
    comment2.addReply(reply);

    final Capture<EventComment> c = Capture.newInstance();
    EventCommentService eventCommentService = EasyMock.createNiceMock(EventCommentService.class);
    EasyMock.expect(eventCommentService.getComments(EasyMock.anyString())).andReturn(Arrays.asList(comment, comment2))
            .anyTimes();
    EasyMock.expect(eventCommentService.getComment(65L)).andReturn(comment2);
    EasyMock.expect(eventCommentService.getComment(33L)).andReturn(comment2);
    EasyMock.expect(eventCommentService.getComment(99999L)).andReturn(null);
    EasyMock.expect(eventCommentService.updateComment(EasyMock.capture(c))).andAnswer(new IAnswer<EventComment>() {
      @Override
      public EventComment answer() throws Throwable {
        EventComment current = c.getValue();
        EventComment result = EventComment.create(Option.some(65L), current.getEventId(), current.getOrganization(),
                current.getText(), current.getAuthor(), current.getReason(), current.isResolvedStatus(), now, now,
                current.getReplies());
        return result;
      }
    });
    EasyMock.replay(eventCommentService);
    env.setEventCommentService(eventCommentService);

    Map<String, String> licences = new HashMap<>();
    licences.put("uuid-series1", "Series 1");
    licences.put("uuid-series2", "Series 2");

    ListProvidersService listProvidersService = EasyMock.createNiceMock(ListProvidersService.class);
    EasyMock.expect(listProvidersService.getList(EasyMock.anyString(), EasyMock.anyObject(ResourceListQuery.class),
            EasyMock.anyObject(Organization.class), EasyMock.anyBoolean())).andReturn(licences).anyTimes();
    EasyMock.replay(listProvidersService);

    final IncidentTree r = new IncidentTreeImpl(
            Immutables.list(mkIncident(Severity.INFO), mkIncident(Severity.INFO), mkIncident(Severity.INFO)),
            Immutables.<IncidentTree> list(
                    new IncidentTreeImpl(Immutables.list(mkIncident(Severity.INFO), mkIncident(Severity.WARNING)),
                            Immutables.<IncidentTree> list(new IncidentTreeImpl(
                                    Immutables.list(mkIncident(Severity.WARNING), mkIncident(Severity.INFO)), Immutables
                                            .<IncidentTree> nil())))));

    IncidentService incidentService = EasyMock.createNiceMock(IncidentService.class);
    EasyMock.expect(incidentService.getIncident(EasyMock.anyLong())).andReturn(mkIncident(Severity.INFO)).anyTimes();
    EasyMock.expect(incidentService.getIncidentsOfJob(EasyMock.anyLong(), EasyMock.anyBoolean())).andReturn(r)
            .anyTimes();
    EasyMock.replay(incidentService);

    JobEndpoint endpoint = new JobEndpoint();
    endpoint.setServiceRegistry(serviceRegistry);
    endpoint.setWorkflowService(workflowService);
    endpoint.setIncidentService(incidentService);
    endpoint.activate(null);
    env.setJobService(endpoint);

    // date, duration, title
    List<MediaPackage> events = new ArrayList<>();
    MediaPackage mp = MediaPackageBuilderFactory.newInstance().newMediaPackageBuilder().createNew();
    mp.setTitle("Land and Vegetation: Key players on the Climate Scene");
    mp.setDuration(36000L);
    mp.setDate(new Date());
    mp.setIdentifier(new IdImpl("asdasd"));
    events.add(mp);
    SchedulerService schedulerService = EasyMock.createNiceMock(SchedulerService.class);
    EasyMock.expect(schedulerService.search(EasyMock.anyObject(Opt.class), EasyMock.anyObject(Opt.class),
            EasyMock.anyObject(Opt.class), EasyMock.anyObject(Opt.class), EasyMock.anyObject(Opt.class)))
            .andReturn(events).anyTimes();
    EasyMock.expect(schedulerService.findConflictingEvents(EasyMock.anyString(), EasyMock.anyObject(RRule.class),
            EasyMock.anyObject(Date.class), EasyMock.anyObject(Date.class), EasyMock.anyLong(),
            EasyMock.anyObject(TimeZone.class))).andReturn(events).anyTimes();
    EasyMock.expect(schedulerService.findConflictingEvents(EasyMock.anyString(), EasyMock.anyObject(Date.class),
            EasyMock.anyObject(Date.class))).andReturn(events).anyTimes();
    schedulerService.updateEvent(EasyMock.anyString(), EasyMock.anyObject(Opt.class), EasyMock.anyObject(Opt.class),
            EasyMock.anyObject(Opt.class), EasyMock.anyObject(Opt.class), EasyMock.anyObject(Opt.class),
            EasyMock.anyObject(Opt.class), EasyMock.anyObject(Opt.class), EasyMock.anyObject(Opt.class),
            EasyMock.anyString());
    EasyMock.expectLastCall().anyTimes();
    EasyMock.expect(schedulerService.getWorkflowConfig("asdasd")).andThrow(new NotFoundException()).anyTimes();
    Map<String, String> workFlowConfig = new HashMap<>();
    workFlowConfig.put("someworkflowconfig", "somevalue");
    EasyMock.expect(schedulerService.getWorkflowConfig("workflowid")).andReturn(workFlowConfig).anyTimes();
    Map<String, String> captureAgentConfig = new HashMap<>();
    captureAgentConfig.put(INGEST_WORKFLOW_DEFINITION, "somevalue");
    expect(schedulerService.getCaptureAgentConfiguration("workflowid")).andReturn(captureAgentConfig).anyTimes();

    Set<String> userIds = new HashSet<>();
    userIds.add("user1");
    userIds.add("user2");
    Map<String, String> caProperties = new HashMap<>();
    caProperties.put("test", "true");
    caProperties.put("clear", "all");
    Map<String, String> wfProperties = new HashMap<>();
    wfProperties.put("test", "false");
    wfProperties.put("skip", "true");
    TechnicalMetadata technicalMetadata = new TechnicalMetadataImpl("asdasd", "demo",
            new Date(fromUTC("2017-01-27T10:00:37Z")), new Date(fromUTC("2017-01-27T10:10:37Z")), false, userIds,
            wfProperties, caProperties, Opt.<Recording> none());
    expect(schedulerService.getTechnicalMetadata(anyString())).andReturn(technicalMetadata).anyTimes();

    EasyMock.replay(schedulerService);
    env.setSchedulerService(schedulerService);

    CaptureAgentStateService captureAgentStateService = EasyMock.createNiceMock(CaptureAgentStateService.class);
    EasyMock.expect(captureAgentStateService.getAgent(EasyMock.anyString())).andReturn(getAgent()).anyTimes();
    EasyMock.replay(captureAgentStateService);
    env.setCaptureAgentStateService(captureAgentStateService);

    EventCatalogUIAdapter catalogUIAdapter = EasyMock.createNiceMock(EventCatalogUIAdapter.class);
    EasyMock.replay(catalogUIAdapter);

    CommonEventCatalogUIAdapter episodeDublinCoreCatalogUIAdapter = new CommonEventCatalogUIAdapter();

    Properties episodeCatalogProperties = getCatalogProperties(getClass(), "/episode-catalog.properties");

    episodeDublinCoreCatalogUIAdapter.updated(toDictionary(episodeCatalogProperties));

    Series series = new Series();
    series.setOptOut(true);

    Event event = createEvent("asdasd", "title", Source.SCHEDULE);
    Event event2 = createEvent("archivedid", "title 2", Source.ARCHIVE);
    Event event3 = createEvent("workflowid", "title 3", Source.WORKFLOW);

    SearchResultItem[] list = new SearchResultItem[3];
    list[0] = new SearchResultItemImpl<>(1, event);
    list[1] = new SearchResultItemImpl<>(1, event2);
    list[2] = new SearchResultItemImpl<>(1, event3);

    SearchResultImpl<Event> eventSearchResult = EasyMock.createNiceMock(SearchResultImpl.class);
    EasyMock.expect(eventSearchResult.getHitCount()).andReturn(3L).anyTimes();
    EasyMock.expect(eventSearchResult.getPageSize()).andReturn(3L).anyTimes();
    EasyMock.expect(eventSearchResult.getItems()).andReturn(list).anyTimes();
    EasyMock.replay(eventSearchResult);

    // AdminUISearchIndex
    AdminUISearchIndex searchIndex = EasyMock.createNiceMock(AdminUISearchIndex.class);
    EasyMock.expect(searchIndex.getByQuery(EasyMock.anyObject(EventSearchQuery.class))).andReturn(eventSearchResult)
            .anyTimes();
    EasyMock.replay(searchIndex);
    env.setIndex(searchIndex);

    List<EventCatalogUIAdapter> eventCatalogAdapterList = new ArrayList<>();
    eventCatalogAdapterList.add(createEventCatalogUIAdapter("name 1"));
    eventCatalogAdapterList.add(createEventCatalogUIAdapter("name 2"));

    IndexService indexService = EasyMock.createNiceMock(IndexService.class);
    EasyMock.expect(indexService.getEvent("asdasd", searchIndex)).andReturn(Opt.some(event)).anyTimes();
    EasyMock.expect(indexService.getEvent("archivedid", searchIndex)).andReturn(Opt.some(event2)).anyTimes();
    EasyMock.expect(indexService.getEvent("workflowid", searchIndex)).andReturn(Opt.some(event3)).anyTimes();
    EasyMock.expect(indexService.getEvent("notExists", searchIndex)).andReturn(Opt.<Event> none()).anyTimes();
    EasyMock.expect(indexService.getSeries("seriesId", searchIndex)).andReturn(Opt.some(series)).anyTimes();
    EasyMock.expect(indexService.getEventMediapackage(event)).andReturn(mp1).anyTimes();
    EasyMock.expect(indexService.getEventCatalogUIAdapters()).andReturn(eventCatalogAdapterList).anyTimes();
    EasyMock.expect(indexService.getCommonEventCatalogUIAdapter()).andReturn(eventCatalogAdapterList.get(0)).anyTimes();
    EasyMock.expect(indexService.getEventSource(event)).andReturn(Source.SCHEDULE).anyTimes();
    EasyMock.expect(indexService.getEventSource(event2)).andReturn(Source.ARCHIVE).anyTimes();
    EasyMock.expect(indexService.getEventSource(event3)).andReturn(Source.WORKFLOW).anyTimes();
    MetadataList metaDataList = new MetadataList();
    EasyMock.expect(indexService.updateAllEventMetadata(EasyMock.anyString(), EasyMock.anyString(),
            EasyMock.anyObject(AbstractSearchIndex.class))).andReturn(metaDataList).anyTimes();
    EasyMock.replay(indexService);
    env.setIndexService(indexService);
  }

  private Event createEvent(String id, String title, Source source) throws URISyntaxException {
    Event event = EasyMock.createNiceMock(Event.class);
    EasyMock.expect(event.getIdentifier()).andReturn(id).anyTimes();
    EasyMock.expect(event.getTitle()).andReturn(title).anyTimes();
    EasyMock.expect(event.getSeriesId()).andReturn("seriesId").anyTimes();
    EasyMock.expect(event.getReviewStatus()).andReturn(ReviewStatus.UNSENT.toString()).anyTimes();
    EasyMock.expect(event.getEventStatus()).andReturn("EVENTS.EVENTS.STATUS.ARCHIVE").anyTimes();
    EasyMock.expect(event.getOptedOut()).andReturn(true).anyTimes();
    EasyMock.expect(event.getRecordingStartDate()).andReturn("2013-03-20T04:00:00Z").anyTimes();
    EasyMock.expect(event.getRecordingEndDate()).andReturn("2013-03-20T05:00:00Z").anyTimes();
    EasyMock.expect(event.getTechnicalStartTime()).andReturn("2013-03-20T04:00:00Z").anyTimes();
    EasyMock.expect(event.getTechnicalEndTime()).andReturn("2013-03-20T05:00:00Z").anyTimes();
    List<Publication> publist = new ArrayList<>();
    publist.add(new PublicationImpl("engage", "rest", new URI("engage.html?e=p-1"), MimeType.mimeType("text", "xml")));
    EasyMock.expect(event.getPublications()).andReturn(publist).anyTimes();
    EasyMock.expect(event.getAccessPolicy())
            .andReturn(
                    "{\"acl\":{\"ace\":[{\"allow\":true,\"action\":\"read\",\"role\":\"ROLE_ADMIN\"},{\"allow\":true,\"action\":\"write\",\"role\":\"ROLE_ADMIN\"}]}}\"")
            .anyTimes();

    EasyMock.expect(event.hasRecordingStarted()).andReturn(true);

    // Simulate different event sources
    switch (source) {
      case ARCHIVE:
        EasyMock.expect(event.getArchiveVersion()).andReturn(1000L).anyTimes();
        break;
      case SCHEDULE:
        EasyMock.expect(event.getWorkflowId()).andReturn(1000L).anyTimes();
        break;
      default:
        // nothing!
    }
    EasyMock.replay(event);
    return event;
  }

  private EventCatalogUIAdapter createEventCatalogUIAdapter(final String s) throws IOException, ConfigurationException {
    Properties eventProperties = new Properties();
    InputStream in = getClass().getResourceAsStream("/episode-catalog.properties");
    eventProperties.load(in);
    in.close();

    Dictionary<String, String> properties = PropertiesUtil.toDictionary(eventProperties);
    CommonEventCatalogUIAdapter commonEventCatalogUIAdapter = new CommonEventCatalogUIAdapter() {
      @Override
      public String getUITitle() {
        return s;
      }

      @Override
      public MetadataCollection getFields(MediaPackage mediapackage) {
        return super.getFields(null);
      }
    };
    commonEventCatalogUIAdapter.updated(properties);
    return commonEventCatalogUIAdapter;
  }

  private Agent getAgent() {
    return new Agent() {
      @Override
      public void setUrl(String agentUrl) {
      }

      @Override
      public void setState(String newState) {
      }

      @Override
      public void setLastHeardFrom(Long time) {
      }

      @Override
      public void setConfiguration(Properties configuration) {
      }

      @Override
      public String getUrl() {
        return "10.234.12.323";
      }

      @Override
      public String getState() {
        return "idle";
      }

      @Override
      public String getName() {
        return "testagent";
      }

      @Override
      public Long getLastHeardFrom() {
        return 13345L;
      }

      @Override
      public Properties getConfiguration() {
        Properties properties = new Properties();
        properties.put(CaptureParameters.CAPTURE_DEVICE_NAMES, "input1,input2");
        properties.put(CaptureParameters.AGENT_NAME, "testagent");
        properties.put("capture.device.timezone.offset", "-360");
        properties.put("capture.device.timezone", "America/Los_Angeles");
        return properties;
      }

      @Override
      public Properties getCapabilities() {
        Properties properties = new Properties();
        properties.put(CaptureParameters.CAPTURE_DEVICE_NAMES, "input1,input2");
        return properties;
      }
    };
  }

  private TransitionResult getTransitionResult(final ManagedAcl macl, final Date now) {
    return new TransitionResultImpl(
            org.opencast.util.data.Collections.<EpisodeACLTransition> list(new EpisodeACLTransition() {
              @Override
              public String getEpisodeId() {
                return "episode";
              }

              @Override
              public Option<ManagedAcl> getAccessControlList() {
                return some(macl);
              }

              @Override
              public boolean isDelete() {
                return getAccessControlList().isNone();
              }

              @Override
              public long getTransitionId() {
                return 1L;
              }

              @Override
              public String getOrganizationId() {
                return "org";
              }

              @Override
              public Date getApplicationDate() {
                return now;
              }

              @Override
              public Option<ConfiguredWorkflowRef> getWorkflow() {
                return none();
              }

              @Override
              public boolean isDone() {
                return false;
              }
            }), org.opencast.util.data.Collections.<SeriesACLTransition> list(new SeriesACLTransition() {
              @Override
              public String getSeriesId() {
                return "series";
              }

              @Override
              public ManagedAcl getAccessControlList() {
                return macl;
              }

              @Override
              public boolean isOverride() {
                return true;
              }

              @Override
              public long getTransitionId() {
                return 2L;
              }

              @Override
              public String getOrganizationId() {
                return "org";
              }

              @Override
              public Date getApplicationDate() {
                return now;
              }

              @Override
              public Option<ConfiguredWorkflowRef> getWorkflow() {
                return none();
              }

              @Override
              public boolean isDone() {
                return false;
              }
            }));
  }

  private MediaPackage loadMpFromResource(String name) throws Exception {
    URI publishedMediaPackageURI = getClass().getResource("/" + name + ".xml").toURI();
    return mpBuilder.loadFromXml(publishedMediaPackageURI.toURL().openStream());
  }

  private Incident mkIncident(Severity s) throws Exception {
    Date date = new Date(DateTimeSupport.fromUTC("2014-06-05T09:15:56Z"));
    return new IncidentImpl(0, 0, "servicetype", "host", date, s, "code", Incidents.NO_DETAILS, Incidents.NO_PARAMS);
  }

  @Override
  public WorkflowService getWorkflowService() {
    return env.getWorkflowService();
  }

  @Override
  public JobEndpoint getJobService() {
    return env.getJobService();
  }

  @Override
  public AclService getAclService() {
    return env.getAclService();
  }

  @Override
  public EventCommentService getEventCommentService() {
    return env.getEventCommentService();
  }

  @Override
  public SecurityService getSecurityService() {
    return env.getSecurityService();
  }

  @Override
  public IndexService getIndexService() {
    return env.getIndexService();
  }

  @Override
  public AuthorizationService getAuthorizationService() {
    return env.getAuthorizationService();
  }

  @Override
  public SchedulerService getSchedulerService() {
    return env.getSchedulerService();
  }

  @Override
  public CaptureAgentStateService getCaptureAgentStateService() {
    return env.getCaptureAgentStateService();
  }

  @Override
  public AdminUISearchIndex getIndex() {
    return env.getIndex();
  }

  @Override
  public UrlSigningService getUrlSigningService() {
    return env.getUrlSigningService();
  }

  @Override
  public AdminUIConfiguration getAdminUIConfiguration() {
    return env.getAdminUIConfiguration();
  }

  @Override
  public long getUrlSigningExpireDuration() {
    return DEFAULT_URL_SIGNING_EXPIRE_DURATION;
  }

  @Override
  public Boolean signWithClientIP() {
    return false;
  }

}
