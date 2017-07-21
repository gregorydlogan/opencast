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

package org.opencast.adminui.endpoint;

import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.createNiceMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.opencast.util.data.Tuple.tuple;

import org.opencast.assetmanager.api.AssetManager;
import org.opencast.assetmanager.api.Snapshot;
import org.opencast.assetmanager.impl.AbstractAssetManager;
import org.opencast.assetmanager.impl.HttpAssetProvider;
import org.opencast.assetmanager.impl.persistence.Database;
import org.opencast.assetmanager.impl.storage.AssetStore;
import org.opencast.assetmanager.impl.storage.AssetStoreException;
import org.opencast.assetmanager.impl.storage.DeletionSelector;
import org.opencast.assetmanager.impl.storage.Source;
import org.opencast.assetmanager.impl.storage.StoragePath;
import org.opencast.mediapackage.MediaPackage;
import org.opencast.mediapackage.MediaPackageBuilderFactory;
import org.opencast.mediapackage.MediaPackageElements;
import org.opencast.mediapackage.attachment.AttachmentImpl;
import org.opencast.mediapackage.identifier.IdImpl;
import org.opencast.security.api.DefaultOrganization;
import org.opencast.util.IoSupport;
import org.opencast.util.NotFoundException;
import org.opencast.util.UrlSupport;
import org.opencast.util.data.Option;
import org.opencast.util.persistencefn.PersistenceEnv;
import org.opencast.util.persistencefn.PersistenceEnvs;
import org.opencast.util.persistencefn.PersistenceUtil;
import org.opencast.workflow.api.WorkflowDatabaseException;
import org.opencast.workflow.api.WorkflowDefinition;
import org.opencast.workflow.api.WorkflowDefinitionImpl;
import org.opencast.workflow.api.WorkflowInstanceImpl;
import org.opencast.workflow.api.WorkflowService;
import org.opencast.workspace.api.Workspace;

import com.entwinemedia.fn.data.Opt;

import org.apache.commons.io.FileUtils;
import org.junit.Ignore;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Arrays;
import java.util.Map;

import javax.persistence.EntityManagerFactory;
import javax.ws.rs.Path;

@Path("/")
@Ignore
public class TestTasksEndpoint extends TasksEndpoint {

  private final File baseDir = new File(new File(IoSupport.getSystemTmpDir()), "tasksendpointtest");

  public TestTasksEndpoint() throws Exception {
    WorkflowDefinition wfD = new WorkflowDefinitionImpl();
    wfD.setTitle("Full");
    wfD.setId("full");
    wfD.addTag("ng-archive");

    WorkflowDefinitionImpl wfD2 = new WorkflowDefinitionImpl();
    wfD2.setTitle("Full HTML5");
    wfD2.setId("full-html5");
    wfD2.setDescription("Test description");
    wfD2.setConfigurationPanel("<h2>Test</h2>");
    wfD2.addTag("ng-archive");

    WorkflowDefinitionImpl wfD3 = new WorkflowDefinitionImpl();
    wfD3.setTitle("Hidden");
    wfD3.setId("hidden");

    WorkflowInstanceImpl wI1 = new WorkflowInstanceImpl();
    wI1.setTitle(wfD.getTitle());
    wI1.setTemplate(wfD.getId());
    wI1.setId(5);
    WorkflowInstanceImpl wI2 = new WorkflowInstanceImpl();
    wI2.setTitle(wfD2.getTitle());
    wI2.setTemplate(wfD2.getId());
    wI2.setId(10);

    Workspace workspace = createNiceMock(Workspace.class);
    expect(workspace.get(anyObject(URI.class)))
            .andReturn(new File(getClass().getResource("/processing-properties.xml").toURI())).anyTimes();

    WorkflowService workflowService = createNiceMock(WorkflowService.class);
    expect(workflowService.listAvailableWorkflowDefinitions()).andReturn(Arrays.asList(wfD, wfD2, wfD3)).anyTimes();
    expect(workflowService.getWorkflowDefinitionById("full")).andReturn(wfD).anyTimes();
    expect(workflowService.getWorkflowDefinitionById("exception")).andThrow(new WorkflowDatabaseException()).anyTimes();
    expect(workflowService.start(anyObject(WorkflowDefinition.class), anyObject(MediaPackage.class),
            anyObject(Map.class))).andReturn(wI1);
    expect(workflowService.start(anyObject(WorkflowDefinition.class), anyObject(MediaPackage.class),
            anyObject(Map.class))).andReturn(wI2);
    replay(workspace, workflowService);

    AssetManager assetManager = mkAssetManager(workspace);
    MediaPackage mp1 = MediaPackageBuilderFactory.newInstance().newMediaPackageBuilder().createNew(new IdImpl("id1"));
    AttachmentImpl attachment = new AttachmentImpl();
    attachment.setFlavor(MediaPackageElements.PROCESSING_PROPERTIES);
    mp1.add(attachment);

    MediaPackage mp2 = MediaPackageBuilderFactory.newInstance().newMediaPackageBuilder().createNew(new IdImpl("id2"));
    assetManager.takeSnapshot(AssetManager.DEFAULT_OWNER, mp1);
    assetManager.takeSnapshot(AssetManager.DEFAULT_OWNER, mp2);

    this.setWorkflowService(workflowService);
    this.setAssetManager(assetManager);
    this.setWorkspace(workspace);
    this.activate(null);
  }

