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

package org.opencastproject.series.impl.update;

import static org.opencastproject.assetmanager.api.fn.Enrichments.enrich;
import static org.opencastproject.job.api.Job.Status.FINISHED;
import static org.opencastproject.mediapackage.MediaPackageElementParser.getFromXml;
import static org.opencastproject.mediapackage.MediaPackageElements.XACML_POLICY_EPISODE;
import static org.opencastproject.workflow.handler.distribution.EngagePublicationChannel.CHANNEL_ID;

import org.opencastproject.assetmanager.api.AssetManager;
import org.opencastproject.assetmanager.api.AssetManagerException;
import org.opencastproject.assetmanager.api.Snapshot;
import org.opencastproject.assetmanager.api.query.AQueryBuilder;
import org.opencastproject.assetmanager.api.query.AResult;
import org.opencastproject.distribution.api.DistributionException;
import org.opencastproject.distribution.api.DistributionService;
import org.opencastproject.job.api.Job;
import org.opencastproject.job.api.JobBarrier;
import org.opencastproject.mediapackage.Attachment;
import org.opencastproject.mediapackage.Catalog;
import org.opencastproject.mediapackage.MediaPackage;
import org.opencastproject.mediapackage.MediaPackageElement;
import org.opencastproject.mediapackage.MediaPackageElementFlavor;
import org.opencastproject.mediapackage.MediaPackageElements;
import org.opencastproject.mediapackage.MediaPackageException;
import org.opencastproject.metadata.dublincore.DublinCore;
import org.opencastproject.metadata.dublincore.DublinCoreCatalog;
import org.opencastproject.metadata.dublincore.DublinCoreCatalogService;
import org.opencastproject.metadata.dublincore.DublinCoreUtil;
import org.opencastproject.search.api.SearchException;
import org.opencastproject.search.api.SearchQuery;
import org.opencastproject.search.api.SearchResult;
import org.opencastproject.search.api.SearchResultItem;
import org.opencastproject.search.api.SearchService;
import org.opencastproject.security.api.AclScope;
import org.opencastproject.security.api.AuthorizationService;
import org.opencastproject.security.api.Organization;
import org.opencastproject.security.api.OrganizationDirectoryService;
import org.opencastproject.security.api.SecurityService;
import org.opencastproject.security.api.UnauthorizedException;
import org.opencastproject.security.api.User;
import org.opencastproject.security.util.SecurityUtil;
import org.opencastproject.serviceregistry.api.ServiceRegistry;
import org.opencastproject.serviceregistry.api.ServiceRegistryException;
import org.opencastproject.util.NotFoundException;
import org.opencastproject.util.data.Function2;
import org.opencastproject.workflow.api.WorkflowException;
import org.opencastproject.workflow.api.WorkflowInstance;
import org.opencastproject.workflow.api.WorkflowQuery;
import org.opencastproject.workflow.api.WorkflowService;
import org.opencastproject.workflow.api.WorkflowSet;
import org.opencastproject.workspace.api.Workspace;

import org.apache.commons.io.FilenameUtils;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;

/**
 * Very simple approach to serialize the work of all three dependend update handlers. Todo: Merge all handlers into one
 * to avoid unnecessary distribution updates etc.
 */
@Component(
    property = {
        "service.description=Conducting event handler for series events"
    },
    immediate = true,
    service = { ConductingSeriesUpdatedEventHandler.class }
)
public class ConductingSeriesUpdatedEventHandler {

  private static final Logger logger = LoggerFactory.getLogger(ConductingSeriesUpdatedEventHandler.class);

  private SecurityService securityService;
  private AssetManager assetManager;
  private OrganizationDirectoryService organizationDirectoryService;
  private AuthorizationService authorizationService;
  private Workspace workspace;
  private DublinCoreCatalogService dublinCoreService;
  private SearchService searchService;
  private DistributionService distributionService;
  private ServiceRegistry serviceRegistry;
  private WorkflowService workflowService;
  private String systemAccount = null;

