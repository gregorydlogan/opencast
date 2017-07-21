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
package org.opencast.event.handler;

import static org.opencast.job.api.Job.Status.FINISHED;
import static org.opencast.mediapackage.MediaPackageElementParser.getFromXml;
import static org.opencast.mediapackage.MediaPackageElements.XACML_POLICY_SERIES;
import static org.opencast.publication.api.OaiPmhPublicationService.PUBLICATION_CHANNEL_PREFIX;

import org.opencast.distribution.api.DistributionException;
import org.opencast.distribution.api.DistributionService;
import org.opencast.job.api.Job;
import org.opencast.job.api.JobBarrier;
import org.opencast.job.api.JobBarrier.Result;
import org.opencast.mediapackage.Attachment;
import org.opencast.mediapackage.Catalog;
import org.opencast.mediapackage.MediaPackage;
import org.opencast.mediapackage.MediaPackageElements;
import org.opencast.mediapackage.MediaPackageException;
import org.opencast.message.broker.api.series.SeriesItem;
import org.opencast.metadata.dublincore.DublinCore;
import org.opencast.metadata.dublincore.DublinCoreCatalog;
import org.opencast.metadata.dublincore.DublinCoreCatalogService;
import org.opencast.metadata.dublincore.DublinCoreUtil;
import org.opencast.oaipmh.persistence.OaiPmhDatabase;
import org.opencast.oaipmh.persistence.OaiPmhDatabaseException;
import org.opencast.oaipmh.persistence.QueryBuilder;
import org.opencast.oaipmh.persistence.SearchResult;
import org.opencast.oaipmh.persistence.SearchResultItem;
import org.opencast.security.api.AclScope;
import org.opencast.security.api.AuthorizationService;
import org.opencast.security.api.Organization;
import org.opencast.security.api.OrganizationDirectoryService;
import org.opencast.security.api.SecurityService;
import org.opencast.security.api.User;
import org.opencast.security.util.SecurityUtil;
import org.opencast.serviceregistry.api.ServiceRegistry;
import org.opencast.serviceregistry.api.ServiceRegistryException;
import org.opencast.util.NotFoundException;
import org.opencast.workspace.api.Workspace;

import org.apache.commons.io.FilenameUtils;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;

public class OaiPmhUpdatedEventHandler {

  /** The logger */
  protected static final Logger logger = LoggerFactory.getLogger(OaiPmhUpdatedEventHandler.class);

  /** The service registry */
  protected ServiceRegistry serviceRegistry = null;

  /** The distribution service */
  protected DistributionService distributionService = null;

  /** The OAI-PMH persistence service */
  protected OaiPmhDatabase oaiPmhPersistence = null;

  /** The security service */
  protected SecurityService securityService = null;

  /** The authorization service */
  protected AuthorizationService authorizationService = null;

  /** The organization directory */
  protected OrganizationDirectoryService organizationDirectoryService = null;

  /** Dublin core catalog service */
  protected DublinCoreCatalogService dublinCoreService = null;

  /** The workspace */
  protected Workspace workspace = null;

  /** The system account to use for running asynchronous events */
  protected String systemAccount = null;

  /**
   * OSGI callback for component activation.
   *
   * @param bundleContext
   *          the OSGI bundle context
   */
  protected void activate(BundleContext bundleContext) {
    this.systemAccount = bundleContext.getProperty("org.opencast.security.digest.user");
  }

  /**
   * @param serviceRegistry
   *          the serviceRegistry to set
   */
  public void setServiceRegistry(ServiceRegistry serviceRegistry) {
    this.serviceRegistry = serviceRegistry;
  }

  /**
   * @param workspace
   *          the workspace to set
   */
  public void setWorkspace(Workspace workspace) {
    this.workspace = workspace;
  }

  /**
   * @param dublinCoreService
   *          the dublin core service to set
   */
  public void setDublinCoreCatalogService(DublinCoreCatalogService dublinCoreService) {
    this.dublinCoreService = dublinCoreService;
  }

  /**
   * @param distributionService
   *          the distributionService to set
   */
  public void setDistributionService(DistributionService distributionService) {
    this.distributionService = distributionService;
  }

  /**
   * @param oaiPmhPersistence
   *          the OAI-PMH persistence service to set
   */
  public void setOaiPmhPersistence(OaiPmhDatabase oaiPmhPersistence) {
    this.oaiPmhPersistence = oaiPmhPersistence;
  }

  /**
   * @param securityService
   *          the securityService to set
   */
  public void setSecurityService(SecurityService securityService) {
    this.securityService = securityService;
  }

  /**
   * @param authorizationService
   *          the authorizationService to set
   */
  public void setAuthorizationService(AuthorizationService authorizationService) {
    this.authorizationService = authorizationService;
  }

  /**
   * @param organizationDirectoryService
   *          the organizationDirectoryService to set
   */
  public void setOrganizationDirectoryService(OrganizationDirectoryService organizationDirectoryService) {
    this.organizationDirectoryService = organizationDirectoryService;
  }

