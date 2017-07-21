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

package org.opencast.distribution.streaming;

import static java.lang.String.format;
import static org.opencast.util.OsgiUtil.getOptContextProperty;
import static org.opencast.util.PathSupport.path;
import static org.opencast.util.UrlSupport.concat;
import static org.opencast.util.data.Option.none;
import static org.opencast.util.data.Option.some;

import org.opencast.distribution.api.AbstractDistributionService;
import org.opencast.distribution.api.DistributionException;
import org.opencast.distribution.api.DistributionService;
import org.opencast.job.api.Job;
import org.opencast.mediapackage.MediaPackage;
import org.opencast.mediapackage.MediaPackageElement;
import org.opencast.mediapackage.MediaPackageElementParser;
import org.opencast.mediapackage.MediaPackageException;
import org.opencast.mediapackage.MediaPackageParser;
import org.opencast.mediapackage.track.TrackImpl;
import org.opencast.serviceregistry.api.ServiceRegistryException;
import org.opencast.util.FileSupport;
import org.opencast.util.LoadUtil;
import org.opencast.util.NotFoundException;
import org.opencast.util.OsgiUtil;
import org.opencast.util.RequireUtil;
import org.opencast.util.data.Option;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Dictionary;
import java.util.List;

/**
 * Distributes media to the local media delivery directory.
 */
public class StreamingDistributionService extends AbstractDistributionService implements DistributionService, ManagedService {

  /** Logging facility */
  private static final Logger logger = LoggerFactory.getLogger(StreamingDistributionService.class);

  /** Receipt type */
  public static final String JOB_TYPE = "org.opencast.distribution.streaming";

  /** List of available operations on jobs */
  private enum Operation {
    Distribute, Retract
  }

  /** The load on the system introduced by creating a distribute job */
  public static final float DEFAULT_DISTRIBUTE_JOB_LOAD = 0.1f;

  /** The load on the system introduced by creating a retract job */
  public static final float DEFAULT_RETRACT_JOB_LOAD = 1.0f;

  /** The key to look for in the service configuration file to override the {@link DEFAULT_DISTRIBUTE_JOB_LOAD} */
  public static final String DISTRIBUTE_JOB_LOAD_KEY = "job.load.streaming.distribute";

  /** The key to look for in the service configuration file to override the {@link DEFAULT_RETRACT_JOB_LOAD} */
  public static final String RETRACT_JOB_LOAD_KEY = "job.load.streaming.retract";

  /** The load on the system introduced by creating a distribute job */
  private float distributeJobLoad = DEFAULT_DISTRIBUTE_JOB_LOAD;

  /** The load on the system introduced by creating a retract job */
  private float retractJobLoad = DEFAULT_RETRACT_JOB_LOAD;

  private Option<Locations> locations = none();

  /** Creates a new instance of the streaming distribution service. */
  public StreamingDistributionService() {
    super(JOB_TYPE);
  }

  @Override
  public void activate(ComponentContext cc) {
    super.activate(cc);
    // Get the configured streaming and server URLs
    if (cc != null) {
      for (final String streamingUrl : getOptContextProperty(cc, "org.opencast.streaming.url")) {
        for (final String distributionDirectoryPath : getOptContextProperty(cc,
                "org.opencast.streaming.directory")) {
          final File distributionDirectory = new File(distributionDirectoryPath);
          if (!distributionDirectory.isDirectory()) {
            try {
              FileUtils.forceMkdir(distributionDirectory);
            } catch (IOException e) {
              throw new IllegalStateException("Distribution directory does not exist and can't be created", e);
            }
          }
          String compatibility = StringUtils
                  .trimToNull(cc.getBundleContext().getProperty("org.opencast.streaming.flvcompatibility"));
          boolean flvCompatibilityMode = false;
          if (compatibility != null) {
            flvCompatibilityMode = Boolean.parseBoolean(compatibility);
            logger.info("Streaming distribution is using FLV compatibility mode");
          }
          locations = some(new Locations(URI.create(streamingUrl), distributionDirectory, flvCompatibilityMode));
          logger.info("Streaming url is {}", streamingUrl);
          logger.info("Streaming distribution directory is {}", distributionDirectory);
          return;
        }
        logger.info("No streaming distribution directory configured (org.opencast.streaming.directory)");
      }
      logger.info("No streaming url configured (org.opencast.streaming.url)");
    }
    this.distributionChannel = OsgiUtil.getComponentContextProperty(cc, CONFIG_KEY_STORE_TYPE);
  }