  @Activate
  public void activate(ComponentContext cc) {
    logger.info("Activating {}", ConductingSeriesUpdatedEventHandler.class.getName());
    this.systemAccount = cc.getBundleContext().getProperty("org.opencastproject.security.digest.user");
  }

  @Deactivate
  public void deactivate(ComponentContext cc) {
    logger.info("Deactivating {}", ConductingSeriesUpdatedEventHandler.class.getName());
  }

  public void execute(SeriesItem seriesItem) {
    // A series or its ACL has been updated. Find any mediapackages with that series, and update them.
    logger.debug("Handling {}", seriesItem);
    String seriesId = seriesItem.getSeriesId();

    // We must be an administrative user to make this query
    final User prevUser = securityService.getUser();
    final Organization prevOrg = securityService.getOrganization();
    try {
      securityService.setUser(SecurityUtil.createSystemUser(systemAccount, prevOrg));

      Function2<Snapshot, SeriesItem, MediaPackage> assetFn = null;
      Function2<SearchResultItem, SeriesItem, MediaPackage> seriesFn = null;
      Function2<WorkflowInstance, SeriesItem, Boolean> workflowFn = null;
      switch (seriesItem.getType()) {
        case UpdateAcl:
          assetFn = assetManagerUpdateAcl;
          seriesFn = seriesUpdateAcl;
          workflowFn = workflowUpdateAcl;
          break;
        case UpdateElement:
          assetFn = assetManagerElementUpdate;
          break;
        case UpdateCatalog:
          assetFn = assetManagerElementUpdate;
          seriesFn = seriesUpdateCatalog;
          workflowFn = workflowUpdateElement;
          break;
        case Delete:
          assetFn = assetManagerDelete;
          seriesFn = seriesDelete;
          workflowFn = workflowDelete;
          break;
        default:
          throw new IllegalArgumentException("Unhandled event type");
      }

      seriesServiceUpdate(seriesItem, seriesFn);
      assetManagerUpdate(seriesId, seriesItem, assetFn);
      workflowServiceUpdate(seriesItem, workflowFn);
    } catch (SearchException e) {
      logger.warn("Unable to find mediapackages for series {} in search: {}", seriesItem, e.getMessage());
    } catch (WorkflowException | UnauthorizedException | MediaPackageException | ServiceRegistryException
             | NotFoundException | IOException | DistributionException e) {
      logger.warn("Unable to update mediapackages for series {} for user {}: {} {}", seriesId, prevUser.getUsername(),
          e.getClass().getSimpleName(), e.getMessage());
    } finally {
      securityService.setOrganization(prevOrg);
      securityService.setUser(prevUser);
    }
  }

  public void assetManagerUpdate(String seriesId, SeriesItem sItem, Function2<Snapshot, SeriesItem, MediaPackage> fn)
          throws NotFoundException {
    //If there's nothing to do, bail out
    if (null == fn) {
      return;
    }
    final AQueryBuilder q = assetManager.createQuery();
    final AResult result = q.select(q.snapshot()).where(q.seriesId().eq(seriesId).and(q.version().isLatest())).run();
    for (Snapshot snapshot : enrich(result).getSnapshots()) {
      final String orgId = snapshot.getOrganizationId();
      final Organization organization = organizationDirectoryService.getOrganization(orgId);
      if (organization == null) {
        logger.warn("Skipping update of episode {} since organization {} is unknown",
            snapshot.getMediaPackage().getIdentifier().toString(), orgId);
        continue;
      }
      securityService.setOrganization(organization);

      MediaPackage mp = fn.apply(snapshot, sItem);
      if (null == mp) {
        logger.error("Error processing mediapackage {}, not snapshotting mediapackage", mp.getIdentifier().toString());
        continue;
      }

      try {
        // Update the asset manager with the modified mediapackage
        assetManager.takeSnapshot(snapshot.getOwner(), mp);
      } catch (AssetManagerException e) {
        logger.error("Error updating mediapackage {}", mp.getIdentifier().toString(), e);
      }
    }
  }

