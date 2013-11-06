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
package org.opencastproject.clipshow.impl;

import java.util.LinkedHashSet;
import java.util.Set;

import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.Table;
import javax.xml.bind.annotation.XmlAccessOrder;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorOrder;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@Entity
@Table(name = "clipshow_tags")
@XmlRootElement(name = "clipshow-tag")
@XmlAccessorOrder(XmlAccessOrder.UNDEFINED)
@XmlAccessorType(XmlAccessType.NONE)
@NamedQueries({
  @NamedQuery(name = "tag.All", query = "SELECT t from ClipshowTag t"),
  //@NamedQuery(name = "tag.ByTag", query = "SELECT t from ClipshowTag t where :tag in (t.tags)"),
  @NamedQuery(name = "tag.ByMediapackage", query = "SELECT t from ClipshowTag t where t.clipshow.mediapackageId = :mediapackageId"),// and :tag in t.tags"),
  @NamedQuery(name = "tag.Select", query = "SELECT t FROM ClipshowTag t where t.tagger = :tagger and t.clipshow = :clipshow"),
})
//FIXME: tag.tag doesn't understand searching by multiple tags
public class ClipshowTag {

  @Id
  @GeneratedValue(strategy = GenerationType.AUTO)
  @Column(name = "id")
  private Long id;

  @JoinColumn(name = "tagger")
  private ClipshowUser tagger;
  
  @JoinColumn(name = "clipshow")
  private Clipshow clipshow;

  @XmlElement(name = "tags")
  @Column(name = "tags")
  @ElementCollection(targetClass = String.class)
  private Set<String> tags;

  public ClipshowTag() {
    setTags(new LinkedHashSet<String>());
  }

  public ClipshowTag(ClipshowUser user, Clipshow clipshow, Set<String> tags) {
    setTagger(user);
    setTags(tags);
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public ClipshowUser getTagger() {
    return tagger;
  }

  public void setTagger(ClipshowUser tagger) {
    this.tagger = tagger;
  }

  public Clipshow getClipshow() {
    return clipshow;
  }

  public void setClipshow(Clipshow clipshow) {
    this.clipshow = clipshow;
  }

  public void addTag(String tag) {
    tags.add(tag.toLowerCase());
  }

  public void removeTag(String tag) {
    tags.remove(tag);
  }

  public Set<String> getTags() {
    return tags;
  }

  public void setTags(Set<String> tags) {
    this.tags = tags;
  }

  @Override
  public boolean equals(Object o) {
    if (o instanceof ClipshowTag) {
      return ((ClipshowTag) o).getTagger().equals(getTagger()) && ((ClipshowTag) o).getClipshow().equals(getClipshow());
    } else {
      return false;
    }
  }

  @Override
  public int hashCode() {
    return getTagger().hashCode() + getClipshow().getId().hashCode();
  }
}
