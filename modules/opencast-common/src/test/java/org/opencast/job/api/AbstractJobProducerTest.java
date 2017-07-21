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

package org.opencast.job.api;

import static org.easymock.EasyMock.expect;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.opencast.job.api.Job.Status;
import org.opencast.security.api.OrganizationDirectoryService;
import org.opencast.security.api.SecurityService;
import org.opencast.security.api.UserDirectoryService;
import org.opencast.serviceregistry.api.ServiceRegistry;
import org.opencast.serviceregistry.api.SystemLoad;
import org.opencast.serviceregistry.api.SystemLoad.NodeLoad;

import org.easymock.Capture;
import org.easymock.EasyMock;
import org.easymock.EasyMockSupport;
import org.easymock.IAnswer;
import org.junit.Before;
import org.junit.Test;

public class AbstractJobProducerTest extends EasyMockSupport {

  private JobProducerTest jobProducer;
  private ServiceRegistry serviceRegistry;

  @Before
  public void setUp() throws Exception {
    serviceRegistry = createNiceMock(ServiceRegistry.class);
    expect(serviceRegistry.count(JobProducerTest.JOB_TYPE, Status.DISPATCHING)).andReturn(2L).anyTimes();
    expect(serviceRegistry.count(JobProducerTest.JOB_TYPE, Status.RUNNING)).andReturn(3L).anyTimes();
    final Capture<Job> job = EasyMock.newCapture();
    expect(serviceRegistry.updateJob(EasyMock.capture(job))).andAnswer(new IAnswer<Job>() {
      @Override
      public Job answer() throws Throwable {
        return job.getValue();
      }
    });

    SecurityService securityService = createNiceMock(SecurityService.class);
    UserDirectoryService userDirectoryService = createNiceMock(UserDirectoryService.class);
    OrganizationDirectoryService organizationDirectoryService = createNiceMock(OrganizationDirectoryService.class);

    jobProducer = new JobProducerTest(serviceRegistry, securityService, userDirectoryService,
            organizationDirectoryService);
  }

  @Test
  public void testGetType() throws Exception {
    replayAll();

    assertEquals("test", jobProducer.getJobType());
  }

  @Test
  public void testIsReadyToAcceptJobs() throws Exception {
    replayAll();

    assertTrue(jobProducer.isReadyToAcceptJobs("any operation"));
  }

  @Test
  public void testCountJobs() throws Exception {
    replayAll();

    assertEquals(2, jobProducer.countJobs(Status.DISPATCHING));
    assertEquals(3, jobProducer.countJobs(Status.RUNNING));
  }

  @Test
  public void testAcceptJob() throws Exception {
    replayAll();

    Job job = new JobImpl();
    job.setStatus(Status.DISPATCHING);
    assertEquals(Status.DISPATCHING, job.getStatus());
    jobProducer.acceptJob(job);
    assertEquals(Status.RUNNING, job.getStatus());
  }

  @Test
  public void testIsReadyToAccept() throws Exception {
    expect(serviceRegistry.getRegistryHostname()).andReturn("test").anyTimes();
    expect(serviceRegistry.getMaxLoadOnNode("test")).andReturn(new NodeLoad("test", 4.0f)).anyTimes();
    SystemLoad systemLoad = new SystemLoad();
    systemLoad.addNodeLoad(new NodeLoad("test", 3.0f));
    expect(serviceRegistry.getCurrentHostLoads(true)).andReturn(systemLoad);
    SystemLoad systemLoad2 = new SystemLoad();
    systemLoad2.addNodeLoad(new NodeLoad("test", 12.0f));
    expect(serviceRegistry.getCurrentHostLoads(true)).andReturn(systemLoad2);
    SystemLoad systemLoad3 = new SystemLoad();
    systemLoad3.addNodeLoad(new NodeLoad("test", 5.0f));
    expect(serviceRegistry.getCurrentHostLoads(true)).andReturn(systemLoad3);
    replayAll();

    Job job = new JobImpl(3);
    job.setJobType("test");
    job.setStatus(Status.DISPATCHING);
    job.setProcessingHost("same");

    // Job load lower than max load and enough free load available
    job.setJobLoad(1.0f);
    assertTrue(jobProducer.isReadyToAccept(job));

    // Job load higher than max load but some load on host
    job.setJobLoad(10.0f);
    assertFalse(jobProducer.isReadyToAccept(job));

    // Job load higher than max load and no load on host
    job.setJobLoad(5.0f);
    assertTrue(jobProducer.isReadyToAccept(job));
  }

  private class JobProducerTest extends AbstractJobProducer {

    public static final String JOB_TYPE = "test";

    private ServiceRegistry serviceRegistry;
    private SecurityService securityService;
    private UserDirectoryService userDirectoryService;
    private OrganizationDirectoryService organizationDirectoryService;

    JobProducerTest(ServiceRegistry serviceRegistry, SecurityService securityService,
            UserDirectoryService userDirectoryService, OrganizationDirectoryService organizationDirectoryService) {
      super(JOB_TYPE);
      this.serviceRegistry = serviceRegistry;
      this.securityService = securityService;
      this.userDirectoryService = userDirectoryService;
      this.organizationDirectoryService = organizationDirectoryService;
    }

    @Override
    protected ServiceRegistry getServiceRegistry() {
      return serviceRegistry;
    }

    @Override
    protected SecurityService getSecurityService() {
      return securityService;
    }

    @Override
    protected UserDirectoryService getUserDirectoryService() {
      return userDirectoryService;
    }

    @Override
    protected OrganizationDirectoryService getOrganizationDirectoryService() {
      return organizationDirectoryService;
    }

    @Override
    protected String process(Job job) throws Exception {
      return null;
    }

  }

}