  public String getDistributionType() {
    return this.distributionChannel;
  }

  /**
   * {@inheritDoc}
   *
   * @see org.opencast.distribution.api.DistributionService#distribute(String,
   *      org.opencast.mediapackage.MediaPackage, String)
   */
  @Override
  public Job distribute(String channelId, MediaPackage mediapackage, String elementId)
          throws DistributionException, MediaPackageException {
    if (locations.isNone())
      return null;

    RequireUtil.notNull(mediapackage, "mediapackage");
    RequireUtil.notNull(elementId, "elementId");
    RequireUtil.notNull(channelId, "channelId");
    //
    try {
      return serviceRegistry.createJob(JOB_TYPE, Operation.Distribute.toString(),
              Arrays.asList(channelId, MediaPackageParser.getAsXml(mediapackage), elementId), distributeJobLoad);
    } catch (ServiceRegistryException e) {
      throw new DistributionException("Unable to create a job", e);
    }
  }

  /**
   * Distribute a Mediapackage element to the download distribution service.
   *
   * @param mp
   *          The media package that contains the element to distribute.
   * @param mpeId
   *          The id of the element that should be distributed contained within the media package.
   * @return A reference to the MediaPackageElement that has been distributed.
   * @throws DistributionException
   *           Thrown if the parent directory of the MediaPackageElement cannot be created, if the MediaPackageElement
   *           cannot be copied or another unexpected exception occurs.
   */

  private MediaPackageElement distributeElement(String channelId, final MediaPackage mp, String mpeId)
          throws DistributionException {
    RequireUtil.notNull(channelId, "channelId");
    RequireUtil.notNull(mp, "mp");
    RequireUtil.notNull(mpeId, "mpeId");
    //
    final MediaPackageElement element = mp.getElementById(mpeId);
    // Make sure the element exists
    if (element == null) {
      throw new IllegalStateException("No element " + mpeId + " found in media package");
    }
    // Streaming servers only deal with tracks
    if (!MediaPackageElement.Type.Track.equals(element.getElementType())) {
      logger.debug("Skipping {} {} for distribution to the streaming server",
              element.getElementType().toString().toLowerCase(), element.getIdentifier());
      return null;
    }
    try {
      File source;
      try {
        source = workspace.get(element.getURI());
      } catch (NotFoundException e) {
        throw new DistributionException("Unable to find " + element.getURI() + " in the workspace", e);
      } catch (IOException e) {
        throw new DistributionException("Error loading " + element.getURI() + " from the workspace", e);
      }

      // Try to find a duplicated element source
      try {
        source = findDuplicatedElementSource(source, mp.getIdentifier().compact());
      } catch (IOException e) {
        logger.warn("Unable to find duplicated source {}: {}", source, ExceptionUtils.getMessage(e));
      }

      final File destination = locations.get().createDistributionFile(securityService.getOrganization().getId(),
              channelId, mp.getIdentifier().compact(), element.getIdentifier(), element.getURI());

      if (!destination.equals(source)) {
        // Put the file in place if sourcesfile differs destinationfile
        try {
          FileUtils.forceMkdir(destination.getParentFile());
        } catch (IOException e) {
          throw new DistributionException("Unable to create " + destination.getParentFile(), e);
        }
        logger.info("Distributing {} to {}", mpeId, destination);

        try {
          FileSupport.link(source, destination, true);
        } catch (IOException e) {
          throw new DistributionException("Unable to copy " + source + " to " + destination, e);
        }
      }
      // Create a representation of the distributed file in the mediapackage
      final MediaPackageElement distributedElement = (MediaPackageElement) element.clone();
      distributedElement.setURI(locations.get().createDistributionUri(securityService.getOrganization().getId(),
              channelId, mp.getIdentifier().compact(), element.getIdentifier(), element.getURI()));
      distributedElement.setIdentifier(null);
      ((TrackImpl) distributedElement).setTransport(TrackImpl.StreamingProtocol.RTMP);
      logger.info("Finished distribution of {}", element);
      return distributedElement;
    } catch (Exception e) {
      logger.warn("Error distributing " + element, e);
      if (e instanceof DistributionException) {
        throw (DistributionException) e;
      } else {
        throw new DistributionException(e);
      }
    }
  }

