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

package org.opencast.metadata;

import static org.opencast.util.data.Collections.map;
import static org.opencast.util.data.Option.none;
import static org.opencast.util.data.Option.option;

import org.opencast.mediapackage.MediaPackage;
import org.opencast.metadata.api.MetadataValue;
import org.opencast.metadata.api.StaticMetadata;
import org.opencast.metadata.api.StaticMetadataService;
import org.opencast.metadata.api.util.Interval;
import org.opencast.util.data.Function;
import org.opencast.util.data.NonEmptyList;
import org.opencast.util.data.Option;
import org.opencast.workspace.api.Workspace;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * This service provides {@link org.opencast.metadata.api.StaticMetadata} for a given mediapackage, based on the
 * information in the media package itself.
 *
 * todo unit tests will follow
 */
public class StaticMetadataServiceMediaPackageImpl implements StaticMetadataService {

  private static final Logger logger = LoggerFactory.getLogger(StaticMetadataServiceMediaPackageImpl.class);

  // a low default priority
  protected int priority = 99;

  protected Workspace workspace = null;

  public void setWorkspace(Workspace workspace) {
    this.workspace = workspace;
  }

  public void activate(@SuppressWarnings("rawtypes") Map properties) {
    logger.debug("activate()");
    if (properties != null) {
      String priorityString = (String) properties.get(PRIORITY_KEY);
      if (priorityString != null) {
        try {
          priority = Integer.parseInt(priorityString);
        } catch (NumberFormatException e) {
          logger.warn("Unable to set priority to {}", priorityString);
          throw e;
        }
      }
    }
  }

  /**
   * {@inheritDoc}
   *
   * @see org.opencast.metadata.api.MetadataService#getMetadata(org.opencast.mediapackage.MediaPackage)
   */
  @Override
  public StaticMetadata getMetadata(final MediaPackage mp) {
    return new StaticMetadata() {
      @Override
      public Option<String> getId() {
        return option(mp.getIdentifier().toString());
      }

      @Override
      public Option<Date> getCreated() {
        return none();
      }

      @Override
      public Option<Long> getExtent() {
        return option(mp.getDuration());
      }

      @Override
      public Option<String> getLanguage() {
        return none();
      }

      @Override
      public Option<String> getIsPartOf() {
        return option(mp.getSeries());
      }

      @Override
      public Option<String> getReplaces() {
        return none();
      }

      @Override
      public Option<String> getType() {
        return none();
      }

      @Override
      public Option<Interval> getAvailable() {
        return none();
      }

      @Override
      public Option<Date[]> getTemporalPeriod() {
        return none();
      }

      @Override
      public Option<Date> getTemporalInstant() {
        return none();
      }

      @Override
      public Option<Long> getTemporalDuration() {
        return none();
      }

      @Override
      public NonEmptyList<MetadataValue<String>> getTitles() {
        if (mp.getTitle() != null)
          return new NonEmptyList<MetadataValue<String>>(new MetadataValue(mp.getTitle(), "title"));
        else
          throw new IllegalArgumentException("MediaPackage " + mp + " does not contain a title");
      }

      @Override
      public List<MetadataValue<String>> getSubjects() {
        return Collections.emptyList();
      }

      @Override
      public List<MetadataValue<String>> getCreators() {
        return strings2MetadataValues(mp.getCreators(), "creator");
      }

      @Override
      public List<MetadataValue<String>> getPublishers() {
        return Collections.emptyList();
      }

      @Override
      public List<MetadataValue<String>> getContributors() {
        return strings2MetadataValues(mp.getContributors(), "contributor");
      }

      @Override
      public List<MetadataValue<String>> getDescription() {
        return Collections.emptyList();
      }

      @Override
      public List<MetadataValue<String>> getRightsHolders() {
        return Collections.emptyList();
      }

      @Override
      public List<MetadataValue<String>> getSpatials() {
        return Collections.emptyList();
      }

      @Override
      public List<MetadataValue<String>> getAccessRights() {
        return Collections.emptyList();
      }

      @Override
      public List<MetadataValue<String>> getLicenses() {
        if (mp.getLicense() != null)
          return Arrays.asList(new MetadataValue<String>(mp.getLicense(), "license"));
        else
          return Collections.emptyList();
      }
    };
  }

  /**
   * {@inheritDoc}
   *
   * @see org.opencast.metadata.api.MetadataService#getPriority()
   */
  @Override
  public int getPriority() {
    return priority;
  }

  /**
   * @param values
   *          may be null
   * @param valueName
   *          the name of the returned {@link MetadataValue}
   */
  private static List<MetadataValue<String>> strings2MetadataValues(final String[] values, final String valueName) {
    if (values != null) {
      return map(Arrays.asList(values), new ArrayList<MetadataValue<String>>(),
              new Function<String, MetadataValue<String>>() {
                @Override
                public MetadataValue<String> apply(String s) {
                  return new MetadataValue<String>(s, valueName);
                }
              });
    } else {
      return Collections.emptyList();
    }

  }

}
