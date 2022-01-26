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

package org.opencastproject.series.impl;

import static org.opencastproject.assetmanager.api.fn.Enrichments.enrich;
import static org.opencastproject.job.api.Job.Status.FINISHED;
import static org.opencastproject.mediapackage.MediaPackageElementParser.getFromXml;
import static org.opencastproject.mediapackage.MediaPackageElements.XACML_POLICY_EPISODE;
import static org.opencastproject.util.EqualsUtil.bothNotNull;
import static org.opencastproject.util.EqualsUtil.eqListSorted;
import static org.opencastproject.util.EqualsUtil.eqListUnsorted;
import static org.opencastproject.util.RequireUtil.notNull;
import static org.opencastproject.util.data.Option.some;
import static org.opencastproject.workflow.handler.distribution.EngagePublicationChannel.CHANNEL_ID;

import org.opencastproject.assetmanager.api.AssetManager;
import org.opencastproject.assetmanager.api.AssetManagerException;
import org.opencastproject.assetmanager.api.Snapshot;
import org.opencastproject.assetmanager.api.query.AQueryBuilder;
import org.opencastproject.assetmanager.api.query.AResult;
import org.opencastproject.authorization.xacml.manager.api.AclServiceFactory;
import org.opencastproject.authorization.xacml.manager.api.ManagedAcl;
import org.opencastproject.authorization.xacml.manager.util.AccessInformationUtil;
import org.opencastproject.distribution.api.DistributionException;
import org.opencastproject.distribution.api.DistributionService;
import org.opencastproject.elasticsearch.api.SearchIndexException;
import org.opencastproject.elasticsearch.index.ElasticsearchIndex;
import org.opencastproject.elasticsearch.index.objects.series.Series;
import org.opencastproject.elasticsearch.index.rebuild.AbstractIndexProducer;
import org.opencastproject.elasticsearch.index.rebuild.IndexProducer;
import org.opencastproject.elasticsearch.index.rebuild.IndexRebuildException;
import org.opencastproject.elasticsearch.index.rebuild.IndexRebuildService;
import org.opencastproject.job.api.Job;
import org.opencastproject.job.api.JobBarrier;
import org.opencastproject.mediapackage.Attachment;
import org.opencastproject.mediapackage.Catalog;
import org.opencastproject.mediapackage.EName;
import org.opencastproject.mediapackage.MediaPackage;
import org.opencastproject.mediapackage.MediaPackageElement;
import org.opencastproject.mediapackage.MediaPackageElementFlavor;
import org.opencastproject.mediapackage.MediaPackageElements;
import org.opencastproject.mediapackage.MediaPackageException;
import org.opencastproject.metadata.dublincore.DublinCore;
import org.opencastproject.metadata.dublincore.DublinCoreCatalog;
import org.opencastproject.metadata.dublincore.DublinCoreCatalogList;
import org.opencastproject.metadata.dublincore.DublinCoreCatalogService;
import org.opencastproject.metadata.dublincore.DublinCoreUtil;
import org.opencastproject.metadata.dublincore.DublinCoreValue;
import org.opencastproject.metadata.dublincore.DublinCoreXmlFormat;
import org.opencastproject.metadata.dublincore.EncodingSchemeUtils;
import org.opencastproject.metadata.dublincore.Precision;
import org.opencastproject.search.api.SearchException;
import org.opencastproject.search.api.SearchQuery;
import org.opencastproject.search.api.SearchResult;
import org.opencastproject.search.api.SearchResultItem;
import org.opencastproject.search.api.SearchService;
import org.opencastproject.security.api.AccessControlList;
import org.opencastproject.security.api.AccessControlParser;
import org.opencastproject.security.api.AclScope;
import org.opencastproject.security.api.AuthorizationService;
import org.opencastproject.security.api.Organization;
import org.opencastproject.security.api.OrganizationDirectoryService;
import org.opencastproject.security.api.SecurityService;
import org.opencastproject.security.api.UnauthorizedException;
import org.opencastproject.security.api.User;
import org.opencastproject.security.util.SecurityUtil;
import org.opencastproject.series.api.SeriesException;
import org.opencastproject.series.api.SeriesQuery;
import org.opencastproject.series.api.SeriesService;
import org.opencastproject.series.impl.persistence.SeriesEntity;
import org.opencastproject.series.impl.update.SeriesItem;
import org.opencastproject.serviceregistry.api.ServiceRegistry;
import org.opencastproject.serviceregistry.api.ServiceRegistryException;
import org.opencastproject.util.NotFoundException;
import org.opencastproject.util.data.Function2;
import org.opencastproject.util.data.Option;
import org.opencastproject.workflow.api.WorkflowException;
import org.opencastproject.workflow.api.WorkflowInstance;
import org.opencastproject.workflow.api.WorkflowQuery;
import org.opencastproject.workflow.api.WorkflowService;
import org.opencastproject.workflow.api.WorkflowSet;
import org.opencastproject.workspace.api.Workspace;

