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
package org.opencast.index.service.message;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.opencast.index.service.impl.index.event.EventIndexUtils.getOrCreateEvent;
import static org.opencast.index.service.impl.index.event.EventIndexUtils.updateEvent;

import org.opencast.authorization.xacml.manager.api.AclServiceFactory;
import org.opencast.authorization.xacml.manager.api.ManagedAcl;
import org.opencast.index.service.impl.index.event.Event;
import org.opencast.index.service.impl.index.event.EventIndexUtils;
import org.opencast.index.service.util.AccessInformationUtil;
import org.opencast.matterhorn.search.SearchIndexException;
import org.opencast.mediapackage.MediaPackage;
import org.opencast.message.broker.api.MessageSender;
import org.opencast.message.broker.api.assetmanager.AssetManagerItem;
import org.opencast.message.broker.api.assetmanager.AssetManagerItem.DeleteEpisode;
import org.opencast.message.broker.api.assetmanager.AssetManagerItem.DeleteSnapshot;
import org.opencast.message.broker.api.assetmanager.AssetManagerItem.TakeSnapshot;
import org.opencast.metadata.dublincore.DublinCoreCatalog;
import org.opencast.security.api.AccessControlList;
import org.opencast.security.api.AccessControlParser;
import org.opencast.security.api.User;
import org.opencast.util.NotFoundException;

import com.entwinemedia.fn.Fn;
import com.entwinemedia.fn.Fx;
import com.entwinemedia.fn.Unit;
import com.entwinemedia.fn.data.Opt;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Handler for messages from the {@link org.opencast.assetmanager.api.AssetManager}.
 */
public class AssetManagerMessageReceiverImpl extends BaseMessageReceiverImpl<AssetManagerItem> {
  private static final Logger logger = LoggerFactory.getLogger(AssetManagerMessageReceiverImpl.class);

  private AclServiceFactory aclServiceFactory;

  /**
   * Creates a new message receiver that is listening to the admin ui destination of the archive queue.
   */
  public AssetManagerMessageReceiverImpl() {
    super(MessageSender.DestinationType.Queue);
  }

  @Override
  protected void execute(AssetManagerItem msg) {
    msg.decompose(takeSnapshot, deleteSnapshot, deleteEpisode);
  }

  /**
   * Handle an update message.
   */
  private void handleMessage(TakeSnapshot msg) {
    logger.debug("Received AssetManager take snapshot message");
    final MediaPackage mp = msg.getMediapackage();
    final Opt<DublinCoreCatalog> episodeDublincore = msg.getEpisodeDublincore();
    final String organization = getSecurityService().getOrganization().getId();
    final User user = getSecurityService().getUser();

    // Load or create the corresponding recording event
    final Event event;
    try {
      event = getOrCreateEvent(mp.getIdentifier().toString(), organization, user, getSearchIndex());
      final AccessControlList acl = msg.getAcl();
      List<ManagedAcl> acls = aclServiceFactory.serviceFor(getSecurityService().getOrganization()).getAcls();
      for (final ManagedAcl managedAcl : AccessInformationUtil.matchAcls(acls, acl)) {
        event.setManagedAcl(managedAcl.getName());
      }
      event.setAccessPolicy(AccessControlParser.toJsonSilent(acl));
      event.setArchiveVersion(msg.getVersion());
      if (isBlank(event.getCreator()))
        event.setCreator(getSecurityService().getUser().getName());
      updateEvent(event, mp);
      if (episodeDublincore.isSome()) {
        updateEvent(event, episodeDublincore.get());
      }
    } catch (SearchIndexException e) {
      logger.error("Error retrieving the recording event from the search index: {}", e.getMessage());
      return;
    }

    // Update series name if not already done
    try {
      EventIndexUtils.updateSeriesName(event, organization, user, getSearchIndex());
    } catch (SearchIndexException e) {
      logger.error("Error updating the series name of the event to index: {}", ExceptionUtils.getStackTrace(e));
    }

    // Persist the scheduling event
    try {
      getSearchIndex().addOrUpdate(event);
      logger.debug("Asset manager entry {} updated in the admin ui search index", event.getIdentifier());
    } catch (SearchIndexException e) {
      logger.error("Error retrieving the recording event from the search index: {}", e.getMessage());
    }
  }

  private final Fn<TakeSnapshot, Unit> takeSnapshot = new Fx<TakeSnapshot>() {
    @Override
    public void apply(TakeSnapshot takeSnapshot) {
      handleMessage(takeSnapshot);
    }
  }.toFn();

  /**
   * Handle a delete message.
   */
  private void handleMessage(DeleteEpisode msg) {
    final String eventId = msg.getMediaPackageId();
    final String organization = getSecurityService().getOrganization().getId();
    final User user = getSecurityService().getUser();
    logger.debug("Received AssetManager delete episode message {}", eventId);
    // Remove the archived entry from the search index
    try {
      getSearchIndex().deleteAssets(organization, user, eventId);
      logger.debug("Archived media package {} removed from admin ui search index", eventId);
    } catch (NotFoundException e) {
      logger.warn("Archived media package {} not found for deletion", eventId);
    } catch (SearchIndexException e) {
      logger.error("Error deleting the archived entry {} from the search index: {}", eventId,
              ExceptionUtils.getStackTrace(e));
    }
  }

  private final Fn<DeleteSnapshot, Unit> deleteSnapshot = new Fx<DeleteSnapshot>() {
    @Override
    public void apply(DeleteSnapshot delete) {
      // do nothing
      // events of this type are not handled by the admin UI index currently
    }
  }.toFn();

  private final Fn<DeleteEpisode, Unit> deleteEpisode = new Fx<DeleteEpisode>() {
    @Override
    public void apply(DeleteEpisode delete) {
      handleMessage(delete);
    }
  }.toFn();

  //

  /** OSGi callback for acl services. */
  public void setAclServiceFactory(AclServiceFactory aclServiceFactory) {
    this.aclServiceFactory = aclServiceFactory;
  }
}
