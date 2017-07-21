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
package org.opencast.workflow.handler.workflow;

import org.opencast.job.api.JobContext;
import org.opencast.mediapackage.Catalog;
import org.opencast.mediapackage.MediaPackage;
import org.opencast.mediapackage.MediaPackageElement;
import org.opencast.mediapackage.MediaPackageElementFlavor;
import org.opencast.mediapackage.MediaPackageElements;
import org.opencast.mediapackage.selector.AbstractMediaPackageElementSelector;
import org.opencast.mediapackage.selector.CatalogSelector;
import org.opencast.metadata.dublincore.DublinCore;
import org.opencast.metadata.dublincore.DublinCoreCatalog;
import org.opencast.metadata.dublincore.DublinCoreUtil;
import org.opencast.metadata.dublincore.DublinCores;
import org.opencast.metadata.dublincore.SeriesCatalogUIAdapter;
import org.opencast.security.api.AccessControlList;
import org.opencast.security.api.AclScope;
import org.opencast.security.api.AuthorizationService;
import org.opencast.security.api.SecurityService;
import org.opencast.security.api.UnauthorizedException;
import org.opencast.series.api.SeriesException;
import org.opencast.series.api.SeriesService;
import org.opencast.util.Checksum;
import org.opencast.util.ChecksumType;
import org.opencast.util.NotFoundException;
import org.opencast.workflow.api.AbstractWorkflowOperationHandler;
import org.opencast.workflow.api.WorkflowInstance;
import org.opencast.workflow.api.WorkflowOperationException;
import org.opencast.workflow.api.WorkflowOperationResult;
import org.opencast.workflow.api.WorkflowOperationResult.Action;
import org.opencast.workspace.api.Workspace;

import com.entwinemedia.fn.Fn;
import com.entwinemedia.fn.Fn2;
import com.entwinemedia.fn.Stream;
import com.entwinemedia.fn.data.Opt;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.UUID;

/**
 * The workflow definition for handling "series" operations
 */
public class SeriesWorkflowOperationHandler extends AbstractWorkflowOperationHandler {

  /** The logging facility */
  private static final Logger logger = LoggerFactory.getLogger(SeriesWorkflowOperationHandler.class);

  /** The configuration options for this handler */
  private static final SortedMap<String, String> CONFIG_OPTIONS;

  /** Name of the configuration option that provides the optional series identifier */
  public static final String SERIES_PROPERTY = "series";

  /** Name of the configuration option that provides the flavors of the series catalogs to attach */
  public static final String ATTACH_PROPERTY = "attach";

  /** Name of the configuration option that provides wheter the ACL should be applied or not */
  public static final String APPLY_ACL_PROPERTY = "apply-acl";

  /** The authorization service */
  private AuthorizationService authorizationService;

  /** The series service */
  private SeriesService seriesService;

  /** The workspace */
  private Workspace workspace;

  /** The security service */
  private SecurityService securityService;

  /** The list series catalog UI adapters */
  private final List<SeriesCatalogUIAdapter> seriesCatalogUIAdapters = new ArrayList<SeriesCatalogUIAdapter>();

  /**
   * Callback for the OSGi declarative services configuration.
   *
   * @param authorizationService
   *          the authorization service
   */
  protected void setAuthorizationService(AuthorizationService authorizationService) {
    this.authorizationService = authorizationService;
  }

  /**
   * Callback for the OSGi declarative services configuration.
   *
   * @param seriesService
   *          the series service
   */
  public void setSeriesService(SeriesService seriesService) {
    this.seriesService = seriesService;
  }

  /**
   * Callback for the OSGi declarative services configuration.
   *
   * @param workspace
   *          the workspace
   */
  public void setWorkspace(Workspace workspace) {
    this.workspace = workspace;
  }

  /**
   * Callback for the OSGi declarative services configuration.
   *
   * @param securityService
   *          the securityService
   */
  public void setSecurityService(SecurityService securityService) {
    this.securityService = securityService;
  }

  /** OSGi callback to add {@link SeriesCatalogUIAdapter} instance. */
  public void addCatalogUIAdapter(SeriesCatalogUIAdapter catalogUIAdapter) {
    seriesCatalogUIAdapters.add(catalogUIAdapter);
  }

  /** OSGi callback to remove {@link SeriesCatalogUIAdapter} instance. */
  public void removeCatalogUIAdapter(SeriesCatalogUIAdapter catalogUIAdapter) {
    seriesCatalogUIAdapters.remove(catalogUIAdapter);
  }

