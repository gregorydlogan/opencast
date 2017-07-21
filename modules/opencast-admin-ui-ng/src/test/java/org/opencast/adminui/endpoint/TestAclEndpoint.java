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

import org.opencast.authorization.xacml.manager.api.AclService;
import org.opencast.authorization.xacml.manager.api.AclServiceFactory;
import org.opencast.authorization.xacml.manager.api.ManagedAcl;
import org.opencast.authorization.xacml.manager.impl.ManagedAclImpl;
import org.opencast.security.api.AccessControlEntry;
import org.opencast.security.api.AccessControlList;
import org.opencast.security.api.DefaultOrganization;
import org.opencast.security.api.Organization;
import org.opencast.security.api.SecurityService;
import org.opencast.util.data.Option;

import org.easymock.EasyMock;
import org.junit.Ignore;

import java.util.ArrayList;
import java.util.List;

import javax.ws.rs.Path;

@Path("/")
@Ignore
public class TestAclEndpoint extends AclEndpoint {

  public TestAclEndpoint() throws Exception {
    setupServices();
    this.activate(null);
  }

  private void setupServices() {
    final DefaultOrganization org = new DefaultOrganization();

    AccessControlEntry ace1 = new AccessControlEntry("ROLE_ADMIN", "read", true);
    AccessControlEntry ace2 = new AccessControlEntry("ROLE_ANONYMOUS", "read", true);
    AccessControlEntry ace3 = new AccessControlEntry("ROLE_ADMIN", "read", false);
    AccessControlEntry ace4 = new AccessControlEntry("ROLE_ANONYMOUS", "read", false);

    AccessControlList publicAcl = new AccessControlList(ace1, ace2);
    AccessControlList privateAcl = new AccessControlList(ace3, ace4);

    List<ManagedAcl> managedAcls = new ArrayList<ManagedAcl>();
    managedAcls.add(new ManagedAclImpl(1L, "public", org.getId(), publicAcl));
    managedAcls.add(new ManagedAclImpl(2L, "private", org.getId(), privateAcl));

    AclService aclService = EasyMock.createNiceMock(AclService.class);
    EasyMock.expect(aclService.getAcls()).andReturn(managedAcls).anyTimes();
    EasyMock.expect(aclService.getAcl(EasyMock.anyLong())).andReturn(Option.some(managedAcls.get(0))).anyTimes();
    EasyMock.replay(aclService);

    AclServiceFactory aclServiceFactory = EasyMock.createNiceMock(AclServiceFactory.class);
    EasyMock.expect(aclServiceFactory.serviceFor(EasyMock.anyObject(Organization.class))).andReturn(aclService)
            .anyTimes();
    EasyMock.replay(aclServiceFactory);

    SecurityService securityService = EasyMock.createNiceMock(SecurityService.class);
    EasyMock.expect(securityService.getOrganization()).andReturn(org).anyTimes();
    EasyMock.replay(securityService);

    this.setAclServiceFactory(aclServiceFactory);
    this.setSecurityService(securityService);
  }
}
