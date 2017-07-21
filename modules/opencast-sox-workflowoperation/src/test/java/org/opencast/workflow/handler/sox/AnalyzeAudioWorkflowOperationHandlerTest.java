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

package org.opencast.workflow.handler.sox;

import org.opencast.composer.api.ComposerService;
import org.opencast.job.api.Job;
import org.opencast.job.api.Job.Status;
import org.opencast.mediapackage.MediaPackage;
import org.opencast.mediapackage.MediaPackageBuilder;
import org.opencast.mediapackage.MediaPackageBuilderFactory;
import org.opencast.mediapackage.MediaPackageElementFlavor;
import org.opencast.mediapackage.Track;
import org.opencast.mediapackage.track.TrackImpl;
import org.opencast.security.api.DefaultOrganization;
import org.opencast.security.api.JaxbRole;
import org.opencast.security.api.JaxbUser;
import org.opencast.security.api.Organization;
import org.opencast.security.api.OrganizationDirectoryService;
import org.opencast.security.api.SecurityService;
import org.opencast.security.api.User;
import org.opencast.security.api.UserDirectoryService;
import org.opencast.serviceregistry.api.IncidentService;
import org.opencast.serviceregistry.api.ServiceRegistry;
import org.opencast.serviceregistry.api.ServiceRegistryInMemoryImpl;
import org.opencast.sox.api.SoxService;
import org.opencast.workflow.api.WorkflowInstance;
import org.opencast.workflow.api.WorkflowOperationInstanceImpl;
import org.opencast.workflow.api.WorkflowOperationResult;
import org.opencast.workflow.api.WorkflowOperationResult.Action;
import org.opencast.workspace.api.Workspace;

import org.apache.commons.io.IOUtils;
import org.easymock.EasyMock;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.net.URI;

public class AnalyzeAudioWorkflowOperationHandlerTest {

  private AnalyzeAudioWorkflowOperationHandler operationHandler;
  private MediaPackage mp;
  private URI uriMP;
  private WorkflowInstance instance;
  private WorkflowOperationInstanceImpl operationInstance = new WorkflowOperationInstanceImpl();

  @Before
  public void setUp() throws Exception {
    MediaPackageBuilder builder = MediaPackageBuilderFactory.newInstance().newMediaPackageBuilder();
    uriMP = AnalyzeAudioWorkflowOperationHandler.class.getResource("/sox_mediapackage.xml").toURI();
    mp = builder.loadFromXml(uriMP.toURL().openStream());

    URI soxTrackUri = AnalyzeAudioWorkflowOperationHandler.class.getResource("/sox-track.xml").toURI();
    URI soxEncodeUri = AnalyzeAudioWorkflowOperationHandler.class.getResource("/sox-encode-track.xml").toURI();

    String soxTrackXml = IOUtils.toString(soxTrackUri.toURL().openStream());
    String encodeTrackXml = IOUtils.toString(soxEncodeUri.toURL().openStream());

    instance = EasyMock.createNiceMock(WorkflowInstance.class);
    EasyMock.expect(instance.getMediaPackage()).andReturn(mp).anyTimes();
    EasyMock.expect(instance.getCurrentOperation()).andReturn(operationInstance).anyTimes();
    EasyMock.replay(instance);

    DefaultOrganization org = new DefaultOrganization();
    User anonymous = new JaxbUser("anonymous", "test", org, new JaxbRole(
            DefaultOrganization.DEFAULT_ORGANIZATION_ANONYMOUS, org));
    UserDirectoryService userDirectoryService = EasyMock.createMock(UserDirectoryService.class);
    EasyMock.expect(userDirectoryService.loadUser((String) EasyMock.anyObject())).andReturn(anonymous).anyTimes();
    EasyMock.replay(userDirectoryService);

    Organization organization = new DefaultOrganization();
    OrganizationDirectoryService organizationDirectoryService = EasyMock.createMock(OrganizationDirectoryService.class);
    EasyMock.expect(organizationDirectoryService.getOrganization((String) EasyMock.anyObject()))
            .andReturn(organization).anyTimes();
    EasyMock.replay(organizationDirectoryService);

    SecurityService securityService = EasyMock.createNiceMock(SecurityService.class);
    EasyMock.expect(securityService.getUser()).andReturn(anonymous).anyTimes();
    EasyMock.expect(securityService.getOrganization()).andReturn(organization).anyTimes();
    EasyMock.replay(securityService);

    IncidentService incidentService = EasyMock.createNiceMock(IncidentService.class);
    EasyMock.replay(incidentService);

    ServiceRegistry serviceRegistry = new ServiceRegistryInMemoryImpl(null, securityService, userDirectoryService,
            organizationDirectoryService, incidentService);

    Job analyzeJob = serviceRegistry.createJob(SoxService.JOB_TYPE, "Analyze", null, soxTrackXml, false);
    analyzeJob.setStatus(Status.FINISHED);
    analyzeJob = serviceRegistry.updateJob(analyzeJob);

    Job encodeJob = serviceRegistry.createJob(ComposerService.JOB_TYPE, "Encode", null, encodeTrackXml, false);
    encodeJob.setStatus(Status.FINISHED);
    encodeJob = serviceRegistry.updateJob(encodeJob);

    SoxService sox = EasyMock.createNiceMock(SoxService.class);
    EasyMock.expect(sox.analyze((Track) EasyMock.anyObject())).andReturn(analyzeJob).anyTimes();
    EasyMock.replay(sox);

    ComposerService composer = EasyMock.createNiceMock(ComposerService.class);
    EasyMock.expect(composer.encode((Track) EasyMock.anyObject(), (String) EasyMock.anyObject())).andReturn(encodeJob);
    EasyMock.replay(composer);

    Workspace workspace = EasyMock.createNiceMock(Workspace.class);
    EasyMock.replay(workspace);

    // set up the handler
    operationHandler = new AnalyzeAudioWorkflowOperationHandler();
    operationHandler.setJobBarrierPollingInterval(0);
    operationHandler.setComposerService(composer);
    operationHandler.setSoxService(sox);
    operationHandler.setWorkspace(workspace);
    operationHandler.setServiceRegistry(serviceRegistry);
  }