  public void seriesServiceUpdate(SeriesItem seriesItem, Function2<SearchResultItem, SeriesItem, MediaPackage> fn)
          throws UnauthorizedException, NotFoundException, DistributionException, MediaPackageException,
          ServiceRegistryException, IOException {
    //If there's nothing to do, bail out
    if (null == fn) {
      return;
    }

    // A series or its ACL has been updated. Find any mediapackages with that series, and update them.
    logger.debug("Handling {}", seriesItem);
    String seriesId = seriesItem.getSeriesId();

    // We must be an administrative user to make this query
    final User prevUser = securityService.getUser();
    final Organization prevOrg = securityService.getOrganization();

    securityService.setUser(SecurityUtil.createSystemUser(systemAccount, prevOrg));

    SearchQuery q = new SearchQuery().withSeriesId(seriesId);
    SearchResult result = searchService.getForAdministrativeRead(q);

    for (SearchResultItem item : result.getItems()) {
      Organization org = organizationDirectoryService.getOrganization(item.getOrganization());
      securityService.setOrganization(org);

      MediaPackage mp = fn.apply(item, seriesItem);
      if (null == mp) {
        logger.error("Error processing mediapackage {}, not updating the search index", mp.getIdentifier().toString());
        continue;
      }

      // Update the search index with the modified mediapackage
      Job searchJob = searchService.add(mp);
      JobBarrier barrier = new JobBarrier(null, serviceRegistry, searchJob);
      barrier.waitForJobs();
    }
  }

  public void workflowServiceUpdate(final SeriesItem seriesItem, Function2<WorkflowInstance, SeriesItem, Boolean> fn)
          throws WorkflowException, UnauthorizedException, NotFoundException, IOException {
    //If there's nothing to do, bail out
    if (null == fn) {
      return;
    }

    // A series or its ACL has been updated. Find any mediapackages with that series, and update them.
    logger.debug("Handling {}", seriesItem);
    String seriesId = seriesItem.getSeriesId();

    // We must be an administrative user to make this query
    final User prevUser = securityService.getUser();
    final Organization prevOrg = securityService.getOrganization();
    securityService.setUser(SecurityUtil.createSystemUser(systemAccount, prevOrg));

    // Note: getWorkflowInstances will only return a given number of results (default 20)
    WorkflowQuery q = new WorkflowQuery().withSeriesId(seriesId);
    WorkflowSet result = workflowService.getWorkflowInstancesForAdministrativeRead(q);
    Integer offset = 0;

    while (result.size() > 0) {
      for (WorkflowInstance instance : result.getItems()) {
        if (!instance.isActive()) {
          continue;
        }

        Organization org = organizationDirectoryService.getOrganization(instance.getOrganizationId());
        securityService.setOrganization(org);

        if (!fn.apply(instance, seriesItem)) {
          logger.error("Error processing workflow {}, not updating the workflow service", instance.getId());
          continue;
        }

        // Update the search index with the modified mediapackage
        workflowService.update(instance);
      }
      offset++;
      q = q.withStartPage(offset);
      result = workflowService.getWorkflowInstancesForAdministrativeRead(q);
    }
  }

  /*
   * Begin workflow update functions
   */

  // Update the series dublin core
  private final Function2<WorkflowInstance, SeriesItem, Boolean> workflowUpdateElement = new Function2<>() {
    @Override
    public Boolean apply(WorkflowInstance instance, SeriesItem seriesItem) {
      try {
        MediaPackage mp = instance.getMediaPackage();
        DublinCoreCatalog seriesDublinCore = seriesItem.getMetadata();
        mp.setSeriesTitle(seriesDublinCore.getFirst(DublinCore.PROPERTY_TITLE));

        // Update the series dublin core
        Catalog[] seriesCatalogs = mp.getCatalogs(MediaPackageElements.SERIES);
        if (seriesCatalogs.length == 1) {
          Catalog c = seriesCatalogs[0];
          String filename = FilenameUtils.getName(c.getURI().toString());

          URI uri = workspace.put(mp.getIdentifier().toString(), c.getIdentifier(), filename,
              dublinCoreService.serialize(seriesDublinCore));
          c.setURI(uri);
          // setting the URI to a new source so the checksum will most like be invalid
          c.setChecksum(null);
        }
        return true;
      } catch (IOException e) {
        logger.error("Unable to update workflow {}", instance.getId(), e);
        return false;
      }
    }
  };

