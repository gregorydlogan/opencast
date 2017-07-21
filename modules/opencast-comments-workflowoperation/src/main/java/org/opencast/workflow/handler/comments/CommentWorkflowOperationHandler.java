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

package org.opencast.workflow.handler.comments;

import org.opencast.event.comment.EventComment;
import org.opencast.event.comment.EventCommentException;
import org.opencast.event.comment.EventCommentService;
import org.opencast.job.api.JobContext;
import org.opencast.security.api.SecurityService;
import org.opencast.util.NotFoundException;
import org.opencast.util.data.Option;
import org.opencast.workflow.api.AbstractWorkflowOperationHandler;
import org.opencast.workflow.api.WorkflowInstance;
import org.opencast.workflow.api.WorkflowOperationException;
import org.opencast.workflow.api.WorkflowOperationInstance;
import org.opencast.workflow.api.WorkflowOperationResult;
import org.opencast.workflow.api.WorkflowOperationResult.Action;

import com.entwinemedia.fn.data.Opt;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * A workflow operation handler for creating, resolving and deleting comments automatically during the workflow process.
 */
public class CommentWorkflowOperationHandler extends AbstractWorkflowOperationHandler {
  protected static final String ACTION = "action";
  protected static final String DESCRIPTION = "description";
  protected static final String REASON = "reason";

  /** The logging facility */
  private static final Logger logger = LoggerFactory.getLogger(CommentWorkflowOperationHandler.class);

  /* service references */
  private EventCommentService eventCommentService;
  private SecurityService securityService;

  /** The configuration options for this handler */
  private static final SortedMap<String, String> CONFIG_OPTIONS;

  public enum Operation {
    create, resolve, delete
  };

  static {
    CONFIG_OPTIONS = new TreeMap<String, String>();
    CONFIG_OPTIONS.put(REASON,
            "The optional comment reason's i18n id. You can find the id in etc/listproviders/event.comment.reasons.properties");
    CONFIG_OPTIONS.put(DESCRIPTION, "The description text to add to the comment.");
    CONFIG_OPTIONS.put(ACTION, "Options are " + StringUtils.join(Operation.values(), ",")
            + ". Creates a new comment, marks a comment as resolved or deletes a comment that matches the same description and reason. By default creates.");
  }

