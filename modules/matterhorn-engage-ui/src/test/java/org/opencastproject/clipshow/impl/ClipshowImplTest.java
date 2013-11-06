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

import org.opencastproject.clipshow.endpoint.ClipshowInfo;

import com.mchange.v2.c3p0.ComboPooledDataSource;

import junit.framework.Assert;

import org.eclipse.persistence.jpa.PersistenceProvider;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.naming.NoPermissionException;

public class ClipshowImplTest {

  private ClipshowServiceImpl service = null;
  private ComboPooledDataSource pooledDataSource = null;
  private final String authorId = "testUserId";

  public ClipshowImplTest() {
  }

  @Before
  public void setUp() throws Exception {
    pooledDataSource = new ComboPooledDataSource();
    pooledDataSource.setDriverClass("org.h2.Driver");
    pooledDataSource.setJdbcUrl("jdbc:h2:./target/db" + System.currentTimeMillis());
    pooledDataSource.setUser("sa");
    pooledDataSource.setPassword("sa");

    // Collect the persistence properties
    Map<String, Object> props = new HashMap<String, Object>();
    props.put("javax.persistence.nonJtaDataSource", pooledDataSource);
    props.put("eclipselink.ddl-generation", "create-tables");
    props.put("eclipselink.ddl-generation.output-mode", "database");

    service = new ClipshowServiceImpl();
    service.setPersistenceProvider(new PersistenceProvider());
    service.setPersistenceProperties(props);

    service.activate(null);
  }

  @After
  public void tearDown() {
    service.deactivate();
    pooledDataSource.close();
  }

  @Test
  public void testClipshowCreation() throws Exception {
    LinkedList<Clip> clips = new LinkedList<Clip>();
    Clip clip = new Clip(5, 10);
    clips.add(clip);
    clip = new Clip(20, 40);
    clips.add(clip);
    //Create the clipshow
    service.createClipshow("testName", clips, "testMediapackageId", "testMediapackageTitle", authorId);
    
    //List the clipshows and verify that the information is correct
    List<ClipshowInfo> list = service.listClipshows("testMediapackageId", null, authorId);
    Assert.assertEquals(1, list.size());
    ClipshowInfo e = list.get(0);
    Assert.assertEquals("testName", e.getTitle());
    Assert.assertEquals(authorId, e.getAuthor());
    Assert.assertEquals("testMediapackageId", e.getMediapackageId());
    Assert.assertEquals("testMediapackageTitle", e.getMediapackageTitle());

    //Get the clipshow itself and make sure everything is correct
    Clipshow c = service.getClipshow(e.getId().toString(), authorId);
    Assert.assertNotNull(c);
    //Assert.assertEquals(clips, c.getClips());
    Assert.assertEquals(authorId, c.getAuthor().getDisplayName());
    Assert.assertEquals("testName", c.getTitle());
    Assert.assertEquals("testMediapackageId", c.getMediapackageId());
    Assert.assertEquals(1, c.getAllowedUsers().size());
    Assert.assertEquals(authorId, c.getAllowedUsers().iterator().next().getUsername());
  }

  @Test
  public void testSeriesCreation() throws Exception {
    //Create the series
    service.createSeries("testSeriesName", null, authorId);

    List<ClipshowInfo> list = service.listSeries(null, authorId);
    Assert.assertEquals(1, list.size());
    ClipshowInfo e = list.get(0);
    Assert.assertEquals("testSeriesName", e.getTitle());
    Assert.assertEquals(authorId, e.getAuthor());

    ClipshowSeries s = service.getSeries(e.getId().toString(), authorId);
    Assert.assertNotNull(s);
    Assert.assertEquals("testSeriesName", s.getTitle());
    Assert.assertEquals(authorId, s.getAuthor().getUsername());
    Assert.assertEquals(0, s.getClipshows().size());
    Assert.assertEquals(1, s.getAllowedUsers().size());
    Assert.assertEquals(authorId, s.getAllowedUsers().iterator().next().getUsername());
  }

