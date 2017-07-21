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

package org.opencast.userdirectory;

import org.opencast.security.api.JaxbOrganization;
import org.opencast.security.api.JaxbRole;
import org.opencast.security.api.Organization;
import org.opencast.security.api.Role;
import org.opencast.security.api.RoleProvider;
import org.opencast.security.api.SecurityService;
import org.opencast.security.api.User;
import org.opencast.security.api.UserDirectoryService;
import org.opencast.security.api.UserProvider;

import com.google.common.base.CharMatcher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;

/**
 * The user id role provider assigns the user id role.
 */
public class UserIdRoleProvider implements RoleProvider {

  private static final String ROLE_USER_PREFIX = "ROLE_USER_";
  private static final String ROLE_USER = "ROLE_USER";

  private static final CharMatcher SAFE_USERNAME = CharMatcher.inRange('a', 'z').or(CharMatcher.inRange('A', 'Z'))
          .or(CharMatcher.inRange('0', '9')).negate().precomputed();

  /** The logger */
  private static final Logger logger = LoggerFactory.getLogger(UserIdRoleProvider.class);

  /** The security service */
  protected SecurityService securityService = null;

  /** The user directory service */
  protected UserDirectoryService userDirectoryService = null;

  /**
   * @param securityService
   *          the securityService to set
   */
  public void setSecurityService(SecurityService securityService) {
    this.securityService = securityService;
  }

  /**
   * Sets the user directory service
   *
   * @param userDirectoryService
   *          the userDirectoryService to set
   */
  public void setUserDirectoryService(UserDirectoryService userDirectoryService) {
    this.userDirectoryService = userDirectoryService;
  }

  public static final String getUserIdRole(String userName) {
    String safeUserName = SAFE_USERNAME.replaceFrom(userName, "_");
    return ROLE_USER_PREFIX.concat(safeUserName.toUpperCase());
  }

  /**
   * @see org.opencast.security.api.RoleProvider#getRoles()
   */
  @Override
  public Iterator<Role> getRoles() {
    List<Role> roles = getRolesForUser(securityService.getUser().getUsername());
    return roles.iterator();
  }

  /**
   * @see org.opencast.security.api.RoleProvider#getRolesForUser(String)
   */
  @Override
  public List<Role> getRolesForUser(String userName) {
    Organization organization = securityService.getOrganization();
    List<Role> roles = new ArrayList<Role>();
    roles.add(new JaxbRole(getUserIdRole(userName), JaxbOrganization.fromOrganization(organization), "The user id role", Role.Type.SYSTEM));
    roles.add(new JaxbRole(ROLE_USER, JaxbOrganization.fromOrganization(organization), "The authenticated user role", Role.Type.SYSTEM));
    return Collections.unmodifiableList(roles);
  }

  /**
   * @see org.opencast.security.api.RoleProvider#getOrganization()
   */
  @Override
  public String getOrganization() {
    return UserProvider.ALL_ORGANIZATIONS;
  }

  /**
   * @see org.opencast.security.api.RoleProvider#findRoles(String,Role.Target, int, int)
   */
  @Override
  public Iterator<Role> findRoles(String query, Role.Target target, int offset, int limit) {
    if (query == null)
      throw new IllegalArgumentException("Query must be set");

    // These roles are not meaningful for users/groups
    if (target == Role.Target.USER) {
      return Collections.emptyIterator();
    }

    logger.debug("findRoles(query={} offset={} limit={})", query, offset, limit);

    HashSet<Role> foundRoles = new HashSet<Role>();
    Organization organization = securityService.getOrganization();

    // Return authenticated user role if it matches the query pattern
    if (like(ROLE_USER, query)) {
      foundRoles.add(new JaxbRole(ROLE_USER, JaxbOrganization.fromOrganization(organization), "The authenticated user role", Role.Type.SYSTEM));
    }

    // Include user id roles only if wildcard search or query matches user id role prefix
    // (iterating through users may be slow)
    if (!"%".equals(query) && !query.startsWith(ROLE_USER_PREFIX)) {
      return foundRoles.iterator();
    }

    String userQuery = "%";
    if (query.startsWith(ROLE_USER_PREFIX)) {
      userQuery = query.substring(ROLE_USER_PREFIX.length());
    }

    Iterator<User> users = userDirectoryService.findUsers(userQuery, offset, limit);
    while (users.hasNext()) {
      User u = users.next();
      // We exclude the digest user, but then add the global ROLE_USER above
      if (!"system".equals(u.getProvider())) {
        foundRoles.add(new JaxbRole(getUserIdRole(u.getUsername()), JaxbOrganization.fromOrganization(u.getOrganization()), "User id role", Role.Type.SYSTEM));
      }
    }

    return foundRoles.iterator();
  }

  private static boolean like(String string, final String query) {
    String regex = query.replace("_", ".").replace("%", ".*?");
    Pattern p = Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    return p.matcher(string).matches();
  }

}
