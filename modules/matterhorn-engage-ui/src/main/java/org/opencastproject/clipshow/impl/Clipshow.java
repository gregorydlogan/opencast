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

import java.io.Serializable;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToMany;
import javax.persistence.ManyToOne;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.OneToMany;
import javax.persistence.OrderColumn;
import javax.persistence.Table;
import javax.xml.bind.annotation.XmlAccessOrder;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorOrder;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

@Entity
@Table(name = "clipshows")
@XmlRootElement(name = "clipshow")
@XmlAccessorOrder(XmlAccessOrder.UNDEFINED)
@XmlAccessorType(XmlAccessType.NONE)
@NamedQueries({
  @NamedQuery(name = "clipshow", query = "SELECT c FROM Clipshow c where c.id = :id AND c.deleted = FALSE"),
  @NamedQuery(name = "clipshow.mediapackage", query = "SELECT c FROM Clipshow c where c.mediapackageId = :mediapackageId AND c.deleted = FALSE"),
  @NamedQuery(name = "clipshow.mediapackageAndAuthor", query = "SELECT c FROM Clipshow c where c.mediapackageId = :mediapackageId and (c.author.username = :author or c.author.displayName = :author) AND c.deleted = FALSE"),
  @NamedQuery(name = "clipshow.mediapackageAndAuthorId", query = "SELECT c FROM Clipshow c where c.mediapackageId = :mediapackageId and c.author.id = :authorId AND c.deleted = FALSE"),
  @NamedQuery(name = "clipshow.author", query = "SELECT c FROM Clipshow c where c.author.username = :author or c.author.displayName = :author AND c.deleted = FALSE"),
  @NamedQuery(name = "clipshow.authorId", query = "SELECT c FROM Clipshow c where c.author.id = :authorId AND c.deleted = FALSE"),
  @NamedQuery(name = "clipshow.title", query = "SELECT c FROM Clipshow c where (LOWER(c.title) like :title OR LOWER(c.mediapackageTitle) like :title) AND c.deleted = FALSE"),
  @NamedQuery(name = "clipshow.mediapackageAndTitle", query = "SELECT c FROM Clipshow c where c.mediapackageId = :mediapackageId and LOWER(c.title) like :title AND c.deleted = FALSE"),
  @NamedQuery(name = "clipshow.all", query = "SELECT c FROM Clipshow c")
})
public class Clipshow implements Serializable, Comparable<Clipshow> {

  private static final long serialVersionUID = 1L;

  @Id
  @GeneratedValue(strategy = GenerationType.AUTO)
  @Column(name = "id")
  @XmlElement(name = "id")
  private Long id;

  @Column(name = "title", nullable = false)
  @XmlElement(name = "title")
  private String title;

  @Column(name = "mediapackage_id", nullable = false)
  @XmlElement(name = "mediapackageId")
  private String mediapackageId;
  
  @Column(name = "mediapackage_title", nullable = false)
  @XmlElement(name = "mediapackageTitle")
  private String mediapackageTitle;

  @ManyToOne(cascade = { })
  @JoinColumn(name = "author", nullable = false)
  @XmlElement(name = "author")
  private ClipshowUser author;

  @ManyToMany(mappedBy = "memberOfClipshowSet", cascade = { CascadeType.MERGE, CascadeType.PERSIST })
  private Set<ClipshowUser> allowedUsers;

  @OneToMany(cascade = CascadeType.ALL)
  @OrderColumn(name = "clipshow")
  @XmlElement(name = "clips")
  private List<Clip> clips;

  @ManyToMany(fetch = FetchType.LAZY, cascade = { })
  private Set<ClipshowSeries> series;

  @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
  @JoinColumn(name = "clipshow_votes")
  private Set<ClipshowVote> votes;

  @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
  @JoinColumn(name = "clipshow_tags")
  @XmlElement(name = "tags")
  private Set<ClipshowTag> tags;

  @XmlTransient
  @Column(name = "public")
  private Boolean publicClipshow = true;

  @Column(name = "deleted")
  @XmlTransient
  private Boolean deleted = false;

  public Clipshow() { 
    setVotes(new LinkedHashSet<ClipshowVote>());
    setSeries(new LinkedHashSet<ClipshowSeries>());
    setClips(new LinkedList<Clip>());
    setAllowedUsers(new LinkedHashSet<ClipshowUser>());
    setTags(new LinkedHashSet<ClipshowTag>());
  }

  public Clipshow(String name, ClipshowUser author, String mediapackageId, String mediapackageTitle, List<Clip> clips) {
    this();
    setTitle(name);
    setAuthor(author);
    setMediapackageId(mediapackageId);
    setMediapackageTitle(mediapackageTitle);
    setClips(clips);
  }

  public void setMediapackageId(String mpId) {
    mediapackageId = mpId;
  }

  public String getMediapackageId() {
    return mediapackageId;
  }

  public void setMediapackageTitle(String mpTitle) {
    mediapackageTitle = mpTitle;
  }

  public String getMediapackageTitle() {
    return mediapackageTitle;
  }

