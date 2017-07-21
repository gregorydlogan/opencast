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

package org.opencast.workflow.handler.composer;

import org.opencast.composer.api.ComposerService;
import org.opencast.composer.api.EncoderException;
import org.opencast.composer.api.EncodingProfile;
import org.opencast.composer.api.EncodingProfile.MediaType;
import org.opencast.composer.api.LaidOutElement;
import org.opencast.composer.layout.Dimension;
import org.opencast.job.api.Job;
import org.opencast.mediapackage.Attachment;
import org.opencast.mediapackage.MediaPackage;
import org.opencast.mediapackage.MediaPackageBuilder;
import org.opencast.mediapackage.MediaPackageBuilderFactory;
import org.opencast.mediapackage.MediaPackageElementParser;
import org.opencast.mediapackage.MediaPackageException;
import org.opencast.mediapackage.Track;
import org.opencast.serviceregistry.api.ServiceRegistry;
import org.opencast.serviceregistry.api.ServiceRegistryException;
import org.opencast.util.MimeTypes;
import org.opencast.util.NotFoundException;
import org.opencast.util.data.Option;
import org.opencast.workflow.api.WorkflowInstance.WorkflowState;
import org.opencast.workflow.api.WorkflowInstanceImpl;
import org.opencast.workflow.api.WorkflowOperationException;
import org.opencast.workflow.api.WorkflowOperationInstance;
import org.opencast.workflow.api.WorkflowOperationInstance.OperationState;
import org.opencast.workflow.api.WorkflowOperationInstanceImpl;
import org.opencast.workflow.api.WorkflowOperationResult;
import org.opencast.workflow.api.WorkflowOperationResult.Action;
import org.opencast.workflow.handler.inspection.InspectWorkflowOperationHandler;
import org.opencast.workspace.api.Workspace;

