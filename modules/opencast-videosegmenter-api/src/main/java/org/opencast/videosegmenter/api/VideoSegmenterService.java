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


package org.opencast.videosegmenter.api;

import org.opencast.job.api.Job;
import org.opencast.mediapackage.MediaPackageException;
import org.opencast.mediapackage.Track;

/**
 * Api for video segmentation implementations, aimed at detecting scenes in audiovisual tracks.
 */
public interface VideoSegmenterService {

  /** Job type */
  String JOB_TYPE = "org.opencast.videosegmenter";

  /**
   * Takes the given track and returns the job that can be used to get the resulting mpeg7 catalog.
   *
   * @param track
   *          track to segment
   * @return the job with which we can obtain the extracted metadata
   * @throws VideoSegmenterException
   *           if the track could not be segmented
   * @throws MediaPackageException
   *           if the track is invalid
   */
  Job segment(Track track) throws VideoSegmenterException, MediaPackageException;

}
