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

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@Entity
@Table(name = "clips")
@XmlRootElement(name = "clip")
@XmlAccessorType(XmlAccessType.NONE)
public class Clip implements Serializable {

  private static final long serialVersionUID = 1L;

  @Id
  @GeneratedValue(strategy = GenerationType.AUTO)
  @Column(name = "id")
  @XmlElement(name = "id")
  private Long id;

  @Column(name = "start")
  @XmlElement(name = "start")
  private Integer start;

  @Column(name = "stop")
  @XmlElement(name = "stop")
  private Integer stop;
  //Notes?

  public Clip() { }

  public Clip(Integer start, Integer stop) {
    this.start = start;
    this.stop = stop;
  }

  public void setStart(Integer start) {
    this.start = start;
  }

  public Integer getStart() {
    return this.start;
  }

  public void setStop(Integer stop) {
    this.stop = stop;
  }

  public Integer getStop() {
    return this.stop;
  }

  public Long getDatabaseId() {
    return this.id;
  }
}