  @Test
  public void testAudioVideo() throws Exception {
    operationInstance.setConfiguration("source-tags", "");
    operationInstance.setConfiguration("source-flavor", "*/video-audio");
    operationInstance.setConfiguration("source-flavors", "");
    operationInstance.setConfiguration("force-transcode", "false");

    WorkflowOperationResult result = operationHandler.start(instance, null);
    Assert.assertEquals(Action.CONTINUE, result.getAction());

    Assert.assertEquals("Resulting mediapackage has the wrong number of tracks", 3, result.getMediaPackage()
            .getElements().length);
    Track[] tracks = result.getMediaPackage().getTracks(new MediaPackageElementFlavor("presentation", "video-audio"));
    Assert.assertEquals("Resulting mediapackage has the wrong number of tracks", 1, tracks.length);
    TrackImpl audioVideo = (TrackImpl) tracks[0];
    Assert.assertEquals(-20f, audioVideo.getAudio().get(0).getRmsLevDb().floatValue(), 0.001d);
  }

  @Test
  public void testAudioContainer() throws Exception {
    operationInstance.setConfiguration("source-tags", "");
    operationInstance.setConfiguration("source-flavor", "*/container-audio");
    operationInstance.setConfiguration("source-flavors", "");
    operationInstance.setConfiguration("force-transcode", "true");

    WorkflowOperationResult result = operationHandler.start(instance, null);
    Assert.assertEquals(Action.CONTINUE, result.getAction());

    Assert.assertEquals("Resulting mediapackage has the wrong number of tracks", 3, result.getMediaPackage()
            .getElements().length);
    Track[] tracks = result.getMediaPackage().getTracks(
            new MediaPackageElementFlavor("presentation", "container-audio"));
    Assert.assertEquals("Resulting mediapackage has the wrong number of tracks", 1, tracks.length);
    TrackImpl audioVideo = (TrackImpl) tracks[0];
    Assert.assertEquals(-20f, audioVideo.getAudio().get(0).getRmsLevDb().floatValue(), 0.001d);
  }

  @Test
  public void testAudio() throws Exception {
    operationInstance.setConfiguration("source-tags", "");
    operationInstance.setConfiguration("source-flavor", "*/audio");
    operationInstance.setConfiguration("source-flavors", "");
    operationInstance.setConfiguration("force-transcode", "false");

    WorkflowOperationResult result = operationHandler.start(instance, null);
    Assert.assertEquals(Action.CONTINUE, result.getAction());

    Assert.assertEquals("Resulting mediapackage has the wrong number of tracks", 3, result.getMediaPackage()
            .getElements().length);
    Track[] tracks = result.getMediaPackage().getTracks(new MediaPackageElementFlavor("presentation", "audio"));
    Assert.assertEquals("Resulting mediapackage has the wrong number of tracks", 1, tracks.length);
    TrackImpl audioVideo = (TrackImpl) tracks[0];
    Assert.assertEquals(-20f, audioVideo.getAudio().get(0).getRmsLevDb().floatValue(), 0.001d);
  }

}
