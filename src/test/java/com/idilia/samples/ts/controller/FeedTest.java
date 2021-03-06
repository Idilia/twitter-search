package com.idilia.samples.ts.controller;

import static org.junit.Assert.assertEquals;

import java.util.List;

import org.junit.Test;

import com.idilia.samples.ts.docs.Document;
import com.idilia.samples.ts.twitter.Tweet;

public class FeedTest {

  /** Test consecutive retrievals from a feed. Documents are not returned twice. */
  @Test
  public void testGetNext() {
    Feed fd = new Feed(FeedType.KEPT, -1);
    fd.add(new FeedDocument(new Tweet(1, "document one")));
    fd.add(new FeedDocument(new Tweet(2, "document two")));
    fd.add(new FeedDocument(new Tweet(3, "document three")));
    assertEquals(3, fd.getNumAvailable());
    assertEquals(3, fd.getNumAssigned());
    
    Document doc = fd.getNext(1).get(0).getDoc();
    assertEquals("document three", doc.getText());
    assertEquals(2, fd.getNumAvailable());
    assertEquals(3, fd.getNumAssigned());
    
    doc = fd.getNext(1).get(0).getDoc();
    assertEquals("document two", doc.getText());
    assertEquals(1, fd.getNumAvailable());
  }
  
  /** Test that adding a negative keyword where no positive ejects the document
   * from the KEPT feed.
   */
  @Test
  public void testFilterOnUserKeyword() {
    Feed fd = new Feed(FeedType.KEPT, -1);
    fd.add(new FeedDocument(new Tweet(1, "document one")));
    fd.add(new FeedDocument(new Tweet(2, "document two")));
    fd.add(new FeedDocument(new Tweet(3, "document three")));
    List<FeedDocument> outDocs = fd.addUserKeyword(KeywordType.NEGATIVE, "two");
    assertEquals(1, outDocs.size());
    assertEquals("document two", outDocs.get(0).getText());
    assertEquals(2, fd.getNumAvailable());
    assertEquals(2, fd.getNumAssigned());
  }
  
  /** Setup a test with a kept document with one positive and one negative keywords.
   * When we remove the positive keyword, the document becomes negative and the
   * feed "ejects" it.
   */
  @Test
  public void testFilterOnFoundUserKeyword() {
    Feed fd = new Feed(FeedType.KEPT, -1);
    fd.add(new FeedDocument(new Tweet(1, "document one")));
    FeedDocument doc = new FeedDocument(new Tweet(2, "document two"));
    fd.add(doc);
    doc.addKeyword(KeywordType.NEGATIVE, "document");
    doc.addKeyword(KeywordType.POSITIVE, "two");
    fd.add(new FeedDocument(new Tweet(3, "document three")));
    List<FeedDocument> outDocs = fd.removeUserKeyword(KeywordType.POSITIVE, "two");
    assertEquals(1, outDocs.size());
    assertEquals("document two", outDocs.get(0).getText());
    assertEquals(2, fd.getNumAvailable());
    assertEquals(2, fd.getNumAssigned());
  }
  
  
  @Test
  public void testLimitedSize() {
    Feed fd = new Feed(FeedType.KEPT, 2);
    fd.add(new FeedDocument(new Tweet(1, "document one")));
    fd.add(new FeedDocument(new Tweet(2, "document two")));
    fd.add(new FeedDocument(new Tweet(3, "document three")));
    assertEquals(2, fd.getNumAvailable());
    assertEquals(3, fd.getNumAssigned());
  }
  
  /** Test matching of keywords with special characters and boundaries */
  @Test
  public void testKeywordMatching() {
    Feed fd = new Feed(FeedType.KEPT, -1);
    FeedDocument doc = new FeedDocument(new Tweet(1, "one (@myten) (#sick) d$r. two"));
    fd.add(doc);
    
    List<FeedDocument> outDocs = fd.addUserKeyword(KeywordType.NEGATIVE, "@myten");
    assertEquals(1, outDocs.size());
    assertEquals(0, fd.getNumAvailable());
    
    fd.add(doc);
    outDocs = fd.addUserKeyword(KeywordType.NEGATIVE, "#sick");
    assertEquals(1, outDocs.size());
    
    fd.add(doc);
    outDocs = fd.addUserKeyword(KeywordType.NEGATIVE, "d$r.");
    assertEquals(1, outDocs.size());
  }
  
  /** Test that adding a user keyword changes the status */
  @Test
  public void testDocumentStatus() {
    // Do once for a kept feed
    {
      Feed feed = new Feed(FeedType.KEPT, -1);
      FeedDocument fd = new FeedDocument(new Tweet(1, "document one"));
      feed.add(fd);
      assertEquals(FeedDocument.Status.INCONCLUSIVE, fd.getStatus());
      List<FeedDocument> outDocs = feed.addUserKeyword(KeywordType.POSITIVE, "one");
      assertEquals(0, outDocs.size());
      assertEquals(FeedDocument.Status.USER_KW_KEPT, fd.getStatus());
    }
    
    // Repeat for a discard feed
    {
      Feed feed = new Feed(FeedType.DISCARDED, -1);
      FeedDocument fd = new FeedDocument(new Tweet(1, "document one"));
      feed.add(fd);
      assertEquals(FeedDocument.Status.INCONCLUSIVE, fd.getStatus());
      List<FeedDocument> outDocs = feed.addUserKeyword(KeywordType.NEGATIVE, "one");
      assertEquals(0, outDocs.size());
      assertEquals(FeedDocument.Status.USER_KW_REJECTED, fd.getStatus());
    }
  }
}
