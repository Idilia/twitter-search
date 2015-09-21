package com.idilia.samples.ts.twitter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.idilia.samples.ts.config.Beans;
import com.idilia.samples.ts.docs.Document;
import com.idilia.samples.ts.docs.SearchToken;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = Beans.class)
public class TwitterDocumentSourceTest {

  @Autowired
  OAuthCredentials creds;
  
  TwitterDocumentSource twtSrc;
  
  @Before
  public void initClass() {
    if (twtSrc == null)
      twtSrc = new TwitterDocumentSource(creds);
  }
  
  @Test
  public void testGetNextDocuments() {
    SearchToken tk = twtSrc.createSearchToken("apple");
    
    CompletableFuture<List<? extends Document>> future = twtSrc.getNextDocuments(tk, 5);
    List<? extends Document> tweets = future.join();
    assertEquals(5, tweets.size());
    Tweet twt = (Tweet) tweets.get(0);
    assertFalse(twt.getId().isEmpty());
    assertFalse(twt.getText().isEmpty());
    
    // Get 5 more and check that not the same
    future = twtSrc.getNextDocuments(tk, 5);
    List<? extends Document> tweets2 = future.join();

    assertEquals(5, tweets2.size());
    assertNotEquals("Repeated tweet", tweets.get(0), tweets2.get(0));
  }
}