  // Update the series XACML file
  private final Function2<WorkflowInstance, SeriesItem, Boolean> workflowUpdateAcl = new Function2<>() {
    @Override
    public Boolean apply(WorkflowInstance instance, SeriesItem seriesItem) {
      MediaPackage mp = instance.getMediaPackage();
      // Build a new XACML file for this mediapackage
      try {
        if (seriesItem.getOverrideEpisodeAcl()) {
          authorizationService.removeAcl(mp, AclScope.Episode);
        }
        authorizationService.setAcl(mp, AclScope.Series, seriesItem.getAcl());
      } catch (MediaPackageException e) {
        logger.error("Error setting ACL for media package {}", mp.getIdentifier(), e);
        return false;
      }
      return true;
    }
  };

  private final Function2<WorkflowInstance, SeriesItem, Boolean> workflowDelete = new Function2<>() {
    @Override
    public Boolean apply(WorkflowInstance instance, SeriesItem seriesItem) {
      try {
        MediaPackage mp = instance.getMediaPackage();
        mp.setSeries(null);
        mp.setSeriesTitle(null);
        for (Catalog c : mp.getCatalogs(MediaPackageElements.SERIES)) {
          mp.remove(c);
          try {
            workspace.delete(c.getURI());
          } catch (NotFoundException e) {
            logger.info("No series catalog to delete found {}", c.getURI());
          }
        }
        for (Catalog episodeCatalog : mp.getCatalogs(MediaPackageElements.EPISODE)) {
          DublinCoreCatalog episodeDublinCore = DublinCoreUtil.loadDublinCore(workspace, episodeCatalog);
          episodeDublinCore.remove(DublinCore.PROPERTY_IS_PART_OF);
          String filename = FilenameUtils.getName(episodeCatalog.getURI().toString());
          URI uri = workspace.put(mp.getIdentifier().toString(), episodeCatalog.getIdentifier(), filename,
              dublinCoreService.serialize(episodeDublinCore));
          episodeCatalog.setURI(uri);
          // setting the URI to a new source so the checksum will most like be invalid
          episodeCatalog.setChecksum(null);
        }
        return true;
      } catch (IOException e) {
        logger.error("Unable to update workflow {}", instance.getId(), e);
        return false;
      }
    }
  };

  /*
   * End workflow update functions
   */

  /*
   * Begin Series update functions
   */
  private final Function2<SearchResultItem, SeriesItem, MediaPackage> seriesUpdateCatalog = new Function2<>() {
    @Override
    public MediaPackage apply(SearchResultItem searchResultItem, SeriesItem seriesItem) {
      MediaPackage mp = searchResultItem.getMediaPackage();
      DublinCoreCatalog seriesDublinCore = seriesItem.getMetadata();
      mp.setSeriesTitle(seriesDublinCore.getFirst(DublinCore.PROPERTY_TITLE));

      try {
        // Update the series dublin core
        Catalog[] seriesCatalogs = mp.getCatalogs(MediaPackageElements.SERIES);
        if (seriesCatalogs.length == 1) {
          Catalog c = seriesCatalogs[0];
          String filename = FilenameUtils.getName(c.getURI().toString());
          URI uri = workspace.put(mp.getIdentifier().toString(), c.getIdentifier(), filename,
              dublinCoreService.serialize(seriesDublinCore));
          c.setURI(uri);
          // setting the URI to a new source so the checksum will most like be invalid
          c.setChecksum(null);

          // Distribute the updated series dc
          Job distributionJob = distributionService.distribute(CHANNEL_ID, mp, c.getIdentifier());
          JobBarrier barrier = new JobBarrier(null, serviceRegistry, distributionJob);
          JobBarrier.Result jobResult = barrier.waitForJobs();
          if (jobResult.getStatus().get(distributionJob).equals(FINISHED)) {
            mp.remove(c);
            mp.add(getFromXml(serviceRegistry.getJob(distributionJob.getId()).getPayload()));
          } else {
            logger.error("Unable to distribute series catalog {}", c.getIdentifier());
            return null;
          }
        }
        return mp;
      } catch (MediaPackageException | ServiceRegistryException | DistributionException
               | NotFoundException | IOException e) {
        logger.error("Unable to update search index for {}", searchResultItem.getId(), e);
        return null;
      }
    }
  };