import org.easymock.EasyMock;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CompositeWorkflowOperationHandlerTest {
  private CompositeWorkflowOperationHandler operationHandler;

  // local resources
  private MediaPackage mp;
  private MediaPackage mpEncode;
  private Job job;
  private Track[] encodedTracks;

  // mock services and objects
  private EncodingProfile profile = null;
  private ComposerService composerService = null;
  private Workspace workspace = null;

  // constant metadata values
  private static final String PROFILE_ID = "composite";
  private static final String COMPOUND_TRACK_ID = "compound-workflow-operation-test-work";

  private static final String TEST_LAYOUT = "{\"horizontalCoverage\":1.0,\"anchorOffset\":{\"referring\":{\"left\":1.0,\"top\":1.0},\"offset\":{\"y\":-20,\"x\":-20},\"reference\":{\"left\":1.0,\"top\":1.0}}};{\"horizontalCoverage\":0.2,\"anchorOffset\":{\"referring\":{\"left\":0.0,\"top\":0.0},\"offset\":{\"y\":-20,\"x\":-20},\"reference\":{\"left\":0.0,\"top\":0.0}}};{\"horizontalCoverage\":1.0,\"anchorOffset\":{\"referring\":{\"left\":1.0,\"top\":0.0},\"offset\":{\"y\":20,\"x\":20},\"reference\":{\"left\":1.0,\"top\":0.0}}}";
  private static final String TEST_SINGLE_LAYOUT = "{\"horizontalCoverage\":1.0,\"anchorOffset\":{\"referring\": {\"left\":1.0,\"top\":1.0} ,\"offset\": {\"y\":-20,\"x\":-20} ,\"reference\": {\"left\":1.0,\"top\":1.0} }}; {\"horizontalCoverage\":1.0,\"anchorOffset\":{\"referring\": {\"left\":1.0,\"top\":0.0} ,\"offset\": {\"y\":20,\"x\":20} ,\"reference\": {\"left\":1.0,\"top\":0.0} }}";
  @Before
  public void setUp() throws Exception {
    MediaPackageBuilder builder = MediaPackageBuilderFactory.newInstance().newMediaPackageBuilder();

    // test resources
    URI uriMP = InspectWorkflowOperationHandler.class.getResource("/composite_mediapackage.xml").toURI();
    URI uriMPEncode = InspectWorkflowOperationHandler.class.getResource("/compound_mediapackage.xml").toURI();
    mp = builder.loadFromXml(uriMP.toURL().openStream());
    mpEncode = builder.loadFromXml(uriMPEncode.toURL().openStream());
    encodedTracks = mpEncode.getTracks();

    // set up mock workspace
    workspace = EasyMock.createNiceMock(Workspace.class);
    EasyMock.expect(
            workspace.moveTo((URI) EasyMock.anyObject(), (String) EasyMock.anyObject(), (String) EasyMock.anyObject(),
                    (String) EasyMock.anyObject())).andReturn(uriMPEncode);
    EasyMock.expect(workspace.get((URI) EasyMock.anyObject())).andReturn(
            new File(getClass().getResource("/watermark.jpg").toURI()));
    EasyMock.replay(workspace);

    // set up mock receipt
    job = EasyMock.createNiceMock(Job.class);
    EasyMock.expect(job.getPayload()).andReturn(MediaPackageElementParser.getAsXml(encodedTracks[0])).anyTimes();
    EasyMock.expect(job.getStatus()).andReturn(Job.Status.FINISHED);
    EasyMock.expect(job.getDateCreated()).andReturn(new Date());
    EasyMock.expect(job.getDateStarted()).andReturn(new Date());
    EasyMock.expect(job.getQueueTime()).andReturn(new Long(0));
    EasyMock.replay(job);

    // set up mock service registry
    ServiceRegistry serviceRegistry = EasyMock.createNiceMock(ServiceRegistry.class);
    EasyMock.expect(serviceRegistry.getJob(EasyMock.anyLong())).andReturn(job);
    EasyMock.replay(serviceRegistry);

    // set up service
    operationHandler = new CompositeWorkflowOperationHandler();
    operationHandler.setJobBarrierPollingInterval(0);
    operationHandler.setWorkspace(workspace);
    operationHandler.setServiceRegistry(serviceRegistry);
  }

  @Test
  public void testAll() throws Exception {
    setMockups();

    // operation configuration
    String targetTags = "engage,compound";
    Map<String, String> configurations = new HashMap<String, String>();
    configurations.put("source-flavor-upper", "presenter/source");
    configurations.put("source-flavor-lower", "presentation/source");
    configurations.put("source-flavor-watermark", "watermark/source");
    configurations.put("source-url-watermark", getClass().getResource("/watermark.jpg").toExternalForm());
    configurations.put("target-tags", targetTags);
    configurations.put("target-flavor", "composite/work");
    configurations.put("encoding-profile", "composite");
    configurations.put("layout", "test");
    configurations.put("layout-test", TEST_LAYOUT);
    configurations.put("layout-single", TEST_SINGLE_LAYOUT);
    configurations.put("output-resolution", "1900x1080");
    configurations.put("output-background", "black");

    // run the operation handler
    WorkflowOperationResult result = getWorkflowOperationResult(mp, configurations);

    // check track metadata
    MediaPackage mpNew = result.getMediaPackage();
    Assert.assertEquals(Action.CONTINUE, result.getAction());
    Track trackEncoded = mpNew.getTrack(COMPOUND_TRACK_ID);
    Assert.assertEquals("composite/work", trackEncoded.getFlavor().toString());
    Assert.assertTrue(Arrays.asList(targetTags.split("\\W")).containsAll(Arrays.asList(trackEncoded.getTags())));
  }

  @Test
  public void testWithoutWatermark() throws Exception {
    setMockups();

    // operation configuration
    String targetTags = "engage,compound";
    Map<String, String> configurations = new HashMap<String, String>();
    configurations.put("source-flavor-upper", "presenter/source");
    configurations.put("source-flavor-lower", "presentation/source");
    configurations.put("target-tags", targetTags);
    configurations.put("target-flavor", "composite/work");
    configurations.put("encoding-profile", "composite");
    configurations.put("layout", "test");
    configurations.put("layout-test", TEST_LAYOUT);
    configurations.put("layout-single", TEST_SINGLE_LAYOUT);
    configurations.put("output-resolution", "1900x1080");
    configurations.put("output-background", "black");

    // run the operation handler
    WorkflowOperationResult result = getWorkflowOperationResult(mp, configurations);

    // check track metadata
    MediaPackage mpNew = result.getMediaPackage();
    Assert.assertEquals(Action.CONTINUE, result.getAction());
    Track trackEncoded = mpNew.getTrack(COMPOUND_TRACK_ID);
    Assert.assertEquals("composite/work", trackEncoded.getFlavor().toString());
    Assert.assertTrue(Arrays.asList(targetTags.split("\\W")).containsAll(Arrays.asList(trackEncoded.getTags())));
  }

  @Test
  public void testSingleLayout() throws Exception {
    setMockups();

    // operation configuration
    String targetTags = "engage,compound";
    Map<String, String> configurations = new HashMap<String, String>();
    configurations.put("source-flavor-upper", "presenter/source");
    configurations.put("source-flavor-lower", "presentation/source");
    configurations.put("target-tags", targetTags);
    configurations.put("target-flavor", "composite/work");
    configurations.put("encoding-profile", "composite");
    configurations.put("layout", TEST_LAYOUT);
    configurations.put("layout-single", TEST_SINGLE_LAYOUT);
    configurations.put("output-resolution", "1900x1080");
    configurations.put("output-background", "black");

    // run the operation handler
    WorkflowOperationResult result = getWorkflowOperationResult(mp, configurations);

    // check track metadata
    MediaPackage mpNew = result.getMediaPackage();
    Assert.assertEquals(Action.CONTINUE, result.getAction());
    Track trackEncoded = mpNew.getTrack(COMPOUND_TRACK_ID);
    Assert.assertEquals("composite/work", trackEncoded.getFlavor().toString());
    Assert.assertTrue(Arrays.asList(targetTags.split("\\W")).containsAll(Arrays.asList(trackEncoded.getTags())));
  }

  @Test
  public void testMissingLayout() throws Exception {
    setMockups();

    // operation configuration
    String targetTags = "engage,compound";
    Map<String, String> configurations = new HashMap<String, String>();
    configurations.put("source-flavor-upper", "presenter/source");
    configurations.put("source-flavor-lower", "presentation/source");
    configurations.put("target-tags", targetTags);
    configurations.put("target-flavor", "composite/work");
    configurations.put("encoding-profile", "composite");
    configurations.put("layout", "test");
    configurations.put("layout-single", TEST_SINGLE_LAYOUT);
    configurations.put("output-resolution", "1900x1080");
    configurations.put("output-background", "black");

    // run the operation handler
    try {
      getWorkflowOperationResult(mp, configurations);
    } catch (WorkflowOperationException e) {
      return;
    }
    Assert.fail("No error occurred when using missing layout");
  }

  @Test
  public void testSingleVideoStream() throws URISyntaxException, MalformedURLException, MediaPackageException, IOException, IllegalArgumentException, NotFoundException, ServiceRegistryException {
    MediaPackageBuilder builder = MediaPackageBuilderFactory.newInstance().newMediaPackageBuilder();

    // test resources
    URI uriMP = InspectWorkflowOperationHandler.class.getResource("/composite_mediapackage.xml").toURI();
    URI uriMPEncode = InspectWorkflowOperationHandler.class.getResource("/compound_mediapackage.xml").toURI();
    mp = builder.loadFromXml(uriMP.toURL().openStream());
    mpEncode = builder.loadFromXml(uriMPEncode.toURL().openStream());
    encodedTracks = mpEncode.getTracks();

    // set up mock workspace
    workspace = EasyMock.createNiceMock(Workspace.class);
    EasyMock.expect(
            workspace.moveTo((URI) EasyMock.anyObject(), (String) EasyMock.anyObject(), (String) EasyMock.anyObject(),
                    (String) EasyMock.anyObject())).andReturn(uriMPEncode);
    EasyMock.expect(workspace.get((URI) EasyMock.anyObject())).andReturn(
            new File(getClass().getResource("/watermark.jpg").toURI()));
    EasyMock.replay(workspace);

    // set up mock receipt
    job = EasyMock.createNiceMock(Job.class);
    EasyMock.expect(job.getPayload()).andReturn(MediaPackageElementParser.getAsXml(encodedTracks[0])).anyTimes();
    EasyMock.expect(job.getStatus()).andReturn(Job.Status.FINISHED);
    EasyMock.expect(job.getDateCreated()).andReturn(new Date());
    EasyMock.expect(job.getDateStarted()).andReturn(new Date());
    EasyMock.expect(job.getQueueTime()).andReturn(new Long(0));
    EasyMock.replay(job);

    // set up mock service registry
    ServiceRegistry serviceRegistry = EasyMock.createNiceMock(ServiceRegistry.class);
    EasyMock.expect(serviceRegistry.getJob(EasyMock.anyLong())).andReturn(job);
    EasyMock.replay(serviceRegistry);

    // set up service
    operationHandler = new CompositeWorkflowOperationHandler();
    operationHandler.setWorkspace(workspace);
    operationHandler.setServiceRegistry(serviceRegistry);
  }

  @SuppressWarnings("unchecked")
  private void setMockups() throws EncoderException, MediaPackageException {
    // set up mock profile
    profile = EasyMock.createNiceMock(EncodingProfile.class);
    EasyMock.expect(profile.getIdentifier()).andReturn(PROFILE_ID);
    EasyMock.expect(profile.getApplicableMediaType()).andReturn(MediaType.Stream);
    EasyMock.expect(profile.getOutputType()).andReturn(MediaType.AudioVisual);
    EasyMock.expect(profile.getMimeType()).andReturn(MimeTypes.MPEG4.toString()).times(2);
    EasyMock.replay(profile);

    // set up mock composer service
    composerService = EasyMock.createNiceMock(ComposerService.class);
    EasyMock.expect(composerService.getProfile(PROFILE_ID)).andReturn(profile);
    EasyMock.expect(
            composerService.composite((Dimension) EasyMock.anyObject(), Option.option((LaidOutElement<Track>) EasyMock.anyObject()),
                    (LaidOutElement<Track>) EasyMock.anyObject(),
                    (Option<LaidOutElement<Attachment>>) EasyMock.anyObject(), (String) EasyMock.anyObject(),
                    (String) EasyMock.anyObject())).andReturn(job);
    EasyMock.replay(composerService);
    operationHandler.setComposerService(composerService);
  }

  private WorkflowOperationResult getWorkflowOperationResult(MediaPackage mp, Map<String, String> configurations)
          throws WorkflowOperationException {
    // Add the mediapackage to a workflow instance
    WorkflowInstanceImpl workflowInstance = new WorkflowInstanceImpl();
    workflowInstance.setId(1);
    workflowInstance.setState(WorkflowState.RUNNING);
    workflowInstance.setMediaPackage(mp);
    WorkflowOperationInstanceImpl operation = new WorkflowOperationInstanceImpl("op", OperationState.RUNNING);
    operation.setTemplate("composite");
    operation.setState(OperationState.RUNNING);
    for (String key : configurations.keySet()) {
      operation.setConfiguration(key, configurations.get(key));
    }

    List<WorkflowOperationInstance> operationsList = new ArrayList<WorkflowOperationInstance>();
    operationsList.add(operation);
    workflowInstance.setOperations(operationsList);

    // Run the media package through the operation handler, ensuring that metadata gets added
    return operationHandler.start(workflowInstance, null);
  }

}
