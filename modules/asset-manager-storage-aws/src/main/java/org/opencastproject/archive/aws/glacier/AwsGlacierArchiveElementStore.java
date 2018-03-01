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

package org.opencastproject.archive.aws.glacier;

import static org.opencastproject.util.IoSupport.file;

import org.opencastproject.archive.aws.AwsAbstractArchive;
import org.opencastproject.archive.aws.AwsUploadOperationResult;
import org.opencastproject.archive.aws.persistence.AwsArchiveDatabaseException;
import org.opencastproject.archive.aws.persistence.AwsArchiveMapping;
import org.opencastproject.assetmanager.impl.VersionImpl;
import org.opencastproject.assetmanager.impl.storage.AssetStore;
import org.opencastproject.assetmanager.impl.storage.AssetStoreException;
import org.opencastproject.assetmanager.impl.storage.RemoteAssetStore;
import org.opencastproject.assetmanager.impl.storage.StoragePath;
import org.opencastproject.util.ConfigurationException;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.glacier.AmazonGlacierClient;
import com.amazonaws.services.glacier.AmazonGlacierClientBuilder;
import com.amazonaws.services.glacier.model.CreateVaultRequest;
import com.amazonaws.services.glacier.model.CreateVaultResult;
import com.amazonaws.services.glacier.model.DeleteArchiveRequest;
import com.amazonaws.services.glacier.model.DescribeVaultOutput;
import com.amazonaws.services.glacier.model.ListVaultsRequest;
import com.amazonaws.services.glacier.model.ListVaultsResult;
import com.amazonaws.services.glacier.transfer.ArchiveTransferManager;
import com.amazonaws.services.glacier.transfer.UploadResult;
import com.amazonaws.services.sns.AmazonSNSClient;
import com.amazonaws.services.sqs.AmazonSQSClient;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.Dictionary;
import java.util.List;

public class AwsGlacierArchiveElementStore extends AwsAbstractArchive implements RemoteAssetStore {

  // Since Glacier does not do versioning the say S3 does, we use a static version string instead
  public static final String GLACIER_VERSION = "GLACIER";

  /** Log facility */
  private static final Logger logger = LoggerFactory.getLogger(AwsGlacierArchiveElementStore.class);

  // Service configuration
  public static final String AWS_GLACIER_ACCESS_KEY_ID_CONFIG = "org.opencastproject.archive.aws.glacier.access.id";
  public static final String AWS_GLACIER_SECRET_ACCESS_KEY_CONFIG = "org.opencastproject.archive.aws.glacier.secret.key";
  public static final String AWS_GLACIER_REGION_CONFIG = "org.opencastproject.archive.aws.glacier.region";
  public static final String AWS_GLACIER_VAULT_CONFIG = "org.opencastproject.archive.aws.glacier.vault";

  /** The AWS client and transfer manager */
  private AmazonGlacierClient glacierClient = null;
  private AmazonSQSClient sqsClient = null;
  private AmazonSNSClient snsClient = null;
  private ArchiveTransferManager atm = null;

  /** The AWS Glacier vault name */
  private String vaultName = null;
  private boolean vaultCreated = false;

  //The local cache of files which have been get()ed from Glacier
  private File localCacheRoot = null;

  /**
   * Service activator, called via declarative services configuration.
   *
   * @param cc
   *          the component context
   */
  public void activate(final ComponentContext cc) throws IllegalStateException, IOException {
    // Get the configuration
    if (cc != null) {
      @SuppressWarnings("rawtypes") Dictionary properties = cc.getProperties();

      // Store type: "aws-glacier"
      storeType = StringUtils.trimToEmpty((String) properties.get(AssetStore.STORE_TYPE_PROPERTY));
      if (StringUtils.isEmpty(storeType)) {
        throw new ConfigurationException("Invalid store type value");
      }
      logger.info("{} is: {}", AssetStore.STORE_TYPE_PROPERTY, storeType);

      localCacheRoot = new File(StringUtils.trimToNull(cc.getBundleContext().getProperty(RemoteAssetStore.ASSET_STORE_CACHE_ROOT)), storeType);
      try {
        FileUtils.forceMkdir(localCacheRoot);
      } catch (IOException e) {
        throw new ConfigurationException("Unable to create " + localCacheRoot.getAbsolutePath());
      }

      // AWS Glacier vault name
      vaultName = getAWSConfigKey(cc, AWS_GLACIER_VAULT_CONFIG);
      logger.info("AWS Glacier vault name is {}", vaultName);

      // AWS region
      regionName = getAWSConfigKey(cc, AWS_GLACIER_REGION_CONFIG);
      logger.info("AWS region is {}", regionName);

      String accessKeyId = getAWSConfigKey(cc, AWS_GLACIER_ACCESS_KEY_ID_CONFIG);
      String accessKeySecret = getAWSConfigKey(cc, AWS_GLACIER_SECRET_ACCESS_KEY_CONFIG);

      // Create AWS client.
      BasicAWSCredentials awsCreds = new BasicAWSCredentials(accessKeyId, accessKeySecret);
      glacierClient = (AmazonGlacierClient) AmazonGlacierClientBuilder.standard()
              .withRegion(regionName)
              .withCredentials(new AWSStaticCredentialsProvider(awsCreds))
              .build();

      sqsClient = new AmazonSQSClient(awsCreds);
      snsClient = new AmazonSNSClient(awsCreds);
      atm = new ArchiveTransferManager(glacierClient, sqsClient, snsClient);

      logger.info("AwsGlacierArchiveElementStore activated!");
      createAWSVault();
    }

  }