  @Test @Ignore
  public void testClipshowVoting() throws Exception {
    testClipshowCreation();
    //List the clipshows
    List<ClipshowInfo> list = service.listClipshows("testMediapackageId", null, authorId);
    ClipshowInfo e = list.get(0);
    //Get the clipshow itself
    Clipshow c = service.getClipshow(e.getId().toString(), authorId);
    String clipshowId = c.getId().toString();

    //Test to make sure votes are counted
    service.voteForClipshow(authorId, ClipshowVote.Type.FUNNY.toString(), clipshowId);
    assertVoteCounts(0, 1, 0, service.getVotesForClipshow(clipshowId, authorId));
    assertVoteCounts(0, 0, 0, service.getVotesForClipshow(clipshowId, "testUserId2"));
    assertVoteCounts(0, 1, 0, service.getVotesForClipshow(clipshowId));

    //Test to make sure that duplicate votes are only counted once
    service.voteForClipshow(authorId, ClipshowVote.Type.FUNNY.toString(), clipshowId);
    assertVoteCounts(0, 1, 0, service.getVotesForClipshow(clipshowId, authorId));
    assertVoteCounts(0, 0, 0, service.getVotesForClipshow(clipshowId, "testUserId2"));
    assertVoteCounts(0, 1, 0, service.getVotesForClipshow(clipshowId));

    //Test to make sure that multiple voters work as expected
    try {
      service.voteForClipshow("testUserId2", ClipshowVote.Type.GOOD.toString(), clipshowId);
    } catch (NoPermissionException ex) {
      //Good, we should not be able to vote on something we do not have the permissions for!
      service.addUserToClipshow("testUserId2", clipshowId, authorId);
      service.voteForClipshow("testUserId2", ClipshowVote.Type.GOOD.toString(), clipshowId);
    }
    assertVoteCounts(0, 1, 0, service.getVotesForClipshow(clipshowId, authorId));
    assertVoteCounts(1, 0, 0, service.getVotesForClipshow(clipshowId, "testUserId2"));
    assertVoteCounts(1, 1, 0, service.getVotesForClipshow(clipshowId));
    service.voteForClipshow(authorId, ClipshowVote.Type.GOOD.toString(), clipshowId);
    assertVoteCounts(1, 0, 0, service.getVotesForClipshow(clipshowId, authorId));
    assertVoteCounts(1, 0, 0, service.getVotesForClipshow(clipshowId, "testUserId2"));
    assertVoteCounts(2, 0, 0, service.getVotesForClipshow(clipshowId));
    service.voteForClipshow(authorId, ClipshowVote.Type.FUNNY.toString() + "," + ClipshowVote.Type.GOOD, clipshowId);
    assertVoteCounts(1, 1, 0, service.getVotesForClipshow(clipshowId, authorId));
    assertVoteCounts(1, 0, 0, service.getVotesForClipshow(clipshowId, "testUserId2"));
    assertVoteCounts(2, 1, 0, service.getVotesForClipshow(clipshowId));

    service.addUserToClipshow("testUserId3", clipshowId, authorId);
    service.voteForClipshow("testUserId3", ClipshowVote.Type.DISLIKE.toString(), clipshowId);
    assertVoteCounts(1, 1, 0, service.getVotesForClipshow(clipshowId, authorId));
    assertVoteCounts(1, 0, 0, service.getVotesForClipshow(clipshowId, "testUserId2"));
    assertVoteCounts(0, 0, 1, service.getVotesForClipshow(clipshowId, "testUserId3"));
    assertVoteCounts(2, 1, 1, service.getVotesForClipshow(clipshowId));


    //Test removing a vote
    service.voteForClipshow(authorId, "", clipshowId);
    assertVoteCounts(0, 0, 0, service.getVotesForClipshow(clipshowId, authorId));
    assertVoteCounts(1, 0, 0, service.getVotesForClipshow(clipshowId, "testUserId2"));
    assertVoteCounts(0, 0, 1, service.getVotesForClipshow(clipshowId, "testUserId3"));
    assertVoteCounts(1, 0, 1, service.getVotesForClipshow(clipshowId));

    service.voteForClipshow("testUserId2", "NEUTRAL", clipshowId);
    assertVoteCounts(0, 0, 0, service.getVotesForClipshow(clipshowId, authorId));
    assertVoteCounts(0, 0, 0, service.getVotesForClipshow(clipshowId, "testUserId2"));
    assertVoteCounts(0, 0, 1, service.getVotesForClipshow(clipshowId, "testUserId3"));
    assertVoteCounts(0, 0, 1, service.getVotesForClipshow(clipshowId));
  }

  private void assertVoteCounts(Integer good, Integer funny, Integer dislike, Map<ClipshowVote.Type, Integer> votes) {
    Assert.assertEquals(good, votes.get(ClipshowVote.Type.GOOD));
    Assert.assertEquals(funny, votes.get(ClipshowVote.Type.FUNNY));
    Assert.assertEquals(dislike, votes.get(ClipshowVote.Type.DISLIKE));
  }
}
