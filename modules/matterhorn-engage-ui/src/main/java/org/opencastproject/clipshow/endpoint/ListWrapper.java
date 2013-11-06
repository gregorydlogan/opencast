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

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "wrapper")
public class ListWrapper {

  @XmlElement(name = "data")
  private List<ClipshowInfo> data;

  public ListWrapper() {
    data = new LinkedList<ClipshowInfo>();
  }

  public ListWrapper(List<ClipshowInfo> clipshows) {
    data = clipshows;
    Collections.sort(data);
  }

  public List<ClipshowInfo> getList() {
    return data;
  }
}
