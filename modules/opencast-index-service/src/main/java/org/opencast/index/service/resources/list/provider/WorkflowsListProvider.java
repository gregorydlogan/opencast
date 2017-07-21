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

package org.opencast.index.service.resources.list.provider;

import org.opencast.index.service.exception.ListProviderException;
import org.opencast.index.service.resources.list.api.ResourceListProvider;
import org.opencast.index.service.resources.list.api.ResourceListQuery;
import org.opencast.security.api.Organization;
import org.opencast.workflow.api.WorkflowDatabaseException;
import org.opencast.workflow.api.WorkflowInstance;
import org.opencast.workflow.api.WorkflowQuery;
import org.opencast.workflow.api.WorkflowService;

import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class WorkflowsListProvider implements ResourceListProvider {

  private static final String[] NAMES = { "workflows" };
  private static final Logger logger = LoggerFactory.getLogger(WorkflowsListProvider.class);

  private WorkflowService workflowService;

  protected void activate(BundleContext bundleContext) {
    logger.info("Workflow instances list provider activated!");
  }

  /** OSGi callback for the workflow service. */
  public void setWorkflowService(WorkflowService workflowService) {
    this.workflowService = workflowService;
  }

  @Override
  public String[] getListNames() {
    return NAMES;
  }

  @Override
  public Map<String, String> getList(String listName, ResourceListQuery query, Organization organization)
          throws ListProviderException {
    Map<String, String> workflowsList = new HashMap<String, String>();

    WorkflowQuery q = new WorkflowQuery();

    if (query != null) {

      if (query.getLimit().isSome())
        q.withCount(query.getLimit().get());

      if (query.getOffset().isSome())
        q.withStartPage(query.getOffset().get());
    }

    WorkflowInstance[] workflowInstances;
    try {
      workflowInstances = workflowService.getWorkflowInstances(q).getItems();
    } catch (WorkflowDatabaseException e) {
      logger.error("Error by querying the workflow instances from the DB:  {}", e);
      throw new ListProviderException(e.getMessage(), e.getCause());
    }

    for (WorkflowInstance w : workflowInstances) {
      workflowsList.put(Long.toString(w.getId()), w.getTitle() + " - " + w.getMediaPackage().getTitle());
    }

    return workflowsList;
  }
}
