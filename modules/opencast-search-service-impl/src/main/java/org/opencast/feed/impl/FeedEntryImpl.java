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


package org.opencast.feed.impl;

import org.opencast.feed.api.Category;
import org.opencast.feed.api.Content;
import org.opencast.feed.api.Enclosure;
import org.opencast.feed.api.Feed;
import org.opencast.feed.api.FeedEntry;
import org.opencast.feed.api.FeedExtension;
import org.opencast.feed.api.Link;
import org.opencast.feed.api.Person;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Implementation for a feed entry.
 */
public class FeedEntryImpl implements FeedEntry {

  /** Unique identifier for this entry */
  private String uri = null;

  /** Date where this entry has been updated */
  private Date updatedDate = null;

  /** Title of this entry */
  private Content title = null;

  /** Description for this entry */
  private Content description = null;

  /** Dublin Core Publication date */
  private Date publishedDate = null;

  /** A list of links */
  private List<Link> links = null;

  /** Entry bodies, can come in multiple content types */
  private List<Content> contents = null;

  /** Modules used in this entry */
  private List<FeedExtension> modules = null;

  /** Enclosures */
  private List<Enclosure> enclosures = null;

  /** Authors of this entry */
  private List<Person> authors = null;

  /** Contributors to this entries */
  private List<Person> contributors = null;

  /** Entry categories */
  private List<Category> categories = null;

  /** The containing feed */
  private Feed feed = null;

  /**
   * Creates a new feed entry for the given feed.
   *
   * @param feed
   *          the containing feed
   * @param title
   *          the entry title
   * @param link
   *          link to the orginal resource
   * @param uri
   *          the entry uri
   */
  public FeedEntryImpl(Feed feed, String title, Link link, String uri) {
    this(feed, title, null, link, uri);
  }

  /**
   * Creates a new feed entry for the given feed.
   *
   * @param feed
   *          the containing feed
   * @param title
   *          the entry title
   * @param description
   *          the entry description
   * @param link
   *          link to the orginal resource
   * @param uri
   *          the entry uri
   */
  public FeedEntryImpl(Feed feed, String title, String description, Link link, String uri) {
    if (feed == null)
      throw new IllegalArgumentException("Argument 'feed' must not be null");
    if (StringUtils.isEmpty(title))
      throw new IllegalArgumentException("Entry title must not be null");
    if (link == null || StringUtils.isEmpty(link.getHref()))
      throw new IllegalArgumentException("Entry link must not be null");
    if (StringUtils.isEmpty(uri))
      throw new IllegalArgumentException("Entry uri must not be null");
    this.feed = feed;
    this.title = new ContentImpl(title);
    if (description != null)
      this.description = new ContentImpl(description);
    addLink(link);
    this.uri = uri;
  }

  /**
   * @see org.opencast.feed.api.FeedEntry#getAuthors()
   */
  public List<Person> getAuthors() {
    return authors;
  }

  /**
   * @see org.opencast.feed.api.FeedEntry#getCategories()
   */
  public List<Category> getCategories() {
    return categories;
  }

  /**
   * @see org.opencast.feed.api.FeedEntry#getContents()
   */
  public List<Content> getContents() {
    return contents;
  }

  /**
   * @see org.opencast.feed.api.FeedEntry#getContributors()
   */
  public List<Person> getContributors() {
    return contributors;
  }

  /**
   * @see org.opencast.feed.api.FeedEntry#getDescription()
   */
  public Content getDescription() {
    return description;
  }

  /**
   * @see org.opencast.feed.api.FeedEntry#getEnclosures()
   */
  public List<Enclosure> getEnclosures() {
    return enclosures;
  }

  /**
   * @see org.opencast.feed.api.FeedEntry#getLinks()
   */
  public List<Link> getLinks() {
    return links;
  }

  /**
   * @see org.opencast.feed.api.FeedEntry#getModule(java.lang.String)
   */
  public FeedExtension getModule(String uri) {
    if (modules == null)
      return null;
    for (FeedExtension m : modules)
      if (uri.equals(m.getUri()))
        return m;
    return null;
  }

  /**
   * @see org.opencast.feed.api.FeedEntry#getModules()
   */
  public List<FeedExtension> getModules() {
    return modules;
  }

  /**
   * @see org.opencast.feed.api.FeedEntry#getPublishedDate()
   */
  public Date getPublishedDate() {
    return publishedDate;
  }

  /**
   * @see org.opencast.feed.api.FeedEntry#getTitle()
   */
  public Content getTitle() {
    return title;
  }

  /**
   * @see org.opencast.feed.api.FeedEntry#getUpdatedDate()
   */
  public Date getUpdatedDate() {
    return updatedDate;
  }