  /**
   * Creates the AWS Glacier vault if it doesn't exist yet.
   */
  private void createAWSVault() {
    //List all the vaults
    ListVaultsRequest listVaultsRequest = new ListVaultsRequest();
    ListVaultsResult listVaultsResult = glacierClient.listVaults(listVaultsRequest);

    List<DescribeVaultOutput> vaultList = listVaultsResult.getVaultList();
    //Does the vault exist?
    vaultCreated = false;
    for (DescribeVaultOutput output : vaultList) {
      if (vaultName.equals(output.getVaultName())) {
        vaultCreated = true;
        break;
      }
    }

    if (!vaultCreated) {
      CreateVaultRequest createVaultRequest = new CreateVaultRequest()
              .withVaultName(vaultName);
      CreateVaultResult createVaultResult = glacierClient.createVault(createVaultRequest);
      //TODO: There does not appear to be an obvious way to check this result
    }
  }

  protected AwsUploadOperationResult uploadObject(File origin, String objectName) {
    String archiveId;
    try {
      UploadResult upload = atm.upload(vaultName, objectName, origin);
      archiveId = upload.getArchiveId();
    } catch (FileNotFoundException e) {
      throw new AssetStoreException("Unable to find " + origin.getAbsolutePath() + " on disk!", e);
    } catch (Exception e) {
      throw new AssetStoreException("Unexpected exception!", e);
    }
    return new AwsUploadOperationResult(archiveId, GLACIER_VERSION);
  }

  public InputStream getObject(AwsArchiveMapping map) throws AssetStoreException {
    StoragePath localPath = new StoragePath(map.getOrganizationId(), map.getMediaPackageId(), new VersionImpl(map.getVersion()), map.getMediaPackageElementId());
    File localFile = getLocalCacheFile(localPath);

    if (database.isLocallyCached(localPath)) {
      return IOUtils.toInputStream(localFile.getAbsolutePath());
    }

    //Ensure the root exists
    try {
      FileUtils.forceMkdir(localCacheRoot);
    } catch (IOException e) {
      throw new AssetStoreException("Unable to create " + localCacheRoot, e);
    }

    //Download the file.  This may block for upwards of 12 hours depending on retrival class and account settings.
    atm.download(vaultName, map.getObjectKey(), localFile);

    try {
      database.addLocallyCachedFile(localPath);
    } catch (AwsArchiveDatabaseException e) {
      throw new AssetStoreException("Unable to add cached copy to database, element " + localFile.getAbsolutePath() + " will not be automatically cleaned up!", e);
    }

    return IOUtils.toInputStream(localFile.getAbsolutePath());
  }

  public void deleteObject(AwsArchiveMapping map) throws AssetStoreException {
    glacierClient.deleteArchive(new DeleteArchiveRequest()
            .withVaultName(vaultName)
            .withArchiveId(map.getObjectKey()));
  }

  public void purgeCache(Date olderThan) throws AssetStoreException {
    try {
      List<StoragePath> purge = database.getLocallyCachedFiles(olderThan);
      for (StoragePath path : purge) {
        File purgeMe = getLocalCacheFile(path);
        FileUtils.deleteQuietly(purgeMe);
        database.deleteCacheMapping(path);
      }
    } catch (AwsArchiveDatabaseException e) {
      throw new AssetStoreException("Unable to purge cache due to database error", e);
    }
  }

  public File getLocalCacheFile(StoragePath path) {
    return file(localCacheRoot.getAbsolutePath(), buildFilename(path, ""));
  }

  //Used for testing
  public void setVaultName(String vaultName) {
    this.vaultName = vaultName;
  }

  //Used for testing
  public void setLocalCacheRoot(File root) {
    this.localCacheRoot = root;
  }

  //Used for testing
  public void setArchiveTransferManager(ArchiveTransferManager atm) {
    this.atm = atm;
  }

  //Used for testing
  public void setGlacierClient(AmazonGlacierClient client) {
    this.glacierClient = client;
  }

}
