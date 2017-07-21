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

import static com.entwinemedia.fn.data.Opt.none;
import static com.entwinemedia.fn.data.Opt.some;
import static org.apache.commons.lang3.exception.ExceptionUtils.getStackTrace;
import static org.opencast.util.OsgiUtil.getOptContextProperty;

import org.opencast.job.api.Incident.Severity;
import org.opencast.job.api.Job.Status;
import org.opencast.security.api.Organization;
import org.opencast.security.api.OrganizationDirectoryService;
import org.opencast.security.api.SecurityService;
import org.opencast.security.api.User;
import org.opencast.security.api.UserDirectoryService;
import org.opencast.serviceregistry.api.Incidents;
import org.opencast.serviceregistry.api.ServiceRegistry;
import org.opencast.serviceregistry.api.ServiceRegistryException;
import org.opencast.serviceregistry.api.SystemLoad;
import org.opencast.serviceregistry.api.SystemLoad.NodeLoad;
import org.opencast.serviceregistry.api.UndispatchableJobException;
import org.opencast.util.JobCanceledException;
import org.opencast.util.NotFoundException;
import org.opencast.util.data.functions.Strings;

import com.entwinemedia.fn.data.Opt;

import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * This class serves as a convenience for services that implement the {@link JobProducer} api to deal with handling long
 * running, asynchronous operations.
 */
public abstract class AbstractJobProducer implements JobProducer {

  /** The logger */
  static final Logger logger = LoggerFactory.getLogger(AbstractJobProducer.class);

  /** The default value whether to accept a job whose load exceeds the host’s max load */
  public static final boolean DEFAULT_ACCEPT_JOB_LOADS_EXCEEDING = true;

  /**
   * The key to look for in the service configuration file to override the {@link DEFAULT_ACCEPT_JOB_LOADS_EXCEEDING}
   */
  public static final String ACCEPT_JOB_LOADS_EXCEEDING_PROPERTY = "org.opencast.job.load.acceptexceeding";

  /** Whether to accept a job whose load exceeds the host’s max load */
  protected boolean acceptJobLoadsExeedingMaxLoad = DEFAULT_ACCEPT_JOB_LOADS_EXCEEDING;

  /** The types of job that this producer can handle */
  protected String jobType = null;

  /** To enable threading when dispatching jobs */
  protected ExecutorService executor = Executors.newCachedThreadPool();

  /**
   * OSGI activate method.
   *
   * @param cc
   *          OSGI component context
   **/
  public void activate(ComponentContext cc) {
    acceptJobLoadsExeedingMaxLoad = getOptContextProperty(cc, ACCEPT_JOB_LOADS_EXCEEDING_PROPERTY).map(Strings.toBool)
            .getOrElse(DEFAULT_ACCEPT_JOB_LOADS_EXCEEDING);
  }

  /**
   * Creates a new abstract job producer for jobs of the given type.
   *
   * @param jobType
   *          the job type
   */
  public AbstractJobProducer(String jobType) {
    this.jobType = jobType;
  }

  /**
   * {@inheritDoc}
   *
   * @see org.opencast.job.api.JobProducer#getJobType()
   */
  @Override
  public String getJobType() {
    return jobType;
  }

  /**
   * {@inheritDoc}
   *
   * @see org.opencast.job.api.JobProducer#countJobs(org.opencast.job.api.Job.Status)
   */
  @Override
  public long countJobs(Status status) throws ServiceRegistryException {
    if (status == null)
      throw new IllegalArgumentException("Status must not be null");
    return getServiceRegistry().count(getJobType(), status);
  }

  /**
   * {@inheritDoc}
   *
   * @see org.opencast.job.api.JobProducer#acceptJob(org.opencast.job.api.Job)
   */
  @Override
  public void acceptJob(final Job job) throws ServiceRegistryException {
    final Job runningJob;
    try {
      job.setStatus(Job.Status.RUNNING);
      runningJob = getServiceRegistry().updateJob(job);
    } catch (NotFoundException e) {
      throw new IllegalStateException(e);
    }
    executor.submit(new JobRunner(runningJob, getServiceRegistry().getCurrentJob()));
  }

  /**
   * {@inheritDoc}
   *
   * @see org.opencast.job.api.JobProducer#isReadyToAcceptJobs(String)
   */
  @Override
  public boolean isReadyToAcceptJobs(String operation) throws ServiceRegistryException {
    return true;
  }

