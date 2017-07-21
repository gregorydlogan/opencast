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

package org.opencast.silencedetection.endpoint;

import org.opencast.job.api.JaxbJob;
import org.opencast.job.api.Job;
import org.opencast.job.api.JobProducer;
import org.opencast.mediapackage.MediaPackageElementParser;
import org.opencast.mediapackage.Track;
import org.opencast.rest.AbstractJobProducerEndpoint;
import org.opencast.serviceregistry.api.ServiceRegistry;
import org.opencast.silencedetection.api.SilenceDetectionService;
import org.opencast.util.doc.rest.RestParameter;
import org.opencast.util.doc.rest.RestQuery;
import org.opencast.util.doc.rest.RestResponse;
import org.opencast.util.doc.rest.RestService;

import java.util.List;

import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.FormParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

/**
 * SilenceDetectionService REST Endpoint.
 */
@Path("/")
@RestService(name = "SilenceDetectionServiceEndpoint", title = "Silence Detection Service REST Endpoint",
        abstractText = "Detect silent sequences in audio file.",
        notes = {"All paths above are relative to the REST endpoint base (something like http://your.server/silencedetection)"})
public class SilenceDetectionServiceEndpoint extends AbstractJobProducerEndpoint {

  private SilenceDetectionService silenceDetectionService;
  private ServiceRegistry serviceRegistry;

  @POST
  @Path("/detect")
  @Produces({MediaType.APPLICATION_XML})
  @RestQuery(name = "detect", description = "Create silence detection job.",
          returnDescription = "Silence detection job.",
          restParameters = {
            @RestParameter(name = "track", type = RestParameter.Type.TEXT,
                    description = "Track where to run silence detection.", isRequired = true),
            @RestParameter(name = "referenceTracks", type = RestParameter.Type.TEXT,
                    description = "Tracks referenced by resulting smil (as sources).", isRequired = false)
          },
          reponses = {
            @RestResponse(description = "Silence detection job created successfully.", responseCode = HttpServletResponse.SC_OK),
            @RestResponse(description = "Create silence detection job failed.", responseCode = HttpServletResponse.SC_INTERNAL_SERVER_ERROR)
          })
  public Response detect(@FormParam("track") String trackXml, @FormParam("referenceTracks") String referenceTracksXml) {
    try {
      Track track = (Track) MediaPackageElementParser.getFromXml(trackXml);
      Job job = null;
      if (referenceTracksXml != null) {
        List<Track> referenceTracks = null;
        referenceTracks = (List<Track>) MediaPackageElementParser.getArrayFromXml(referenceTracksXml);
        job = silenceDetectionService.detect(track, referenceTracks.toArray(new Track[referenceTracks.size()]));
      } else {
        job = silenceDetectionService.detect(track);
      }
      return Response.ok(new JaxbJob(job)).build();
    } catch (Exception ex) {
      return Response.serverError().entity(ex.getMessage()).build();
    }
  }

  @Override
  public JobProducer getService() {
    if (silenceDetectionService instanceof JobProducer) {
      return (JobProducer) silenceDetectionService;
    } else {
      return null;
    }
  }

  @Override
  public ServiceRegistry getServiceRegistry() {
    return serviceRegistry;
  }

  public void setSilenceDetectionService(SilenceDetectionService silenceDetectionService) {
    this.silenceDetectionService = silenceDetectionService;
  }

  public void setServiceRegistry(ServiceRegistry serviceRegistry) {
    this.serviceRegistry = serviceRegistry;
  }
}
