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
import javax.persistence.JoinTable;
import javax.persistence.ManyToMany;
import javax.persistence.ManyToOne;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.Table;
import javax.persistence.Transient;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

@Entity
@Table(name = "clipshow_series")
@XmlRootElement(name = "clipshow-series")
@NamedQueries({
  @NamedQuery(name = "clipshow-series.all", query = "SELECT c FROM ClipshowSeries c where c.deleted = FALSE"),
  @NamedQuery(name = "clipshow-series.byAuthor", query = "SELECT c FROM ClipshowSeries c WHERE c.author = :author AND c.deleted = FALSE")
})
public class ClipshowSeries implements Serializable {

  private static final long serialVersionUID = 1L;

  @Id
  @GeneratedValue(strategy = GenerationType.AUTO)
  @Column(name = "id")
  private Long id;

  @Column(name = "title", nullable = false)
  private String title;

  @Column(name = "description")
  private String description;

  @ManyToOne(cascade = { })
  @JoinColumn(name = "author", nullable = false)
  private ClipshowUser author;

  @ManyToMany(mappedBy = "series", fetch = FetchType.LAZY, cascade = { CascadeType.PERSIST, CascadeType.MERGE })
  @JoinTable(name = "clipshow_series_mappings")
  @XmlTransient
  private List<Clipshow> shows;

  @ManyToMany(mappedBy = "memberOfSeriesSet", fetch = FetchType.LAZY)
  @XmlTransient //This could be a very large number of users
  private Set<ClipshowUser> allowedUsers;

  @Transient
  @XmlTransient
  private Boolean publicSeries = true;

  @Column(name = "deleted")
  @XmlTransient
  private Boolean deleted = false;

  public ClipshowSeries() {
    setClipshows(new LinkedList<Clipshow>());
    setAllowedUsers(new LinkedHashSet<ClipshowUser>());
  }
  
  public ClipshowSeries(String name, ClipshowUser author) {
    this();
    setTitle(name);
    setAuthor(author);
  }

  @XmlElement(name = "id")
  public Long getId() {
    return id;
  }

  public void setTitle(String t) {
    title = t;
  }

  @XmlElement(name = "title")
  public String getTitle() {
    return title;
  }

  public void setDescription(String d) {
    description = d;
  }

  @XmlElement(name = "description")
  public String getDescription() {
    return description;
  }

  public void setAuthor(ClipshowUser a) {
    if (a == null) {
      return;
    }

    //Just in case we ever change the author for some reason, remove the old author from the allowed users
    if (author != null) {
      removeAllowedUser(author);
      author.removeAuthoredSeries(this);
    }
    author = a;
    addAllowedUser(author);
  }

  @XmlElement(name = "author")
  public ClipshowUser getAuthor() {
    return author;
  }

  public void setClipshows(List<Clipshow> set) {
    if (set != null) {
      shows = set;
    } else {
      shows = new LinkedList<Clipshow>();
    }
  }

  @XmlElement(name = "clipshows")
  public List<Clipshow> getClipshows() {
    return shows;
  }

  public void clearClipshows() {
    if (shows == null) {
      shows = new LinkedList<Clipshow>();
    }

    for (Clipshow c : shows) {
      c.removeSeries(this);
    }
    shows.clear();
    rebuildAllowedUsers();
  }

  public void addClipshow(Clipshow clipshow) {
    if (clipshow == null) {
      return;
    }

    if (shows == null) {
      shows = new LinkedList<Clipshow>();
    }

    shows.add(clipshow);
    clipshow.addSeries(this);
    rebuildAllowedUsers();
  }

  public void removeClipshow(Clipshow clipshow) {
    if (clipshow == null) {
      return;
    }

    if (shows != null && clipshow != null) {
      shows.remove(clipshow);
      clipshow.removeSeries(this);
      rebuildAllowedUsers();
    }
  }

  private void rebuildAllowedUsers() {
    Set<ClipshowUser> users = new LinkedHashSet<ClipshowUser>();
    for (Clipshow c : shows) {
      users.addAll(c.getAllowedUsers());
      setPublicSeries(getPublicSeries() || c.isPublicClipshow());
    }
    setAllowedUsers(users);
  }

  public Boolean containsClipshow(Clipshow c) {
    return shows.contains(c);
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
    user.addMemberOfSeries(this);
  }

  public void removeAllowedUser(ClipshowUser user) {
    if (user == null) {
      return;
    }

    if (allowedUsers == null) {
      allowedUsers = new LinkedHashSet<ClipshowUser>();
    }
    allowedUsers.remove(user);
    user.removeMemberOfSeries(this);
  }

  public Boolean userAllowed(ClipshowUser user) {
    return allowedUsers.contains(user) || getPublicSeries();
  }

  public Boolean getPublicSeries() {
    return publicSeries;
  }

  private void setPublicSeries(Boolean publicSeries) {
    this.publicSeries = publicSeries;
  }

  public Boolean getDeleted() {
    return this.deleted;
  }

  public void setDeleted(Boolean isDeleted) {
    this.deleted = isDeleted;
  }
}
