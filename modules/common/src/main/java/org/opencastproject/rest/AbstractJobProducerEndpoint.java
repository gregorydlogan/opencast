/*
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

package org.opencastproject.rest;

import org.opencastproject.job.api.Job;
import org.opencastproject.job.api.JobProducer;
import org.opencastproject.serviceregistry.api.ServiceRegistry;
import org.opencastproject.serviceregistry.api.ServiceRegistryException;
import org.opencastproject.serviceregistry.api.UndispatchableJobException;
import org.opencastproject.util.NotFoundException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.HEAD;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

/**
 * Base implementation for job producer REST endpoints.
 */
public abstract class AbstractJobProducerEndpoint {

  /** The logger */
  private static final Logger logger = LoggerFactory.getLogger(AbstractJobProducerEndpoint.class);

  /**
   * @see org.opencastproject.job.api.JobProducer#acceptJob(org.opencastproject.job.api.Job)
   */
  @POST
  @Path("/dispatch")
  public Response dispatchJob(@FormParam("id") long jobId, @FormParam("operation") String jobOperation)
          throws ServiceRegistryException {
    final JobProducer service = getService();
    if (service == null)
      throw new WebApplicationException(Response.Status.SERVICE_UNAVAILABLE);

    // See if the service is ready to accept anything
    if (!service.isReadyToAcceptJobs(jobOperation)) {
      logger.debug("Service {} is not ready to accept jobs with operation {}", service, jobOperation);
      return Response.status(Response.Status.SERVICE_UNAVAILABLE).build();
    }

    Job job;
    try {
      job = getServiceRegistry().getJob(jobId);
    } catch (NotFoundException e) {
      logger.warn("Unable to find dispatched job {}", jobId);
      return Response.status(Response.Status.NOT_FOUND).build();
    }

    // See if the service has strong feelings about this particular job
    try {
      if (!service.isReadyToAccept(job)) {
        logger.debug("Service {} temporarily refused to accept job {}", service, jobId);
        return Response.status(Response.Status.SERVICE_UNAVAILABLE).build();
      }
    } catch (UndispatchableJobException e) {
      logger.warn("Service {} permanently refused to accept job {}", service, jobId);
      return Response.status(Response.Status.PRECONDITION_FAILED).build();
    }

    service.acceptJob(job);
    return Response.noContent().build();

  }

  @HEAD
  @Path("/dispatch")
  public Response checkHeartbeat() {
    return Response.ok().build();
  }

  /**
   * Returns the job producer that is backing this REST endpoint.
   *
   * @return the job producer
   */
  public abstract JobProducer getService();

  /**
   * Return the service registry.
   */
  public abstract ServiceRegistry getServiceRegistry();

}
