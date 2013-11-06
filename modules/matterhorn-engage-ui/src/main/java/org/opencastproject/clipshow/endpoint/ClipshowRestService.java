/**
 *  Copyright 2009, 2010 The Regents of the University of California
 *  Licensed under the Educational Community License, Version 2.0
 *  (the "License"); you may not use this file except in compliance
 *  with the License. You may obtain a copy of the License at
 *
 *  http://www.osedu.org/licenses/ECL-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an "AS IS"
 *  BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 *  or implied. See the License for the specific language governing
 *  permissions and limitations under the License.
 *
 */
package org.opencastproject.clipshow.endpoint;

import org.opencastproject.clipshow.impl.Clip;
import org.opencastproject.clipshow.impl.Clipshow;
import org.opencastproject.clipshow.impl.ClipshowServiceImpl;
import org.opencastproject.clipshow.impl.ClipshowVote.Type;
import org.opencastproject.util.NotFoundException;
import org.opencastproject.util.PropertiesResponse;
import org.opencastproject.util.doc.rest.RestParameter;
import org.opencastproject.util.doc.rest.RestQuery;
import org.opencastproject.util.doc.rest.RestResponse;
import org.opencastproject.util.doc.rest.RestService;

import org.apache.commons.lang.StringUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map.Entry;
import java.util.Properties;

import javax.naming.NoPermissionException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.Consumes;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

/**
 * The REST endpoint for the capture agent service on the capture device
 */
@Path("/")
@RestService(name = "clipshowservice", title = "Clipshow Service", notes = {
        "All paths above are relative to the REST endpoint base (something like http://your.server/files)",
        "If the service is down or not working it will return a status 503, this means the the underlying service is not working and "
                + "is either restarting or has failed",
        "A status code 500 means a general failure has occurred which is not recoverable and was not anticipated. In other words, there is a bug! "
                + "You should file an error report with your server logs from the time when the error occurred: "
                + "<a href=\"https://issues.opencastproject.org\">Opencast Issue Tracker</a>" }, abstractText = "This service is a registry of capture agents and their recordings. "
        + "Please see the <a href='http://wiki.opencastproject.org/confluence/display/open/Capture+Admin+Service'>service contract</a> for further information.")
public class ClipshowRestService {

  private static final Logger logger = LoggerFactory.getLogger(ClipshowRestService.class);
  private ClipshowServiceImpl service;
  private JSONParser parser = new JSONParser();

  /**
   * Callback from OSGi that is called when this service is activated.
   * 
   * @param cc
   *          OSGi component context
   */
  public void activate(ComponentContext cc) {

  }

  public void setService(ClipshowServiceImpl s) {
    service = s;
  }

  public void unsetService() {
    service = null;
  }

  //TODO: Fix return descriptions on GET endpoints

