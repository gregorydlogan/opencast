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

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "vote-counts")
public class ClipshowVoteCounts {

  @XmlElement(name = "good")
  private Integer good;
  
  @XmlElement(name = "funny")
  private Integer funny;

  @XmlElement(name = "dislike")
  private Integer dislike;

  public ClipshowVoteCounts() { }
  public ClipshowVoteCounts(Integer g, Integer f, Integer d) {
    good = g;
    funny = f;
    dislike = d;
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
}