  /**
   * {@inheritDoc}
   *
   * @see org.opencast.distribution.api.DistributionService#retract(String,
   *      org.opencast.mediapackage.MediaPackage, String) java.lang.String)
   */
  @Override
  public Job retract(String channelId, MediaPackage mediaPackage, String elementId) throws DistributionException {
    if (locations.isNone())
      return null;

    RequireUtil.notNull(mediaPackage, "mediaPackage");
    RequireUtil.notNull(elementId, "elementId");
    RequireUtil.notNull(channelId, "channelId");
    //
    try {
      return serviceRegistry.createJob(JOB_TYPE, Operation.Retract.toString(),
              Arrays.asList(channelId, MediaPackageParser.getAsXml(mediaPackage), elementId), retractJobLoad);
    } catch (ServiceRegistryException e) {
      throw new DistributionException("Unable to create a job", e);
    }
  }

  /**
   * Retracts the mediapackage with the given identifier from the distribution channel.
   *
   * @param channelId
   *          the channel id
   * @param mp
   *          the mediapackage
   * @param mpeId
   *          the element identifier
   * @return the retracted element or <code>null</code> if the element was not retracted
   */
  private MediaPackageElement retractElement(final String channelId, final MediaPackage mp, final String mpeId)
          throws DistributionException {
    RequireUtil.notNull(channelId, "channelId");
    RequireUtil.notNull(mp, "mp");
    RequireUtil.notNull(mpeId, "elementId");
    // Make sure the element exists
    final MediaPackageElement mpe = mp.getElementById(mpeId);
    if (mpe == null) {
      throw new IllegalStateException("No element " + mpeId + " found in media package");
    }
    try {
      for (final File mpeFile : locations.get().getDistributionFileFrom(mpe.getURI())) {
        logger.info("Retracting element {} from {}", mpe, mpeFile);
        // Does the file exist? If not, the current element has not been distributed to this channel
        // or has been removed otherwise
        if (mpeFile.exists()) {
          // Try to remove the file and - if possible - the parent folder
          final File parentDir = mpeFile.getParentFile();
          FileUtils.forceDelete(mpeFile);
          FileSupport.deleteHierarchyIfEmpty(new File(locations.get().getBaseDir()), parentDir);
          logger.info("Finished retracting element {} of media package {}", mpeId, mp);
          return mpe;
        } else {
          logger.info(format("Element %s@%s has already been removed from publication channel %s", mpeId,
                  mp.getIdentifier(), channelId));
          return mpe;
        }
      }
      // could not extract a file from the element's URI
      logger.info(format("Element %s has not been published to publication channel %s", mpe.getURI(), channelId));
      return mpe;
    } catch (Exception e) {
      logger.warn(format("Error retracting element %s of media package %s", mpeId, mp), e);
      if (e instanceof DistributionException) {
        throw (DistributionException) e;
      } else {
        throw new DistributionException(e);
      }
    }

  }

  /**
   * {@inheritDoc}
   *
   * @see org.opencast.job.api.AbstractJobProducer#process(org.opencast.job.api.Job)
   */
  @Override
  protected String process(Job job) throws Exception {
    Operation op = null;
    String operation = job.getOperation();
    List<String> arguments = job.getArguments();
    try {
      op = Operation.valueOf(operation);
      String channelId = arguments.get(0);
      MediaPackage mediapackage = MediaPackageParser.getFromXml(arguments.get(1));
      String elementId = arguments.get(2);
      switch (op) {
        case Distribute:
          MediaPackageElement distributedElement = distributeElement(channelId, mediapackage, elementId);
          return (distributedElement != null) ? MediaPackageElementParser.getAsXml(distributedElement) : null;
        case Retract:
          return locations.isSome()
                  ? MediaPackageElementParser.getAsXml(retractElement(channelId, mediapackage, elementId)) : null;
        default:
          throw new IllegalStateException("Don't know how to handle operation '" + operation + "'");
      }
    } catch (IllegalArgumentException e) {
      throw new ServiceRegistryException("This service can't handle operations of type '" + op + "'", e);
    } catch (IndexOutOfBoundsException e) {
      throw new ServiceRegistryException("This argument list for operation '" + op + "' does not meet expectations", e);
    } catch (Exception e) {
      throw new ServiceRegistryException("Error handling operation '" + op + "'", e);
    }
  }

