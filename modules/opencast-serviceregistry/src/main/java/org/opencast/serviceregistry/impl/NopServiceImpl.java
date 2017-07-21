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

package org.opencast.serviceregistry.impl;

import static org.opencast.util.data.functions.Misc.chuck;

import org.opencast.job.api.Job;
import org.opencast.job.api.OsgiAbstractJobProducer;
import org.opencast.serviceregistry.api.NopService;
import org.opencast.serviceregistry.api.ServiceRegistryException;
import org.opencast.util.Log;

/**
 * No operation service.
 * <p>
 * This dummy service just exists for creating jobs for testing purposes.
 */
public final class NopServiceImpl extends OsgiAbstractJobProducer implements NopService {
  private static final Log log = Log.mk(NopServiceImpl.class);

  public static final String PAYLOAD = "NopServicePayload";

  public NopServiceImpl() {
    super("org.opencast.nop");
  }

  @Override protected String process(Job job) throws Exception {
    log.info("Processing job %d", job.getId());
    return PAYLOAD;
  }

  @Override public Job nop() {
    try {
      return getServiceRegistry().createJob(getJobType(), "nop");
    } catch (ServiceRegistryException e) {
      return chuck(e);
    }
  }
}
