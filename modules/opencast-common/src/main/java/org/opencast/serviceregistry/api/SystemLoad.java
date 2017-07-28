/**
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

package org.opencast.serviceregistry.api;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

/**
 * Mappings between the registered hosts and their load factors.
 */
@XmlType(name = "load", namespace = "http://serviceregistry.opencastproject.org")
@XmlRootElement(name = "load", namespace = "http://serviceregistry.opencastproject.org")
@XmlAccessorType(XmlAccessType.NONE)
public class SystemLoad {

  /** No-arg constructor needed by JAXB */
  public SystemLoad() {
    nodeLoads = new LinkedHashMap<String, NodeLoad>();
  }

  /** The list of nodes and their current load */
  protected Map<String, NodeLoad> nodeLoads;

  /**
   * Get the list of nodes and their current loadfactor.
   *
   * @return the nodeLoads
   */
  @XmlElementWrapper(name = "nodes")
  @XmlElement(name = "node")
  public Collection<NodeLoad> getNodeLoads() {
    return nodeLoads.values();
  }

  /**
   * Sets the list of nodes and their current loadfactor.
   *
   * @param newLoads
   *          the nodeLoads to set
   */
  public void setNodeLoads(Collection<NodeLoad> newLoads) {
    for (NodeLoad node : newLoads) {
      nodeLoads.put(node.getHost(), node);
    }
  }

  /**
   * Adds a node to the map of load values, overwriting an existing entry if the node is already present in the map
   * @param load
   *          the nodeLoad to add
   */
  public void addNodeLoad(NodeLoad load) {
    nodeLoads.put(load.getHost(), load);
  }

  /**
   * Gets a specific host from the map, if present.  If the node is not present, or the host value is null, this method returns null.
   * @param host
   *        The hostname of the host you are interested in.
   * @return
   *        The specific host from the map, if present.  If the node is not present, or the host value is null, this method returns null.
   */
  public NodeLoad get(String host) {
    if (host != null && this.containsHost(host))
      return nodeLoads.get(host);
    return null;
  }

  /**
   * Returns true if the load map contains the host in question.
   * @param host
   *          The hostname of the host you are interested in.
   * @return
   *          True if the host is present, false if the host is not, or the host variable is null.
   */
  public boolean containsHost(String host) {
    if (host != null)
      return nodeLoads.containsKey(host);
    return false;
  }

  /** A record of a node in the cluster and its load factor */
  @XmlType(name = "nodetype", namespace = "http://serviceregistry.opencastproject.org")
  @XmlRootElement(name = "nodetype", namespace = "http://serviceregistry.opencastproject.org")
  @XmlAccessorType(XmlAccessType.NONE)
  public static class NodeLoad implements Comparable<NodeLoad> {

    /** No-arg constructor needed by JAXB */
    public NodeLoad() {
    }

    public NodeLoad(String host, float load) {
      this.host = host;
      this.loadFactor = load;
    }

    /** This node's base URL */
    @XmlAttribute
    protected String host;

    /** This node's current load */
    @XmlAttribute
    protected float loadFactor;

    /**
     * @return the host
     */
    public String getHost() {
      return host;
    }

    /**
     * @param host
     *          the host to set
     */
    public void setHost(String host) {
      this.host = host;
    }

    /**
     * @return the loadFactor
     */
    public float getLoadFactor() {
      return loadFactor;
    }

    /**
     * @param loadFactor
     *          the loadFactor to set
     */
    public void setLoadFactor(float loadFactor) {
      this.loadFactor = loadFactor;
    }

    @Override
    public int compareTo(NodeLoad other) {
      if (other.getLoadFactor() > this.loadFactor) {
        return 1;
      } else if (this.loadFactor > other.getLoadFactor()) {
        return -1;
      } else {
        return 0;
      }
    }

    public boolean exceeds(NodeLoad other) {
      if (this.compareTo(other) > 1) {
        return true;
      }
      return false;
    }
  }
}