  @POST
  @Path("create")
  @Consumes(MediaType.APPLICATION_JSON)
  @RestQuery(name = "createClipshow", description = "Creates a clipshow from a JSON object description", 
    pathParameters = { }, 
    restParameters = {
      @RestParameter(name = "clipshowText", description = "The clipshow as a JSON object", isRequired = true, type = RestParameter.Type.STRING)
    },
    reponses = {
      @RestResponse(description = "Creation successful", responseCode = HttpServletResponse.SC_OK),
      @RestResponse(description = "Bad parameters", responseCode = HttpServletResponse.SC_BAD_REQUEST)
    }, returnDescription = ""
  )
  //N.B.  If you add @FormParam to clipshowText you *will* get an HTTP 415.  Don't do it!
  //TODO:  This is ridiculous, but JAXB's braindead implementation doesn't understand that JSON doesn't have namespaces
  public Response createClipshow(String clipshowText, @Context HttpServletRequest req) {
    if (StringUtils.isBlank(clipshowText)) {
      return Response.status(Response.Status.BAD_REQUEST).build();
    }
    JSONObject decodedShow = null;
    try {
      decodedShow = (JSONObject) parser.parse(clipshowText);
    } catch (ParseException e) {
      return Response.status(Response.Status.BAD_REQUEST).build();
    }
    String name = (String) decodedShow.get("title");
    String mediapackageId = (String) decodedShow.get("mediapackageId");
    String mediapackageTitle = (String) decodedShow.get("mediapackageTitle");
    JSONArray show = (JSONArray) decodedShow.get("clips");


    List<Clip> clips = new LinkedList<Clip>();
    ListIterator clipIterator = show.listIterator();
    while (clipIterator.hasNext()) {
      JSONObject e = (JSONObject) clipIterator.next();
      int start = Integer.valueOf(e.get("start").toString());
      int stop = Integer.valueOf(e.get("stop").toString());
      Clip clip = new Clip(start, stop);
      clips.add(clip);
    }

    String series = (String) decodedShow.get("series");
    String tags = (String) decodedShow.get("tags");
    String users = (String) decodedShow.get("allowedUsers");

    try {
      //Create the clipshow
      Long clipshowId = this.service.createClipshow(name, clips, mediapackageId, mediapackageTitle, req.getRemoteUser());

      //Add any tags, if they exist
      if (tags != null && StringUtils.isNotBlank(tags)) {
        try {
          this.service.addTags(clipshowId.toString(), tags, req.getRemoteUser());
        } catch (NoPermissionException e) {
          return Response.status(Response.Status.FORBIDDEN).build();
        } catch (NotFoundException e) {
          return Response.status(Response.Status.NOT_FOUND).build();
        }
      }

      //TODO:  Check this splitting logic.  What happens if they enter "," as their allowed users?
      if (users != null && StringUtils.isNotBlank(users)) {
        String[] userAry = users.split(",");
        for (String u : userAry) {
          try {
            this.service.setClipshowPublic(false, clipshowId.toString(), req.getRemoteUser());
            this.service.addUserToClipshow(StringUtils.trimToEmpty(u), clipshowId.toString(), req.getRemoteUser());
          } catch (NoPermissionException e1) {
            return Response.status(Response.Status.FORBIDDEN).build();
          } catch (NotFoundException e1) {
            return Response.status(Response.Status.NOT_FOUND).build();
          } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).build();
          }
        }
      }

      //Add the clipshow to the series, if any
      if (series != null && StringUtils.isNotBlank(series)) {
        try {
          //Try to parse out a numerical series id (ie, one that already exists) and add the clipshow to that series
          Long seriesId = Long.parseLong(series);
          this.service.addClipshowToSeries(clipshowId.toString(), seriesId.toString(), req.getRemoteUser());
        } catch (NumberFormatException e) {
          return Response.status(Response.Status.BAD_REQUEST).build();
        } catch (NoPermissionException e) {
          return Response.status(Response.Status.FORBIDDEN).build();
        } catch (NotFoundException e) {
          return Response.status(Response.Status.NOT_FOUND).build();
        }
      } else {
        //So the series is blank, or missing so let's check to see if we need to create a new one
        String seriesName = (String) decodedShow.get("newSeriesName");
        if (seriesName != null && StringUtils.isNotBlank(seriesName)) {
          //Great, we have a series name, and no series id.  Create the series and add teh clipshow to the series.
          Long seriesId = this.service.createSeries(seriesName, null, req.getRemoteUser());
          try {
            this.service.addClipshowToSeries(clipshowId.toString(), seriesId.toString(), req.getRemoteUser());
          } catch (NoPermissionException e) {
            return Response.status(Response.Status.FORBIDDEN).build();
          } catch (NotFoundException e) {
            return Response.status(Response.Status.NOT_FOUND).build();
          }
        }
      }

