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

import org.opencast.adminui.util.TestServiceRegistryFactory;
import org.opencast.index.service.resources.list.api.ResourceListProvider;
import org.opencast.index.service.resources.list.api.ResourceListQuery;
import org.opencast.index.service.resources.list.impl.ListProvidersServiceImpl;
import org.opencast.index.service.resources.list.provider.ServersListProvider;
import org.opencast.index.service.util.ListProviderUtil;
import org.opencast.security.api.Organization;
import org.opencast.security.api.SecurityService;

import org.easymock.EasyMock;
import org.junit.Ignore;

import java.util.HashMap;
import java.util.Map;

import javax.ws.rs.Path;

@Path("/")
@Ignore
public class TestListProvidersEndpoint extends ListProvidersEndpoint {

  public static final String PROVIDER_NAME = "test";
  public static final String[] PROVIDER_VALUES = { "x", "a", "c", "z", "t", "h" };
  private final Map<String, String> baseMap = new HashMap<String, String>();

  private ListProvidersServiceImpl listProvidersService = new ListProvidersServiceImpl();
  private SecurityService securityService;

  public TestListProvidersEndpoint() {
    this.securityService = EasyMock.createNiceMock(SecurityService.class);
    EasyMock.expect(securityService.getOrganization()).andReturn(null);

    for (int i = 0; i < PROVIDER_VALUES.length; i++) {
      baseMap.put(Integer.toString(i), PROVIDER_VALUES[i]);
    }

    listProvidersService.addProvider(new ResourceListProvider() {
      @Override
      public String[] getListNames() {
        return new String[] { PROVIDER_NAME };
      }

      @Override
      public Map<String, String> getList(String listName, ResourceListQuery query, Organization organization) {
        return ListProviderUtil.filterMap(baseMap, query);
      }
    });

    listProvidersService.addProvider(makeServicesListProvider());

    this.setSecurityService(securityService);
    this.setListProvidersService(listProvidersService);
    this.activate(null);
  }

  private ResourceListProvider makeServicesListProvider() {
    ServersListProvider serversListProvider = new ServersListProvider();
    serversListProvider.setServiceRegistry(TestServiceRegistryFactory.getStub());
    return serversListProvider;
  }
}
