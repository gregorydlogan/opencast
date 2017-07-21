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
package org.opencast.assetmanager.impl;

import org.opencast.assetmanager.api.Asset;
import org.opencast.assetmanager.api.AssetManager;
import org.opencast.assetmanager.api.Availability;
import org.opencast.assetmanager.api.Property;
import org.opencast.assetmanager.api.Snapshot;
import org.opencast.assetmanager.api.Version;
import org.opencast.assetmanager.api.query.AQueryBuilder;
import org.opencast.assetmanager.impl.persistence.Database;
import org.opencast.assetmanager.impl.storage.AssetStore;
import org.opencast.mediapackage.MediaPackage;
import org.opencast.message.broker.api.MessageReceiver;
import org.opencast.message.broker.api.MessageSender;
import org.opencast.security.api.AuthorizationService;
import org.opencast.security.api.OrganizationDirectoryService;
import org.opencast.security.api.SecurityService;
import org.opencast.security.util.SecurityUtil;
import org.opencast.util.persistencefn.PersistenceEnvs;
import org.opencast.workspace.api.Workspace;

import com.entwinemedia.fn.data.Opt;

import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.persistence.EntityManagerFactory;

/**
 * Ties the asset manager to the OSGi environment.
 * <p>
 * Composes the core asset manager with the {@link AssetManagerWithMessaging} and {@link AssetManagerWithSecurity}
 * implementations.
 */
public class OsgiAssetManager implements AssetManager {
  /** Log facility */
  private static final Logger logger = LoggerFactory.getLogger(OsgiAssetManager.class);

  private SecurityService secSvc;
  private AuthorizationService authSvc;
  private OrganizationDirectoryService orgDir;
  private Workspace workspace;
  private AssetStore assetStore;
  private HttpAssetProvider httpAssetProvider;
  private MessageSender messageSender;
  private MessageReceiver messageReceiver;
  private EntityManagerFactory emf;

  // collect all objects that need to be closed on service deactivation
  private AutoCloseable toClose;

  private AssetManager delegate;

  /** OSGi callback. */
  public void activate(ComponentContext cc) {
    logger.info("Activating AssetManager");
    final Database db = new Database(PersistenceEnvs.mk(emf));
    final String systemUserName = SecurityUtil.getSystemUserName(cc);
    // create the core asset manager
    final AssetManager core = new AbstractAssetManager() {
      @Override
      public Database getDb() {
        return db;
      }

      @Override
      public HttpAssetProvider getHttpAssetProvider() {
        return httpAssetProvider;
      }

      @Override
      public AssetStore getAssetStore() {
        return assetStore;
      }

      @Override
      protected Workspace getWorkspace() {
        return workspace;
      }

      @Override
      protected String getCurrentOrgId() {
        return secSvc.getOrganization().getId();
      }
    };
    // compose with ActiveMQ messaging
    final AssetManagerWithMessaging withMessaging = new AssetManagerWithMessaging(
            core,
            messageSender,
            messageReceiver,
            authSvc,
            orgDir,
            secSvc,
            workspace,
            systemUserName);
    // compose with security
    delegate = new AssetManagerWithSecurity(withMessaging, authSvc, secSvc);
    // collect all objects that need to be closed
    toClose = new AutoCloseable() {
      @Override
      public void close() throws Exception {
        withMessaging.close();
      }
    };
  }

  /** OSGi callback. Close the database. */
  public void deactivate(ComponentContext cc) throws Exception {
    toClose.close();
  }

  //
  // AssetManager impl
  //

  @Override
  public Snapshot takeSnapshot(String owner, MediaPackage mp) {
    return delegate.takeSnapshot(owner, mp);
  }

  @Override
  public Opt<Asset> getAsset(Version version, String mpId, String mpeId) {
    return delegate.getAsset(version, mpId, mpeId);
  }

  @Override
  public void setAvailability(Version version, String mpId, Availability availability) {
    delegate.setAvailability(version, mpId, availability);
  }

  @Override
  public boolean setProperty(Property property) {
    return delegate.setProperty(property);
  }

  @Override
  public AQueryBuilder createQuery() {
    return delegate.createQuery();
  }

  @Override
  public Opt<Version> toVersion(String version) {
    return delegate.toVersion(version);
  }

  //
  // OSGi depedency injection
  //

  public void setEntityManagerFactory(EntityManagerFactory emf) {
    this.emf = emf;
  }

  public void setSecurityService(SecurityService securityService) {
    this.secSvc = securityService;
  }

  public void setAuthSvc(AuthorizationService authSvc) {
    this.authSvc = authSvc;
  }

  public void setOrgDir(OrganizationDirectoryService orgDir) {
    this.orgDir = orgDir;
  }

  public void setWorkspace(Workspace workspace) {
    this.workspace = workspace;
  }

  public void setAssetStore(AssetStore assetStore) {
    this.assetStore = assetStore;
  }

  public void setHttpAssetProvider(HttpAssetProvider httpAssetProvider) {
    this.httpAssetProvider = httpAssetProvider;
  }

  public void setMessageSender(MessageSender messageSender) {
    this.messageSender = messageSender;
  }

  public void setMessageReceiver(MessageReceiver messageReceiver) {
    this.messageReceiver = messageReceiver;
  }
}
