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


package org.opencast.oaipmh.matterhorn;

import static org.opencast.util.UrlSupport.uri;
import static org.opencast.util.UrlSupport.url;

import org.opencast.mediapackage.MediaPackageParser;
import org.opencast.oaipmh.persistence.SearchResultItem;
import org.opencast.oaipmh.server.MetadataFormat;
import org.opencast.oaipmh.server.MetadataProvider;
import org.opencast.oaipmh.server.OaiPmhRepository;
import org.opencast.oaipmh.util.XmlGen;
import org.opencast.util.data.Option;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.net.URI;
import java.net.URL;

/**
 * The Matterhorn metadata provider provides whole media packages.
 */
public class MatterhornMetadataProvider implements MetadataProvider {
  private static final URL SCHEMA_URL = url("http://www.opencast.org/oai/matterhorn.xsd");
  private static final URI NAMESPACE_URI = uri("http://www.opencast.org/oai/matterhorn");

  private static final MetadataFormat METADATA_FORMAT = new MetadataFormat() {
    @Override
    public String getPrefix() {
      return "matterhorn";
    }

    @Override
    public URL getSchema() {
      return SCHEMA_URL;
    }

    @Override
    public URI getNamespace() {
      return NAMESPACE_URI;
    }
  };

  @Override
  public MetadataFormat getMetadataFormat() {
    return METADATA_FORMAT;
  }

  @Override
  public Element createMetadata(OaiPmhRepository repository, final SearchResultItem item, Option<String> set) {
    final Document mp = MediaPackageParser.getAsXmlDocument(item.getMediaPackage());
    XmlGen xml = new XmlGen(Option.<String>none()) {
      @Override
      public Element create() {
        return (Element) mp.getFirstChild();
      }
    };
    return xml.create();
  }
}
