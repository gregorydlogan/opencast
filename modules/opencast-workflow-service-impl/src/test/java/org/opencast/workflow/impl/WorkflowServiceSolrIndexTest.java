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

package org.opencast.workflow.impl;

import static org.junit.Assert.assertEquals;

import org.opencast.job.api.Job;
import org.opencast.job.api.JobImpl;
import org.opencast.mediapackage.MediaPackageBuilderFactory;
import org.opencast.message.broker.api.MessageSender;
import org.opencast.security.api.DefaultOrganization;
import org.opencast.security.api.JaxbRole;
import org.opencast.security.api.JaxbUser;
import org.opencast.security.api.OrganizationDirectoryService;
import org.opencast.security.api.Permissions;
import org.opencast.security.api.SecurityService;
import org.opencast.security.api.User;
import org.opencast.serviceregistry.api.ServiceRegistry;
import org.opencast.util.PathSupport;
import org.opencast.workflow.api.WorkflowInstance.WorkflowState;
import org.opencast.workflow.api.WorkflowInstanceImpl;
import org.opencast.workflow.api.WorkflowParser;
import org.opencast.workflow.api.WorkflowQuery;
import org.opencast.workflow.api.WorkflowService;

import org.apache.commons.io.FileUtils;
import org.easymock.EasyMock;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Test cases for the implementation at {@link WorkflowServiceSolrIndex}.
 */
public class WorkflowServiceSolrIndexTest {

  private WorkflowServiceSolrIndex dao = null;

  @Before
  public void setUp() throws Exception {
    // security service
    SecurityService securityService = EasyMock.createNiceMock(SecurityService.class);
    EasyMock.expect(securityService.getUser()).andReturn(SecurityServiceStub.DEFAULT_ORG_ADMIN).anyTimes();
    EasyMock.expect(securityService.getOrganization()).andReturn(new DefaultOrganization()).anyTimes();
    EasyMock.replay(securityService);

    OrganizationDirectoryService orgDirectroy = EasyMock.createNiceMock(OrganizationDirectoryService.class);
    EasyMock.expect(orgDirectroy.getOrganization((String) EasyMock.anyObject()))
            .andReturn(securityService.getOrganization()).anyTimes();
    EasyMock.replay(orgDirectroy);

    // Create a job with a workflow as its payload
    List<Job> jobs = new ArrayList<Job>();
    Job job = new JobImpl();
    WorkflowInstanceImpl workflow = new WorkflowInstanceImpl();
    workflow.setId(123);
    workflow.setCreator(securityService.getUser());
    workflow.setOrganization(securityService.getOrganization());
    workflow.setState(WorkflowState.INSTANTIATED);
    workflow.setMediaPackage(MediaPackageBuilderFactory.newInstance().newMediaPackageBuilder().createNew());
    job.setPayload(WorkflowParser.toXml(workflow));
    job.setOrganization(securityService.getOrganization().getId());
    jobs.add(job);

    // Mock up the service registry to return the job
    ServiceRegistry serviceRegistry = EasyMock.createNiceMock(ServiceRegistry.class);
    EasyMock.expect(serviceRegistry.count(WorkflowService.JOB_TYPE, null)).andReturn(new Long(1));
    EasyMock.expect(serviceRegistry.getJobs(WorkflowService.JOB_TYPE, null)).andReturn(jobs);
    EasyMock.expect(serviceRegistry.getJob(123)).andReturn(job);
    EasyMock.replay(serviceRegistry);

    MessageSender messageSender = EasyMock.createNiceMock(MessageSender.class);
    EasyMock.replay(messageSender);

    // Now create the dao
    dao = new WorkflowServiceSolrIndex();
    dao.solrRoot = PathSupport.concat("target", Long.toString(System.currentTimeMillis()));
    dao.setServiceRegistry(serviceRegistry);
    dao.setSecurityService(securityService);
    dao.setOrgDirectory(orgDirectroy);
    dao.activate("System Admin");
  }

  @After
  public void tearDown() throws Exception {
    dao.deactivate();
    FileUtils.deleteDirectory(new File(dao.solrRoot));
    dao = null;
  }

  /**
   * Tests whether a simple query is built correctly
   */
  @Test
  public void testBuildSimpleQuery() throws Exception {
    WorkflowQuery q = new WorkflowQuery().withMediaPackage("123").withSeriesId("series1");
    String solrQuery = dao.createQuery(q, Permissions.Action.READ.toString(), true);
    String expected = "oc_org:mh_default_org AND mediapackageid:123 AND seriesid:series1";
    assertEquals(expected, solrQuery);
  }

  /**
   * Tests whether the query is built properly, using OR rather than AND, when supplying multiple inclusive states
   */
  @Test
  public void testBuildMultiStateQuery() throws Exception {
    WorkflowQuery q = new WorkflowQuery().withSeriesId("series1").withState(WorkflowState.RUNNING)
            .withState(WorkflowState.PAUSED);
    String solrQuery = dao.createQuery(q, Permissions.Action.READ.toString(), true);
    String expected = "oc_org:mh_default_org AND seriesid:series1 AND (state:running OR state:paused)";
    assertEquals(expected, solrQuery);
  }

  /**
   * Tests whether the query is built using AND rather than OR when supplying multiple excluded states
   */
  @Test
  public void testBuildNegativeStatesQuery() throws Exception {
    WorkflowQuery q = new WorkflowQuery().withSeriesId("series1").withoutState(WorkflowState.RUNNING)
            .withoutState(WorkflowState.PAUSED);
    String solrQuery = dao.createQuery(q, Permissions.Action.READ.toString(), true);
    String expected = "oc_org:mh_default_org AND seriesid:series1 AND (-state:running AND -state:paused AND *:*)";
    assertEquals(expected, solrQuery);
  }

  /**
   * Tests whether the query is built using *:* when supplying a single excluded state
   */
  @Test
  public void testBuildNegativeStateQuery() throws Exception {
    WorkflowQuery q = new WorkflowQuery().withSeriesId("series1").withoutState(WorkflowState.RUNNING);
    String solrQuery = dao.createQuery(q, Permissions.Action.READ.toString(), true);
    String expected = "oc_org:mh_default_org AND seriesid:series1 AND (-state:running AND *:*)";
    assertEquals(expected, solrQuery);
  }

  /**
   * Tests whether a simple query is built correctly with the authentication fragment
   */
  @Test
  public void testNonAdminQuery() throws Exception {
    String userRole = "ROLE_USER";
    User nonAdminUser = new JaxbUser("noAdmin", "test", new DefaultOrganization(), new JaxbRole(userRole,
            new DefaultOrganization()));

    // security service
    SecurityService securityService = EasyMock.createNiceMock(SecurityService.class);
    EasyMock.expect(securityService.getUser()).andReturn(nonAdminUser).anyTimes();
    EasyMock.expect(securityService.getOrganization()).andReturn(new DefaultOrganization()).anyTimes();
    EasyMock.replay(securityService);
    dao.setSecurityService(securityService);

    WorkflowQuery q = new WorkflowQuery().withMediaPackage("123").withSeriesId("series1");
    String solrQuery = dao.createQuery(q, Permissions.Action.READ.toString(), true);
    String expected = "oc_org:mh_default_org AND mediapackageid:123 AND seriesid:series1 AND oc_org:"
            + DefaultOrganization.DEFAULT_ORGANIZATION_ID + " AND (oc_creator:" + nonAdminUser.getUsername()
            + " OR oc_acl_read:" + userRole + ")";

    assertEquals(expected, solrQuery);
  }

}
