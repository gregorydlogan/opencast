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

package org.opencastproject.archive.aws.glacier;

import static org.opencastproject.util.data.Option.some;

import org.opencastproject.capture.CaptureParameters;
import org.opencastproject.kernel.scanner.AbstractScanner;
import org.opencastproject.util.Log;
import org.opencastproject.util.NeedleEye;
import org.opencastproject.workflow.api.WorkflowService;

import org.apache.commons.lang3.StringUtils;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;
import org.quartz.CronExpression;
import org.quartz.JobExecutionContext;
import org.quartz.impl.StdSchedulerFactory;
import org.slf4j.LoggerFactory;

import java.util.Calendar;
import java.util.Date;
import java.util.Dictionary;

public class AwsGlacierCacheCleaner extends AbstractScanner implements ManagedService {
  private static final Log logger = new Log(LoggerFactory.getLogger(AwsGlacierCacheCleaner.class));

  public static final String PARAM_KEY_ARCHIVE_MAX_AGE = "max-age";
  public static final String JOB_GROUP = "mh-archive-timed-glacier-cleaner-group";
  public static final String JOB_NAME = "mh-archive-timed-glacier-cleaner-job";
  public static final String SCANNER_NAME = "Timed Glacier cache cleaner";
  public static final String TRIGGER_GROUP = "mh-archive-timed-glacier-cleaner-trigger-group";
  public static final String TRIGGER_NAME = "mh-archive-timed-glacier-cleaner-trigger";

  private AwsGlacierArchiveElementStore elementStore;
  private WorkflowService workflowService;
  private long ageModifier;

  public AwsGlacierCacheCleaner() {
    try {
      quartz = new StdSchedulerFactory().getScheduler();
      quartz.start();
      // create and set the job. To actually run it call schedule(..)
      //GDLGDL TODO
      logger.error("UNFINISHED CODE");
      return;
      /*final JobDetail job = null; //new JobDetail(getJobName(), getJobGroup(), TimedMediaArchiver.Runner.class);
      job.setDurability(false);
      job.setVolatility(true);
      job.getJobDataMap().put(JOB_PARAM_PARENT, this);
      quartz.addJob(job, true);*/
    } catch (org.quartz.SchedulerException e) {
      throw new RuntimeException(e);
    }
  }

  public void updated(Dictionary<String, ?> properties) throws ConfigurationException {
    String cronExpression;
    boolean enabled;

    unschedule();

    if (properties != null) {
      logger.debug("Updating configuration...");

      //We never want to disable this, since otherwise the cache grows indefinitely...
      setEnabled(true);

      cronExpression = (String) properties.get(PARAM_KEY_CRON_EXPR);
      if (StringUtils.isBlank(cronExpression) || !CronExpression.isValidExpression(cronExpression))
        throw new ConfigurationException(PARAM_KEY_CRON_EXPR, "Cron expression must be valid");
      setCronExpression(cronExpression);
      logger.debug("Timed media offload cron expression: '" + cronExpression + "'");

      try {
        ageModifier = Long.parseLong((String) properties.get(PARAM_KEY_ARCHIVE_MAX_AGE));
      } catch (NumberFormatException e) {
        throw new ConfigurationException(PARAM_KEY_ARCHIVE_MAX_AGE, "Invalid max age");
      }
      if (ageModifier < 0) {
        throw new ConfigurationException(PARAM_KEY_ARCHIVE_MAX_AGE, "Max age must be greater than zero");
      }
    }

    schedule();
  }

  @Override
  public String getJobGroup() {
    return JOB_GROUP;
  }

  @Override
  public String getJobName() {
    return JOB_NAME;
  }

  @Override
  public String getTriggerGroupName() {
    return TRIGGER_GROUP;
  }

  @Override
  public String getTriggerName() {
    return TRIGGER_NAME;
  }

  @Override
  public void scan() {
    Date maxAge = Calendar.getInstance().getTime();
    maxAge.setTime(maxAge.getTime() - (ageModifier * CaptureParameters.HOURS * CaptureParameters.MILLISECONDS));

    elementStore.purgeCache(maxAge);
  }

  @Override
  public String getScannerName() {
    return SCANNER_NAME;
  }

  public void setElementStore(AwsGlacierArchiveElementStore elementStore) {
    this.elementStore = elementStore;
  }

  /** Quartz job to which offloads old mediapackages from the archive to remote storage */
  public static class Runner extends AbstractScanner.TypedQuartzJob<AbstractScanner> {
    private static final NeedleEye eye = new NeedleEye();

    public Runner() {
      super(some(eye));
    }

    @Override
    protected void execute(final AbstractScanner parameters, JobExecutionContext ctx) {
      logger.debug("Starting " + parameters.getScannerName() + " job.");

      parameters.scan();

      logger.debug("Finished " + parameters.getScannerName() + " job.");
    }
  }
}
