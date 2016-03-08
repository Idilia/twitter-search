package com.idilia.samples.ts.twitter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.idilia.samples.ts.config.Beans;
import com.idilia.samples.ts.controller.SearchForm;
import com.idilia.samples.ts.docs.Document;
import com.idilia.samples.ts.docs.DocumentSource;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = Beans.class)
public class TwitterDocumentSourceTest {

  // This autowires a TwitterDocumentSource
  @Autowired
  DocumentSource twtSrc;
  
  
  @Test
  public void testGetNextDocuments() {
    SearchForm sf = new SearchForm();
    sf.setIncludeRetweets(true);
    sf.setQuery("@nytimes");
    TwitterSearchToken tk = (TwitterSearchToken) twtSrc.createSearchToken(sf);
    
    CompletableFuture<List<? extends Document>> future = twtSrc.getNextDocuments(tk, 1);
    List<? extends Document> tweets = future.join();
    assertFalse(tk.isFinished());
    assertEquals(1, tweets.size());
    assertNotNull(tk.getMaxId());
    Tweet twt = (Tweet) tweets.get(0);
    assertFalse(twt.getId().isEmpty());
    assertFalse(twt.getText().isEmpty());
    
    // Get 1 more and check that not the same
    future = twtSrc.getNextDocuments(tk, 1);
    List<? extends Document> tweets2 = future.join();

    assertEquals(1, tweets2.size());
    assertNotEquals("Repeated tweet", tweets.get(0), tweets2.get(0));
  }
}