import com.entwinemedia.fn.data.Opt;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.osgi.framework.ServiceException;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import javax.xml.parsers.ParserConfigurationException;

/**
 * Implements {@link SeriesService}. Uses {@link SeriesServiceDatabase} for permanent storage and
 * {@link SeriesServiceIndex} for searching.
 */
@Component(
    property = {
        "service.description=Series Service"
    },
    immediate = true,
    service = { SeriesService.class, IndexProducer.class }
)
public class SeriesServiceImpl extends AbstractIndexProducer implements SeriesService {

  /** Logging utility */
  private static final Logger logger = LoggerFactory.getLogger(SeriesServiceImpl.class);

  private static final String THEME_PROPERTY_NAME = "theme";

  /** Index for searching */
  protected SeriesServiceIndex index;

  /** Persistent storage */
  protected SeriesServiceDatabase persistence;

  /** The organization directory */
  protected OrganizationDirectoryService orgDirectory;

  /** The system user name */
  private String systemUserName;

  /** The API index */
  private ElasticsearchIndex elasticsearchIndex;

  private AclServiceFactory aclServiceFactory;

  private SecurityService securityService;
  private AssetManager assetManager;
  private AuthorizationService authorizationService;
  private Workspace workspace;
  private DublinCoreCatalogService dublinCoreService;
  private SearchService searchService;
  private DistributionService distributionService;
  private ServiceRegistry serviceRegistry;
  private WorkflowService workflowService;

  /** OSGi callback for setting index. */
  @Reference(name = "series-index")
  public void setIndex(SeriesServiceIndex index) {
    this.index = index;
  }

  /** OSGi callback for setting persistance. */
  @Reference(name = "series-persistence")
  public void setPersistence(SeriesServiceDatabase persistence) {
    this.persistence = persistence;
  }

  /** OSGi callback for setting the security service. */
  @Reference(name = "security-service")
  public void setSecurityService(SecurityService securityService) {
    this.securityService = securityService;
  }

  /** OSGi callback for setting the organization directory service. */
  @Reference(name = "orgDirectory")
  public void setOrgDirectory(OrganizationDirectoryService orgDirectory) {
    this.orgDirectory = orgDirectory;
  }

  /** OSGi callbacks for setting the API index. */
  @Reference(name = "elasticsearch-index")
  public void setElasticsearchIndex(ElasticsearchIndex index) {
    this.elasticsearchIndex = index;
  }

  @Reference
  public void setAclServiceFactory(AclServiceFactory aclServiceFactory) {
    this.aclServiceFactory = aclServiceFactory;
  }