  private final Function2<SearchResultItem, SeriesItem, MediaPackage> seriesUpdateAcl = new Function2<>() {
    @Override
    public MediaPackage apply(SearchResultItem searchResultItem, SeriesItem seriesItem) {
      // If the security policy has been updated, make sure to distribute that change
      // to the distribution channels as well
      MediaPackage mp = searchResultItem.getMediaPackage();
      if (seriesItem.getOverrideEpisodeAcl()) {

        MediaPackageElement[] distributedEpisodeAcls = mp.getElementsByFlavor(XACML_POLICY_EPISODE);
        authorizationService.removeAcl(mp, AclScope.Episode);

        for (MediaPackageElement distributedEpisodeAcl : distributedEpisodeAcls) {
          try {
            Job retractJob = distributionService.retract(CHANNEL_ID, mp, distributedEpisodeAcl.getIdentifier());
            JobBarrier barrier = new JobBarrier(null, serviceRegistry, retractJob);
            JobBarrier.Result jobResult = barrier.waitForJobs();
            if (!jobResult.getStatus().get(retractJob).equals(FINISHED)) {
              logger.error("Unable to retract episode XACML {}", distributedEpisodeAcl.getIdentifier());
            }
          } catch (DistributionException e) {
            logger.error("Unable to retract episode XACML {}", distributedEpisodeAcl.getIdentifier(), e);
          }
        }
      }

      try {
        Attachment fileRepoCopy = authorizationService.setAcl(mp, AclScope.Series, seriesItem.getAcl()).getB();

        // Distribute the updated XACML file
        Job distributionJob = distributionService.distribute(CHANNEL_ID, mp, fileRepoCopy.getIdentifier());
        JobBarrier barrier = new JobBarrier(null, serviceRegistry, distributionJob);
        JobBarrier.Result jobResult = barrier.waitForJobs();
        if (jobResult.getStatus().get(distributionJob).equals(FINISHED)) {
          mp.remove(fileRepoCopy);
          mp.add(getFromXml(serviceRegistry.getJob(distributionJob.getId()).getPayload()));
        } else {
          logger.error("Unable to distribute series XACML {}", fileRepoCopy.getIdentifier());
          return null;
        }
      } catch (MediaPackageException | DistributionException | NotFoundException | ServiceRegistryException e) {
        logger.error("Unable to set series XACML {}", mp.getIdentifier().toString(), e);
        return null;
      }
      return mp;
    }
  };

  private final Function2<SearchResultItem, SeriesItem, MediaPackage> seriesDelete = new Function2<>() {
    @Override
    public MediaPackage apply(SearchResultItem searchResultItem, SeriesItem seriesItem) {
      MediaPackage mp = searchResultItem.getMediaPackage();
      mp.setSeries(null);
      mp.setSeriesTitle(null);

      try {
        boolean retractSeriesCatalog = retractSeriesCatalog(mp);
        boolean updateEpisodeCatalog = updateEpisodeCatalog(mp);

        if (!retractSeriesCatalog || !updateEpisodeCatalog) {
          return null;
        }
        return mp;
      } catch (DistributionException | MediaPackageException | NotFoundException
               | ServiceRegistryException | IOException e) {
        logger.error("Error deleting series {}", seriesItem.getSeriesId(), e);
        return null;
      }
    }
  };

