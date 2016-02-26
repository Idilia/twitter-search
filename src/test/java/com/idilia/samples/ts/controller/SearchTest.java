package com.idilia.samples.ts.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.idilia.samples.ts.config.Beans;
import com.idilia.samples.ts.db.User;
import com.idilia.samples.ts.docs.DocumentSource;
import com.idilia.samples.ts.docs.SearchToken;
import com.idilia.samples.ts.idilia.MatchingEvalService;
import com.idilia.samples.ts.twitter.Tweet;
import com.idilia.tagging.Sense;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = Beans.class)
public class SearchTest {

  @Autowired
  DocumentSource docSrc;
  
  @Autowired
  MatchingEvalService matchSvc;
  
  /** Test adding and removing user keywords */
  @Test
  public void testUserKws() {
    
    User user = User.create();
    Search search = new Search(user, "apple", docSrc, matchSvc);
    assertSame(search.kwsKeep, search.getKeywords(KeywordType.POSITIVE));
    assertSame(search.kwsDiscard, search.getKeywords(KeywordType.NEGATIVE));
    
    search.addKeyword(KeywordType.POSITIVE, "seed");
    assertFalse(search.getKeywords(KeywordType.POSITIVE).isEmpty());
    search.removeKeyword(KeywordType.POSITIVE, "seed");
    assertTrue(search.getKeywords(KeywordType.POSITIVE).isEmpty());
  }
  
  /** Test getting documents */
  @Test
  public void testGetDocument() throws InterruptedException, ExecutionException {
    User user = User.create();
    SearchForm sParms = new SearchForm();
    sParms.setQuery("apple");
    Search search = new Search(user, sParms.getQuery(), docSrc, matchSvc);
    List<Sense> exprSenses = Collections.singletonList(
        new Sense(0, 1, "Apple", "Apple/N8"));
    search.setExpressionSenses(exprSenses);
    SearchToken st = docSrc.createSearchToken(sParms);
    search.start(st, sParms);
    List<FeedDocument> docs = search.getDocuments(FeedType.KEPT, 1, 1);
    assertEquals(1, docs.size());
    
    docs = search.getDocuments(FeedType.DISCARDED, 1, 1);
    assertEquals(1, docs.size());
  }
  
  /** Test document status when removing user keywords */
  @Test
  public void testAddingRemKeyword() {
    User user = User.create();
    SearchForm sParms = new SearchForm();
    sParms.setQuery("apple");
    Search search = new Search(user, sParms.getQuery(), docSrc, matchSvc);
    search.setExpressionSenses(Collections.singletonList(new Sense(0,1,"apple", "APPLE/N21")));
    Feed kept = search.getFeed(FeedType.KEPT), disc = search.getFeed(FeedType.DISCARDED);
    kept.add(new FeedDocument(new Tweet(1L, "a in")));
    disc.add(new FeedDocument(new Tweet(2L, "to in")));
    
    // Add a positive keyword present in both and check that now both are kept with status
    assertTrue(search.addKeyword(KeywordType.POSITIVE, "in"));
    assertEquals(2, kept.getNumAvailable());
    assertEquals(0, disc.getNumAvailable());
    List<FeedDocument> docs = kept.getMatching("in");
    docs.stream().forEach(d -> assertEquals(FeedDocument.Status.USER_KW_KEPT, d.getStatus()));
    
    // Remove the keyword. Not having the seach term "apple" will cause them to be discarded
    assertTrue(sParms.isRejectedOnMissingWords());
    assertTrue(search.removeKeyword(KeywordType.POSITIVE, "in"));
    assertEquals(2, disc.getNumAvailable());
    assertEquals(0, kept.getNumAvailable());
    docs = disc.getMatching("in");
    docs.stream().forEach(d -> assertEquals(FeedDocument.Status.REJECTED, d.getStatus()));
    
    // Add a negative keyword to one of them. They stay discarded
    assertTrue(search.addKeyword(KeywordType.NEGATIVE, "a"));
    assertEquals(2, disc.getNumAvailable());
    assertEquals(FeedDocument.Status.USER_KW_REJECTED, disc.getMatching("a").get(0).getStatus());
    
    // Remove the negative keyword. The state changes but both remain in discarded
    assertTrue(search.removeKeyword(KeywordType.NEGATIVE, "a"));
    docs = disc.getMatching("in");
    docs.stream().forEach(d -> assertEquals(FeedDocument.Status.REJECTED, d.getStatus()));
  }
}