  /**
   * Try to find the same file being already distributed in one of the other channels
   *
   * @param source
   *          the source file
   * @param mpId
   *          the element's mediapackage id
   * @return the found duplicated file or the given source if nothing has been found
   * @throws IOException
   *           if an I/O error occurs
   */
  private File findDuplicatedElementSource(final File source, final String mpId) throws IOException {
    String orgId = securityService.getOrganization().getId();
    final Path rootPath = new File(path(locations.get().getBaseDir(), orgId)).toPath();

    // Check if root path exists, if not you're file system has not been migrated to the new distribution service yet
    // and does not support this function
    if (!Files.exists(rootPath))
      return source;

    // Find matching mediapackage directories
    List<Path> mediaPackageDirectories = new ArrayList<>();
    try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(rootPath)) {
      for (Path path : directoryStream) {
        Path mpDir = new File(path.toFile(), mpId).toPath();
        if (Files.exists(mpDir)) {
          mediaPackageDirectories.add(mpDir);
        }
      }
    }

    if (mediaPackageDirectories.isEmpty())
      return source;

    final long size = Files.size(source.toPath());

    final File[] result = new File[1];
    for (Path p : mediaPackageDirectories) {
      // Walk through found mediapackage directories to find duplicated element
      Files.walkFileTree(p, new SimpleFileVisitor<Path>() {
        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
          // Walk through files only
          if (Files.isDirectory(file))
            return FileVisitResult.CONTINUE;

          // Check for same file size
          if (size != attrs.size())
            return FileVisitResult.CONTINUE;

          // If size less than 4096 bytes use readAllBytes method which performs better
          if (size < 4096) {
            if (!Arrays.equals(Files.readAllBytes(source.toPath()), Files.readAllBytes(file)))
              return FileVisitResult.CONTINUE;

          } else {
            // Otherwise compare file input stream
            try (InputStream is1 = Files.newInputStream(source.toPath());
                    InputStream is2 = Files.newInputStream(file)) {
              if (!IOUtils.contentEquals(is1, is2))
                return FileVisitResult.CONTINUE;
            }
          }

          // File is equal, store file and terminate file walking
          result[0] = file.toFile();
          return FileVisitResult.TERMINATE;
        }
      });

