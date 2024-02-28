/*
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


package org.opencastproject.silencedetection.api;

import org.opencastproject.util.XmlSafeParser;

import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.List;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.Unmarshaller;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElementWrapper;
import jakarta.xml.bind.annotation.XmlRootElement;

/**
 * Wrapper class for holding many {@link MediaSegment}s and implements XML serialization methods.
 */
@XmlRootElement(name = "media-segments")
@XmlAccessorType(XmlAccessType.NONE)
public class MediaSegments {

  /** Track ID */
  @XmlElement(name = "trackId", required = true)
  private String trackId;

  /** File path to a media file */
  @XmlElement(name = "filePath")
  private String filePath;

  /** List with media segments, that holds start and stop positions */
  @XmlElementWrapper(name = "segments", required = true, nillable = false)
  @XmlElement(name = "segment")
  private List<MediaSegment> mediaSegments;

  public MediaSegments() {
    this(null, null, null);
  }

  public MediaSegments(String trackId, String filePath, List<MediaSegment> mediaSegments) {
    this.trackId = trackId;
    this.filePath = filePath;
    this.mediaSegments = mediaSegments;
  }

  /**
   * Returns a list with media segments, that holds start and stop positions or null.
   * @return list with {@link MediaSegment}s or null
   */
  public List<MediaSegment> getMediaSegments() {
    return mediaSegments;
  }

  /**
   * Returns the Track ID.
   * @return Track ID
   */
  public String getTrackId() {
    return trackId;
  }

  /**
   * Returns file Path of media file.
   * @return file path of media file
   */
  public String getFilePath() {
    return filePath;
  }

  /**
   * Serialize to XML.
   * @return XML as String
   * @throws JAXBException if an error occures
   */
  public String toXml() throws JAXBException {
    StringWriter sw = new StringWriter();
    JAXBContext jctx = JAXBContext.newInstance(MediaSegments.class);
    Marshaller mediaSegmentsMarshaller = jctx.createMarshaller();
    mediaSegmentsMarshaller.marshal(this, sw);
    return sw.toString();
  }

  /**
   * Deserialize from XML.
   * @param mediaSegmentsXml {@link MediaSegments} XML as String
   * @return deserialized {@link MediaSegments}
   * @throws JAXBException if an error occures
   */
  public static MediaSegments fromXml(String mediaSegmentsXml) throws JAXBException {
    MediaSegments mediaSegments = null;
    JAXBContext jctx = JAXBContext.newInstance(MediaSegments.class);
    Unmarshaller unmarshaller = jctx.createUnmarshaller();
    try (StringReader sr = new StringReader(mediaSegmentsXml)) {
      InputSource is = new InputSource(sr);
      mediaSegments = (MediaSegments) unmarshaller.unmarshal(XmlSafeParser.parse(is));
    } catch (IOException | SAXException e) {
      throw new JAXBException(e);
    }
    return mediaSegments;
  }
}