  /**
   * @see org.opencast.feed.api.FeedEntry#getUri()
   */
  public String getUri() {
    return uri;
  }

  /**
   * @see org.opencast.feed.api.FeedEntry#addAuthor(org.opencast.feed.api.Person)
   */
  public void addAuthor(Person author) {
    if (authors == null)
      authors = new ArrayList<Person>();
    authors.add(author);
  }

  /**
   * @see org.opencast.feed.api.FeedEntry#setAuthors(java.util.List)
   */
  public void setAuthors(List<Person> authors) {
    this.authors = authors;
  }

  /**
   * @see org.opencast.feed.api.FeedEntry#setCategories(java.util.List)
   */
  public void setCategories(List<Category> categories) {
    this.categories = categories;
  }

  /**
   * @see org.opencast.feed.api.FeedEntry#addCategory(org.opencast.feed.api.Category)
   */
  public void addCategory(Category category) {
    if (categories == null)
      categories = new ArrayList<Category>();
    categories.add(category);
  }

  /**
   * @see org.opencast.feed.api.FeedEntry#setContents(java.util.List)
   */
  public void setContents(List<Content> contents) {
    this.contents = contents;
  }

  /**
   * @see org.opencast.feed.api.FeedEntry#addContent(org.opencast.feed.api.Content)
   */
  public void addContent(Content content) {
    if (contents == null)
      contents = new ArrayList<Content>();
    contents.add(content);
  }

  /**
   * @see org.opencast.feed.api.FeedEntry#setContributors(java.util.List)
   */
  public void setContributors(List<Person> contributors) {
    this.contributors = contributors;
  }

  /**
   * @see org.opencast.feed.api.FeedEntry#addContributor(org.opencast.feed.api.Person)
   */
  public void addContributor(Person contributor) {
    if (contributors == null)
      contributors = new ArrayList<Person>();
    contributors.add(contributor);
  }

  /**
   * @see org.opencast.feed.api.FeedEntry#setDescription(org.opencast.feed.api.Content)
   */
  public void setDescription(Content description) {
    this.description = description;
  }

  /**
   * @see org.opencast.feed.api.FeedEntry#setEnclosures(java.util.List)
   */
  public void setEnclosures(List<Enclosure> enclosures) {
    this.enclosures = enclosures;
  }

  /**
   * @see org.opencast.feed.api.FeedEntry#addEnclosure(org.opencast.feed.api.Enclosure)
   */
  public void addEnclosure(Enclosure enclosure) {
    if (enclosures == null)
      enclosures = new ArrayList<Enclosure>();
    enclosures.add(enclosure);
  }

  /**
   * @see org.opencast.feed.api.FeedEntry#setLinks(java.util.List)
   */
  public void setLinks(List<Link> links) {
    this.links = links;
  }

  /**
   * @see org.opencast.feed.api.FeedEntry#addLink(org.opencast.feed.api.Link)
   */
  public void addLink(Link link) {
    if (links == null)
      links = new ArrayList<Link>();
    links.add(link);
  }

  /**
   * @see org.opencast.feed.api.FeedEntry#setExtensions(java.util.List)
   */
  public void setExtensions(List<FeedExtension> modules) {
    this.modules = modules;
  }

  /**
   * @see org.opencast.feed.api.FeedEntry#addExtension(org.opencast.feed.api.FeedExtension)
   */
  public void addExtension(FeedExtension module) {
    if (modules == null)
      modules = new ArrayList<FeedExtension>();
    modules.add(module);
  }

  /**
   * @see org.opencast.feed.api.FeedEntry#setPublishedDate(java.util.Date)
   */
  public void setPublishedDate(Date publishedDate) {
    this.publishedDate = publishedDate;
  }

  /**
   * @see org.opencast.feed.api.FeedEntry#setTitle(java.lang.String)
   */
  public void setTitle(String title) {
    setTitle(new ContentImpl(title));
  }

  /**
   * @see org.opencast.feed.api.FeedEntry#setTitle(org.opencast.feed.api.Content)
   */
  public void setTitle(Content title) {
    this.title = title;
  }

  /**
   * @see org.opencast.feed.api.FeedEntry#setUpdatedDate(java.util.Date)
   */
  public void setUpdatedDate(Date updatedDate) {
    this.updatedDate = updatedDate;
  }

  /**
   * @see org.opencast.feed.api.FeedEntry#setUri(java.lang.String)
   */
  public void setUri(String uri) {
    this.uri = uri;
  }

  /**
   * @see org.opencast.feed.api.FeedEntry#getSource()
   */
  public Feed getSource() {
    return feed;
  }

  /**
   * {@inheritDoc}
   *
   * @see java.lang.Object#toString()
   */
  @Override
  public String toString() {
    return (title != null) ? title.getValue() : uri;
  }

}
