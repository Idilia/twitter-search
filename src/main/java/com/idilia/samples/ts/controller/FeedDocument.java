package com.idilia.samples.ts.controller;

import java.util.Collection;
import java.util.TreeSet;

import com.idilia.samples.ts.docs.Document;

/**
 * Class for a document that is present in a Feed. This mostly defined to track
 * user keyword information. By tracking it we can move the document from the
 * rejected feed to the kept feed and vice-versa when a user keyword is added
 * and then removed.
 */
public class FeedDocument {

  /**
   * Create from a document obtained from the DocumentSource
   * 
   * @param doc
   *          document to insert in the feed
   */
  FeedDocument(Document doc) {
    this.doc = doc;
  }

  /**
   * Return the encapsulated document
   */
  public Document getDoc() {
    return doc;
  }

  /**
   * Return the text of the encapsulated document
   */
  public String getText() {
    return doc.getText();
  }

  /**
   * Add a keyword of the given type. Case insensitive. If the keyword is
   * already known, nothing happens.
   * 
   * @param kwType
   *          type of keyword to add
   * @param kw
   *          the keyword.
   * @return true if the keyword was added.
   */
  boolean addKeyword(KeywordType kwType, String kw) {
    TreeSet<String> kws = kwType == KeywordType.POSITIVE ? posKws : negKws;
    return kws.add(kw);
  }

  /**
   * Attempt to remove a keyword of the given type. Does nothing if the keyword
   * was not previously matched in the document. Case insensitive.
   * 
   * @param kwType
   *          type of keyword to remove
   * @param kw
   *          the keyword.
   * @return true if the keyword was removed.
   */
  boolean removeKeyword(KeywordType kwType, String kw) {
    TreeSet<String> kws = kwType == KeywordType.POSITIVE ? posKws : negKws;
    return kws.remove(kw);
  }

  /**
   * Return the document classification when considering the positive and
   * negative user keywords found in it. The classification is positive if any
   * positive keywords were found, negative if some negatives were found and 0
   * when none were found. This same policy is implemented in feed.js.
   * 
   * @return a number &lt; 0, == 0, or &gt; 0 for rejected, inconclusive, kept
   */
  int getClassificationFromUserKeywords() {
    if (!posKws.isEmpty())
      return 1;
    else if (!negKws.isEmpty())
      return -1;
    else
      return 0;
  }

  /**
   * Record the keywords used to kept this document.
   * 
   * @param kw
   *          The list of keywords that was found in the document
   */
  public void setPositiveKeywords(Collection<String> kws) {
    posKws.addAll(kws);
  }

  /**
   * Record the keywords used to reject this document
   * 
   * @param kw
   *          The list of keywords that was found in the document
   */
  public void setNegativeKeywords(Collection<String> kws) {
    negKws.addAll(kws);
  }

  /**
   * Retrieve the user keywords to keep the document.
   * 
   * @return user keywords found in document or null if none
   */
  public TreeSet<String> getPositiveKeywords() {
    return posKws;
  }

  /**
   * Retrieve the user keywords to discard the document.
   * 
   * @return user keywords found in document or null if none
   */
  public TreeSet<String> getNegativeKeywords() {
    return negKws;
  }

  /**
   * Return a string where each keyword is tab-separated. This makes it possible
   * to easily edit the string in the javascript.
   * 
   * @return tab-separated sequence of keywords.
   */
  public String getPositiveKeywordsStr() {
    return keywordsToString(posKws);
  }

  /**
   * Return a string where each keyword is tab-separated. This makes it possible
   * to easily edit the string in the javascript.
   * 
   * @return tab-separated sequence of keywords.
   */
  public String getNegativeKeywordsStr() {
    return keywordsToString(negKws);
  }

  /**
   * Convert a collection of keywords to a tab-seperated sequence. Always start
   * and end with a tab to ensure that we can always search for a token between
   * two tabs. The javascript expects this.
   * 
   * @param kws
   *          collection of keywords to serialize
   * @return tab-seperated sequence of keywords
   */
  private String keywordsToString(Collection<String> kws) {
    if (kws.isEmpty())
      return null;
    StringBuilder sb = new StringBuilder(kws.size() * 16);
    sb.append('\t');
    for (String kw : kws)
      sb.append(kw).append('\t');
    return sb.toString();
  }
  
  /**
   * Return the status classes for the tweet to communicate with the javascript
   * client (see feed.js) the status of the tweet. The classes are:
   * <li>status-keep / status-discard: Whether the tweet was kept or discarded
   * by either the eval API or the user keywords
   * <li>eval-status-keep / eval-status-discard: Present when the classification
   * decision was determined by the API instead of user keywords.
   * 
   * @param isKept true when the document has been marked as kept
   */
  public String getStatusClasses(boolean isKept) {
    if (getClassificationFromUserKeywords() == 0)
      return isKept ? "status-keep eval-status-keep" : "status-discard eval-status-discard";
    else
      return isKept ? "status-keep" : "status-discard";
  }

  final private Document doc;
  final private TreeSet<String> posKws = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
  final private TreeSet<String> negKws = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
}