  public void setClips(List<Clip> list) {
    if (list != null) {
      clips = list;
    } else {
      clips = new LinkedList<Clip>();
    }
  }

  public List<Clip> getClips() {
    return clips;
  }

  public void setSeries(Set<ClipshowSeries> s) {
    if (s != null) {
      series = s;
    } else {
      series = new LinkedHashSet<ClipshowSeries>();
    }
  }

  public Set<ClipshowSeries> getSeries() {
    return series;
  }

  public void addSeries(ClipshowSeries s) {
    series.add(s);
  }

  public void removeSeries(ClipshowSeries s) {
    series.remove(s);
  }

  public Boolean memberOfSeries(ClipshowSeries s) {
    return series.contains(s);
  }

  public void setAuthor(ClipshowUser a) {
    if (a == null) {
      return;
    }

    //Just in case we ever change the author for some reason, remove the old author from the allowed users
    if (author != null) {
      removeAllowedUser(author);
      author.removeAuthoredClipshow(this);
    }
    author = a;
    addAllowedUser(author);
    author.addAuthoredClipshow(this);
  }
  
  public ClipshowUser getAuthor() {
    return author;
  }

  public void setAllowedUsers(Set<ClipshowUser> users) {
    if (users != null) {
      allowedUsers = users;
    } else {
      allowedUsers = new LinkedHashSet<ClipshowUser>();
    }
    addAllowedUser(author);
  }

  public Set<ClipshowUser> getAllowedUsers() {
    return allowedUsers;
  }
  
  public void addAllowedUser(ClipshowUser user) {
    if (user == null) {
      return;
    }

    if (allowedUsers == null) {
      allowedUsers = new LinkedHashSet<ClipshowUser>();
    }
    allowedUsers.add(user);
    user.addMemberOfClipshow(this);
  }

  public void removeAllowedUser(ClipshowUser user) {
    if (allowedUsers != null && author != null && !author.equals(user)) {
      allowedUsers.remove(user);
    }
  }

  public Boolean userAllowed(ClipshowUser user) {
    if (isPublicClipshow()) {
      return true;
    }
    return allowedUsers.contains(user) || author.equals(user);
  }
  
  public void setVotes(Set<ClipshowVote> v) {
    votes = v;
  }

  public HashMap<ClipshowVote.Type, Integer> getVotes() {
    LinkedList<ClipshowVote> list = new LinkedList<ClipshowVote>();
    list.addAll(votes);
    return countVotes(list);
  }

  public static HashMap<ClipshowVote.Type, Integer> countVotes(List<ClipshowVote> votes) {
    HashMap<ClipshowVote.Type, Integer> voteCounts = new HashMap<ClipshowVote.Type, Integer>();
    voteCounts.put(ClipshowVote.Type.FUNNY, 0);
    voteCounts.put(ClipshowVote.Type.GOOD, 0);
    voteCounts.put(ClipshowVote.Type.DISLIKE, 0);
    for (ClipshowVote v : votes) {
      if (v.getDislike()) {
        voteCounts.put(ClipshowVote.Type.DISLIKE, voteCounts.get(ClipshowVote.Type.DISLIKE) + 1);
        continue;
      }
      if (v.getFunny()) {
        voteCounts.put(ClipshowVote.Type.FUNNY, voteCounts.get(ClipshowVote.Type.FUNNY) + 1);
      }
      if (v.getGood()) {
        voteCounts.put(ClipshowVote.Type.GOOD, voteCounts.get(ClipshowVote.Type.GOOD) + 1);
      }
    }
    return voteCounts;
  }

  public Set<ClipshowVote> getVoters() {
    return votes;
  }

  public void addVoter(ClipshowVote v) {
    votes.add(v);
  }

  public void removeVoter(ClipshowVote v) {
    votes.remove(v);
  }

  public void setTitle(String t) {
    title = t;
  }

  public String getTitle() {
    return title;
  }

  public Long getId() {
    return id;
  }

  public void setTags(Set<ClipshowTag> newTags) {
    tags = newTags;
  }

  public Set<ClipshowTag> getTags() {
    return tags;
  }

  public void addTag(ClipshowTag tag) {
    removeTag(tag); //Remove the old one so that the new one definitely replaces it
    tags.add(tag);
  }

  public void removeTag(ClipshowTag tag) {
    tags.remove(tag);
  }

  public Boolean getPublicClipshow() {
    return publicClipshow;
  }

  public void setPublicClipshow(Boolean publicClipshow) {
    this.publicClipshow = publicClipshow;
  }

  public boolean isPublicClipshow() {
    return publicClipshow.booleanValue();
  }

  @Override
  public int compareTo(Clipshow o) {
    if (o.getVoters().size() < this.getVoters().size()) {
      return 1;
    } else if (o.getVoters().size() > this.getVoters().size()) {
      return -1;
    } else {
      return 0;
    }
  }

  public Boolean getDeleted() {
    return this.deleted;
  }

  public void setDeleted(Boolean isDeleted) {
    this.deleted = isDeleted;
  }
}
