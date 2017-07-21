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


package org.opencast.silencedetection.api;

import org.opencast.job.api.Job;
import org.opencast.mediapackage.Track;

/**
 * SilenceDetectionService detect silent seqences in audio tracks.
 */
public interface SilenceDetectionService {

  /**
   * ServiceRegistry job type.
   */
  String JOB_TYPE = "org.opencast.silencedetection";

  /**
   * Run silence detection on audio (visual) file.
   *
   * @param sourceTrack track to detect non silent segments from
   * @return Job detection job
   * @throws SilenceDetectionFailedException if fails
   */
  Job detect(Track sourceTrack) throws SilenceDetectionFailedException;

  /**
   * Run silence detection on audio (visual) file.
   *
   * @param sourceTrack track to detect non silent segments from
   * @param referenceTracks tracks to reference in smil file instead of sourceTrack
   * @return Job detection job
   * @throws SilenceDetectionFailedException if fails
   */
  Job detect(Track sourceTrack, Track[] referenceTracks) throws SilenceDetectionFailedException;
}