      // A duplicate has already been found, no further file walking is needed
      if (result[0] != null)
        break;
    }

    // Return found duplicate otherwise source
    if (result[0] != null)
      return result[0];

    return source;
  }

  @Override
  public void updated(@SuppressWarnings("rawtypes") Dictionary properties) throws ConfigurationException {
    distributeJobLoad = LoadUtil.getConfiguredLoadValue(properties, DISTRIBUTE_JOB_LOAD_KEY,
            DEFAULT_DISTRIBUTE_JOB_LOAD, serviceRegistry);
    retractJobLoad = LoadUtil.getConfiguredLoadValue(properties, RETRACT_JOB_LOAD_KEY, DEFAULT_RETRACT_JOB_LOAD,
            serviceRegistry);
  }

  public static class Locations {
    private final URI baseUri;
    private final String baseDir;

    /** Compatibility mode for nginx and maybe other streaming servers */
    private boolean flvCompatibilityMode = false;

    /**
     * @param baseUri
     *          the base URL of the distributed streaming artifacts
     * @param baseDir
     *          the file system base directory below which streaming distribution artifacts are stored
     */
    public Locations(URI baseUri, File baseDir, boolean flvCompatibilityMode) {
      this.flvCompatibilityMode = flvCompatibilityMode;
      try {
        final String ensureSlash = baseUri.getSchemeSpecificPart().endsWith("/") ? baseUri.getSchemeSpecificPart()
                : baseUri.getSchemeSpecificPart() + "/";
        this.baseUri = new URI(baseUri.getScheme(), ensureSlash, null);
        this.baseDir = baseDir.getAbsolutePath();
      } catch (URISyntaxException e) {
        throw new RuntimeException(e);
      }
    }

    public String getBaseUri() {
      return baseUri.toString();
    }

    public String getBaseDir() {
      return baseDir;
    }

    public boolean isDistributionUrl(URI mpeUrl) {
      return mpeUrl.toString().startsWith(getBaseUri());
    }

    public Option<URI> dropBase(URI mpeUrl) {
      if (isDistributionUrl(mpeUrl)) {
        return some(baseUri.relativize(mpeUrl));
      } else {
        return none();
      }
    }

    /**
     * Try to retrieve the distribution file from a distribution URI. This is the the inverse function of
     * {@link #createDistributionUri(String, String, String, String, java.net.URI)}.
     *
     * @param mpeDistUri
     *          the URI of a distributed media package element
     * @see #createDistributionUri(String, String, String, String, java.net.URI)
     * @see #createDistributionFile(String, String, String, String, java.net.URI)
     */
    public Option<File> getDistributionFileFrom(final URI mpeDistUri) {
      // if the given URI is not a distribution URI there cannot be a corresponding file
      for (URI distPath : dropBase(mpeDistUri)) {
        // 0: orgId | [extension ":" ] orgId ;
        // extension = "mp4" | ...
        // 1: channelId
        // 2: mediaPackageId
        // 3: mediaPackageElementId
        // 4: fileName
        final String[] splitUrl = distPath.toString().split("/");
        if (splitUrl.length == 5) {
          final String[] split = splitUrl[0].split(":");
          final String ext;
          final String orgId;
          if (split.length == 2) {
            ext = split[0];
            orgId = split[1];
          } else {
            ext = "flv";
            orgId = split[0];
          }
          return some(new File(path(baseDir, orgId, splitUrl[1], splitUrl[2], splitUrl[3], splitUrl[4] + "." + ext)));
        } else {
          return none();
        }
      }
      return none();
    }

    /**
     * Create a file to distribute a media package element to.
     *
     * @param orgId
     *          the id of the organization
     * @param channelId
     *          the id of the distribution channel
     * @param mpId
     *          the media package id
     * @param mpeId
     *          the media package element id
     * @param mpeUri
     *          the URI of the media package element to distribute
     * @see #createDistributionUri(String, String, String, String, java.net.URI)
     * @see #getDistributionFileFrom(java.net.URI)
     */
    public File createDistributionFile(final String orgId, final String channelId, final String mpId,
            final String mpeId, final URI mpeUri) {
      for (File f : getDistributionFileFrom(mpeUri)) {
        return f;
      }
      return new File(path(baseDir, orgId, channelId, mpId, mpeId, FilenameUtils.getName(mpeUri.toString())));
    }

    /**
     * Create a distribution URI for a media package element. This is the inverse function of
     * {@link #getDistributionFileFrom(java.net.URI)}.
     * <p>
     * Distribution URIs look like this:
     *
     * <pre>
     * Flash video (flv)
     *   rtmp://localhost/matterhorn-engage/mh_default_org/engage-player/9f411edb-edf5-4308-8df5-f9b111d9d346/bed1cdba-2d42-49b1-b78f-6c6745fb064a/Hans_Arp_1m10s
     * H.264 (mp4)
     *   rtmp://localhost/matterhorn-engage/mp4:mh_default_org/engage-player/9f411edb-edf5-4308-8df5-f9b111d9d346/bd4d5a48-41a8-4362-93dc-be41aaae77f8/Hans_Arp_1m10s
     * </pre>
     *
     * @param orgId
     *          the id of the organization
     * @param channelId
     *          the id of the distribution channel
     * @param mpId
     *          the media package id
     * @param mpeId
     *          the media package element id
     * @param mpeUri
     *          the URI of the media package element to distribute
     * @see #createDistributionFile(String, String, String, String, java.net.URI)
     * @see #getDistributionFileFrom(java.net.URI)
     */
    public URI createDistributionUri(final String orgId, String channelId, String mpId, String mpeId, URI mpeUri) {
      // if the given media package element URI is already a distribution URI just return it
      if (!isDistributionUrl(mpeUri)) {
        final String ext = FilenameUtils.getExtension(mpeUri.toString());
        final String fileName = FilenameUtils.getBaseName(mpeUri.toString());
        String tag = ext + ":";

        // removes the tag for flv files, but keeps it for all others (mp4 needs it)
        if (flvCompatibilityMode && "flv:".equals(tag))
          tag = "";

        return URI.create(concat(getBaseUri(), tag + orgId, channelId, mpId, mpeId, fileName));
      } else {
        return mpeUri;
      }
    }
  }
}
