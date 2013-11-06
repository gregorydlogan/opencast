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

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.Table;

@Entity
@Table(name = "clipshow_votes")
@NamedQueries({
  @NamedQuery(name = "vote", query = "SELECT v FROM ClipshowVote v where v.user = :user and v.clipshow = :clipshow")
})
public class ClipshowVote {

  public enum Type { GOOD, FUNNY, NEUTRAL, DISLIKE }

  @Id
  @GeneratedValue(strategy = GenerationType.AUTO)
  @Column(name = "id")
  private Long id;

  @JoinColumn(name = "clipshow")
  private Clipshow clipshow;

  //@ManyToOne()
  @JoinColumn(name = "user")
  private ClipshowUser user;

  @Column(name = "good")
  private Boolean good = false;

  @Column(name = "funny")
  private Boolean funny = false;

  @Column(name = "dislike")
  private Boolean dislike = false;

  public ClipshowVote() { }

  public ClipshowVote(ClipshowUser u, Clipshow c) {
    setUser(u);
    setClipshow(c);
  }

  public Long getId() {
    return id;
  }

  public void setUser(ClipshowUser u) {
    user = u;
  }

  public ClipshowUser getUser() {
    return user;
  }

  public void setClipshow(Clipshow c) {
    clipshow = c;
  }

  public Clipshow getClipshow() {
    return clipshow;
  }

  public void setGood(Boolean b) {
    good = b;
    dislike = false;
  }

  public Boolean getGood() {
    return good;
  }

  public void setFunny(Boolean b) {
    funny = b;
    dislike = false;
  }

  public Boolean getFunny() {
    return funny;
  }

  public void setDislike(Boolean b) {
    dislike = b;
    good = false;
    funny = false;
  }

  public Boolean getDislike() {
    return dislike;
  }

  public Integer getVoteCount() {
    Integer count = 0;
    if (getGood()) {
      count++;
    }
    if (getFunny()) {
      count++;
    }
    if (getDislike()) {
      count--;
    }
    return count;
  }

  @Override
  public boolean equals(Object o) {
    if (o instanceof ClipshowVote) {
      return ((ClipshowVote) o).getUser().equals(getUser()) && ((ClipshowVote) o).getClipshow().equals(getClipshow());
    } else {
      return false;
    }
  }

  @Override
  public int hashCode() {
    return getUser().hashCode();
  }
}
