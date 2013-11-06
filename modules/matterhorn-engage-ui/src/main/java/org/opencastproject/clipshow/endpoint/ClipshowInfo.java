/**
 *  Copyright 2009, 2010 The Regents of the University of California
 *  Licensed under the Educational Community License, Version 2.0
 *  (the "License"); you may not use this file except in compliance
 *  with the License. You may obtain a copy of the License at
 *
 *  http://www.osedu.org/licenses/ECL-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an "AS IS"
 *  BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 *  or implied. See the License for the specific language governing
 *  permissions and limitations under the License.
 *
 */
package org.opencastproject.clipshow.endpoint;

import org.opencastproject.clipshow.impl.Clipshow;
import org.opencastproject.clipshow.impl.ClipshowTag;
import org.opencastproject.clipshow.impl.ClipshowVote;

import java.util.HashMap;
import java.util.Set;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

@XmlType(name = "clipshow-info-blob", namespace = "http://clipshow.engage.opencastproject.org")
@XmlRootElement(name = "clipshow-info-blob", namespace = "http://clipshow.engage.opencastproject.org")
@XmlAccessorType(XmlAccessType.FIELD)
public class ClipshowInfo implements Comparable<ClipshowInfo> {

  @XmlElement(name = "id")
  private Long id;

  @XmlElement(name = "mediapackageId")
  private String mediapackageId;

  @XmlElement(name = "mediapackageTitle")
  private String mediapackageTitle;

  @XmlElement(name = "author")
  private String author;

  @XmlElement(name = "title")
  private String title;

  @XmlElement(name = "good")
  private Integer good = -1;

  @XmlElement(name = "funny")
  private Integer funny = -1;

  @XmlElement(name = "dislike")
  private Integer dislike = -1;

  @XmlElement(name = "tags")
  private Set<ClipshowTag> tags;

  public ClipshowInfo() { }

  public ClipshowInfo(Clipshow c) {
    this.setId(c.getId());
    this.setMediapackageId(c.getMediapackageId());
    this.setMediapackageTitle(c.getMediapackageTitle());
    this.setAuthor(c.getAuthor().getDisplayName());
    this.setTitle(c.getTitle());
    this.setTags(c.getTags());
    HashMap<ClipshowVote.Type, Integer> votes = c.getVotes();
    this.setDislike(votes.get(ClipshowVote.Type.DISLIKE));
    this.setFunny(votes.get(ClipshowVote.Type.FUNNY));
    this.setGood(votes.get(ClipshowVote.Type.GOOD));
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getMediapackageId() {
    return mediapackageId;
  }

  public void setMediapackageId(String mediapackageId) {
    this.mediapackageId = mediapackageId;
  }

  public String getMediapackageTitle() {
    return mediapackageTitle;
  }

  public void setMediapackageTitle(String mediapackageTitle) {
    this.mediapackageTitle = mediapackageTitle;
  }

  public String getAuthor() {
    return author;
  }

  public void setAuthor(String author) {
    this.author = author;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }


  public Integer getGood() {
    return good;
  }

  public void setGood(Integer good) {
    this.good = good;
  }

  public Integer getFunny() {
    return funny;
  }

  public void setFunny(Integer funny) {
    this.funny = funny;
  }

  public Integer getDislike() {
    return dislike;
  }

  public void setDislike(Integer dislike) {
    this.dislike = dislike;
  }

  public Integer getVotes() {
    return getGood() + getFunny();
  }

  public Set<ClipshowTag> getTags() {
    return tags;
  }

  public void setTags(Set<ClipshowTag> tags) {
    this.tags = tags;
  }

  @Override
  public int compareTo(ClipshowInfo o) {
    if (o.getVotes() < getVotes()) {
      return 1;
    } else if (o.getVotes() > getVotes()) {
      return -1;
    } else {
      return 0;
    }
  }
}