  static {
    CONFIG_OPTIONS = new TreeMap<String, String>();
    CONFIG_OPTIONS.put(SERIES_PROPERTY, "The optional series identifier");
    CONFIG_OPTIONS.put(ATTACH_PROPERTY, "The flavors of the series catalogs to attach to the mediapackage.");
    CONFIG_OPTIONS.put(APPLY_ACL_PROPERTY, "Whether the ACL should be applied or not");
  }

  /**
   * {@inheritDoc}
   *
   * @see org.opencast.workflow.api.WorkflowOperationHandler#getConfigurationOptions()
   */
  @Override
  public SortedMap<String, String> getConfigurationOptions() {
    return CONFIG_OPTIONS;
  }

  /**
   * {@inheritDoc}
   *
   * @see org.opencast.workflow.api.WorkflowOperationHandler#start(org.opencast.workflow.api.WorkflowInstance,
   *      JobContext)
   */
  @Override
  public WorkflowOperationResult start(final WorkflowInstance workflowInstance, JobContext context)
          throws WorkflowOperationException {
    logger.debug("Running series workflow operation");

    MediaPackage mediaPackage = workflowInstance.getMediaPackage();

    Opt<String> optSeries = getOptConfig(workflowInstance.getCurrentOperation(), SERIES_PROPERTY);
    Opt<String> optAttachFlavors = getOptConfig(workflowInstance.getCurrentOperation(), ATTACH_PROPERTY);
    Boolean applyAcl = getOptConfig(workflowInstance.getCurrentOperation(), APPLY_ACL_PROPERTY).map(toBoolean)
            .getOr(false);

    if (optSeries.isSome() && !optSeries.get().equals(mediaPackage.getSeries())) {
      logger.info("Changing series id from '{}' to '{}'", StringUtils.trimToEmpty(mediaPackage.getSeries()),
              optSeries.get());
      mediaPackage.setSeries(optSeries.get());
    }

    String seriesId = mediaPackage.getSeries();
    if (seriesId == null) {
      logger.info("No series set, skip operation");
      return createResult(mediaPackage, Action.SKIP);
    }

    DublinCoreCatalog series;
    try {
      series = seriesService.getSeries(seriesId);
    } catch (NotFoundException e) {
      logger.info("No series with the identifier '{}' found, skip operation", seriesId);
      return createResult(mediaPackage, Action.SKIP);
    } catch (UnauthorizedException e) {
      logger.warn("Not authorized to get series with identifier '{}' found, skip operation", seriesId);
      return createResult(mediaPackage, Action.SKIP);
    } catch (SeriesException e) {
      logger.error("Unable to get series with identifier '{}', skip operation: {}", seriesId,
              ExceptionUtils.getStackTrace(e));
      throw new WorkflowOperationException(e);
    }

    mediaPackage.setSeriesTitle(series.getFirst(DublinCore.PROPERTY_TITLE));
    // Update the episode catalog
    for (Catalog episodeCatalog : mediaPackage.getCatalogs(MediaPackageElements.EPISODE)) {
      DublinCoreCatalog episodeDublinCore = DublinCoreUtil.loadDublinCore(workspace, episodeCatalog);
      episodeDublinCore.set(DublinCore.PROPERTY_IS_PART_OF, seriesId);
      try (InputStream in = IOUtils.toInputStream(episodeDublinCore.toXmlString(), "UTF-8")) {
        String filename = FilenameUtils.getName(episodeCatalog.getURI().toString());
        URI uri = workspace.put(mediaPackage.getIdentifier().toString(), episodeCatalog.getIdentifier(), filename,
                IOUtils.toInputStream(episodeDublinCore.toXmlString(), "UTF-8"));
        episodeCatalog.setURI(uri);
        // setting the URI to a new source so the checksum will most like be invalid
        episodeCatalog.setChecksum(null);
      } catch (Exception e) {
        logger.error("Unable to update episode catalog isPartOf field: {}", ExceptionUtils.getStackTrace(e));
        throw new WorkflowOperationException(e);
      }
    }

    // Attach series catalogs
    if (optAttachFlavors.isSome()) {
      // Remove existing series catalogs
      AbstractMediaPackageElementSelector<Catalog> catalogSelector = new CatalogSelector();
      String[] seriesFlavors = StringUtils.split(optAttachFlavors.get(), ",");
      for (String flavor : seriesFlavors) {
        if ("*".equals(flavor)) {
          catalogSelector.addFlavor("*/*");
        } else {
          catalogSelector.addFlavor(flavor);
        }
      }
      for (Catalog c : catalogSelector.select(mediaPackage, false)) {
        if (MediaPackageElements.SERIES.equals(c.getFlavor()) || "series".equals(c.getFlavor().getSubtype())) {
          mediaPackage.remove(c);
        }
      }

      List<SeriesCatalogUIAdapter> adapters = getSeriesCatalogUIAdapters();
      for (String flavorString : seriesFlavors) {
        MediaPackageElementFlavor flavor;
        if ("*".equals(flavorString)) {
          flavor = MediaPackageElementFlavor.parseFlavor("*/*");
        } else {
          flavor = MediaPackageElementFlavor.parseFlavor(flavorString);
        }
        for (SeriesCatalogUIAdapter a : adapters) {
          MediaPackageElementFlavor adapterFlavor = MediaPackageElementFlavor.parseFlavor(a.getFlavor());
          if (flavor.matches(adapterFlavor)) {
            if (MediaPackageElements.SERIES.eq(a.getFlavor())) {
              addDublinCoreCatalog(series, MediaPackageElements.SERIES, mediaPackage);
            } else {
              try {
                Opt<byte[]> seriesElementData = seriesService.getSeriesElementData(seriesId, adapterFlavor.getType());
                if (seriesElementData.isSome()) {
                  DublinCoreCatalog catalog = DublinCores.read(new ByteArrayInputStream(seriesElementData.get()));
                  addDublinCoreCatalog(catalog, adapterFlavor, mediaPackage);
                } else {
                  logger.warn("No extended series catalog found for flavor '{}' and series '{}', skip adding catalog",
                          adapterFlavor.getType(), seriesId);
                }
              } catch (SeriesException e) {
                logger.error("Unable to load extended series metadata for flavor {}", adapterFlavor.getType());
                throw new WorkflowOperationException(e);
              }
            }
          }
        }
      }
    }

    if (applyAcl) {
      try {
        AccessControlList acl = seriesService.getSeriesAccessControl(seriesId);
        if (acl != null)
          authorizationService.setAcl(mediaPackage, AclScope.Series, acl);
      } catch (Exception e) {
        logger.error("Unable to update series ACL: {}", ExceptionUtils.getStackTrace(e));
        throw new WorkflowOperationException(e);
      }
    }
    return createResult(mediaPackage, Action.CONTINUE);
  }

