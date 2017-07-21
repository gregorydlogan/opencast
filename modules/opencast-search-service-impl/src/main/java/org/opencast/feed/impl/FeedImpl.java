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
import org.opencast.feed.api.Feed;
import org.opencast.feed.api.FeedEntry;
import org.opencast.feed.api.FeedExtension;
import org.opencast.feed.api.Image;
import org.opencast.feed.api.Link;
import org.opencast.feed.api.Person;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Default feed implementation.
 */
public class FeedImpl implements Feed {

  /** Feed enconding, defaults to utf-8 */
  private String encoding = "utf-8";

  /** Unique uri */
  private String uri = null;

  /** The feed title */
  private Content title = null;

  /** The feed description */
  private Content description = null;

  /** Copyright disclaimer */
  private String copyright = null;

  /** Dublin Core Language */
  private String language = null;

  /** Dublin Core Publication date */
  private Date publishedDate = null;

  /** Date when the feed has bee updated */
  private Date updatedDate = null;

  /** Dublin core categories */
  private List<Category> categories = null;

  /** Additional links */
  private List<Link> links = null;

  /** Feed image */
  private Image image = null;

  /** The feed entries */
  private List<FeedEntry> entries = null;

  /** Modules that are used in this feed */
  private List<FeedExtension> modules = null;

  /** The list of authors */
  private List<Person> authors = null;

  /** The list of contributors */
  private List<Person> contributors = null;

  /** Link to the feed homepage */
  private String link = null;

  /** The feed type */
  private Type type = null;

  /**
   * Constructor used to create a new feed with the given uri and title.
   *
   * @param type
   *          feed type
   * @param uri
   *          the feed uri
   * @param title
   *          the feed title
   * @param description
   *          the feed description
   * @param link
   *          the link to the feed homepage
   */
  FeedImpl(Type type, String uri, Content title, Content description, String link) {
    this.type = type;
    this.uri = uri;
    this.title = title;
    this.description = description;
    this.link = link;
  }

  /**
   * {@inheritDoc}
   *
   * @see org.opencast.feed.api.Feed#getType()
   */
  public Type getType() {
    return type;
  }

  /**
   * {@inheritDoc}
   *
   * @see org.opencast.feed.api.Feed#getLink()
   */
  public String getLink() {
    return link;
  }

  /**
   * {@inheritDoc}
   *
   * @see org.opencast.feed.api.Feed#setLink(java.lang.String)
   */
  public void setLink(String link) {
    this.link = link;
  }

  /**
   * {@inheritDoc}
   *
   * @see org.opencast.feed.api.Feed#addAuthor(org.opencast.feed.api.Person)
   */
  public void addAuthor(Person author) {
    if (authors == null)
      authors = new ArrayList<Person>();
    authors.add(author);
  }

  /**
   * {@inheritDoc}
   *
   * @see org.opencast.feed.api.Feed#addContributor(org.opencast.feed.api.Person)
   */
  public void addContributor(Person contributor) {
    if (contributors == null)
      contributors = new ArrayList<Person>();
    contributors.add(contributor);
  }

  /**
   * {@inheritDoc}
   *
   * @see org.opencast.feed.api.Feed#addEntry(org.opencast.feed.api.FeedEntry)
   */
  public void addEntry(FeedEntry entry) {
    if (entries == null)
      entries = new ArrayList<FeedEntry>();
    entries.add(entry);
  }

  /**
   * {@inheritDoc}
   *
   * @see org.opencast.feed.api.Feed#addLink(org.opencast.feed.api.Link)
   */
  public void addLink(Link link) {
    if (links == null)
      links = new ArrayList<Link>();
    links.add(link);
  }

  /**
   * {@inheritDoc}
   *
   * @see org.opencast.feed.api.Feed#addModule(org.opencast.feed.api.FeedExtension)
   */
  public void addModule(FeedExtension module) {
    if (modules == null)
      modules = new ArrayList<FeedExtension>();
    modules.add(module);
  }

  /**
   * {@inheritDoc}
   *
   * @see org.opencast.feed.api.Feed#getAuthors()
   */
  public List<Person> getAuthors() {
    return authors;
  }

  /**
   * {@inheritDoc}
   *
   * @see org.opencast.feed.api.Feed#getCategories()
   */
  public List<Category> getCategories() {
    return categories;
  }

  /**
   * {@inheritDoc}
   *
   * @see org.opencast.feed.api.Feed#getContributors()
   */
  public List<Person> getContributors() {
    return contributors;
  }

  /**
   * {@inheritDoc}
   *
   * @see org.opencast.feed.api.Feed#getCopyright()
   */
  public String getCopyright() {
    return copyright;
  }

  /**
   * {@inheritDoc}
   *
   * @see org.opencast.feed.api.Feed#getDescription()
   */
  public Content getDescription() {
    return description;
  }

  /**
   * {@inheritDoc}
   *
   * @see org.opencast.feed.api.Feed#getEncoding()
   */
  public String getEncoding() {
    return encoding;
  }

  /**
   * {@inheritDoc}
   *
   * @see org.opencast.feed.api.Feed#getEntries()
   */
  public List<FeedEntry> getEntries() {
    return entries;
  }

