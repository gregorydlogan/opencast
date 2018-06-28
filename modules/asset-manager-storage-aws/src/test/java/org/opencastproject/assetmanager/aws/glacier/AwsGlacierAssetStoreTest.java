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

package org.opencastproject.assetmanager.aws.glacier;

import org.opencastproject.assetmanager.aws.persistence.AwsAssetDatabaseImpl;
import org.opencastproject.assetmanager.aws.persistence.AwsAssetMapping;
import org.opencastproject.assetmanager.impl.VersionImpl;
import org.opencastproject.assetmanager.impl.storage.DeletionSelector;
import org.opencastproject.assetmanager.impl.storage.Source;
import org.opencastproject.assetmanager.impl.storage.StoragePath;
import org.opencastproject.util.persistence.PersistenceUtil;
import org.opencastproject.workspace.api.Workspace;

import com.amazonaws.services.glacier.AmazonGlacierClient;
import com.amazonaws.services.glacier.model.DeleteArchiveRequest;
import com.amazonaws.services.glacier.model.DeleteArchiveResult;
import com.amazonaws.services.glacier.transfer.ArchiveTransferManager;
import com.amazonaws.services.glacier.transfer.UploadResult;
import com.entwinemedia.fn.data.Opt;
import com.mchange.v2.c3p0.ComboPooledDataSource;

import org.easymock.EasyMock;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.osgi.framework.BundleContext;
import org.osgi.service.component.ComponentContext;

import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.util.Date;

public class AwsGlacierAssetStoreTest {
  private ComboPooledDataSource pooledDataSource;
  private ComponentContext cc;
  private AwsAssetDatabaseImpl database;

  private AmazonGlacierClient glacierClient;
  private ArchiveTransferManager archiveTransfer;
  private Workspace workspace;

  private static final String VAULT_NAME = "aws-glacier-vault";

  private static final String ORG_ID = "org";
  private static final String MP_ID = "abcd";
  private static final String ASSET_ID = "efgh";
  private static final String ASSET_ID2 = "ijkl";
  private static final String FILE_NAME = "dublincore.xml";

  private URI uri;
  private File sampleFile;
  private File cacheRoot;

  private AwsGlacierAssetStore store;

  @Before
  public void setUp() throws Exception {
    long currentTime = System.currentTimeMillis();

    BundleContext bc = EasyMock.createNiceMock(BundleContext.class);
    cc = EasyMock.createNiceMock(ComponentContext.class);
    EasyMock.expect(cc.getBundleContext()).andReturn(bc).anyTimes();
    EasyMock.replay(bc, cc);

    database = new AwsAssetDatabaseImpl();
    database.setEntityManagerFactory(PersistenceUtil.newTestEntityManagerFactory(AwsAssetDatabaseImpl.PERSISTENCE_UNIT));
    database.activate(cc);

    uri = getClass().getClassLoader().getResource(FILE_NAME).toURI();
    sampleFile = new File(uri);
    cacheRoot = new File(sampleFile.getParentFile(), "local-cache");

    // Set up the service
    glacierClient = EasyMock.createStrictMock(AmazonGlacierClient.class);
    archiveTransfer = EasyMock.createStrictMock(ArchiveTransferManager.class);
    // Replay will be called in each test

    workspace = EasyMock.createNiceMock(Workspace.class);
    EasyMock.expect(workspace.get(uri)).andReturn(sampleFile).anyTimes();
    EasyMock.replay(workspace);

    store = new AwsGlacierAssetStore();
    store.setArchiveTransferManager(archiveTransfer);
    store.setVaultName(VAULT_NAME);
    store.setGlacierClient(glacierClient);
    store.setLocalCacheRoot(cacheRoot);
    store.setWorkspace(workspace);
    store.setDatabase(database);
  }

  @After
  public void tearDown() throws Exception {
  }

