/**
 * Workflow service.
 */
@XmlSchema(elementFormDefault = XmlNsForm.QUALIFIED, attributeFormDefault = XmlNsForm.UNQUALIFIED, namespace = "http://workflow.opencast.org", xmlns = {
        @XmlNs(prefix = "mp", namespaceURI = "http://mediapackage.opencast.org"),
        @XmlNs(prefix = "wf", namespaceURI = "http://workflow.opencast.org"),
        @XmlNs(prefix = "sec", namespaceURI = "http://org.opencast.security") })
package org.opencast.workflow.api;

import javax.xml.bind.annotation.XmlNs;
import javax.xml.bind.annotation.XmlNsForm;
import javax.xml.bind.annotation.XmlSchema;