  @Reference
  public void setAssetManager(AssetManager assetManager) {
    this.assetManager = assetManager;
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

  /**
   * Activates Series Service. Checks whether we are using synchronous or asynchronous indexing. If
   * asynchronous is used, Executor service is set. If index is empty, persistent storage is queried
   * if it contains any series. If that is the case, series are retrieved and indexed.
   */
  @Activate
  public void activate(ComponentContext cc) throws Exception {
    logger.info("Activating Series Service");
    systemUserName = cc.getBundleContext().getProperty(SecurityUtil.PROPERTY_KEY_SYS_USER);
    populateSolr(systemUserName);
  }

  /** If the solr index is empty, but there are series in the database, populate the solr index. */
  private void populateSolr(String systemUserName) {
    long instancesInSolr;
    try {
      instancesInSolr = index.count();
    } catch (Exception e) {
      throw new IllegalStateException("Repopulating series Solr index failed", e);
    }
    if (instancesInSolr != 0L) {
      return;
    }

    logger.info("The series index is empty. Populating it now with series");
    List<SeriesEntity> allSeries = null;
    try {
      allSeries = persistence.getAllSeries();
    } catch (SeriesServiceDatabaseException ex) {
      throw new ServiceException("Unable to get all series from the database", ex);
    }
    final int total = allSeries.size();
    if (total == 0) {
      logger.info("No series found. Repopulating index finished.");
      return;
    }

    int current = 0;
    for (SeriesEntity series: allSeries) {
      current++;
      try {
        // Run as the superuser so we get all series, regardless of organization or role
        Organization organization = orgDirectory.getOrganization(series.getOrganization());
        securityService.setOrganization(organization);
        securityService.setUser(SecurityUtil.createSystemUser(systemUserName, organization));

        index.updateIndex(DublinCoreXmlFormat.read(series.getDublinCoreXML()));
        String aclStr = series.getAccessControl();
        if (StringUtils.isNotBlank(aclStr)) {
          AccessControlList acl = AccessControlParser.parseAcl(aclStr);
          index.updateSecurityPolicy(series.getSeriesId(), acl);
        }
      } catch (Exception ex) {
        logger.error("Unable to repopulate index for series {}", series.getSeriesId(), ex);
      } finally {
        securityService.setOrganization(null);
        securityService.setUser(null);
      }
      // log progress
      if (current % 100 == 0) {
        logger.info("Indexing series {}/{} ({} percent done)", current, total, current * 100 / total);
      }
    }
    logger.info("Finished populating series search index");
  }

  @Override
  public DublinCoreCatalog updateSeries(DublinCoreCatalog dc) throws SeriesException, UnauthorizedException {
    try {
      for (DublinCoreCatalog dublinCore : isNew(notNull(dc, "dc"))) {
        final String id = dublinCore.getFirst(DublinCore.PROPERTY_IDENTIFIER);

        if (!dublinCore.hasValue(DublinCore.PROPERTY_CREATED)) {
          DublinCoreValue date = EncodingSchemeUtils.encodeDate(new Date(), Precision.Minute);
          dublinCore.set(DublinCore.PROPERTY_CREATED, date);
          logger.debug("Setting series creation date to '{}'", date.getValue());
        }

        if (dublinCore.hasValue(DublinCore.PROPERTY_TITLE)) {
          if (dublinCore.getFirst(DublinCore.PROPERTY_TITLE).length() > 255) {
            dublinCore.set(DublinCore.PROPERTY_TITLE, dublinCore.getFirst(DublinCore.PROPERTY_TITLE).substring(0, 255));
            logger.warn("Title was longer than 255 characters. Cutting excess off.");
          }
        }

        logger.debug("Updating series {}", id);
        index.updateIndex(dublinCore);
        try {
          final AccessControlList acl = persistence.getAccessControlList(id);
          if (acl != null) {
            index.updateSecurityPolicy(id, acl);
          }
        } catch (NotFoundException ignore) {
          // Ignore not found since this is the first indexing
        }
        // Make sure store to persistence comes after index, return value can be null
        DublinCoreCatalog updated = persistence.storeSeries(dublinCore);
        // update API index
        updateSeriesMetadataInIndex(id, elasticsearchIndex, dublinCore);
        // still sent for other asynchronous updates
        this.execute(SeriesItem.updateCatalog(dublinCore));
        return (updated == null) ? null : dublinCore;
      }
      return dc;
    } catch (Exception e) {
      throw new SeriesException(e);
    }
  }

  /** Check if <code>dc</code> is new and, if so, return an updated version ready to store. */
  private Option<DublinCoreCatalog> isNew(DublinCoreCatalog dc) throws SeriesServiceDatabaseException {
    final String id = dc.getFirst(DublinCore.PROPERTY_IDENTIFIER);
    if (id != null) {
      try {
        return equals(persistence.getSeries(id), dc) ? Option.none() : some(dc);
      } catch (NotFoundException e) {
        return some(dc);
      }
    } else {
      logger.info("Series Dublin Core does not contain identifier, generating one");
      dc.set(DublinCore.PROPERTY_IDENTIFIER, UUID.randomUUID().toString());
      return some(dc);
    }
  }

  @Override
  public boolean updateAccessControl(final String seriesId, final AccessControlList accessControl)
          throws NotFoundException, SeriesException {
    return updateAccessControl(seriesId, accessControl, false);
  }

  // todo method signature does not fit the three different possible return values
  @Override
  public boolean updateAccessControl(final String seriesId, final AccessControlList accessControl,
          boolean overrideEpisodeAcl)
          throws NotFoundException, SeriesException {
    if (StringUtils.isEmpty(seriesId)) {
      throw new IllegalArgumentException("Series ID parameter must not be null or empty.");
    }
    if (accessControl == null) {
      throw new IllegalArgumentException("ACL parameter must not be null");
    }
    if (needsUpdate(seriesId, accessControl) || overrideEpisodeAcl) {
      logger.debug("Updating ACL of series {}", seriesId);
      boolean updated;
      // not found is thrown if it doesn't exist
      try {
        index.updateSecurityPolicy(seriesId, accessControl);
      } catch (SeriesServiceDatabaseException e) {
        logger.error("Could not update series {} with access control rules: {}", seriesId, e.getMessage());
        throw new SeriesException(e);
      }

      try {
        updated = persistence.storeSeriesAccessControl(seriesId, accessControl);
        //update API index
        updateSeriesAclInIndex(seriesId, elasticsearchIndex, accessControl);
        // still sent for other asynchronous updates
        this.execute(SeriesItem.updateAcl(seriesId, accessControl, overrideEpisodeAcl));
      } catch (SeriesServiceDatabaseException e) {
        logger.error("Could not update series {} with access control rules: {}", seriesId, e.getMessage());
        throw new SeriesException(e);
      }
      return updated;
    } else {
      // todo not the right return code
      return true;
    }
  }

  /** Check if <code>acl</code> needs to be updated for the given series. */
  private boolean needsUpdate(String seriesId, AccessControlList acl) throws SeriesException {
    try {
      return !equals(persistence.getAccessControlList(seriesId), acl);
    } catch (NotFoundException e) {
      return true;
    } catch (SeriesServiceDatabaseException e) {
      throw new SeriesException(e);
    }
  }

  /*
   * (non-Javadoc)
   *
   * @see org.opencastproject.series.api.SeriesService#deleteSeries(java.lang.String)
   */
  @Override
  public void deleteSeries(final String seriesID) throws SeriesException, NotFoundException {
    try {
      persistence.deleteSeries(seriesID);
      // remove from API index
      removeSeriesFromIndex(seriesID, elasticsearchIndex);
      // still sent for other asynchronous updates
      this.execute(SeriesItem.delete(seriesID));
    } catch (SeriesServiceDatabaseException e1) {
      logger.error("Could not delete series with id {} from persistence storage", seriesID);
      throw new SeriesException(e1);
    }

    try {
      index.delete(seriesID);
    } catch (SeriesServiceDatabaseException e) {
      logger.error("Unable to delete series with id {}: {}", seriesID, e.getMessage());
      throw new SeriesException(e);
    }
  }

  @Override
  public DublinCoreCatalogList getSeries(SeriesQuery query) throws SeriesException {
    try {
      return index.search(query);
    } catch (SeriesServiceDatabaseException e) {
      logger.error("Failed to execute search query: {}", e.getMessage());
      throw new SeriesException(e);
    }
  }

  @Override
  public List<org.opencastproject.series.api.Series> getAllForAdministrativeRead(
      Date from,
      Optional<Date> to,
      int limit
  ) throws SeriesException, UnauthorizedException {
    try {
      return persistence.getAllForAdministrativeRead(from, to, limit);
    } catch (SeriesServiceDatabaseException e) {
      String msg = String.format(
          "Exception while reading all series in range %s to %s from persistence storage",
          from,
          to
      );
      throw new SeriesException(msg, e);
    }
  }


  @Override
  public Map<String, String> getIdTitleMapOfAllSeries() throws SeriesException, UnauthorizedException {
    try {
      return index.queryIdTitleMap();
    } catch (SeriesServiceDatabaseException e) {
      logger.error("Failed to execute search query: {}", e.getMessage());
      throw new SeriesException(e);
    }
  }

  @Override
  public DublinCoreCatalog getSeries(String seriesID) throws SeriesException, NotFoundException {
    try {
      return index.getDublinCore(notNull(seriesID, "seriesID"));
    } catch (SeriesServiceDatabaseException e) {
      logger.error("Exception occured while retrieving series {}: {}", seriesID, e.getMessage());
      throw new SeriesException(e);
    }
  }

  @Override
  public AccessControlList getSeriesAccessControl(String seriesID) throws NotFoundException, SeriesException {
    try {
      return index.getAccessControl(notNull(seriesID, "seriesID"));
    } catch (SeriesServiceDatabaseException e) {
      throw new SeriesException(
          String.format("Exception occurred while retrieving access control rules for series %s", seriesID), e);
    }
  }

  @Override
  public int getSeriesCount() throws SeriesException {
    try {
      return (int) index.count();
    } catch (SeriesServiceDatabaseException e) {
      logger.error("Exception occured while counting series.", e);
      throw new SeriesException(e);
    }
  }

  @Override
  public Map<String, String> getSeriesProperties(String seriesID)
          throws SeriesException, NotFoundException, UnauthorizedException {
    try {
      return persistence.getSeriesProperties(seriesID);
    } catch (SeriesServiceDatabaseException e) {
      logger.error("Failed to get series properties for series with id '{}'", seriesID, e);
      throw new SeriesException(e);
    }
  }

  @Override
  public String getSeriesProperty(String seriesID, String propertyName)
          throws SeriesException, NotFoundException, UnauthorizedException {
    try {
      return persistence.getSeriesProperty(seriesID, propertyName);
    } catch (SeriesServiceDatabaseException e) {
      logger.error("Failed to get series property for series with series id '{}' and property name '{}'", seriesID,
              propertyName, e);
      throw new SeriesException(e);
    }
  }

  @Override
  public void updateSeriesProperty(String seriesID, String propertyName, String propertyValue)
          throws SeriesException, NotFoundException, UnauthorizedException {
    try {
      persistence.updateSeriesProperty(seriesID, propertyName, propertyValue);

      // update API index
      if (propertyName.equals(THEME_PROPERTY_NAME)) {
        updateThemePropertyInIndex(seriesID, Optional.ofNullable(propertyValue), elasticsearchIndex);
      }
    } catch (SeriesServiceDatabaseException e) {
      logger.error(
              "Failed to get series property for series with series id '{}' and property name '{}' and value '{}'",
              seriesID, propertyName, propertyValue, e);
      throw new SeriesException(e);
    }
  }

  @Override
  public void deleteSeriesProperty(String seriesID, String propertyName)
          throws SeriesException, NotFoundException, UnauthorizedException {
    try {
      persistence.deleteSeriesProperty(seriesID, propertyName);

      // update API index
      if (propertyName.equals(THEME_PROPERTY_NAME)) {
        updateThemePropertyInIndex(seriesID, Optional.empty(), elasticsearchIndex);
      }
    } catch (SeriesServiceDatabaseException e) {
      logger.error("Failed to delete series property for series with series id '{}' and property name '{}'",
              seriesID, propertyName, e);
      throw new SeriesException(e);
    }
  }

  /**
   * Define equality on DublinCoreCatalogs. Two DublinCores are considered equal if they have the same properties and if
   * each property has the same values in the same order.
   * <p>
   * Note: As long as http://opencast.jira.com/browse/MH-8759 is not fixed, the encoding scheme of values is not
   * considered.
   * <p>
   * Implementation Note: DublinCores should not be compared by their string serialization since the ordering of
   * properties is not defined and cannot be guaranteed between serializations.
   */
  public static boolean equals(DublinCoreCatalog a, DublinCoreCatalog b) {
    final Map<EName, List<DublinCoreValue>> av = a.getValues();
    final Map<EName, List<DublinCoreValue>> bv = b.getValues();
    if (av.size() == bv.size()) {
      for (Map.Entry<EName, List<DublinCoreValue>> ave : av.entrySet()) {
        if (!eqListSorted(ave.getValue(), bv.get(ave.getKey()))) {
          return false;
        }
      }
      return true;
    } else {
      return false;
    }
  }

  /**
   * Define equality on AccessControlLists. Two AccessControlLists are considered equal if they contain the exact same
   * entries no matter in which order.
   */
  public static boolean equals(AccessControlList a, AccessControlList b) {
    return bothNotNull(a, b) && eqListUnsorted(a.getEntries(), b.getEntries());
  }

  @Override
  public Opt<Map<String, byte[]>> getSeriesElements(String seriesId) throws SeriesException {
    try {
      return persistence.getSeriesElements(seriesId);
    } catch (SeriesServiceDatabaseException e) {
      throw new SeriesException(e);
    }
  }

  @Override
  public Opt<byte[]> getSeriesElementData(String seriesId, String type) throws SeriesException {
    try {
      return persistence.getSeriesElement(seriesId, type);
    } catch (SeriesServiceDatabaseException e) {
      throw new SeriesException(e);
    }
  }

  @Override
  public boolean addSeriesElement(String seriesID, String type, byte[] data) throws SeriesException {
    try {
      if (persistence.existsSeriesElement(seriesID, type)) {
        return false;
      } else {
        return persistence.storeSeriesElement(seriesID, type, data);
      }
    } catch (SeriesServiceDatabaseException e) {
      throw new SeriesException(e);
    }
  }

  @Override
  public boolean updateSeriesElement(String seriesID, String type, byte[] data) throws SeriesException {
    try {
      if (persistence.existsSeriesElement(seriesID, type) && persistence.storeSeriesElement(seriesID, type, data)) {
        this.execute(SeriesItem.updateElement(seriesID, type, new String(data, StandardCharsets.UTF_8)));
        return true;
      } else {
        return false;
      }
    } catch (SeriesServiceDatabaseException e) {
      throw new SeriesException(e);
    }
  }

  @Override
  public boolean deleteSeriesElement(String seriesID, String type) throws SeriesException {
    try {
      if (persistence.existsSeriesElement(seriesID, type)) {
        return persistence.deleteSeriesElement(seriesID, type);
      } else {
        return false;
      }
    } catch (SeriesServiceDatabaseException e) {
      throw new SeriesException(e);
    }
  }

  @Override
  public void repopulate(final ElasticsearchIndex index) throws IndexRebuildException {
    try {
      List<SeriesEntity> databaseSeries = persistence.getAllSeries();
      final int total = databaseSeries.size();
      int current = 1;
      logIndexRebuildBegin(logger, index.getIndexName(), total, "series");

      for (SeriesEntity series: databaseSeries) {
        Organization organization = orgDirectory.getOrganization(series.getOrganization());
        User systemUser = SecurityUtil.createSystemUser(systemUserName, organization);
        SecurityUtil.runAs(securityService, organization, systemUser,
                () -> {
                  String seriesId = series.getSeriesId();
                  logger.trace("Adding series {} for organization {} to the {} index.", seriesId,
                          series.getOrganization(), index.getIndexName());
                  List<Function<Optional<Series>, Optional<Series>>> updateFunctions = new ArrayList<>();

                  DublinCoreCatalog catalog;
                  try {
                    catalog = DublinCoreXmlFormat.read(series.getDublinCoreXML());
                    updateFunctions.add(getMetadataUpdateFunction(seriesId, catalog, organization.getId()));
                  } catch (IOException | ParserConfigurationException | SAXException e) {
                    logger.error("Could not read dublincore XML of series {}.", seriesId, e);
                    return;
                  }

                  String aclStr = series.getAccessControl();
                  if (StringUtils.isNotBlank(aclStr)) {
                    try {
                      AccessControlList acl = AccessControlParser.parseAcl(aclStr);
                      updateFunctions.add(getAclUpdateFunction(seriesId, acl, organization.getId()));
                    } catch (Exception ex) {
                      logger.error("Unable to parse ACL of series {}.", seriesId, ex);
                    }
                  }

                  try {
                    Map<String, String> properties = persistence.getSeriesProperties(seriesId);
                    updateFunctions.add(getThemePropertyUpdateFunction(seriesId,
                            Optional.ofNullable(properties.get(THEME_PROPERTY_NAME)), organization.getId()));
                  } catch (NotFoundException | SeriesServiceDatabaseException e) {
                    logger.error("Error reading properties of series {}", seriesId, e);
                  }

                  // do the actual index update
                  updateSeriesInIndex(seriesId, index, organization.getId(),
                          updateFunctions.toArray(new Function[0]));

                });
        logIndexRebuildProgress(logger, index.getIndexName(), total, current);
        current++;
      }
    } catch (Exception e) {
      logIndexRebuildError(logger, index.getIndexName(), e);
      throw new IndexRebuildException(index.getIndexName(), getService(), e);
    }
  }

  @Override
  public IndexRebuildService.Service getService() {
    return IndexRebuildService.Service.Series;
  }

  /**
   * Remove series from API index.
   *
   * @param seriesId
   *          The series id
   * @param index
   *          The API index to update
   */
  private void removeSeriesFromIndex(String seriesId, ElasticsearchIndex index) {
    String orgId = securityService.getOrganization().getId();
    logger.debug("Removing series {} from the {} index.", seriesId, index.getIndexName());

    try {
      index.delete(Series.DOCUMENT_TYPE, seriesId, orgId);
      logger.debug("Series {} removed from the {} index.", seriesId, index.getIndexName());
    } catch (SearchIndexException e) {
      logger.error("Series {} couldn't be removed from the {} index.", seriesId, index.getIndexName(), e);
    }
  }

  /**
   * Update series metadata in API index. Also update events if series title has changed (optional).
   *
   * @param seriesId
   *          The series id
   * @param index
   *          The API index to update
   * @param dc
   *          The dublin core catalog
   */
  private void updateSeriesMetadataInIndex(String seriesId, ElasticsearchIndex index, DublinCoreCatalog dc) {
    String orgId = securityService.getOrganization().getId();
    logger.debug("Updating metadata of series {} in the {} index.", seriesId, index.getIndexName());

    // update series
    Function<Optional<Series>, Optional<Series>> updateFunction = getMetadataUpdateFunction(seriesId, dc, orgId);
    updateSeriesInIndex(seriesId, index, orgId, updateFunction);
  }

  /**
   * Get the function to update the metadata for a series in an API index.
   *
   * @param seriesId
   *          The series id
   * @param dc
   *          The dublin core catalog
   * @param orgId
   *          The id of the current organization
   * @return the function to do the update
   */
  private Function<Optional<Series>, Optional<Series>> getMetadataUpdateFunction(String seriesId, DublinCoreCatalog dc,
          String orgId) {
    return (Optional<Series> seriesOpt) -> {
      Series series = seriesOpt.orElse(new Series(seriesId, orgId));

      // only for new series
      if (!seriesOpt.isPresent()) {
        series.setCreator(securityService.getUser().getName());
      }

      series.setTitle(dc.getFirst(DublinCoreCatalog.PROPERTY_TITLE));
      series.setDescription(dc.getFirst(DublinCore.PROPERTY_DESCRIPTION));
      series.setSubject(dc.getFirst(DublinCore.PROPERTY_SUBJECT));
      series.setLanguage(dc.getFirst(DublinCoreCatalog.PROPERTY_LANGUAGE));
      series.setLicense(dc.getFirst(DublinCoreCatalog.PROPERTY_LICENSE));
      series.setRightsHolder(dc.getFirst(DublinCore.PROPERTY_RIGHTS_HOLDER));
      String createdDateStr = dc.getFirst(DublinCoreCatalog.PROPERTY_CREATED);
      if (createdDateStr != null) {
        series.setCreatedDateTime(EncodingSchemeUtils.decodeDate(createdDateStr));
      }
      series.setPublishers(dc.get(DublinCore.PROPERTY_PUBLISHER, DublinCore.LANGUAGE_ANY));
      series.setContributors(dc.get(DublinCore.PROPERTY_CONTRIBUTOR, DublinCore.LANGUAGE_ANY));
      series.setOrganizers(dc.get(DublinCoreCatalog.PROPERTY_CREATOR, DublinCore.LANGUAGE_ANY));
      return Optional.of(series);
    };
  }

  /**
   * Update series acl in API index.
   *
   * @param seriesId
   *          The series id
   * @param index
   *          The API index to update
   * @param acl
   *          The acl to update
   */
  private void updateSeriesAclInIndex(String seriesId, ElasticsearchIndex index, AccessControlList acl) {
    String orgId = securityService.getOrganization().getId();
    logger.debug("Updating ACL of series {} in the {} index.", seriesId, index.getIndexName());
    Function<Optional<Series>, Optional<Series>> updateFunction = getAclUpdateFunction(seriesId, acl, orgId);
    updateSeriesInIndex(seriesId, index, orgId, updateFunction);
  }

  /**
   * Get the function to update the acl for a series in an API index.
   *
   * @param seriesId
   *          The series id
   * @param acl
   *          The acl to update
   * @param orgId
   *          The id of the current organization
   * @return the function to do the update
   */
  private Function<Optional<Series>, Optional<Series>> getAclUpdateFunction(String seriesId, AccessControlList acl,
          String orgId) {
    return (Optional<Series> seriesOpt) -> {
      Series series = seriesOpt.orElse(new Series(seriesId, orgId));

      List<ManagedAcl> acls = aclServiceFactory.serviceFor(securityService.getOrganization()).getAcls();
      Option<ManagedAcl> managedAcl = AccessInformationUtil.matchAcls(acls, acl);
      if (managedAcl.isSome()) {
        series.setManagedAcl(managedAcl.get().getName());
      }

      series.setAccessPolicy(AccessControlParser.toJsonSilent(acl));
      return Optional.of(series);
    };
  }

  /**
   * Update series theme property in an API index.
   *
   * @param seriesId
   *          The series id
   * @param propertyValueOpt
   *          The value of the property (optional)
   * @param index
   *          The API index to update
   */
  private void updateThemePropertyInIndex(String seriesId, Optional<String> propertyValueOpt,
          ElasticsearchIndex index) {
    String orgId = securityService.getOrganization().getId();
    logger.debug("Updating theme property of series {} in the {} index.", seriesId, index.getIndexName());
    Function<Optional<Series>, Optional<Series>> updateFunction =
            getThemePropertyUpdateFunction(seriesId, propertyValueOpt, orgId);
    updateSeriesInIndex(seriesId, index, orgId, updateFunction);
  }

  /**
   * Get the function to update the theme property for a series in an API index.
   *
   * @param seriesId
   *          The series id
   * @param propertyValueOpt
   *          The value of the property (optional)
   * @param orgId
   *          The id of the current organization
   * @return the function to do the update
   */
  private Function<Optional<Series>, Optional<Series>> getThemePropertyUpdateFunction(String seriesId,
          Optional<String> propertyValueOpt, String orgId) {
    return (Optional<Series> seriesOpt) -> {
      Series series = seriesOpt.orElse(new Series(seriesId, orgId));
      if (propertyValueOpt.isPresent()) {
        series.setTheme(Long.valueOf(propertyValueOpt.get()));
      } else {
        series.setTheme(null);
      }
      return Optional.of(series);
    };
  }

  /**
   * Update a series in an API index.
   *
   * @param seriesId
   *          The series id
   * @param updateFunctions
   *          The function(s) to do the actual updating
   * @param index
   *          The API index to update
   * @param orgId
   *          The id of the current organization
   * @return the updated series (optional)
   */
  @SafeVarargs
  private final Optional<Series> updateSeriesInIndex(String seriesId, ElasticsearchIndex index, String orgId,
          Function<Optional<Series>, Optional<Series>>... updateFunctions) {
    User user = securityService.getUser();
    Function<Optional<Series>, Optional<Series>> updateFunction = Arrays.stream(updateFunctions)
            .reduce(Function.identity(), Function::andThen);

    try {
      Optional<Series> seriesOpt = index.addOrUpdateSeries(seriesId, updateFunction, orgId, user);
      logger.debug("Series {} updated in the {} index", seriesId, index.getIndexName());
      return seriesOpt;
    } catch (SearchIndexException e) {
      logger.error("Series {} couldn't be updated in the {} index.", seriesId, index.getIndexName(), e);
      return Optional.empty();
    }
  }

  private void execute(SeriesItem seriesItem) {
    // A series or its ACL has been updated. Find any mediapackages with that series, and update them.
    logger.debug("Handling {}", seriesItem);
    String seriesId = seriesItem.getSeriesId();

    // We must be an administrative user to make this query
    final User prevUser = securityService.getUser();
    final Organization prevOrg = securityService.getOrganization();
    try {
      securityService.setUser(SecurityUtil.createSystemUser(systemUserName, prevOrg));

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
    } catch (
    SearchException e) {
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
      final Organization organization = orgDirectory.getOrganization(orgId);
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

    securityService.setUser(SecurityUtil.createSystemUser(systemUserName, prevOrg));

    SearchQuery q = new SearchQuery().withSeriesId(seriesId);
    SearchResult result = searchService.getForAdministrativeRead(q);

    for (SearchResultItem item : result.getItems()) {
      Organization org = orgDirectory.getOrganization(item.getOrganization());
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
    securityService.setUser(SecurityUtil.createSystemUser(systemUserName, prevOrg));

    // Note: getWorkflowInstances will only return a given number of results (default 20)
    WorkflowQuery q = new WorkflowQuery().withSeriesId(seriesId);
    WorkflowSet result = workflowService.getWorkflowInstancesForAdministrativeRead(q);
    Integer offset = 0;

    while (result.size() > 0) {
      for (WorkflowInstance instance : result.getItems()) {
        if (!instance.isActive()) {
          continue;
        }

        Organization org = orgDirectory.getOrganization(instance.getOrganizationId());
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
}