  /**
   * @param organization
   *          The organization to filter the results with.
   * @return A {@link List} of {@link SeriesCatalogUIAdapter} that provide the metadata to the front end.
   */
  private List<SeriesCatalogUIAdapter> getSeriesCatalogUIAdapters() {
    String organization = securityService.getOrganization().getId();
    return Stream.$(seriesCatalogUIAdapters).filter(seriesOrganizationFilter._2(organization)).toList();
  }

  private MediaPackage addDublinCoreCatalog(DublinCoreCatalog catalog, MediaPackageElementFlavor flavor,
          MediaPackage mediaPackage) throws WorkflowOperationException {
    try (InputStream in = IOUtils.toInputStream(catalog.toXmlString(), "UTF-8")) {
      String elementId = UUID.randomUUID().toString();
      URI catalogUrl = workspace.put(mediaPackage.getIdentifier().compact(), elementId, "dublincore.xml", in);
      logger.info("Adding catalog with flavor {} to mediapackage {}", flavor, mediaPackage);
      MediaPackageElement mpe = mediaPackage.add(catalogUrl, MediaPackageElement.Type.Catalog, flavor);
      mpe.setIdentifier(elementId);
      mpe.setChecksum(Checksum.create(ChecksumType.DEFAULT_TYPE, workspace.get(catalogUrl)));
      return mediaPackage;
    } catch (IOException | NotFoundException e) {
      throw new WorkflowOperationException(e);
    }
  }

  private static final Fn2<SeriesCatalogUIAdapter, String, Boolean> seriesOrganizationFilter = new Fn2<SeriesCatalogUIAdapter, String, Boolean>() {
    @Override
    public Boolean apply(SeriesCatalogUIAdapter catalogUIAdapter, String organization) {
      return catalogUIAdapter.getOrganization().equals(organization);
    }
  };

  /** Convert a string into a boolean. */
  private static final Fn<String, Boolean> toBoolean = new Fn<String, Boolean>() {
    @Override
    public Boolean apply(String s) {
      return BooleanUtils.toBoolean(s);
    }
  };

}
