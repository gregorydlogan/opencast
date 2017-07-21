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

package org.opencast.authorization.xacml.manager.endpoint;

import static com.entwinemedia.fn.Stream.$;
import static org.opencast.rest.RestServiceTestEnv.localhostRandomPort;
import static org.opencast.util.persistence.PersistenceEnvs.persistenceEnvironment;
import static org.opencast.util.persistence.PersistenceUtil.newTestEntityManagerFactory;

import org.opencast.assetmanager.api.AssetManager;
import org.opencast.assetmanager.api.Snapshot;
import org.opencast.assetmanager.api.query.AQueryBuilder;
import org.opencast.assetmanager.api.query.ARecord;
import org.opencast.assetmanager.api.query.AResult;
import org.opencast.assetmanager.api.query.ASelectQuery;
import org.opencast.assetmanager.api.query.Predicate;
import org.opencast.assetmanager.api.query.Target;
import org.opencast.assetmanager.api.query.VersionField;
import org.opencast.authorization.xacml.manager.api.AclService;
import org.opencast.authorization.xacml.manager.api.AclServiceFactory;
import org.opencast.authorization.xacml.manager.impl.AclDb;
import org.opencast.authorization.xacml.manager.impl.AclServiceImpl;
import org.opencast.authorization.xacml.manager.impl.AclTransitionDb;
import org.opencast.authorization.xacml.manager.impl.persistence.JpaAclDb;
import org.opencast.authorization.xacml.manager.impl.persistence.OsgiJpaAclTransitionDb;
import org.opencast.distribution.download.DownloadDistributionServiceImpl;
import org.opencast.mediapackage.Attachment;
import org.opencast.mediapackage.MediaPackage;
import org.opencast.mediapackage.MediaPackageBuilderImpl;
import org.opencast.mediapackage.MediaPackageException;
import org.opencast.mediapackage.attachment.AttachmentImpl;
import org.opencast.message.broker.api.MessageSender;
import org.opencast.security.api.AccessControlList;
import org.opencast.security.api.AclScope;
import org.opencast.security.api.AuthorizationService;
import org.opencast.security.api.DefaultOrganization;
import org.opencast.security.api.JaxbRole;
import org.opencast.security.api.JaxbUser;
import org.opencast.security.api.Organization;
import org.opencast.security.api.SecurityConstants;
import org.opencast.security.api.SecurityService;
import org.opencast.security.api.User;
import org.opencast.series.api.SeriesService;
import org.opencast.util.NotFoundException;
import org.opencast.util.data.Tuple;
import org.opencast.workflow.api.WorkflowService;
import org.opencast.workspace.api.Workspace;

import com.entwinemedia.fn.data.Opt;

import org.easymock.EasyMock;
import org.junit.Ignore;

import java.net.URL;

import javax.persistence.EntityManagerFactory;
import javax.ws.rs.Path;

// use base path /test to prevent conflicts with the production service
@Path("/test")
// put @Ignore here to prevent maven surefire from complaining about missing test methods
@Ignore
public class TestRestService extends AbstractAclServiceRestEndpoint {

  public static final URL BASE_URL = localhostRandomPort();

  // Declare this dependency static since the TestRestService gets instantiated multiple times.
  // Haven't found out who's responsible for this but that's the way it is.
  public static final AclServiceFactory aclServiceFactory;
  public static final SecurityService securityService;
  public static final SeriesService seriesService;
  public static final AuthorizationService authorizationService;
  public static final AssetManager assetManager;
  public static final DownloadDistributionServiceImpl distributionService = new DownloadDistributionServiceImpl();
  public static final MessageSender messageSender;
  public static final Workspace workspace;
  public static final EntityManagerFactory authorizationEMF = newTestEntityManagerFactory(
          OsgiJpaAclTransitionDb.PERSISTENCE_UNIT);

  static {
    SecurityService testSecurityService = EasyMock.createNiceMock(SecurityService.class);
    User user = new JaxbUser("admin", "test", new DefaultOrganization(),
            new JaxbRole(SecurityConstants.GLOBAL_ADMIN_ROLE, new DefaultOrganization()));
    EasyMock.expect(testSecurityService.getOrganization()).andReturn(new DefaultOrganization()).anyTimes();
    EasyMock.expect(testSecurityService.getUser()).andReturn(user).anyTimes();
    EasyMock.replay(testSecurityService);
    securityService = testSecurityService;
    authorizationService = newAuthorizationService();
    seriesService = newSeriesService();
    assetManager = newAssetManager();
    messageSender = newMessageSender();
    workspace = newWorkspace();
    aclServiceFactory = new AclServiceFactory() {
      @Override
      public AclService serviceFor(Organization org) {
        return new AclServiceImpl(new DefaultOrganization(), newAclPersistence(), newTransitionPersistence(),
                seriesService, assetManager, newWorkflowService(), authorizationService, messageSender, workspace);
      }
    };
  }