  /**
   * {@inheritDoc}
   *
   * @see org.opencast.feed.api.Feed#getImage()
   */
  public Image getImage() {
    return image;
  }

  /**
   * {@inheritDoc}
   *
   * @see org.opencast.feed.api.Feed#getLanguage()
   */
  public String getLanguage() {
    return language;
  }

  /**
   * {@inheritDoc}
   *
   * @see org.opencast.feed.api.Feed#getLinks()
   */
  public List<Link> getLinks() {
    return links;
  }

  /**
   * {@inheritDoc}
   *
   * @see org.opencast.feed.api.Feed#getModule(java.lang.String)
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
   * {@inheritDoc}
   *
   * @see org.opencast.feed.api.Feed#getModules()
   */
  public List<FeedExtension> getModules() {
    return modules;
  }

  /**
   * {@inheritDoc}
   *
   * @see org.opencast.feed.api.Feed#getPublishedDate()
   */
  public Date getPublishedDate() {
    return publishedDate;
  }

  /**
   * {@inheritDoc}
   *
   * @see org.opencast.feed.api.Feed#getUpdatedDate()
   */
  public Date getUpdatedDate() {
    return updatedDate;
  }

  /**
   * {@inheritDoc}
   *
   * @see org.opencast.feed.api.Feed#getTitle()
   */
  public Content getTitle() {
    return title;
  }

  /**
   * {@inheritDoc}
   *
   * @see org.opencast.feed.api.Feed#getUri()
   */
  public String getUri() {
    return uri;
  }

  /**
   * {@inheritDoc}
   *
   * @see org.opencast.feed.api.Feed#setAuthors(java.util.List)
   */
  public void setAuthors(List<Person> authors) {
    this.authors = authors;
  }

  /**
   * {@inheritDoc}
   *
   * @see org.opencast.feed.api.Feed#setCategories(java.util.List)
   */
  public void setCategories(List<Category> categories) {
    this.categories = categories;
  }

  /**
   * {@inheritDoc}
   *
   * @see org.opencast.feed.api.Feed#setContributors(java.util.List)
   */
  public void setContributors(List<Person> contributors) {
    this.contributors = contributors;
  }

  /**
   * {@inheritDoc}
   *
   * @see org.opencast.feed.api.Feed#setCopyright(java.lang.String)
   */
  public void setCopyright(String copyright) {
    this.copyright = copyright;
  }

  /**
   * {@inheritDoc}
   *
   * @see org.opencast.feed.api.Feed#setDescription(java.lang.String)
   */
  public void setDescription(String description) {
    this.description = new ContentImpl(description);
  }

  /**
   * {@inheritDoc}
   *
   * @see org.opencast.feed.api.Feed#setDescription(org.opencast.feed.api.Content)
   */
  public void setDescription(Content description) {
    this.description = description;
  }

  /**
   * {@inheritDoc}
   *
   * @see org.opencast.feed.api.Feed#setEncoding(java.lang.String)
   */
  public void setEncoding(String encoding) {
    this.encoding = encoding;
  }

  /**
   * {@inheritDoc}
   *
   * @see org.opencast.feed.api.Feed#setEntries(java.util.List)
   */
  public void setEntries(List<FeedEntry> entries) {
    this.entries = entries;
  }

  /**
   * {@inheritDoc}
   *
   * @see org.opencast.feed.api.Feed#setImage(org.opencast.feed.api.Image)
   */
  public void setImage(Image image) {
    this.image = image;
  }

  /**
   * {@inheritDoc}
   *
   * @see org.opencast.feed.api.Feed#setLanguage(java.lang.String)
   */
  public void setLanguage(String language) {
    this.language = language;
  }

  /**
   * {@inheritDoc}
   *
   * @see org.opencast.feed.api.Feed#setLinks(java.util.List)
   */
  public void setLinks(List<Link> links) {
    this.links = links;
  }

  /**
   * {@inheritDoc}
   *
   * @see org.opencast.feed.api.Feed#setModules(java.util.List)
   */
  public void setModules(List<FeedExtension> modules) {
    this.modules = modules;
  }

  /**
   * {@inheritDoc}
   *
   * @see org.opencast.feed.api.Feed#setPublishedDate(java.util.Date)
   */
  public void setPublishedDate(Date publishedDate) {
    this.publishedDate = publishedDate;
  }

  /**
   * {@inheritDoc}
   *
   * @see org.opencast.feed.api.Feed#setPublishedDate(java.util.Date)
   */
  public void setUpdatedDate(Date updatedDate) {
    this.updatedDate = updatedDate;
  }

  /**
   * {@inheritDoc}
   *
   * @see org.opencast.feed.api.Feed#setTitle(java.lang.String)
   */
  public void setTitle(String title) {
    this.title = new ContentImpl(title);
  }

  /**
   * {@inheritDoc}
   *
   * @see org.opencast.feed.api.Feed#setTitle(org.opencast.feed.api.Content)
   */
  public void setTitle(Content title) {
    this.title = title;
  }

  /**
   * {@inheritDoc}
   *
   * @see org.opencast.feed.api.Feed#setUri(java.lang.String)
   */
  public void setUri(String uri) {
    this.uri = uri;
  }

}