  @Test
  public void testPut() throws Exception {
    StoragePath path = new StoragePath(ORG_ID, MP_ID, new VersionImpl(1L), ASSET_ID);
    String objectName = store.buildObjectName(sampleFile, path);

    UploadResult result = EasyMock.createStrictMock(UploadResult.class);
    EasyMock.expect(result.getArchiveId()).andReturn(objectName).once();

    EasyMock.expect(archiveTransfer.upload(VAULT_NAME, objectName, sampleFile)).andReturn(result);

    EasyMock.replay(result, archiveTransfer);

    store.put(path, Source.mk(uri));

    // Check if mapping saved to db
    AwsAssetMapping mapping = database.findMapping(path);
    Assert.assertNotNull(mapping);
    Assert.assertEquals(ORG_ID, mapping.getOrganizationId());
    Assert.assertEquals(MP_ID, mapping.getMediaPackageId());
    Assert.assertEquals(1L, mapping.getVersion().longValue());
    Assert.assertEquals(ASSET_ID, mapping.getMediaPackageElementId());

    //Ensure that we don't have a locally cached copy
    Assert.assertFalse(database.isLocallyCached(path));
    Assert.assertEquals(0, database.getLocallyCachedFiles(new Date()).size());
  }

  @Test
  public void testGet() throws Exception {
    StoragePath path = new StoragePath(ORG_ID, MP_ID, new VersionImpl(1L), ASSET_ID);
    String objectName = store.buildObjectName(sampleFile, path);

    UploadResult result = EasyMock.createStrictMock(UploadResult.class);
    EasyMock.expect(result.getArchiveId()).andReturn(objectName).once();

    EasyMock.expect(archiveTransfer.upload(VAULT_NAME, objectName, sampleFile)).andReturn(result);
    archiveTransfer.download(VAULT_NAME, objectName, store.getLocalCacheFile(path));
    EasyMock.expectLastCall().once();
    EasyMock.replay(result, archiveTransfer);

    store.put(path, Source.mk(uri));

    //Ensure that we don't have a locally cached copy
    Assert.assertFalse(database.isLocallyCached(path));
    Assert.assertEquals(0, database.getLocallyCachedFiles(new Date()).size());

    Opt<InputStream> stream = store.get(path);

    Assert.assertTrue(stream.isSome());
    //Ensure that we *do* have a locally cached copy
    Assert.assertTrue(database.isLocallyCached(path));
    Assert.assertEquals(1, database.getLocallyCachedFiles(new Date()).size());

    //This ensures that we're actually pulling from the cached copy, rather than refetching
    stream = store.get(path);
    Assert.assertTrue(stream.isSome());
    EasyMock.verify(archiveTransfer);
  }

  @Test
  public void testCopy() throws Exception {
    StoragePath path1 = new StoragePath(ORG_ID, MP_ID, new VersionImpl(1L), ASSET_ID);
    StoragePath path2 = new StoragePath(ORG_ID, MP_ID, new VersionImpl(1L), ASSET_ID2);

    String objectName1 = store.buildObjectName(sampleFile, path1);

    UploadResult result = EasyMock.createStrictMock(UploadResult.class);
    EasyMock.expect(result.getArchiveId()).andReturn(objectName1).once();

    EasyMock.expect(archiveTransfer.upload(VAULT_NAME, objectName1, sampleFile)).andReturn(result);
    //Note that we're using objectName1 here.  Take a look at the copy() method to figure out why.
    archiveTransfer.download(VAULT_NAME, objectName1, store.getLocalCacheFile(path2));
    EasyMock.expectLastCall().once();

    EasyMock.replay(result, archiveTransfer);

    store.put(path1, Source.mk(uri));

    Assert.assertTrue(store.copy(path1, path2));

    Assert.assertFalse(database.isLocallyCached(path2));
    Opt<InputStream> is = store.get(path2);
    Assert.assertTrue(is.isSome());
    Assert.assertTrue(database.isLocallyCached(path2));
  }

  @Test
  public void testBadGet() throws Exception {
    StoragePath path = new StoragePath(ORG_ID, MP_ID, new VersionImpl(1L), ASSET_ID);

    EasyMock.replay(archiveTransfer);

    //Ensure that we don't have a locally cached copy
    Assert.assertFalse(database.isLocallyCached(path));
    Assert.assertEquals(0, database.getLocallyCachedFiles(new Date()).size());

    Opt<InputStream> stream = store.get(path);

    Assert.assertTrue(stream.isNone());
  }

