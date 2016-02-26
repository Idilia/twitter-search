package com.idilia.samples.ts.controller;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Represent a list of documents presented together. In this application, we
 * have a feed for documents that match the search query and another feed for
 * those that did not match.
 * 
 * To prevent the memory from growing beyond reasonable bounds when the number
 * of documents matching and documents not matching is very asymmetrical, the
 * feeds can be bounded.
 *
 */
public class Feed {

  final private FeedType feedType;

  /**
   * The documents still available for retrieval. Key of map is the
   * Document.getId() string to maintain ordering based on that value
   */
  final private TreeMap<String, FeedDocument> docs;

  /** Total of the number of documents retrieved and those still available */
  final private AtomicInteger numAssigned;

  /** Maximum number of documents to store. */
  final private int maxSize;

  /**
   * Constructor
   * 
   * @param feedType type of the feed
   * @param maxSize maximum number of documents to retain. -1 for no limit.
   */
  Feed(FeedType feedType, int maxSize) {
    this.feedType = feedType;
    docs = new TreeMap<>(Collections.reverseOrder());
    numAssigned = new AtomicInteger();
    this.maxSize = maxSize;
  }

  /**
   * @return the historical number of documents assigned to this feed.
   */
  int getNumAssigned() {
    return numAssigned.get();
  }

  /**
   * Add the difference to the current value and return the updated value. This
   * is used when we move document from one feed to another as user keywords are
   * added/removed.
   * <p>
   * 
   * @param diff difference to add to the feed. Can be a negative number.
   * @return new value for historical number of documents assigned to the feed
   */
  int adjNumAssigned(int diff) {
    return numAssigned.addAndGet(diff);
  }

  /**
   * Return true if the feed is unbounded, i.e., stores an unlimited number of
   * documents.
   */
  boolean isBounded() {
    return maxSize > 0;
  }

  /**
   * Return true when the feed cannot store more documents.
   */
  boolean isFull() {
    return isBounded() && getNumAvailable() >= maxSize;
  }

  /**
   * Return the number of documents currently available in the feed.
   */
  synchronized int getNumAvailable() {
    return docs.size();
  }

  /**
   * Add a new document to the feed. If full, does not store. In all cases
   * update the historical number of stored documents.
   * <p>
   * 
   * @param doc document to store
   */
  synchronized void add(FeedDocument doc) {
    if (!isFull())
      docs.put(doc.getDoc().getId(), doc);
    numAssigned.incrementAndGet();
  }

  /**
   * Add new documents to the feed. If full, does not store. In all cases update
   * the historical number of stored documents.
   * <p>
   * 
   * @param ds documents to store
   */
  synchronized void addAll(List<FeedDocument> ds) {
    if (!isFull())
      for (FeedDocument d : ds)
        this.docs.put(d.getDoc().getId(), d);
    numAssigned.addAndGet(ds.size());
  }

  /**
   * Remove the requested number of documents at the front of the collection and
   * return them. If the number of available documents is fewer than the maximum
   * requested, returns what is available.
   * <p>
   * 
   * @param max maximum number of documents to return
   * @return list with documents. May be empty or shorter than requested
   *         maximum.
   */
  synchronized List<FeedDocument> getNext(int max) {
    if (docs.isEmpty())
      return Collections.emptyList();
    List<FeedDocument> res = new ArrayList<>(max);
    for (Iterator<Map.Entry<String, FeedDocument>> docIt = docs.entrySet()
        .iterator(); docIt.hasNext();) {
      res.add(docIt.next().getValue());
      docIt.remove();
      if (res.size() == max)
        break;
    }
    return res;
  }

  /**
   * Traverse the document collection to attempt matching the given keyword in
   * the text of the document. If it is found, the document is returned but not
   * removed from the collection
   * <p>
   * 
   * @param kw Keyword to search for
   * @return The documents removed because they contained the keyword. Ordered
   *         by id.
   */
  synchronized List<FeedDocument> getMatching(String kw) {
    if (docs.isEmpty())
      return Collections.emptyList();
    Pattern re = getKeywordRe(kw);
    return docs.values().stream().filter(d -> re.matcher(d.getDoc().getText()).find()).collect(Collectors.toList());
  }

  /**
   * Traverse the document collection to attempt matching the added given
   * keyword in the text of the document. Should the classification of the
   * document change base on having added the keywords, the document is
   * returned.
   * <p>
   * 
   * @param kwType type of keyword being added
   * @param kw Keyword to search for
   * @return The documents removed because they contained the keyword.
   */
  synchronized List<FeedDocument> addUserKeyword(KeywordType kwType, String kw) {
    if (docs.isEmpty())
      return Collections.emptyList();
    List<FeedDocument> toMove = new ArrayList<>();
    Pattern re = getKeywordRe(kw);
    for (Iterator<Map.Entry<String, FeedDocument>> docIt = docs.entrySet()
        .iterator(); docIt.hasNext();) {

      FeedDocument doc = docIt.next().getValue();
      if (re.matcher(doc.getDoc().getText()).find()) {
        if (doc.addKeyword(kwType, kw)) {
          int rc = doc.getClassificationFromUserKeywords();
          if ((feedType == FeedType.DISCARDED && rc > 0)
              || (feedType == FeedType.KEPT && rc < 0)) {
            toMove.add(doc);
            docIt.remove();
          } else {
            doc.setStatus(kwType == KeywordType.NEGATIVE ? FeedDocument.Status.USER_KW_REJECTED
                : FeedDocument.Status.USER_KW_KEPT);
          }
        }
      }
    }

    numAssigned.addAndGet(toMove.size() * -1);
    return toMove;
  }

  /**
   * Helper function to generate a regular expression for matching the given
   * keyword
   * 
   * @param kw string of the keyword to match
   * @return a regex that will match the keyword on word boundaries, case
   *         insensitive
   */
  private static Pattern getKeywordRe(String kw) {
    StringBuilder sb = new StringBuilder();
    if (Character.isAlphabetic(kw.charAt(0)))
      sb.append("\\b");
    sb.append(Pattern.quote(kw));
    if (Character.isAlphabetic(kw.charAt(kw.length() - 1)))
      sb.append("\\b");
    return Pattern.compile(sb.toString(), Pattern.CASE_INSENSITIVE);
  }

  /**
   * Traverse the document collection to remove the given keyword. Should the
   * classification of the document change base on having removed the keywords,
   * the document is returned.
   * <p>
   * The keyword is matched in the collection of keywords recorded for each
   * document. We don't need to look at the text.
   * 
   * @param kwType type of keyword being removed
   * @param kw Keyword to search for
   * @return The documents removed because they had the keyword.
   */
  synchronized List<FeedDocument> removeUserKeyword(KeywordType kwType,
      String kw) {
    if (docs.isEmpty())
      return Collections.emptyList();
    List<FeedDocument> toMove = new ArrayList<>();
    for (Iterator<Map.Entry<String, FeedDocument>> docIt = docs.entrySet()
        .iterator(); docIt.hasNext();) {

      FeedDocument doc = docIt.next().getValue();
      if (doc.removeKeyword(kwType, kw)) {
        int rc = doc.getClassificationFromUserKeywords();
        if (rc == 0 || (feedType == FeedType.DISCARDED && rc > 0)
            || (feedType == FeedType.KEPT && rc < 0)) {
          toMove.add(doc);
          docIt.remove();
        }
      }
    }
    numAssigned.addAndGet(toMove.size() * -1);
    return toMove;
  }

  /**
   * Reset the feed to its initial state.
   */
  synchronized void empty() {
    numAssigned.set(0);
    docs.clear();
  }

}
