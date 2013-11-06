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
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

@Entity
@Table(name = "clipshow_user")
@XmlRootElement(name = "clipshow-user")
@XmlAccessorType(XmlAccessType.NONE)
@NamedQueries({
  @NamedQuery(name = "user", query = "SELECT u FROM ClipshowUser u WHERE u.username = :username"),
  @NamedQuery(name = "user.all", query = "SELECT u FROM ClipshowUser u")
})
public class ClipshowUser implements Serializable, Comparable<ClipshowUser> {

  private static final long serialVersionUID = 1L;

  @Id
  @GeneratedValue(strategy = GenerationType.AUTO)
  @Column(name = "id")
  @XmlElement(name = "id")
  private Long id;

  @Column(name = "displayname")
  @XmlElement(name = "name")
  private String displayName;
 
  @Column(name = "username")
  private String username;

  @Column(name = "popularity")
  private Integer popularity;

  @OneToMany(mappedBy = "author", fetch = FetchType.LAZY, cascade = { CascadeType.MERGE, CascadeType.PERSIST })
  @JoinColumn(name = "authored_series_set")
  private Set<ClipshowSeries> authoredSeriesSet;

  @OneToMany(mappedBy = "author", fetch = FetchType.LAZY, cascade = { CascadeType.MERGE, CascadeType.PERSIST })
  @JoinColumn(name = "authored_clipshow_set")
  private Set<Clipshow> authoredClipshowSet;

  @ManyToMany(fetch = FetchType.LAZY, cascade = { CascadeType.MERGE, CascadeType.PERSIST })
  @JoinColumn(name = "series_allowed_users")
  private Set<ClipshowSeries> memberOfSeriesSet;

  @ManyToMany(fetch = FetchType.LAZY, cascade = { CascadeType.MERGE, CascadeType.PERSIST })
  @JoinColumn(name = "clipshow_allowed_users")
  private Set<Clipshow> memberOfClipshowSet;

  @Column(name = "consent")
  @XmlTransient
  private Boolean hasConsented = false;

/*  @ManyToMany(mappedBy = "voters", fetch = FetchType.LAZY)
  @JoinColumn(name = "voted_for_clipshow")
  private Set<Clipshow> votes;
*/
  public ClipshowUser() {
    setAuthoredClipshowSet(new LinkedHashSet<Clipshow>());
    setMemberOfClipshowSet(new LinkedHashSet<Clipshow>());
    setAuthoredSeriesSet(new LinkedHashSet<ClipshowSeries>());
    setMemberOfSeriesSet(new LinkedHashSet<ClipshowSeries>());
  }

  public ClipshowUser(String name) {
    this();
    setUsername(name);
    setDisplayName(name);
  }

  public Long getId() {
    return id;
  }

  public void setId(Long newId) {
    id = newId;
  }

  public void setDisplayName(String name) {
    displayName = name;
  }

  public String getDisplayName() {
    return displayName;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String name) {
    username = name;
  }

  public Set<ClipshowSeries> getAuthoredSeriesSet() {
    return authoredSeriesSet;
  }

  public void setAuthoredSeriesSet(Set<ClipshowSeries> set) {
    if (set != null) {
      authoredSeriesSet = set;
    } else {
      authoredSeriesSet = new LinkedHashSet<ClipshowSeries>();
    }
  }

  public void addAuthoredSeries(ClipshowSeries s) {
    if (authoredSeriesSet  == null) {
      authoredSeriesSet = new LinkedHashSet<ClipshowSeries>();
    }
    authoredSeriesSet.add(s);
    addMemberOfSeries(s);
  }

  public void removeAuthoredSeries(ClipshowSeries s) {
    if (authoredSeriesSet != null) {
      authoredSeriesSet.remove(s);
    }
    removeMemberOfSeries(s);
  }

  public Set<Clipshow> getAuthoredClipshowSet() {
    return authoredClipshowSet;
  }

  public void setAuthoredClipshowSet(Set<Clipshow> set) {
    if (set != null) {
      authoredClipshowSet = set;
    } else {
      authoredClipshowSet = new LinkedHashSet<Clipshow>();
    }
  }

  public void addAuthoredClipshow(Clipshow c) {
    if (authoredClipshowSet  == null) {
      authoredClipshowSet = new LinkedHashSet<Clipshow>();
    }
    authoredClipshowSet.add(c);
    addMemberOfClipshow(c);
  }

  public void removeAuthoredClipshow(Clipshow c) {
    if (authoredClipshowSet != null) {
      authoredClipshowSet.remove(c);
    }
    removeMemberOfClipshow(c);
  }

  public Set<ClipshowSeries> getMemberOfSeriesSet() {
    return memberOfSeriesSet;
  }

  public void setMemberOfSeriesSet(Set<ClipshowSeries> set) {
    if (set != null) {
      memberOfSeriesSet = set;
    } else {
      memberOfSeriesSet = new LinkedHashSet<ClipshowSeries>();
    }
  }

  public void addMemberOfSeries(ClipshowSeries s) {
    if (memberOfSeriesSet == null) {
      memberOfSeriesSet = new LinkedHashSet<ClipshowSeries>();
    }

    memberOfSeriesSet.add(s);
  }

  public void removeMemberOfSeries(ClipshowSeries s) {
    if (memberOfSeriesSet != null) {
      memberOfSeriesSet.remove(s);
    }
  }

  public Set<Clipshow> getMemberOfClipshowSet() {
    return memberOfClipshowSet;
  }

  public void setMemberOfClipshowSet(Set<Clipshow> set) {
    if (set != null) {
      memberOfClipshowSet = set;
    } else {
      memberOfClipshowSet = new LinkedHashSet<Clipshow>();
    }
  }

  public void addMemberOfClipshow(Clipshow c) {
    if (memberOfClipshowSet == null) {
      memberOfClipshowSet = new LinkedHashSet<Clipshow>();
    }

    memberOfClipshowSet.add(c);
  }

  public void removeMemberOfClipshow(Clipshow c) {
    if (memberOfClipshowSet != null) {
      memberOfClipshowSet.remove(c);
    }
  }
  
  public Integer getPopularity() {
    return popularity;
  }

  public void setPopularity(Integer popularity) {
    this.popularity = popularity;
  }

  public Boolean getHasConsented() {
    return hasConsented;
  }

  public void setHasConsented(Boolean newVal) {
    hasConsented = newVal;
  }

  @Override
  public int compareTo(ClipshowUser o) {
    return getUsername().compareTo(o.getUsername());
  }

  @Override
  public boolean equals(Object o) {
    if (o instanceof ClipshowUser) {
      return ((ClipshowUser) o).getUsername().equals(getUsername());
    } else {
      return false;
    }
  }

  @Override
  public int hashCode() {
    return getUsername().hashCode();
  }
}