      return Response.status(Response.Status.CREATED).build();
    } catch (IllegalArgumentException e) {
      e.printStackTrace();
      return Response.serverError().build();
    }
  }

  @GET
  @Path("get")
  @Produces(MediaType.APPLICATION_JSON)
  @RestQuery(name = "getClipshow", description = "Gets the JSON description of a clipshow", 
    pathParameters = { }, 
    restParameters = {
      @RestParameter(name = "clipshowId", description = "The clipshow's ID", isRequired = false, type = RestParameter.Type.STRING)
    },
    reponses = {
      @RestResponse(description = "Clipshow found and returned", responseCode = HttpServletResponse.SC_OK),
      @RestResponse(description = "Bad parameters", responseCode = HttpServletResponse.SC_BAD_REQUEST),
      @RestResponse(description = "User not permitted", responseCode = HttpServletResponse.SC_FORBIDDEN),
      @RestResponse(description = "Clipshow not found", responseCode = HttpServletResponse.SC_NOT_FOUND)
    }, returnDescription = ""
  )
  public Response getClipshow(@FormParam("clipshowId") String clipshowId, @Context HttpServletRequest req) {
    if (StringUtils.isBlank(clipshowId)) {
      return Response.status(Response.Status.BAD_REQUEST).build();
    }

    Clipshow show;
    try {
      show = this.service.getClipshow(clipshowId, req.getRemoteUser());
      return Response.ok(show).build();
    } catch (IllegalArgumentException e) {
      return Response.serverError().build();
    } catch (NoPermissionException e) {
      return Response.status(Response.Status.FORBIDDEN).build();
    } catch (NotFoundException e) {
      return Response.status(Response.Status.NOT_FOUND).build();
    }
  }

  @POST
  @Path("delete")
  @RestQuery(name = "deleteClipshow", description = "Deletes a clipshow from the system", 
    pathParameters = { }, 
    restParameters = {
      @RestParameter(name = "clipshowId", description = "The clipshow's ID", isRequired = false, type = RestParameter.Type.STRING)
    },
    reponses = {
      @RestResponse(description = "Clipshow found and deleted", responseCode = HttpServletResponse.SC_OK),
      @RestResponse(description = "Bad parameters", responseCode = HttpServletResponse.SC_BAD_REQUEST),
      @RestResponse(description = "User not permitted", responseCode = HttpServletResponse.SC_FORBIDDEN),
      @RestResponse(description = "Clipshow not found", responseCode = HttpServletResponse.SC_NOT_FOUND)
    }, returnDescription = ""
  )
  public Response deleteClipshow(@FormParam("clipshowId") String clipshowId, @Context HttpServletRequest req) {
    if (StringUtils.isBlank(clipshowId)) {
      return Response.status(Response.Status.BAD_REQUEST).build();
    }

    try {
      service.deleteClipshow(clipshowId, req.getRemoteUser());
      return Response.ok().build();
    } catch (IllegalArgumentException e) {
      return Response.serverError().build();
    } catch (NoPermissionException e) {
      return Response.status(Response.Status.FORBIDDEN).build();
    } catch (NotFoundException e) {
      return Response.status(Response.Status.NOT_FOUND).build();
    }
  }

  @GET
  @Path("list")
  @Produces(MediaType.APPLICATION_JSON)
  @RestQuery(name = "listClipshow", description = "Lists the available clipshows in the system for a given mediapackage", 
    pathParameters = { }, 
    restParameters = {
      @RestParameter(name = "mediapackageId", description = "The mediapackage ID", isRequired = false, type = RestParameter.Type.STRING),
      @RestParameter(name = "mediapackageId", description = "The mediapackage ID", isRequired = false, type = RestParameter.Type.STRING)
    },
    reponses = {
      @RestResponse(description = "Clipshows returned", responseCode = HttpServletResponse.SC_OK),
    }, returnDescription = "A JSON array of something"
  )
  public Response listClipshows(@FormParam("mediapackageId") String mediapackageId, @FormParam("authorId") String authorId, @Context HttpServletRequest req) {
    ListWrapper list = new ListWrapper(this.service.listClipshows(mediapackageId, authorId, req.getRemoteUser()));
    return Response.ok(list).build();
  }

  @POST
  @Path("series/create")
  @Consumes(MediaType.APPLICATION_JSON)
  @RestQuery(name = "createClipshowSeries", description = "Creates a clipshow series with a given series name", 
    pathParameters = { }, 
    restParameters = {
      @RestParameter(name = "seriesText", description = "The json description of the series", isRequired = true, type = RestParameter.Type.STRING)
    },
    reponses = {
      @RestResponse(description = "Creation successful", responseCode = HttpServletResponse.SC_OK),
      @RestResponse(description = "Bad parameters", responseCode = HttpServletResponse.SC_BAD_REQUEST)
    }, returnDescription = "The ID of the created series"
  )
  //N.B.  If you add @FormParam to seriesText you *will* get an HTTP 415.  Don't do it!
  public Response createSeries(String seriesText, @Context HttpServletRequest req) {
    if (StringUtils.isBlank(seriesText)) {
      return Response.status(Response.Status.BAD_REQUEST).build();
    }

    JSONObject decodedSeries = null;
    try {
      decodedSeries = (JSONObject) parser.parse(seriesText);
    } catch (ParseException e) {
      e.printStackTrace();
      return Response.status(Response.Status.BAD_REQUEST).build();
    }

    String title = (String) decodedSeries.get("title");
    String description = (String) decodedSeries.get("description");
    Long seriesId = service.createSeries(title, description, req.getRemoteUser());

    JSONArray show = (JSONArray) decodedSeries.get("clipshows");
    ListIterator clipshowIterator = show.listIterator();
    while (clipshowIterator.hasNext()) {
      try {
        service.addClipshowToSeries(clipshowIterator.next().toString(), seriesId.toString(), req.getRemoteUser());
      } catch (NoPermissionException e1) {
        return Response.status(Response.Status.FORBIDDEN).build();
      } catch (NotFoundException e1) {
        return Response.status(Response.Status.NOT_FOUND).build();
      }
    }

    return Response.ok(seriesId).build();
  }

  @POST
  @Path("series/update")
  @Consumes(MediaType.APPLICATION_JSON)
  @RestQuery(name = "updateClipshowSeries", description = "Creates a clipshow series with a given series name", 
    pathParameters = { }, 
    restParameters = {
      @RestParameter(name = "seriesText", description = "The json description of the series", isRequired = true, type = RestParameter.Type.STRING)
    },
    reponses = {
      @RestResponse(description = "Update successful", responseCode = HttpServletResponse.SC_OK),
      @RestResponse(description = "Bad parameters", responseCode = HttpServletResponse.SC_BAD_REQUEST)
    }, returnDescription = "The ID of the created series"
  )
  //N.B.  If you add @FormParam to seriesText you *will* get an HTTP 415.  Don't do it!
  public Response updateSeries(String seriesText, @Context HttpServletRequest req) {
    if (StringUtils.isBlank(seriesText)) {
      return Response.status(Response.Status.BAD_REQUEST).build();
    }

    JSONObject decodedSeries = null;
    try {
      decodedSeries = (JSONObject) parser.parse(seriesText);
    } catch (ParseException e) {
      e.printStackTrace();
      return Response.status(Response.Status.BAD_REQUEST).build();
    }

    String id = (String) decodedSeries.get("id");
    String title = (String) decodedSeries.get("title");
    String description = (String) decodedSeries.get("description");
    LinkedList<String> clipshows = new LinkedList<String>();    
    JSONArray show = (JSONArray) decodedSeries.get("clipshows");
    ListIterator clipshowIterator = show.listIterator();
    while (clipshowIterator.hasNext()) {
      clipshows.add((String) clipshowIterator.next());
    }

    Long seriesId;
    try {
      seriesId = service.updateSeries(id, title, description, clipshows, req.getRemoteUser());
      return Response.ok(seriesId).build();
    } catch (NoPermissionException e) {
      return Response.status(Response.Status.FORBIDDEN).build();
    } catch (NotFoundException e) {
      return Response.status(Response.Status.NOT_FOUND).build();
    }
  }

  @POST
  @Path("series/add")
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  @RestQuery(name = "addClipshowToSeries", description = "Adds a clipshow to a series", 
    pathParameters = { }, 
    restParameters = {
      @RestParameter(name = "clipshowId", description = "The ID of the clipshow", isRequired = true, type = RestParameter.Type.STRING),
      @RestParameter(name = "seriesId", description = "The ID of the series", isRequired = true, type = RestParameter.Type.STRING)
    },
    reponses = {
      @RestResponse(description = "Creation successful", responseCode = HttpServletResponse.SC_OK),
      @RestResponse(description = "Bad parameters", responseCode = HttpServletResponse.SC_BAD_REQUEST),
      @RestResponse(description = "User does not have access to the clipshow or series", responseCode = HttpServletResponse.SC_FORBIDDEN),
      @RestResponse(description = "Clipshow or series not found", responseCode = HttpServletResponse.SC_NOT_FOUND)
    }, returnDescription = ""
  )
  public Response addClipshowToSeries(@FormParam("clipshowId") String clipshowId,
          @FormParam("seriesId") String seriesId, @Context HttpServletRequest req) {
    if (StringUtils.isBlank(clipshowId) || StringUtils.isBlank(seriesId)) {
      return Response.status(Response.Status.BAD_REQUEST).build();
    }

    try {
      service.addClipshowToSeries(clipshowId, seriesId, req.getRemoteUser());
      return Response.ok().build();
    } catch (IllegalArgumentException e) {
      return Response.serverError().build();
    } catch (NoPermissionException e) {
      return Response.status(Response.Status.FORBIDDEN).build();
    } catch (NotFoundException e) {
      return Response.status(Response.Status.NOT_FOUND).build();
    }
  }

  @POST
  @Path("series/remove")
  @RestQuery(name = "removeClipshowFromSeries", description = "Removes a clipshow from a series", 
    pathParameters = { }, 
    restParameters = {
      @RestParameter(name = "clipshowId", description = "The ID of the clipshow", isRequired = true, type = RestParameter.Type.STRING),
      @RestParameter(name = "seriesId", description = "The ID of the series", isRequired = true, type = RestParameter.Type.STRING)
    },
    reponses = {
      @RestResponse(description = "Removal successful", responseCode = HttpServletResponse.SC_OK),
      @RestResponse(description = "Bad parameters", responseCode = HttpServletResponse.SC_BAD_REQUEST),
      @RestResponse(description = "User does not have access to the clipshow or series", responseCode = HttpServletResponse.SC_FORBIDDEN),
      @RestResponse(description = "Clipshow or series not found", responseCode = HttpServletResponse.SC_NOT_FOUND)
    }, returnDescription = ""
  )
  public Response removeClipshowFromSeries(@FormParam("clipshowId") String clipshowId,
          @FormParam("seriesId") String seriesId, @Context HttpServletRequest req) {
    if (StringUtils.isBlank(clipshowId) || StringUtils.isBlank(seriesId)) {
      return Response.status(Response.Status.BAD_REQUEST).build();
    }

    try {
      service.removeClipshowFromSeries(clipshowId, seriesId, req.getRemoteUser());
      return Response.ok().build();
    } catch (IllegalArgumentException e) {
      return Response.serverError().build();
    } catch (NoPermissionException e) {
      return Response.status(Response.Status.FORBIDDEN).build();
    } catch (NotFoundException e) {
      return Response.status(Response.Status.NOT_FOUND).build();
    }
  }

  @GET
  @Path("series/get")
  @Produces(MediaType.APPLICATION_JSON)
  @RestQuery(name = "getClipshowSeries", description = "Gets the a full clipshow series", 
    pathParameters = { }, 
    restParameters = {
      @RestParameter(name = "seriesId", description = "The ID of the series", isRequired = false, type = RestParameter.Type.STRING)
    },
    reponses = {
      @RestResponse(description = "Series found and returned successfully", responseCode = HttpServletResponse.SC_OK),
      @RestResponse(description = "Bad parameters", responseCode = HttpServletResponse.SC_BAD_REQUEST),
      @RestResponse(description = "User does not have access to the clipshow or series", responseCode = HttpServletResponse.SC_FORBIDDEN),
      @RestResponse(description = "Clipshow or series not found", responseCode = HttpServletResponse.SC_NOT_FOUND)
    }, returnDescription = ""
  )
  public Response getSeries(@FormParam("seriesId") String seriesId, @Context HttpServletRequest req) {
    if (StringUtils.isBlank(seriesId)) {
      return Response.status(Response.Status.BAD_REQUEST).build();
    }

    try {
      return Response.ok(service.getSeries(seriesId, req.getRemoteUser())).build();
    } catch (IllegalArgumentException e) {
      return Response.serverError().build();
    } catch (NoPermissionException e) {
      return Response.status(Response.Status.FORBIDDEN).build();
    } catch (NotFoundException e) {
      return Response.status(Response.Status.NOT_FOUND).build();
    }
  }

  @POST
  @Path("series/delete")
  @RestQuery(name = "deleteClipshowSeries", description = "Deletes a clipshow series", 
    pathParameters = { }, 
    restParameters = {
      @RestParameter(name = "seriesId", description = "The ID of the series", isRequired = true, type = RestParameter.Type.STRING)
    },
    reponses = {
      @RestResponse(description = "Deletion successful", responseCode = HttpServletResponse.SC_OK),
      @RestResponse(description = "Bad parameters", responseCode = HttpServletResponse.SC_BAD_REQUEST),
      @RestResponse(description = "User does not have access to the series", responseCode = HttpServletResponse.SC_FORBIDDEN),
      @RestResponse(description = "Clipshow or series not found", responseCode = HttpServletResponse.SC_NOT_FOUND)
    }, returnDescription = ""
  )
  public Response deleteSeries(@FormParam("seriesId") String seriesId, @Context HttpServletRequest req) {
    if (StringUtils.isBlank(seriesId)) {
      return Response.status(Response.Status.BAD_REQUEST).build();
    }

    try {
      service.deleteSeries(seriesId, req.getRemoteUser());
      return Response.ok().build();
    } catch (IllegalArgumentException e) {
      return Response.serverError().build();
    } catch (NoPermissionException e) {
      return Response.status(Response.Status.FORBIDDEN).build();
    } catch (NotFoundException e) {
      return Response.status(Response.Status.NOT_FOUND).build();
    }
  }

  @GET
  @Path("series/list")
  @Produces(MediaType.APPLICATION_JSON)
  @RestQuery(name = "listClipshow", description = "Lists the available clipshows in the system for a given mediapackage", 
    pathParameters = { }, 
    restParameters = { 
      @RestParameter(name = "authorId", description = "Setting this parameter will filter by author name", isRequired = false, type = RestParameter.Type.STRING)
    },
    reponses = {
      @RestResponse(description = "Series list returned", responseCode = HttpServletResponse.SC_OK),
      @RestResponse(description = "Bad parameters", responseCode = HttpServletResponse.SC_BAD_REQUEST)
    }, returnDescription = "A JSON array of something"
  )
  public Response listSeries(@FormParam("authorId") String authorId, @Context HttpServletRequest req) {
    ListWrapper list = new ListWrapper(this.service.listSeries(authorId, req.getRemoteUser()));
    return Response.ok(list).build();
  }

  @POST
  @Path("vote/add")
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  @RestQuery(name = "voteForClipshow", description = "Registers a vote for a clipshow by the current user", 
    pathParameters = { }, 
    restParameters = {
      @RestParameter(name = "clipshowId", description = "The ID of the clipshow", isRequired = true, type = RestParameter.Type.STRING),
      @RestParameter(name = "types", description = "The types of vote (funny, good, dislike)", isRequired = true, type = RestParameter.Type.STRING)
    },
    reponses = {
      @RestResponse(description = "Vote successful", responseCode = HttpServletResponse.SC_OK),
      @RestResponse(description = "Bad parameters", responseCode = HttpServletResponse.SC_BAD_REQUEST),
      @RestResponse(description = "User does not have access to the series", responseCode = HttpServletResponse.SC_FORBIDDEN),
      @RestResponse(description = "Clipshow or series not found", responseCode = HttpServletResponse.SC_NOT_FOUND)
    }, returnDescription = ""
  )
  public Response voteForClipshow(@FormParam("clipshowId") String clipshowId, @FormParam("types") String types, @Context HttpServletRequest req) {
    if (StringUtils.isBlank(clipshowId) || StringUtils.isBlank(types)) {
      return Response.status(Response.Status.BAD_REQUEST).build();
    }

    try {
      service.voteForClipshow(req.getRemoteUser(), types, clipshowId);
      return Response.ok().build();
    } catch (IllegalArgumentException e) {
      return Response.serverError().build();
    } catch (NoPermissionException e) {
      return Response.status(Response.Status.FORBIDDEN).build();
    } catch (NotFoundException e) {
      return Response.status(Response.Status.NOT_FOUND).build();
    }
  }

  @GET
  @Path("vote/mine")
  @RestQuery(name = "getUserVotesForClipshow", description = "Returns the number of votes for a given clipshow by the current user", 
    pathParameters = { }, 
    restParameters = {
      @RestParameter(name = "clipshowId", description = "The ID of the clipshow", isRequired = false, type = RestParameter.Type.STRING)
    },
    reponses = {
      @RestResponse(description = "Vote count returned successfully", responseCode = HttpServletResponse.SC_OK),
      @RestResponse(description = "Bad parameters", responseCode = HttpServletResponse.SC_BAD_REQUEST),
      @RestResponse(description = "Clipshow or series not found", responseCode = HttpServletResponse.SC_NOT_FOUND)
    }, returnDescription = ""
  )
  public Response getUserVotesForClipshow(@FormParam("clipshowId") String clipshowId, @Context HttpServletRequest req) {
    if (StringUtils.isBlank(clipshowId)) {
      return Response.status(Response.Status.BAD_REQUEST).build();
    }

    try {
      Properties p = new Properties();
      for (Entry<Type, Integer> e : service.getVotesForClipshow(clipshowId, req.getRemoteUser()).entrySet()) {
        p.put(e.getKey().toString(), e.getValue().toString());
      }
      return Response.ok(new PropertiesResponse(p)).build();
    } catch (IllegalArgumentException e) {
      return Response.serverError().build();
    } catch (NotFoundException e) {
      return Response.status(Response.Status.NOT_FOUND).build();
    }
  }

  @GET
  @Path("vote/count")
  @RestQuery(name = "getVotesForClipshow", description = "Returns the number of votes for a given clipshow", 
    pathParameters = { }, 
    restParameters = {
      @RestParameter(name = "clipshowId", description = "The ID of the clipshow", isRequired = false, type = RestParameter.Type.STRING)
    },
    reponses = {
      @RestResponse(description = "Vote count returned successfully", responseCode = HttpServletResponse.SC_OK),
      @RestResponse(description = "Bad parameters", responseCode = HttpServletResponse.SC_BAD_REQUEST),
      @RestResponse(description = "Clipshow or series not found", responseCode = HttpServletResponse.SC_NOT_FOUND)
    }, returnDescription = ""
  )
  public Response getVotesForClipshow(@FormParam("clipshowId") String clipshowId) {
    if (StringUtils.isBlank(clipshowId)) {
      return Response.status(Response.Status.BAD_REQUEST).build();
    }

    try {
      Properties p = new Properties();
      for (Entry<Type, Integer> e : service.getVotesForClipshow(clipshowId).entrySet()) {
        p.put(e.getKey().toString(), e.getValue().toString());
      }
      return Response.ok(new PropertiesResponse(p)).build();
    } catch (IllegalArgumentException e) {
      return Response.serverError().build();
    } catch (NotFoundException e) {
      return Response.status(Response.Status.NOT_FOUND).build();
    }
  }

  @GET
  @Path("user/getName")
  @Produces(MediaType.TEXT_PLAIN)
  @RestQuery(name = "getName", description = "Gets the current user's display name", 
  pathParameters = { }, 
  restParameters = { },
  reponses = {
    @RestResponse(description = "Vote successful", responseCode = HttpServletResponse.SC_OK),
    @RestResponse(description = "Server error (please file a bug)", responseCode = HttpServletResponse.SC_INTERNAL_SERVER_ERROR)
  }, returnDescription = "The user's display name"
)
  public Response getName(@Context HttpServletRequest req) {
    try {
      return Response.ok(service.getUserDisplayName(req.getRemoteUser())).build();
    } catch (IllegalArgumentException e) {
      return Response.serverError().build();
    }
  }

  @POST
  @Path("user/newName")
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  @RestQuery(name = "changeName", description = "Changes the displayed username for a user", 
    pathParameters = { }, 
    restParameters = {
      @RestParameter(name = "newName", description = "The user's desired new name", isRequired = true, type = RestParameter.Type.STRING)
    },
    reponses = {
      @RestResponse(description = "Change succeeded", responseCode = HttpServletResponse.SC_OK)
    }, returnDescription = ""
  )
  public Response changeName(@FormParam("newName") String newName, @Context HttpServletRequest req) {
    if (StringUtils.isBlank(newName)) {
      return Response.status(Response.Status.BAD_REQUEST).build();
    }

    this.service.changeUsername(req.getRemoteUser(), newName);
    return Response.ok().build();
  }

  @GET
  @Path("user/rankings")
  @Produces(MediaType.APPLICATION_JSON)
  @RestQuery(name = "getRankings", description = "Returns a list of the top five display names, and the number of votes for the current user",
    pathParameters = { },
    restParameters = { },
    reponses = {
      @RestResponse(description = "Rankings found successfully", responseCode = HttpServletResponse.SC_OK)
    }, returnDescription = ""
  )
  public Response getRankings(@Context HttpServletRequest req) {
    return Response.ok(service.getRankings(req.getRemoteUser())).build();
  }

  //TODO:  Remove type as a path parameter here.
  @GET
  @Path("search/{type}")
  @Produces(MediaType.APPLICATION_JSON)
  @RestQuery(name = "search", description = "Returns a list of clipshows based on the query parameter and type",
    pathParameters = {
      @RestParameter(name = "type", isRequired = false, type = RestParameter.Type.STRING, description = "Type of query")
    },
    restParameters = {
      @RestParameter(name = "parameter", isRequired = false, type = RestParameter.Type.STRING, description = "The thing you are searching for"),
      @RestParameter(name = "mediapackageId", isRequired = false, type = RestParameter.Type.STRING, description = "The optional mediapackage id")
    },
    reponses = { 
      @RestResponse(description = "Search succeeded", responseCode = HttpServletResponse.SC_OK),
      @RestResponse(description = "Missing or invalid search parameters", responseCode = HttpServletResponse.SC_BAD_REQUEST)
  }, returnDescription = " A list of clipshows"
  )
  public Response search(@FormParam("parameter") String parameter, @FormParam("mediapackageId") String mediapackageId, @PathParam("type") String type, @Context HttpServletRequest req) {
    ListWrapper list = new ListWrapper(this.service.search(type, parameter, mediapackageId, req.getRemoteUser()));
    return Response.ok(list).build();
  }

  @GET
  @Path("consented")
  @Produces(MediaType.TEXT_PLAIN)
  public Response hasConsented(@Context HttpServletRequest req) {
    return Response.ok(this.service.hasConsented(req.getRemoteUser())).build();
  }

  @GET
  @Path("giveConsent")
  public Response giveConsent(@Context HttpServletRequest req) {
    this.service.giveConsent(req.getRemoteUser());
    return Response.ok().build();
  }

  public ClipshowRestService() {
  }
}