  @Test
  public void testBadCopy() throws Exception {
    //We're testing to see if copying a non-existant mapping actually fails as expected
    StoragePath path1 = new StoragePath(ORG_ID, MP_ID, new VersionImpl(1L), ASSET_ID);
    StoragePath path2 = new StoragePath(ORG_ID, MP_ID, new VersionImpl(1L), ASSET_ID2);

    Assert.assertFalse(store.copy(path1, path2));
  }

  @Test @Ignore
  public void testCorruptDatabase() throws Exception {
    throw new RuntimeException("This test needs to test what happens when the database contains a mapping but the file has been deleted in Glacier");
  }

  @Test
  public void testDelete() throws Exception {
    StoragePath path = new StoragePath(ORG_ID, MP_ID, new VersionImpl(1L), ASSET_ID);
    String objectName = store.buildObjectName(sampleFile, path);

    UploadResult result = EasyMock.createStrictMock(UploadResult.class);
    EasyMock.expect(result.getArchiveId()).andReturn(objectName).once();

    EasyMock.expect(archiveTransfer.upload(VAULT_NAME, objectName, sampleFile)).andReturn(result);

    DeleteArchiveResult dar = EasyMock.createNiceMock(DeleteArchiveResult.class);
    EasyMock.expect(glacierClient.deleteArchive(new DeleteArchiveRequest()
            .withVaultName(VAULT_NAME)
            .withArchiveId(objectName))).andReturn(dar).once();

    EasyMock.replay(result, dar, glacierClient, archiveTransfer);

    store.put(path, Source.mk(uri));

    // Check if mapping saved to db
    AwsAssetMapping mapping = database.findMapping(path);
    Assert.assertNotNull(mapping);
    Assert.assertEquals(ORG_ID, mapping.getOrganizationId());
    Assert.assertEquals(MP_ID, mapping.getMediaPackageId());
    Assert.assertEquals(1L, mapping.getVersion().longValue());
    Assert.assertEquals(ASSET_ID, mapping.getMediaPackageElementId());

    //Ensure that we don't have a locally cached copy
    Assert.assertFalse(database.isLocallyCached(path));
    Assert.assertEquals(0, database.getLocallyCachedFiles(new Date()).size());

    DeletionSelector ds = DeletionSelector.deleteAll(ORG_ID, MP_ID);
    Assert.assertTrue(store.delete(ds));
    EasyMock.verify(archiveTransfer);
  }

  @Test
  public void testCachePurge() throws Exception {
    StoragePath path = new StoragePath(ORG_ID, MP_ID, new VersionImpl(1L), ASSET_ID);
    String objectName = store.buildObjectName(sampleFile, path);

    UploadResult result = EasyMock.createStrictMock(UploadResult.class);
    EasyMock.expect(result.getArchiveId()).andReturn(objectName).once();

    EasyMock.expect(archiveTransfer.upload(VAULT_NAME, objectName, sampleFile)).andReturn(result);
    archiveTransfer.download(VAULT_NAME, objectName, store.getLocalCacheFile(path));
    EasyMock.expectLastCall().once();
    EasyMock.replay(result, archiveTransfer);

    store.put(path, Source.mk(uri));

    //Ensure that we don't have a locally cached copy
    Assert.assertFalse(database.isLocallyCached(path));
    Assert.assertEquals(0, database.getLocallyCachedFiles(new Date()).size());

    Opt<InputStream> stream = store.get(path);

    Assert.assertTrue(stream.isSome());
    //Ensure that we *do* have a locally cached copy
    Assert.assertTrue(database.isLocallyCached(path));
    Assert.assertEquals(1, database.getLocallyCachedFiles(new Date()).size());

    //This ensures that we're actually pulling from the cached copy, rather than refetching
    stream = store.get(path);
    Assert.assertTrue(stream.isSome());
    EasyMock.verify(archiveTransfer);

    Thread.sleep(1000L);

    store.purgeCache(new Date());
  }
}