  private boolean retractSeriesCatalog(MediaPackage mp) throws DistributionException {
    // Retract the series catalog
    for (Catalog c : mp.getCatalogs(MediaPackageElements.SERIES)) {
      Job retractJob = distributionService.retract(CHANNEL_ID, mp, c.getIdentifier());
      JobBarrier barrier = new JobBarrier(null, serviceRegistry, retractJob);
      JobBarrier.Result jobResult = barrier.waitForJobs();
      if (jobResult.getStatus().get(retractJob).equals(FINISHED)) {
        mp.remove(c);
      } else {
        logger.error("Unable to retract series catalog {}", c.getIdentifier());
        return false;
      }
    }
    return true;
  }

  private boolean updateEpisodeCatalog(MediaPackage mp)
          throws DistributionException, MediaPackageException, NotFoundException, ServiceRegistryException,
          IllegalArgumentException, IOException {
    // Update the episode catalog
    for (Catalog episodeCatalog : mp.getCatalogs(MediaPackageElements.EPISODE)) {
      DublinCoreCatalog episodeDublinCore = DublinCoreUtil.loadDublinCore(workspace, episodeCatalog);
      episodeDublinCore.remove(DublinCore.PROPERTY_IS_PART_OF);
      String filename = FilenameUtils.getName(episodeCatalog.getURI().toString());
      URI uri = workspace.put(mp.getIdentifier().toString(), episodeCatalog.getIdentifier(), filename,
          dublinCoreService.serialize(episodeDublinCore));
      episodeCatalog.setURI(uri);
      // setting the URI to a new source so the checksum will most like be invalid
      episodeCatalog.setChecksum(null);

      // Distribute the updated episode dublincore
      Job distributionJob = distributionService.distribute(CHANNEL_ID, mp, episodeCatalog.getIdentifier());
      JobBarrier barrier = new JobBarrier(null, serviceRegistry, distributionJob);
      JobBarrier.Result jobResult = barrier.waitForJobs();
      if (jobResult.getStatus().get(distributionJob).equals(FINISHED)) {
        mp.remove(episodeCatalog);
        mp.add(getFromXml(serviceRegistry.getJob(distributionJob.getId()).getPayload()));
      } else {
        logger.error("Unable to distribute episode catalog {}", episodeCatalog.getIdentifier());
        return false;
      }
    }
    return true;
  }

  /*
   * End Series update functions
   */

  /*
   * Begin AssetManager update functions
   */

  private final Function2<Snapshot, SeriesItem, MediaPackage> assetManagerElementUpdate = new Function2<>() {
    @Override
    public MediaPackage apply(Snapshot snapshot, SeriesItem seriesItem) {
      MediaPackage mp = snapshot.getMediaPackage();
      DublinCoreCatalog seriesDublinCore = null;
      MediaPackageElementFlavor catalogType = null;
      if (SeriesItem.Type.UpdateCatalog.equals(seriesItem.getType())) {
        seriesDublinCore = seriesItem.getMetadata();
        mp.setSeriesTitle(seriesDublinCore.getFirst(DublinCore.PROPERTY_TITLE));
        catalogType = MediaPackageElements.SERIES;
      } else {
        seriesDublinCore = seriesItem.getExtendedMetadata();
        catalogType = MediaPackageElementFlavor.flavor(seriesItem.getElementType(), "series");
      }


      // Update the series dublin core
      Catalog[] seriesCatalogs = mp.getCatalogs(catalogType);
      if (seriesCatalogs.length == 1) {
        Catalog c = seriesCatalogs[0];
        try {
          String filename = FilenameUtils.getName(c.getURI().toString());
          URI uri = workspace.put(mp.getIdentifier().toString(), c.getIdentifier(), filename,
              dublinCoreService.serialize(seriesDublinCore));
          c.setURI(uri);
          // setting the URI to a new source so the checksum will most like be invalid
          c.setChecksum(null);
        } catch (IOException e) {
          logger.error("Unable to update asset manager element {}", c.getIdentifier(), e);
          return null;
        }
      }
      return mp;
    }
  };

