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


package org.opencast.mediapackage.elementbuilder;

import org.opencast.mediapackage.Catalog;
import org.opencast.mediapackage.CatalogImpl;
import org.opencast.mediapackage.MediaPackageElement;
import org.opencast.mediapackage.MediaPackageElement.Type;
import org.opencast.mediapackage.MediaPackageElementFlavor;
import org.opencast.mediapackage.MediaPackageReferenceImpl;
import org.opencast.mediapackage.MediaPackageSerializer;
import org.opencast.mediapackage.UnsupportedElementException;
import org.opencast.util.Checksum;
import org.opencast.util.MimeType;
import org.opencast.util.MimeTypes;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.net.URI;
import java.net.URISyntaxException;
import java.security.NoSuchAlgorithmException;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

/**
 * This implementation of the {@link MediaPackageElementBuilderPlugin} recognizes metadata catalogs and provides the
 * functionality of reading it on behalf of the media package.
 */
public class CatalogBuilderPlugin implements MediaPackageElementBuilderPlugin {

  /** The xpath facility */
  protected XPath xpath = XPathFactory.newInstance().newXPath();

  /**
   * the logging facility provided by log4j
   */
  private static final Logger logger = LoggerFactory.getLogger(CatalogBuilderPlugin.class);

  /**
   * @see org.opencast.mediapackage.elementbuilder.MediaPackageElementBuilderPlugin#accept(org.opencast.mediapackage.MediaPackageElement.Type,
   *      org.opencast.mediapackage.MediaPackageElementFlavor)
   */
  @Override
  public boolean accept(MediaPackageElement.Type type, MediaPackageElementFlavor flavor) {
    return type.equals(MediaPackageElement.Type.Catalog);
  }

  /**
   * @see org.opencast.mediapackage.elementbuilder.MediaPackageElementBuilderPlugin#accept(org.w3c.dom.Node)
   */
  @Override
  public boolean accept(Node elementNode) {
    String name = elementNode.getNodeName();
    if (name.contains(":")) {
      name = name.substring(name.indexOf(":") + 1);
    }
    return name.equalsIgnoreCase(MediaPackageElement.Type.Catalog.toString());
  }

  /**
   * @see org.opencast.mediapackage.elementbuilder.MediaPackageElementBuilderPlugin#accept(URI,
   *      org.opencast.mediapackage.MediaPackageElement.Type,
   *      org.opencast.mediapackage.MediaPackageElementFlavor)
   */
  @Override
  public boolean accept(URI uri, MediaPackageElement.Type type, MediaPackageElementFlavor flavor) {
    return MediaPackageElement.Type.Catalog.equals(type);
  }

  /**
   * @see org.opencast.mediapackage.elementbuilder.MediaPackageElementBuilderPlugin#elementFromURI(URI)
   */
  @Override
  public MediaPackageElement elementFromURI(URI uri) throws UnsupportedElementException {
    logger.trace("Creating video track from " + uri);
    Catalog track = CatalogImpl.fromURI(uri);
    return track;
  }

  @Override
  public String toString() {
    return "Indefinite Catalog Builder Plugin";
  }

  protected Catalog catalogFromManifest(String id, URI uri) {
    Catalog cat = CatalogImpl.fromURI(uri);
    cat.setIdentifier(id);
    return cat;
  }

  /**
   * {@inheritDoc}
   *
   * @see org.opencast.mediapackage.elementbuilder.MediaPackageElementBuilderPlugin#destroy()
   */
  @Override
  public void destroy() {
  }

  /**
   * {@inheritDoc}
   *
   * @see org.opencast.mediapackage.elementbuilder.MediaPackageElementBuilderPlugin#elementFromManifest(org.w3c.dom.Node,
   *      org.opencast.mediapackage.MediaPackageSerializer)
   */
  @Override
  public MediaPackageElement elementFromManifest(Node elementNode, MediaPackageSerializer serializer)
          throws UnsupportedElementException {
    String id = null;
    String flavor = null;
    URI url = null;
    long size = -1;
    Checksum checksum = null;
    MimeType mimeType = null;
    String reference = null;

    try {
      // id
      id = (String) xpath.evaluate("@id", elementNode, XPathConstants.STRING);

      // url
      url = serializer.decodeURI(new URI(xpath.evaluate("url/text()", elementNode).trim()));

      // flavor
      flavor = (String) xpath.evaluate("@type", elementNode, XPathConstants.STRING);

      // reference
      reference = (String) xpath.evaluate("@ref", elementNode, XPathConstants.STRING);

      // size
      String documentSize = xpath.evaluate("size/text()", elementNode).trim();
      if (!"".equals(documentSize))
        size = Long.parseLong(documentSize);

      // checksum
      String checksumValue = (String) xpath.evaluate("checksum/text()", elementNode, XPathConstants.STRING);
      String checksumType = (String) xpath.evaluate("checksum/@type", elementNode, XPathConstants.STRING);
      if (StringUtils.isNotEmpty(checksumValue) && checksumType != null)
        checksum = Checksum.create(checksumType.trim(), checksumValue.trim());

      // mimetype
      String mimeTypeValue = (String) xpath.evaluate("mimetype/text()", elementNode, XPathConstants.STRING);
      if (StringUtils.isNotEmpty(mimeTypeValue))
        mimeType = MimeTypes.parseMimeType(mimeTypeValue);

      // create the catalog
      Catalog dc = CatalogImpl.fromURI(url);
      if (StringUtils.isNotEmpty(id))
        dc.setIdentifier(id);

      // Add url
      dc.setURI(url);

      // Add flavor
      if (flavor != null)
        dc.setFlavor(MediaPackageElementFlavor.parseFlavor(flavor));

      // Add reference
      if (StringUtils.isNotEmpty(reference))
        dc.referTo(MediaPackageReferenceImpl.fromString(reference));

      // Set size
      if (size > 0)
        dc.setSize(size);

      // Set checksum
      if (checksum != null)
        dc.setChecksum(checksum);

      // Set Mimetype
      if (mimeType != null)
        dc.setMimeType(mimeType);

      // Tags
      NodeList tagNodes = (NodeList) xpath.evaluate("tags/tag", elementNode, XPathConstants.NODESET);
      for (int i = 0; i < tagNodes.getLength(); i++) {
        dc.addTag(tagNodes.item(i).getTextContent());
      }

      return dc;
    } catch (XPathExpressionException e) {
      throw new UnsupportedElementException("Error while reading catalog information from manifest: " + e.getMessage());
    } catch (NoSuchAlgorithmException e) {
      throw new UnsupportedElementException("Unsupported digest algorithm: " + e.getMessage());
    } catch (URISyntaxException e) {
      throw new UnsupportedElementException("Error while reading dublin core catalog " + url + ": " + e.getMessage());
    }
  }

  /**
   * {@inheritDoc}
   *
   * @see org.opencast.mediapackage.elementbuilder.MediaPackageElementBuilderPlugin#newElement(org.opencast.mediapackage.MediaPackageElement.Type,
   *      org.opencast.mediapackage.MediaPackageElementFlavor)
   */
  @Override
  public MediaPackageElement newElement(Type type, MediaPackageElementFlavor flavor) {
    Catalog cat = CatalogImpl.newInstance();
    cat.setFlavor(flavor);
    return cat;
  }

  /**
   * {@inheritDoc}
   *
   * @see org.opencast.mediapackage.elementbuilder.MediaPackageElementBuilderPlugin#init()
   */
  @Override
  public void init() throws Exception {
  }

}
