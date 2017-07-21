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

package org.opencast.composer.impl;

import org.opencast.composer.api.EmbedderEngine;
import org.opencast.composer.api.EmbedderEngineFactory;
import org.opencast.composer.impl.ffmpeg.FFmpegEmbedderEngine;

import org.osgi.service.component.ComponentContext;

/**
 * Implementation of {@link EmbedderEngineFactory} that creates new {@link EmbedderEngine} instance.
 *
 */
public class EmbedderEngineFactoryImpl implements EmbedderEngineFactory {

  /** Component context from where configurations are retrieved */
  private ComponentContext context;

  /** Activates component via OSGi */
  public void activate(ComponentContext context) {
    this.context = context;
  }

  /**
   *
   * {@inheritDoc}
   *
   * @see org.opencast.composer.api.EmbedderEngineFactory#newEmbedderEngine()
   */
  @Override
  public EmbedderEngine newEmbedderEngine() {
    FFmpegEmbedderEngine engine = new FFmpegEmbedderEngine();
    engine.activate(context);
    return engine;
  }

}