  @Override
  public SortedMap<String, String> getConfigurationOptions() {
    return CONFIG_OPTIONS;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public WorkflowOperationResult start(WorkflowInstance workflowInstance, JobContext context)
          throws WorkflowOperationException {
    logger.debug("Running comment workflow operation on workflow {}", workflowInstance.getId());
    try {
      return handleCommentOperation(workflowInstance);
    } catch (Exception e) {
      throw new WorkflowOperationException(e);
    }
  }

  /**
   * Determine the type of operation to do on a comment and execute it.
   *
   * @param workflowInstance
   *          The {@link WorkflowInstance} to be handled.
   * @return The result of handling the {@link WorkflowInstance}
   * @throws EventCommentException
   *           Thrown if there is an issue creating, resolving or deleting a comment
   * @throws NotFoundException
   *           Thrown if the comment cannot be found to delete.
   */
  private WorkflowOperationResult handleCommentOperation(WorkflowInstance workflowInstance)
          throws EventCommentException, NotFoundException {
    Date date = new Date();
    WorkflowOperationInstance operation = workflowInstance.getCurrentOperation();
    Operation action;
    String inputAction = StringUtils.trimToNull(operation.getConfiguration(ACTION));
    if (inputAction == null) {
      action = Operation.create;
    } else {
      action = Operation.valueOf(inputAction.toLowerCase());
    }
    String reason = StringUtils.trimToNull(operation.getConfiguration(REASON));
    String description = StringUtils.trimToNull(operation.getConfiguration(DESCRIPTION));
    switch (action) {
      case create:
        createComment(workflowInstance, reason, description);
        break;
      case resolve:
        resolveComment(workflowInstance, reason, description);
        break;
      case delete:
        deleteComment(workflowInstance, reason, description);
        break;
      default:
        logger.warn(
                "Unknown action '{}' for comment with description '{}' and reason '{}'. It should be one of the following: ",
                new Object[] { inputAction, description, reason, StringUtils.join(Operation.values(), ",") });
    }
    WorkflowOperationResult result = createResult(workflowInstance.getMediaPackage(), Action.CONTINUE,
            (new Date().getTime()) - date.getTime());
    return result;
  }

  /**
   * Create a new comment if one doesn't already exist with the reason and description.
   *
   * @param workflowInstance
   *          The {@link WorkflowInstance} to handle.
   * @param reason
   *          The reason for the comment.
   * @param description
   *          The descriptive text of the comment.
   * @throws EventCommentException
   *           Thrown if unable to create the comment.
   */
  private void createComment(WorkflowInstance workflowInstance, String reason, String description)
          throws EventCommentException {
    Opt<EventComment> optComment = findComment(workflowInstance.getMediaPackage().getIdentifier().toString(), reason,
            description);
    if (optComment.isNone()) {
      EventComment comment = EventComment.create(Option.<Long> none(), workflowInstance.getMediaPackage().getIdentifier().toString(),
              securityService.getOrganization().getId(), description, workflowInstance.getCreator(), reason, false);
      eventCommentService.updateComment(comment);
    } else {
      logger.warn("Not creating comment with '{}' text and '{}' reason as it already exists for this event.",
              description, reason);
    }
  }

  /**
   * Resolve a comment with matching reason and description.
   *
   * @param workflowInstance
   *          The {@link WorkflowInstance} to handle.
   * @param reason
   *          The reason for the comment.
   * @param description
   *          The comment's descriptive text.
   * @throws EventCommentException
   *           Thrown if unable to update the comment.
   */
  private void resolveComment(WorkflowInstance workflowInstance, String reason, String description)
          throws EventCommentException {
    Opt<EventComment> optComment = findComment(workflowInstance.getMediaPackage().getIdentifier().toString(), reason,
            description);
    if (optComment.isSome()) {
      EventComment comment = EventComment.create(optComment.get().getId(), workflowInstance.getMediaPackage().getIdentifier().toString(),
              securityService.getOrganization().getId(), optComment.get().getText(),
              optComment.get().getAuthor(), optComment.get().getReason(), true, optComment.get().getCreationDate(),
              optComment.get().getModificationDate(), optComment.get().getReplies());
      eventCommentService.updateComment(comment);
    } else {
      logger.warn("Not resolving comment with '{}' text and '{}' reason as it doesn't exist.", description, reason);
    }
  }

  /**
   * Delete a comment with matching reason and description.
   *
   * @param workflowInstance
   *          The {@link WorkflowInstance} to handle.
   * @param reason
   *          The reason for the comment.
   * @param description
   *          The comment's descriptive text.
   * @throws EventCommentException
   *           Thrown if unable to delete the comment.
   * @throws NotFoundException
   *           Thrown if unable to find the comment.
   */
  private void deleteComment(WorkflowInstance workflowInstance, String reason, String description)
          throws EventCommentException, NotFoundException {
    Opt<EventComment> optComment = findComment(workflowInstance.getMediaPackage().getIdentifier().toString(), reason,
            description);
    if (optComment.isSome()) {
      try {
        eventCommentService.deleteComment(optComment.get().getId().get());
      } catch (NotFoundException e) {
        logger.warn("Not deleting comment with '{}' text and '{}' reason and id '{}' as it doesn't exist.",
                new Object[] { description, reason, optComment.get().getId() });
      }
    } else {
      logger.warn("Not deleting comment with '{}' text and '{}' reason as it doesn't exist.", description, reason);
    }
  }

  /**
   * Find a comment by its reason and description.
   *
   * @param eventId
   *          The event id to search the comments for.
   * @param reason
   *          The reason for the comment.
   * @param description
   *          The description for the comment.
   * @return The comment if one is found matching the reason and description.
   * @throws EventCommentException
   *           Thrown if there was a problem finding the comment.
   */
  private Opt<EventComment> findComment(String eventId, String reason, String description) throws EventCommentException {
    Opt<EventComment> comment = Opt.none();
    List<EventComment> eventComments = eventCommentService.getComments(eventId);
    for (EventComment existingComment : eventComments) {
      if (isSameComment(existingComment, reason, description)) {
        comment = Opt.some(existingComment);
        break;
      }
    }
    return comment;
  }

  /**
   * Determines if a comment has a given reason and description.
   *
   * @param comment
   *          The comment to compare.
   * @param reason
   *          The reason for the comment.
   * @param description
   *          The description for the comment.
   * @return True if the two properties match.
   */
  private boolean isSameComment(EventComment comment, String reason, String description) {
    return description == null ? comment.getText() == null
            : description.equals(comment.getText())
                    && (reason == null ? comment.getReason() == null : reason.equals(comment.getReason()));
  }

  /**
   * Callback from the OSGi environment that will pass a reference to the workflow service upon component activation.
   *
   * @param eventCommentService
   *          the workflow service
   */
  public void setEventCommentService(EventCommentService eventCommentService) {
    this.eventCommentService = eventCommentService;
  }

  /** OSGi DI */
  void setSecurityService(SecurityService service) {
    this.securityService = service;
  }

}