  @Override
  protected AclServiceFactory getAclServiceFactory() {
    return aclServiceFactory;
  }

  @Override
  protected SecurityService getSecurityService() {
    return securityService;
  }

  @Override
  protected AuthorizationService getAuthorizationService() {
    return authorizationService;
  }

  @Override
  protected AssetManager getAssetManager() {
    return assetManager;
  }

  @Override
  protected SeriesService getSeriesService() {
    return seriesService;
  }

  private static MessageSender newMessageSender() {
    return EasyMock.createNiceMock(MessageSender.class);
  }

  private static WorkflowService newWorkflowService() {
    return EasyMock.createNiceMock(WorkflowService.class);
  }

  private static Workspace newWorkspace() {
    return EasyMock.createNiceMock(Workspace.class);
  }

  private static AuthorizationService newAuthorizationService() {
    AccessControlList acl = new AccessControlList();
    Attachment attachment = new AttachmentImpl();
    MediaPackage mediapackage;
    try {
      mediapackage = new MediaPackageBuilderImpl().createNew();
    } catch (MediaPackageException e) {
      throw new RuntimeException(e);
    }
    AuthorizationService authorizationService = EasyMock.createNiceMock(AuthorizationService.class);
    EasyMock.expect(authorizationService.getActiveAcl((MediaPackage) EasyMock.anyObject()))
            .andReturn(Tuple.tuple(acl, AclScope.Series)).anyTimes();
    EasyMock.expect(authorizationService.setAcl((MediaPackage) EasyMock.anyObject(), (AclScope) EasyMock.anyObject(),
            (AccessControlList) EasyMock.anyObject())).andReturn(Tuple.tuple(mediapackage, attachment));
    EasyMock.replay(authorizationService);

    return authorizationService;
  }

  private static AssetManager newAssetManager() {
    Snapshot snapshot = EasyMock.createNiceMock(Snapshot.class);
    try {
      EasyMock.expect(snapshot.getMediaPackage()).andReturn(new MediaPackageBuilderImpl().createNew()).anyTimes();
    } catch (MediaPackageException e) {
      throw new RuntimeException(e);
    }
    ARecord record = EasyMock.createNiceMock(ARecord.class);
    EasyMock.expect(record.getSnapshot()).andReturn(Opt.some(snapshot)).anyTimes();

    AResult result = EasyMock.createNiceMock(AResult.class);
    EasyMock.expect(result.getRecords()).andReturn($(record)).anyTimes();

    ASelectQuery select = EasyMock.createNiceMock(ASelectQuery.class);
    EasyMock.expect(select.where(EasyMock.anyObject(Predicate.class))).andReturn(select).anyTimes();
    EasyMock.expect(select.run()).andReturn(result).anyTimes();

    Predicate predicate = EasyMock.createNiceMock(Predicate.class);
    EasyMock.expect(predicate.and(EasyMock.anyObject(Predicate.class))).andReturn(predicate).anyTimes();

    AQueryBuilder query = EasyMock.createNiceMock(AQueryBuilder.class);

    VersionField version = EasyMock.createNiceMock(VersionField.class);

    EasyMock.expect(query.version()).andReturn(version).anyTimes();
    EasyMock.expect(query.mediaPackageId(EasyMock.anyString())).andReturn(predicate).anyTimes();
    EasyMock.expect(query.select(EasyMock.anyObject(Target.class))).andReturn(select).anyTimes();

    AssetManager assetManager = EasyMock.createNiceMock(AssetManager.class);
    EasyMock.expect(assetManager.createQuery()).andReturn(query).anyTimes();
    EasyMock.replay(assetManager, version, query, predicate, select, result, record, snapshot);
    return assetManager;
  }

  private static AclTransitionDb newTransitionPersistence() {
    OsgiJpaAclTransitionDb aclManagerDatabase = new OsgiJpaAclTransitionDb();
    aclManagerDatabase.setEntityManagerFactory(authorizationEMF);
    aclManagerDatabase.activate(null);
    return aclManagerDatabase;
  }

  private static AclDb newAclPersistence() {
    return new JpaAclDb(persistenceEnvironment(authorizationEMF));
  }

  private static SeriesService newSeriesService() {
    AccessControlList acl = new AccessControlList();
    SeriesService seriesService = EasyMock.createNiceMock(SeriesService.class);
    try {
      EasyMock.expect(seriesService.getSeriesAccessControl((String) EasyMock.anyObject())).andReturn(acl).anyTimes();
      EasyMock.expect(seriesService.updateAccessControl((String) EasyMock.anyObject(),
              (AccessControlList) EasyMock.anyObject())).andThrow(new NotFoundException()).andReturn(true);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    EasyMock.replay(seriesService);
    return seriesService;
  }

  @Override
  protected String getEndpointBaseUrl() {
    return BASE_URL.toString();
  }

}
