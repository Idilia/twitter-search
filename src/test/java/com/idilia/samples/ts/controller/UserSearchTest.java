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
import com.idilia.samples.ts.idilia.MatchingEvalService;
import com.idilia.tagging.Sense;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = Beans.class)
public class UserSearchTest {

  @Autowired
  DocumentSource docSrc;
  
  @Autowired
  MatchingEvalService matchSvc;
  
  @Test
  public void testUserKws() {
    
    User user = User.create();
    UserSearch us = new UserSearch(user, "apple", docSrc, matchSvc);
    assertSame(us.kwsKeep, us.getKeywords(KeywordType.POSITIVE));
    assertSame(us.kwsDiscard, us.getKeywords(KeywordType.NEGATIVE));
    
    us.addKeyword(KeywordType.POSITIVE, "seed");
    assertFalse(us.getKeywords(KeywordType.POSITIVE).isEmpty());
    us.removeKeyword(KeywordType.POSITIVE, "seed");
    assertTrue(us.getKeywords(KeywordType.POSITIVE).isEmpty());
  }
  
  @Test
  public void testGetDocument() throws InterruptedException, ExecutionException {
    User user = User.create();
    UserSearch us = new UserSearch(user, "apple", docSrc, matchSvc);
    List<Sense> exprSenses = Collections.singletonList(
        new Sense(0, 1, "Apple", "Apple/N8"));
    us.setExpressionSenses(exprSenses);
    us.start();
    List<FeedDocument> docs = us.getDocuments(FeedType.KEPT, 1, 1);
    assertEquals(1, docs.size());
    
    docs = us.getDocuments(FeedType.DISCARDED, 1, 1);
    assertEquals(1, docs.size());
  }
}
