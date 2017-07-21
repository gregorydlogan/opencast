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
package org.opencast.workflow.handler.assetmanager;

import static org.opencast.assetmanager.api.AssetManager.DEFAULT_OWNER;
import static org.opencast.util.data.Collections.smap;
import static org.opencast.util.data.Tuple.tuple;

import org.opencast.assetmanager.api.AssetManager;
import org.opencast.job.api.JobContext;
import org.opencast.mediapackage.MediaPackage;
import org.opencast.mediapackage.MediaPackageElement;
import org.opencast.mediapackage.MediaPackageException;
import org.opencast.mediapackage.MediaPackageReference;
import org.opencast.mediapackage.Publication;
import org.opencast.mediapackage.selector.SimpleElementSelector;
import org.opencast.workflow.api.AbstractWorkflowOperationHandler;
import org.opencast.workflow.api.WorkflowInstance;
import org.opencast.workflow.api.WorkflowOperationException;
import org.opencast.workflow.api.WorkflowOperationInstance;
import org.opencast.workflow.api.WorkflowOperationResult;
import org.opencast.workflow.api.WorkflowOperationResult.Action;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;

/**
 * Workflow operation for taking a snapshot of a media package.
 *
 * @see AssetManager#takeSnapshot(String, MediaPackage)
 */
public class AssetManagerSnapshotWorkflowOperationHandler extends AbstractWorkflowOperationHandler {
  private static final Logger logger = LoggerFactory.getLogger(AssetManagerSnapshotWorkflowOperationHandler.class);

  /** The asset manager. */
  private AssetManager assetManager;

  /** The configuration options for this handler */
  private static final SortedMap<String, String> CONFIG_OPTIONS = smap(
          tuple("source-tags", "Add any media package elements with one of these (comma separated) tags"),
          tuple("source-flavors", "Add any media package elements with one of these (comma separated) flavors"));

  @Override
  public SortedMap<String, String> getConfigurationOptions() {
    return CONFIG_OPTIONS;
  }

  /** OSGi DI */
  public void setAssetManager(AssetManager assetManager) {
    this.assetManager = assetManager;
  }

  @Override
  public WorkflowOperationResult start(WorkflowInstance wi, JobContext ctx)
          throws WorkflowOperationException {
    final MediaPackage mpWorkflow = wi.getMediaPackage();
    final WorkflowOperationInstance currentOperation = wi.getCurrentOperation();

    // Check which tags have been configured
    final String tags = StringUtils.trimToNull(currentOperation.getConfiguration("source-tags"));
    final String sourceFlavorsString = StringUtils.trimToEmpty(currentOperation.getConfiguration("source-flavors"));

    final String[] sourceFlavors = StringUtils.split(sourceFlavorsString, ",");
    if (sourceFlavors.length < 1 && tags == null)
      logger.debug("No source tags have been specified, so everything will be added to the AssetManager");

    final List<String> tagSet;
    // If a set of tags has been specified, use it
    if (tags != null) {
      tagSet = asList(tags);
    } else {
      tagSet = new ArrayList<>();
    }

    try {
      final MediaPackage mpAssetManager = getMediaPackageForArchival(mpWorkflow, tagSet, sourceFlavors);
      if (mpAssetManager != null) {
        logger.info("Take snapshot of media package {}", mpAssetManager);
        // adding media package to the episode service
        assetManager.takeSnapshot(DEFAULT_OWNER, mpAssetManager);
        logger.debug("Snapshot operation complete");
        return createResult(mpWorkflow, Action.CONTINUE);
      } else {
        return createResult(mpWorkflow, Action.CONTINUE);
      }
    } catch (Throwable t) {
      throw new WorkflowOperationException(t);
    }
  }

  protected MediaPackage getMediaPackageForArchival(MediaPackage current, List<String> tags, String[] sourceFlavors)
          throws MediaPackageException {
    MediaPackage mp = (MediaPackage) current.clone();

    Collection<MediaPackageElement> keep;

    if (tags.isEmpty() && sourceFlavors.length < 1) {
      keep = new ArrayList<>(Arrays.asList(current.getElementsByTags(tags)));
    } else {
      SimpleElementSelector simpleElementSelector = new SimpleElementSelector();
      for (String flavor : sourceFlavors) {
        simpleElementSelector.addFlavor(flavor);
      }
      for (String tag : tags) {
        simpleElementSelector.addTag(tag);
      }
      keep = simpleElementSelector.select(current, false);
    }

    // Also archive the publication elements
    for (Publication publication : current.getPublications()) {
      keep.add(publication);
    }

    // Mark everything that is set for removal
    List<MediaPackageElement> removals = new ArrayList<MediaPackageElement>();
    for (MediaPackageElement element : mp.getElements()) {
      if (!keep.contains(element)) {
        removals.add(element);
      }
    }

    // Fix references and flavors
    for (MediaPackageElement element : mp.getElements()) {

      if (removals.contains(element))
        continue;

      // Is the element referencing anything?
      MediaPackageReference reference = element.getReference();
      if (reference != null) {
        Map<String, String> referenceProperties = reference.getProperties();
        MediaPackageElement referencedElement = mp.getElementByReference(reference);

        // if we are distributing the referenced element, everything is fine. Otherwise...
        if (referencedElement != null && removals.contains(referencedElement)) {

          // Follow the references until we find a flavor
          MediaPackageElement parent;
          while ((parent = current.getElementByReference(reference)) != null) {
            if (parent.getFlavor() != null && element.getFlavor() == null) {
              element.setFlavor(parent.getFlavor());
            }
            if (parent.getReference() == null) {
              break;
            }
            reference = parent.getReference();
          }

          // Done. Let's cut the path but keep references to the mediapackage itself
          if (reference != null && reference.getType().equals(MediaPackageReference.TYPE_MEDIAPACKAGE))
            element.setReference(reference);
          else if (reference != null && (referenceProperties == null || referenceProperties.size() == 0))
            element.clearReference();
          else {
            // Ok, there is more to that reference than just pointing at an element. Let's keep the original,
            // you never know.
            removals.remove(referencedElement);
            referencedElement.setURI(null);
            referencedElement.setChecksum(null);
          }
        }
      }
    }

    // Remove everything we don't want to add to publish
    for (MediaPackageElement element : removals) {
      mp.remove(element);
    }
    return mp;
  }
}
