CREATE TABLE oc_aws_asset_mapping (
  id BIGINT(20) NOT NULL,
  media_package_element VARCHAR(128) NOT NULL,
  media_package VARCHAR(128) NOT NULL,
  version BIGINT(20) NOT NULL,
  organization VARCHAR(128) NOT NULL,
  deletion_date datetime DEFAULT NULL,
  object_key VARCHAR(1024) NOT NULL,
  object_version VARCHAR(1024) NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT UNQ_aws_archive_mapping_0 UNIQUE (organization, media_package, media_package_element, version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

CREATE TABLE oc_aws_cache_mapping (
  id BIGINT(20) NOT NULL,
  media_package_element VARCHAR(128) NOT NULL,
  media_package VARCHAR(128) NOT NULL,
  version BIGINT(20) NOT NULL,
  organization VARCHAR(128) NOT NULL,
  cached_on datetime DEFAULT NULL,
  PRIMARY KEY (id),
  CONSTRAINT UNQ_aws_archive_mapping_0 UNIQUE (organization, media_package, media_package_element, version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;