  /**
   * {@inheritDoc}
   *
   * @see org.opencast.job.api.JobProducer#isReadyToAccept(org.opencast.job.api.Job)
   */
  @Override
  public boolean isReadyToAccept(Job job) throws ServiceRegistryException, UndispatchableJobException {
    if (!jobType.equals(job.getJobType())) {
      logger.debug("Invalid job type submitted: {}", job.getJobType());
      return false;
    }
    NodeLoad maxload;
    try {
      maxload = getServiceRegistry().getMaxLoadOnNode(getServiceRegistry().getRegistryHostname());
    } catch (NotFoundException e) {
      throw new ServiceRegistryException(e);
    }

    SystemLoad systemLoad = getServiceRegistry().getCurrentHostLoads(true);
    // Note: We are not adding the job load in the next line because it is already accounted for in the load values we
    // get back from the service registry.
    float currentLoad = systemLoad.get(getServiceRegistry().getRegistryHostname()).getLoadFactor();

    // Whether to accept a job whose load exceeds the host’s max load
    if (acceptJobLoadsExeedingMaxLoad) {
      // If the actual job load is greater the host's max load this job never get's processed. So decrease the current
      // load by the difference of the job load and max load
      if (job.getJobLoad() > maxload.getLoadFactor()) {
        float decreaseLoad = job.getJobLoad() - maxload.getLoadFactor();
        currentLoad -= decreaseLoad;
      }
    }

    if (currentLoad > maxload.getLoadFactor()) {
      logger.debug("Declining job {} of type {} because load of {} would exceed this node's limit of {}.",
              new Object[] { job.getId(), job.getJobType(), currentLoad, maxload.getLoadFactor() });
      return false;
    }
    logger.debug("Accepting job {} of type {} because load of {} is within this node's limit of {}.",
            new Object[] { job.getId(), job.getJobType(), currentLoad, maxload.getLoadFactor() });
    return true;
  }

  /** Shorthand for {@link #getServiceRegistry()}.incident() */
  public Incidents incident() {
    return getServiceRegistry().incident();
  }

  /**
   * Returns a reference to the service registry.
   *
   * @return the service registry
   */
  protected abstract ServiceRegistry getServiceRegistry();

  /**
   * Returns a reference to the security service
   *
   * @return the security service
   */
  protected abstract SecurityService getSecurityService();

  /**
   * Returns a reference to the user directory service
   *
   * @return the user directory service
   */
  protected abstract UserDirectoryService getUserDirectoryService();

  /**
   * Returns a reference to the organization directory service.
   *
   * @return the organization directory service
   */
  protected abstract OrganizationDirectoryService getOrganizationDirectoryService();

  /**
   * Asks the overriding class to process the arguments using the given operation. The result will be added to the
   * associated job as the payload.
   *
   * @param job
   *          the job to process
   * @return the operation result
   * @throws Exception
   */
  protected abstract String process(Job job) throws Exception;

  /** A utility class to run jobs */
  class JobRunner implements Callable<Void> {

    /** The job to dispatch */
    private final long jobId;

    /** The current job */
    private final Opt<Long> currentJobId;

    /**
     * Constructs a new job runner
     *
     * @param job
     *          the job to run
     * @param currentJob
     *          the current running job
     */
    JobRunner(Job job, Job currentJob) {
      this.jobId = job.getId();
      if (currentJob != null) {
        this.currentJobId = some(currentJob.getId());
      } else {
        currentJobId = none();
      }
    }

    @Override
    public Void call() throws Exception {
      final SecurityService securityService = getSecurityService();
      final ServiceRegistry serviceRegistry = getServiceRegistry();
      final Job jobBeforeProcessing = serviceRegistry.getJob(jobId);

      if (currentJobId.isSome())
        serviceRegistry.setCurrentJob(serviceRegistry.getJob(currentJobId.get()));

      final Organization organization = getOrganizationDirectoryService()
              .getOrganization(jobBeforeProcessing.getOrganization());
      securityService.setOrganization(organization);
      final User user = getUserDirectoryService().loadUser(jobBeforeProcessing.getCreator());
      securityService.setUser(user);

      try {
        final String payload = process(jobBeforeProcessing);
        handleSuccessfulProcessing(payload);
      } catch (Throwable t) {
        handleFailedProcessing(t);
      } finally {
        serviceRegistry.setCurrentJob(null);
        securityService.setUser(null);
        securityService.setOrganization(null);
      }

      return null;
    }

    private void handleSuccessfulProcessing(final String payload) throws Exception {
      // The job may gets updated internally during processing. It therefore needs to be reload from the service
      // registry in order to prevent inconsistencies.
      final Job jobAfterProcessing = getServiceRegistry().getJob(jobId);
      jobAfterProcessing.setPayload(payload);
      jobAfterProcessing.setStatus(Status.FINISHED);
      getServiceRegistry().updateJob(jobAfterProcessing);
    }

    private void handleFailedProcessing(final Throwable t) throws Exception {
      if (t instanceof JobCanceledException) {
        logger.info(t.getMessage());
      } else {
        Job jobAfterProcessing = getServiceRegistry().getJob(jobId);
        jobAfterProcessing.setStatus(Status.FAILED);
        jobAfterProcessing = getServiceRegistry().updateJob(jobAfterProcessing);
        getServiceRegistry().incident().unhandledException(jobAfterProcessing, Severity.FAILURE, t);
        logger.error("Error handling operation '{}': {}", jobAfterProcessing.getOperation(), getStackTrace(t));
        if (t instanceof ServiceRegistryException)
          throw (ServiceRegistryException) t;
      }
    }

  }
}
