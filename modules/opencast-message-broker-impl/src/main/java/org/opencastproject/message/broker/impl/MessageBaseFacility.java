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

package org.opencastproject.message.broker.impl;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.transport.TransportListener;
import org.apache.commons.lang3.StringUtils;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Date;

import javax.jms.Connection;
import javax.jms.DeliveryMode;
import javax.jms.JMSException;
import javax.jms.MessageProducer;
import javax.jms.Session;

/**
 * This is a base facility that handles connections and sessions to an ActiveMQ message broker.
 */
public class MessageBaseFacility {

  /** The key to find the URL to connect to the ActiveMQ Message Broker */
  protected static final String ACTIVEMQ_BROKER_URL_KEY = "activemq.broker.url";

  /** The key to find the username to connect to the ActiveMQ Message Broker */
  protected static final String ACTIVEMQ_BROKER_USERNAME_KEY = "activemq.broker.username";

  /** The key to find the password to connect to the ActiveMQ Message Broker */
  protected static final String ACTIVEMQ_BROKER_PASSWORD_KEY = "activemq.broker.password";

  /** Default Broker URL */
  private static final String ACTIVEMQ_DEFAULT_URL
    = "failover://(tcp://localhost:61616)?initialReconnectDelay=2000&maxReconnectAttempts=2";

  /** Minimum time in milliseconds between two connection attempts */
  private static final long MIN_RECONNECT_TIME = 60000;

  /** The logging facility */
  private static final Logger logger = LoggerFactory.getLogger(MessageBaseFacility.class);

  /** Create a ConnectionFactory for establishing connections to the Active MQ broker */
  private ActiveMQConnectionFactory connectionFactory = null;

  /** The connection to the ActiveMQ broker */
  private Connection connection = null;

  /** Session used to communicate with the ActiveMQ broker */
  private Session session = null;

  /** The message producer */
  private MessageProducer producer = null;

  /** Time of last connection attempt */
  private long lastConnection = 0;

  /** Disabled state of the JMS connection. Used to ignore connection errors. */
  protected boolean enabled = false;

  /** Connection details */
  private String url = ACTIVEMQ_DEFAULT_URL;
  private String username = null;
  private String password = null;

  /** OSGi component activate callback */
  public void activate(BundleContext bc) throws Exception {
    final String name = this.getClass().getSimpleName();
    url = bc.getProperty(ACTIVEMQ_BROKER_URL_KEY);
    if (StringUtils.isBlank(url)) {
      logger.info("No valid URL found. Using default URL");
      url = ACTIVEMQ_DEFAULT_URL;
    }
    username = bc.getProperty(ACTIVEMQ_BROKER_USERNAME_KEY);
    password = bc.getProperty(ACTIVEMQ_BROKER_PASSWORD_KEY);

    logger.info("{} is configured to connect with URL {}", name, url);
    enable(true);
    if (connectMessageBroker()) {
      logger.info("{} service successfully started", name);
    }
  }

  /** OSGi component deactivate callback */
  public void deactivate() {
    logger.info("{} service is stopping...", this.getClass().getSimpleName());
    enable(false);
    disconnectMessageBroker();
    logger.info("{} service successfully stopped", this.getClass().getSimpleName());
  }

  /** Opens new sessions and connections to the message broker */
  protected synchronized boolean connectMessageBroker() {
    if (!this.enabled) {
      return false;
    }
    if (isConnected()) {
      return true;
    }

    /* Wait between reconnection attempts */
    long time = (new Date()).getTime();
    if (time - lastConnection < MIN_RECONNECT_TIME) {
      try {
        Thread.sleep(MIN_RECONNECT_TIME - time + lastConnection);
      } catch (Exception e) {
      }
    }
    lastConnection = (new Date()).getTime();

    disconnectMessageBroker(false);
    try {
      connectionFactory = new ActiveMQConnectionFactory(url);
      if (StringUtils.isNotBlank(username) && StringUtils.isNotBlank(password)) {
        connectionFactory.setUserName(username);
        connectionFactory.setPassword(password);
      }
      connectionFactory.setTransportListener(new TransportListener() {
        @Override
        public void transportResumed() {
          logger.info("Connection to ActiveMQ is working");
        }

        @Override
        public void transportInterupted() {
          logger.error("Connection to ActiveMQ message broker interupted ({}, username: {})",
              new Object[] {url, username});
        }

        @Override
        public void onException(IOException ex) {
          logger.error("ActiveMQ transport exception: {}", ex.getMessage());
        }

        @Override
        public void onCommand(Object obj) {
          logger.trace("ActiveMQ command: {}", obj);
        }
      });

      logger.info("Starting connection to ActiveMQ message broker, waiting until connection is established...");
      connection = connectionFactory.createConnection();
      connection.start();

      session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

      producer = session.createProducer(null);
      producer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);
    } catch (JMSException e) {
      logger.error("Failed connecting to ActiveMQ message broker using url '{}'", url);
      /* Make sure to set session, etc. to null if connecting failed */
      disconnectMessageBroker(false);
      return false;
    }
    logger.info("Connection to ActiveMQ message broker successfully started");
    return true;
  }

  /** Closes all open sessions and connections to the message broker */
  protected void disconnectMessageBroker() {
    disconnectMessageBroker(true);
  }

  /** Closes all open sessions and connections to the message broker */
  protected void disconnectMessageBroker(final boolean verbose) {
    if (producer != null || session != null || connection != null) {
      if (verbose) {
        logger.info("Stopping connection to ActiveMQ message broker...");
      }

      try {
        if (producer != null) {
          producer.close();
        }
      } catch (JMSException e) {
        if (verbose) {
          logger.error("Error while trying to close producer: {}", e);
        }
      }
      producer = null;

      try {
        if (session != null) {
          session.close();
        }
      } catch (JMSException e) {
        if (verbose) {
          logger.error("Error while trying to close session: {}", e);
        }
      }
      session = null;

      try {
        if (connection != null) {
          connection.close();
        }
      } catch (JMSException e) {
        if (verbose) {
          logger.error("Error while trying to close session: {}", e);
        }
      }
      connection = null;

      if (verbose) {
        logger.info("Connection to ActiveMQ message broker successfully stopped");
      }
    }
  }

  /**
   * Returns an open session or {@code null} if the facility is not yet connected.
   */
  protected Session getSession() {
    return session;
  }

  /**
   * Return if there is a connection to the message broker.
   */
  public boolean isConnected() {
    try {
      connection.getMetaData();
      return this.enabled;
    } catch (Exception e) {
      return false;
    }
  }

  /**
   * Try reconnecting if there was no reconnection attempt in the last MIN_RECONNECT_TIME.
   */
  public boolean reconnect() {
    if (isConnected()) {
      return true;
    }
    if ((new Date()).getTime() - lastConnection >= MIN_RECONNECT_TIME) {
      return connectMessageBroker();
    }
    return false;
  }

  public void enable(final boolean state) {
    this.enabled = state;
  }

  /**
   * Returns an anonymous message producer or {@code null} if the facility is not yet connected.
   * <p>
   * The destination needs to be defined when sending the message ({@code producer.send(destination, message)})
   */
  protected MessageProducer getMessageProducer() {
    return producer;
  }

}
