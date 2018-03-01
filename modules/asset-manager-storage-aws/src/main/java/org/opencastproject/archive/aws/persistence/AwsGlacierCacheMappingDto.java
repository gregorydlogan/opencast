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

import org.opencastproject.assetmanager.impl.storage.StoragePath;

import java.util.Calendar;
import java.util.Date;
import java.util.List;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.NoResultException;
import javax.persistence.Query;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import javax.xml.bind.annotation.XmlAttribute;

@Entity(name = "AwsGlacierCacheMapping")
@Table(name = "oc_aws_cache_mapping")
@NamedQueries({
        @NamedQuery(name = "AwsGlacierCacheMapping.findExpiredItems", query = "SELECT m FROM AwsGlacierCacheMapping m WHERE m.cachedOn <= :expireEarlierThan"),
        @NamedQuery(name = "AwsGlacierCacheMapping.findByPath", query = "SELECT m FROM AwsGlacierCacheMapping m WHERE m.organizationId = :organizationId AND m.mediaPackageId = :mediaPackageId AND m.mediaPackageElementId = :mediaPackageElementId AND m.version = :version")})
public final class AwsGlacierCacheMappingDto {

  @Id
  @GeneratedValue(strategy = GenerationType.AUTO)
  @Column(name = "id", length = 128)
  @XmlAttribute
  private long id;

  @Column(name = "organization", nullable = false, length = 128)
  private String organizationId;

  @Column(name = "media_package", nullable = false, length = 128)
  private String mediaPackageId;

  @Column(name = "media_package_element", nullable = false, length = 128)
  private String mediaPackageElementId;

  @Column(name = "version", nullable = false)
  private Long version;

  @Column(name = "cached_on")
  @Temporal(TemporalType.TIMESTAMP)
  private Date cachedOn;

  public AwsGlacierCacheMappingDto() {
  }

  /** Constructor with all fields. */
  public AwsGlacierCacheMappingDto(String organizationId, String mediaPackageId, String elementId, Long version) {
    this.organizationId = organizationId;
    this.mediaPackageId = mediaPackageId;
    this.mediaPackageElementId = elementId;
    this.version = version;
    this.cachedOn = Calendar.getInstance().getTime();
  }

  /** Convert into business object. */
  public AwsArchiveMapping toAWSArchiveMapping() {
    return new AwsArchiveMapping(organizationId, mediaPackageId, mediaPackageElementId, version, null,
            null, null);
  }

  public static AwsGlacierCacheMappingDto storeMapping(EntityManager em, StoragePath path) throws AwsArchiveDatabaseException {
    AwsGlacierCacheMappingDto mapDto = new AwsGlacierCacheMappingDto(path.getOrganizationId(), path.getMediaPackageId(),
            path.getMediaPackageElementId(), Long.valueOf(path.getVersion().toString()));

    EntityTransaction tx = em.getTransaction();
    try {
      tx.begin();
      em.persist(mapDto);
      tx.commit();
      return mapDto;
    } catch (Exception e) {
      if (tx.isActive()) {
        tx.rollback();
      }
      throw new AwsArchiveDatabaseException(String.format("Could not store mapping for path %s", path), e);
    } finally {
      em.close();
    }
  }

  /** Finds all mappings cached earlier than a given date. Returns null if not found. */
  public static List<AwsGlacierCacheMappingDto> findMapping(EntityManager em, final Date expireEarlierThan)
          throws AwsArchiveDatabaseException {
    Query query = null;
    try {
      query = em.createNamedQuery("AwsGlacierCacheMapping.findExpiredItems");
      if (null != expireEarlierThan) {
        query.setParameter("expireEarlierThan", expireEarlierThan);
      } else {
        query.setParameter("expireEarlierThan", new Date());
      }
      return (List<AwsGlacierCacheMappingDto>) query.getResultList();
    } catch (NoResultException e) {
      return null; // Not found
    } catch (Exception e) {
      throw new AwsArchiveDatabaseException(e);
    }
  }

  /** Find a mapping by its storage path. Returns null if not found. */
  public static AwsGlacierCacheMappingDto findMapping(EntityManager em, final StoragePath path)
          throws AwsArchiveDatabaseException {
    Query query = null;
    try {
      query = em.createNamedQuery("AwsGlacierCacheMapping.findByPath");
      query.setParameter("organizationId", path.getOrganizationId());
      query.setParameter("mediaPackageId", path.getMediaPackageId());
      query.setParameter("mediaPackageElementId", path.getMediaPackageElementId());
      query.setParameter("version", Long.valueOf(path.getVersion().toString()));
      return (AwsGlacierCacheMappingDto) query.getSingleResult();
    } catch (NoResultException e) {
      return null; // Not found
    } catch (Exception e) {
      throw new AwsArchiveDatabaseException(e);
    }
  }

  /**
   * Deletes a mapping.
   */
  public static void deleteMapping(EntityManager em, StoragePath path) throws AwsArchiveDatabaseException {
    AwsGlacierCacheMappingDto mapDto = findMapping(em, path);
    if (mapDto == null)
      return;

    EntityTransaction tx = em.getTransaction();
    try {
      tx.begin();
      em.remove(mapDto);
      tx.commit();
    } catch (Exception e) {
      if (tx.isActive()) {
        tx.rollback();
      }
      throw new AwsArchiveDatabaseException(String.format("Could not store mapping for path %s", path), e);
    } finally {
      em.close();
    }
  }
}