  AssetManager mkAssetManager(final Workspace workspace) throws Exception {
    final PersistenceEnv penv = PersistenceEnvs.mk(mkEntityManagerFactory("org.opencast.assetmanager.impl"));
    final Database db = new Database(penv);
    return new AbstractAssetManager() {
      @Override
      public HttpAssetProvider getHttpAssetProvider() {
        // identity provider
        return new HttpAssetProvider() {
          @Override
          public Snapshot prepareForDelivery(Snapshot snapshot) {
            return snapshot;
          }
        };
      }

      @Override
      public Database getDb() {
        return db;
      }

      @Override
      protected Workspace getWorkspace() {
        return workspace;
      }

      @Override
      public AssetStore getAssetStore() {
        return mkAssetStore(workspace);
      }

      @Override
      protected String getCurrentOrgId() {
        return DefaultOrganization.DEFAULT_ORGANIZATION_ID;
      }
    };
  }

  AssetStore mkAssetStore(final Workspace workspace) {
    return new AssetStore() {

      @Override
      public Option<Long> getUsedSpace() {
        return Option.none();
      }

      @Override
      public Option<Long> getUsableSpace() {
        return Option.none();
      }

      @Override
      public Option<Long> getTotalSpace() {
        return Option.none();
      }

      @Override
      public void put(StoragePath path, Source source) throws AssetStoreException {
        File destFile = new File(baseDir, UrlSupport.concat(path.getMediaPackageId(), path.getMediaPackageElementId(),
                path.getVersion().toString()));
        try {
          FileUtils.copyFile(workspace.get(source.getUri()), destFile);
        } catch (IOException e) {
          throw new RuntimeException(e);
        } catch (NotFoundException e) {
          throw new RuntimeException(e);
        }
      }

      @Override
      public Opt<InputStream> get(StoragePath path) throws AssetStoreException {
        File file = new File(baseDir, UrlSupport.concat(path.getMediaPackageId(), path.getMediaPackageElementId(),
                path.getVersion().toString()));
        InputStream inputStream;
        try {
          inputStream = new ByteArrayInputStream(FileUtils.readFileToByteArray(file));
          return Opt.some(inputStream);
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      }

      @Override
      public boolean delete(DeletionSelector sel) throws AssetStoreException {
        return false;
      }

      @Override
      public boolean copy(StoragePath from, StoragePath to) throws AssetStoreException {
        File file = new File(baseDir, UrlSupport.concat(from.getMediaPackageId(), from.getMediaPackageElementId(),
                from.getVersion().toString()));
        File destFile = new File(baseDir,
                UrlSupport.concat(to.getMediaPackageId(), to.getMediaPackageElementId(), to.getVersion().toString()));
        try {
          FileUtils.copyFile(file, destFile);
          return true;
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      }

      @Override
      public boolean contains(StoragePath path) throws AssetStoreException {
        return false;
      }
    };
  }

  static EntityManagerFactory mkEntityManagerFactory(String persistenceUnit) {
    if ("mysql".equals(System.getProperty("useDatabase"))) {
      return mkMySqlEntityManagerFactory(persistenceUnit);
    } else {
      return mkH2EntityManagerFactory(persistenceUnit);
    }
  }

  static EntityManagerFactory mkH2EntityManagerFactory(String persistenceUnit) {
    return PersistenceUtil.mkTestEntityManagerFactory(persistenceUnit, true);
  }

  static EntityManagerFactory mkMySqlEntityManagerFactory(String persistenceUnit) {
    return PersistenceUtil.mkEntityManagerFactory(persistenceUnit, "MySQL", "com.mysql.jdbc.Driver",
            "jdbc:mysql://localhost/test_scheduler", "matterhorn", "matterhorn",
            org.opencast.util.data.Collections.map(tuple("eclipselink.ddl-generation", "drop-and-create-tables"),
                    tuple("eclipselink.ddl-generation.output-mode", "database"),
                    tuple("eclipselink.logging.level.sql", "FINE"), tuple("eclipselink.logging.parameters", "true")),
            PersistenceUtil.mkTestPersistenceProvider());
  }

}
