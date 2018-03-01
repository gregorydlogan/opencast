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

package org.opencastproject.archive.aws.persistence;

import org.opencastproject.assetmanager.impl.VersionImpl;
import org.opencastproject.assetmanager.impl.storage.StoragePath;

import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

import javax.persistence.EntityManagerFactory;

public class AwsArchiveDatabaseImpl implements AwsArchiveDatabase {

  /** Logging utilities */
  private static final Logger logger = LoggerFactory.getLogger(AwsArchiveDatabaseImpl.class);

  public static final String PERSISTENCE_UNIT = "org.opencastproject.archive.aws.persistence";

  /** Factory used to create {@link javax.persistence.EntityManager}s for transactions */
  protected EntityManagerFactory emf;

  /** OSGi callback. */
  public void activate(ComponentContext cc) {
    logger.info("Activating AWS S3 archive");
  }

  /** OSGi callback. Closes entity manager factory. */
  public void deactivate(ComponentContext cc) {
    emf.close();
  }

  /** OSGi DI */
  public void setEntityManagerFactory(EntityManagerFactory emf) {
    this.emf = emf;
  }

  @Override
  public AwsArchiveMapping storeMapping(StoragePath path, String objectKey, String objectVersion)
          throws AwsArchiveDatabaseException {
    AwsArchiveMappingDto dto = AwsArchiveMappingDto.storeMapping(emf.createEntityManager(), path, objectKey,
            objectVersion);
    if (dto != null)
      return dto.toAWSArchiveMapping();
    return null;
  }

  @Override
  public void deleteMapping(StoragePath path) throws AwsArchiveDatabaseException {
    AwsArchiveMappingDto.deleteMappping(emf.createEntityManager(), path);
  }

  @Override
  public AwsArchiveMapping findMapping(StoragePath path) throws AwsArchiveDatabaseException {
    AwsArchiveMappingDto dto = AwsArchiveMappingDto.findMapping(emf.createEntityManager(), path);
    if (dto != null)
      return dto.toAWSArchiveMapping();
    return null;
  }

  @Override
  public List<AwsArchiveMapping> findMappingsByKey(String objectKey) throws AwsArchiveDatabaseException {
    List<AwsArchiveMappingDto> list = AwsArchiveMappingDto.findMappingsByKey(emf.createEntityManager(), objectKey);
    List<AwsArchiveMapping> resultList = new ArrayList<AwsArchiveMapping>();
    for (AwsArchiveMappingDto dto : list) {
      resultList.add(dto.toAWSArchiveMapping());
    }
    return resultList;
  }

  @Override
  public List<AwsArchiveMapping> findMappingsByMediaPackageAndVersion(StoragePath path)
          throws AwsArchiveDatabaseException {
    List<AwsArchiveMappingDto> list = AwsArchiveMappingDto.findMappingsByMediaPackageAndVersion(
            emf.createEntityManager(), path);
    List<AwsArchiveMapping> resultList = new ArrayList<AwsArchiveMapping>();
    for (AwsArchiveMappingDto dto : list) {
      resultList.add(dto.toAWSArchiveMapping());
    }
    return resultList;
  }

  @Override
  public List<AwsArchiveMapping> findAllByMediaPackage(String mpId) throws AwsArchiveDatabaseException {
    List<AwsArchiveMappingDto> list = AwsArchiveMappingDto.findMappingsByMediaPackage(emf.createEntityManager(),
            mpId);
    List<AwsArchiveMapping> resultList = new ArrayList<AwsArchiveMapping>();
    for (AwsArchiveMappingDto dto : list) {
      resultList.add(dto.toAWSArchiveMapping());
    }
    return resultList;
  }

  public void addLocallyCachedFile(StoragePath path) throws AwsArchiveDatabaseException {
    AwsGlacierCacheMappingDto.storeMapping(emf.createEntityManager(), path);
  }

  public List<StoragePath> getLocallyCachedFiles(Date expireEarlierThan) throws AwsArchiveDatabaseException {
    List<AwsGlacierCacheMappingDto> list = AwsGlacierCacheMappingDto.findMapping(emf.createEntityManager(), expireEarlierThan);
    List<StoragePath> results = new LinkedList<>();
    for (AwsGlacierCacheMappingDto dto : list) {
      AwsArchiveMapping map = dto.toAWSArchiveMapping();
      results.add(new StoragePath(map.getOrganizationId(), map.getMediaPackageId(), new VersionImpl(map.getVersion()), map.getMediaPackageElementId()));
    }
    return results;
  }

  public boolean isLocallyCached(StoragePath path) {
    try {
      AwsGlacierCacheMappingDto map = AwsGlacierCacheMappingDto.findMapping(emf.createEntityManager(), path);
      if (null != map) {
        return true;
      }
    } catch (AwsArchiveDatabaseException e) {
      logger.error("Database error attempting to resolve path " + path, e);
    }
    return false;
  }

  public void deleteCacheMapping(StoragePath path) throws AwsArchiveDatabaseException {
    AwsGlacierCacheMappingDto.deleteMapping(emf.createEntityManager(), path);
  }

}
