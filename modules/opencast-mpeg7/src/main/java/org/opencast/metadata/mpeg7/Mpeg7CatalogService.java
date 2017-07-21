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

package org.opencast.metadata.mpeg7;

import org.opencast.mediapackage.Catalog;
import org.opencast.mediapackage.MediaPackageElementFlavor;
import org.opencast.metadata.api.CatalogService;

import org.apache.commons.io.output.ByteArrayOutputStream;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

/**
 * Loads {@link Mpeg7Catalog}s
 */
public class Mpeg7CatalogService implements CatalogService<Mpeg7Catalog> {

  /**
   * {@inheritDoc}
   *
   * @see org.opencast.metadata.api.CatalogService#accepts(org.opencast.mediapackage.Catalog)
   */
  @Override
  public boolean accepts(Catalog catalog) {
    if (catalog == null)
      throw new IllegalArgumentException("Catalog must not be null");
    MediaPackageElementFlavor flavor = catalog.getFlavor();
    return flavor != null && (flavor.equals(Mpeg7Catalog.ANY_MPEG7));
  }

  /**
   * {@inheritDoc}
   *
   * @see org.opencast.metadata.api.CatalogService#serialize(org.opencast.metadata.api.MetadataCatalog)
   */
  @Override
  public InputStream serialize(Mpeg7Catalog catalog) throws IOException {
    try {
      Transformer tf = TransformerFactory.newInstance().newTransformer();
      DOMSource xmlSource = new DOMSource(catalog.toXml());
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      tf.transform(xmlSource, new StreamResult(out));
      return new ByteArrayInputStream(out.toByteArray());
    } catch (Exception e) {
      throw new IOException(e);
    }
  }

  /**
   * {@inheritDoc}
   *
   * @see org.opencast.metadata.api.CatalogService#load(java.io.InputStream)
   */
  @Override
  public Mpeg7Catalog load(InputStream in) throws IOException {
    if (in == null)
      throw new IllegalArgumentException("Stream must not be null");
    return new Mpeg7CatalogImpl(in);
  }

  /**
   * {@inheritDoc}
   *
   * @see org.opencast.metadata.api.CatalogService#newInstance()
   */
  @Override
  public Mpeg7Catalog newInstance() {
    return new Mpeg7CatalogImpl();
  }

}