  public void handleEvent(final SeriesItem seriesItem) {
    // A series or its ACL has been updated. Find any mediapackages with that series, and update them.
    logger.debug("Handling {}", seriesItem);
    String seriesId = seriesItem.getSeriesId();

    // We must be an administrative user to make this query
    final User prevUser = securityService.getUser();
    final Organization prevOrg = securityService.getOrganization();
    try {
      securityService.setUser(SecurityUtil.createSystemUser(systemAccount, prevOrg));

      SearchResult result = oaiPmhPersistence.search(QueryBuilder.query().seriesId(seriesId).build());

      for (SearchResultItem item : result.getItems()) {
        MediaPackage mp = item.getMediaPackage();

        Organization org = organizationDirectoryService.getOrganization(item.getOrganization());
        securityService.setOrganization(org);

        // If the security policy has been updated, make sure to distribute that change
        // to the distribution channels as well
        if (SeriesItem.Type.UpdateAcl.equals(seriesItem.getType())) {
          // Build a new XACML file for this mediapackage
          authorizationService.setAcl(mp, AclScope.Series, seriesItem.getAcl());
          Attachment fileRepoCopy = mp.getAttachments(XACML_POLICY_SERIES)[0];

          // Distribute the updated XACML file
          Job distributionJob = distributionService.distribute(PUBLICATION_CHANNEL_PREFIX.concat(item.getRepository()),
                  mp, fileRepoCopy.getIdentifier());
          JobBarrier barrier = new JobBarrier(null, serviceRegistry, distributionJob);
          Result jobResult = barrier.waitForJobs();
          if (jobResult.getStatus().get(distributionJob).equals(FINISHED)) {
            mp.remove(fileRepoCopy);
            mp.add(getFromXml(serviceRegistry.getJob(distributionJob.getId()).getPayload()));
          } else {
            logger.error("Unable to distribute XACML {}", fileRepoCopy.getIdentifier());
            continue;
          }
        }

        // Update the series dublin core
        if (SeriesItem.Type.UpdateCatalog.equals(seriesItem.getType())) {
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

            // Distribute the updated series dc
            Job distributionJob = distributionService.distribute(
                    PUBLICATION_CHANNEL_PREFIX.concat(item.getRepository()), mp, c.getIdentifier());
            JobBarrier barrier = new JobBarrier(null, serviceRegistry, distributionJob);
            Result jobResult = barrier.waitForJobs();
            if (jobResult.getStatus().get(distributionJob).equals(FINISHED)) {
              mp.remove(c);
              mp.add(getFromXml(serviceRegistry.getJob(distributionJob.getId()).getPayload()));
            } else {
              logger.error("Unable to distribute series catalog {}", c.getIdentifier());
              continue;
            }
          }
        }

        // Remove the series catalog and isPartOf from episode catalog
        if (SeriesItem.Type.Delete.equals(seriesItem.getType())) {
          mp.setSeries(null);
          mp.setSeriesTitle(null);

          boolean retractSeriesCatalog = retractSeriesCatalog(mp, item.getRepository());
          boolean updateEpisodeCatalog = updateEpisodeCatalog(mp, item.getRepository());

          if (!retractSeriesCatalog || !updateEpisodeCatalog)
            continue;
        }

        // Update the OAI-PMH persitence with the modified mediapackage
        oaiPmhPersistence.store(mp, item.getRepository());
      }
    } catch (ServiceRegistryException e) {
      logger.warn(e.getMessage());
    } catch (NotFoundException e) {
      logger.warn(e.getMessage());
    } catch (MediaPackageException e) {
      logger.warn(e.getMessage());
    } catch (IOException e) {
      logger.warn(e.getMessage());
    } catch (DistributionException e) {
      logger.warn(e.getMessage());
    } catch (OaiPmhDatabaseException e) {
      logger.warn(e.getMessage());
    } finally {
      securityService.setOrganization(prevOrg);
      securityService.setUser(prevUser);
    }
  }

  private boolean retractSeriesCatalog(MediaPackage mp, String repository) throws DistributionException {
    // Retract the series catalog
    for (Catalog c : mp.getCatalogs(MediaPackageElements.SERIES)) {
      Job retractJob = distributionService
              .retract(PUBLICATION_CHANNEL_PREFIX.concat(repository), mp, c.getIdentifier());
      JobBarrier barrier = new JobBarrier(null, serviceRegistry, retractJob);
      Result jobResult = barrier.waitForJobs();
      if (jobResult.getStatus().get(retractJob).equals(FINISHED)) {
        mp.remove(c);
      } else {
        logger.error("Unable to retract series catalog {}", c.getIdentifier());
        return false;
      }
    }
    return true;
  }

  private boolean updateEpisodeCatalog(MediaPackage mp, String repository) throws DistributionException,
      MediaPackageException, NotFoundException, ServiceRegistryException, IllegalArgumentException, IOException {
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
      Job distributionJob = distributionService.distribute(PUBLICATION_CHANNEL_PREFIX.concat(repository), mp,
              episodeCatalog.getIdentifier());
      JobBarrier barrier = new JobBarrier(null, serviceRegistry, distributionJob);
      Result jobResult = barrier.waitForJobs();
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

}
