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
package org.opencast.workflow.handler.distribution;

import static org.opencast.util.RequireUtil.notNull;

import org.opencast.distribution.api.DownloadDistributionService;
import org.opencast.job.api.JobContext;
import org.opencast.mediapackage.MediaPackage;
import org.opencast.workflow.api.WorkflowInstance;
import org.opencast.workflow.api.WorkflowOperationException;
import org.opencast.workflow.api.WorkflowOperationResult;
import org.opencast.workflow.api.WorkflowOperationResult.Action;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * WOH that retracts elements from an internal distribution channel and removes the reflective publication elements from
 * the media package.
 */
public class ConfigurableRetractWorkflowOperationHandler extends ConfigurableWorkflowOperationHandlerBase {

  /** The logging facility */
  private static final Logger logger = LoggerFactory.getLogger(ConfigurableRetractWorkflowOperationHandler.class);
  private static final String CHANNEL_ID_KEY = "channel-id";
  // service references
  private DownloadDistributionService distributionService;

  /** OSGi DI */
  void setDownloadDistributionService(DownloadDistributionService distributionService) {
    this.distributionService = distributionService;
  }

  @Override
  protected DownloadDistributionService getDistributionService() {
    assert (distributionService != null);
    return distributionService;
  }

  @Override
  public WorkflowOperationResult start(WorkflowInstance workflowInstance, JobContext context)
          throws WorkflowOperationException {
    notNull(workflowInstance, "workflowInstance");

    final MediaPackage mp = workflowInstance.getMediaPackage();
    final String channelId = StringUtils.trimToEmpty(workflowInstance.getCurrentOperation().getConfiguration(
            CHANNEL_ID_KEY));
    if (StringUtils.isBlank((channelId))) {
      throw new WorkflowOperationException("Unable to publish this mediapackage as the configuration key "
              + CHANNEL_ID_KEY + " is missing. Unable to determine where to publish these elements.");
    }

    retract(mp, channelId);

    return createResult(mp, Action.CONTINUE);
  }

}