  private final Function2<Snapshot, SeriesItem, MediaPackage> assetManagerUpdateAcl = new Function2<>() {
    @Override
    public MediaPackage apply(Snapshot snapshot, SeriesItem seriesItem) {
      MediaPackage mp = snapshot.getMediaPackage();

      // Build a new XACML file for this mediapackage
      try {
        if (seriesItem.getOverrideEpisodeAcl()) {
          authorizationService.removeAcl(mp, AclScope.Episode);
        }
        authorizationService.setAcl(mp, AclScope.Series, seriesItem.getAcl());
      } catch (MediaPackageException e) {
        logger.error("Error setting ACL for media package {}", mp.getIdentifier(), e);
        return null;
      }
      return mp;
    }
  };

  private final Function2<Snapshot, SeriesItem, MediaPackage> assetManagerDelete = new Function2<>() {
    @Override
    public MediaPackage apply(Snapshot snapshot, SeriesItem seriesItem) {
      MediaPackage mp = snapshot.getMediaPackage();
      mp.setSeries(null);
      mp.setSeriesTitle(null);
      for (Catalog seriesCatalog : mp.getCatalogs(MediaPackageElements.SERIES)) {
        mp.remove(seriesCatalog);
      }
      authorizationService.removeAcl(mp, AclScope.Series);
      try {
        for (Catalog episodeCatalog : mp.getCatalogs(MediaPackageElements.EPISODE)) {
          DublinCoreCatalog episodeDublinCore = DublinCoreUtil.loadDublinCore(workspace, episodeCatalog);
          episodeDublinCore.remove(DublinCore.PROPERTY_IS_PART_OF);
          String filename = FilenameUtils.getName(episodeCatalog.getURI().toString());
          URI uri = workspace.put(mp.getIdentifier().toString(), episodeCatalog.getIdentifier(), filename,
              dublinCoreService.serialize(episodeDublinCore));
          episodeCatalog.setURI(uri);
          // setting the URI to a new source so the checksum will most like be invalid
          episodeCatalog.setChecksum(null);
        }
      } catch (IOException e) {
        logger.error("Unable to remove series from episode catalog for mp {}", mp.getIdentifier().toString(), e);
        return null;
      }
      // here we don't know the series extended metadata types,
      // we assume that all series catalog flavors have a fixed subtype: series
      MediaPackageElementFlavor seriesFlavor = MediaPackageElementFlavor.flavor("*", "series");
      for (Catalog catalog : mp.getCatalogs()) {
        if (catalog.getFlavor().matches(seriesFlavor)) {
          mp.remove(catalog);
        }
      }
      return mp;
    }
  };

  /*
   * End AssetManager update functions
   */

  @Reference
  public void setSecurityService(SecurityService secSvc) {
    this.securityService = secSvc;
  }

  @Reference
  public void setAssetManager(AssetManager assetManager) {
    this.assetManager = assetManager;
  }

  @Reference
  public void setOrganizationDirectoryService(OrganizationDirectoryService orgDirSvc) {
    this.organizationDirectoryService = orgDirSvc;
  }

  @Reference
  public void setAuthorizationService(AuthorizationService authSvc) {
    this.authorizationService = authSvc;
  }

  @Reference
  public void setWorkspace(Workspace workspace) {
    this.workspace = workspace;
  }

  @Reference
  public void setDublinCoreService(DublinCoreCatalogService dublinCoreService) {
    this.dublinCoreService = dublinCoreService;
  }

  @Reference
  public void setSearchService(SearchService searchService) {
    this.searchService = searchService;
  }

  @Reference(target = "(distribution.channel=download)")
  public void setDistributionService(DistributionService distributionService) {
    this.distributionService = distributionService;
  }

  @Reference
  public void setServiceRegistry(ServiceRegistry serviceRegistry) {
    this.serviceRegistry = serviceRegistry;
  }

  @Reference
  public void setWorkflowService(WorkflowService workflowService) {
    this.workflowService = workflowService;
  }
}
