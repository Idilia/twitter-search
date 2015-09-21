package com.idilia.samples.ts.controller;

import java.util.Collection;
import java.util.List;
import java.util.NavigableSet;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

/**
 * A collection of keywords entered by the user to refine the results for a
 * search expression. This is the runtime object used during operation. When the
 * search is persisted in the database, the content is stored in a persistent
 * field for the Search.
 */
public class Keywords {

  /**
   * A set of keywords that can be used to classify documents This is a
   * concurrent structure because we can be using when classifying while its
   * being edited by the user.
   */
  final private ConcurrentSkipListSet<String> keywords = 
      new ConcurrentSkipListSet<>(String.CASE_INSENSITIVE_ORDER);

  /**
   * Regular expression to match any of the keywords. Constructed on demand and
   * cached.
   */
  final private AtomicReference<Pattern> reRef = new AtomicReference<>();

  /**
   * Constructor
   */
  public Keywords() {
  }

  /**
   * Constructor. Add the given keywords as the initial list of keywords.
   */
  public Keywords(List<String> kws) {
    keywords.addAll(kws);
  }

  /**
   * Return a compiled regex that can be used to match any of the contained
   * keywords in the text of a document. null when no keywords.
   */
  public Pattern getRegexPattern() {
    Pattern re = reRef.get();
    if (re != null)
      return re;

    if (keywords.isEmpty())
      return null;

    StringBuilder sb = new StringBuilder(keywords.size() * 10);
    sb.append("\\b(");
    for (String kw : keywords)
      sb.append(kw).append('|');
    sb.setLength(sb.length() - 1);
    sb.append(")\\b");
    re = Pattern.compile(sb.toString(), Pattern.CASE_INSENSITIVE);
    reRef.set(re);
    return re;
  }

  /**
   * Add the given keywords to the current collection.
   * <p>
   * Obsoletes the cached regular expression if needed.
   * 
   * @param kws
   *          a collection of keywords to add
   * @return true when a new keyword was inserted into the collection
   */
  public boolean addAll(Collection<String> kws) {
    boolean added = keywords.addAll(kws);
    if (added)
      reRef.set(null);
    return added;
  }

  /**
   * Add the given keyword to the current collection.
   * <p>
   * Obsoletes the cached regular expression if needed.
   * 
   * @param kw
   *          a keyword to add
   * @return true when the keyword was inserted into the collection
   */
  public boolean add(String kw) {
    boolean added = keywords.add(kw);
    if (added)
      reRef.set(null);
    return added;
  }

  /**
   * Attempt to remove the given keyword from the collection.
   * 
   * @param kw
   *          keyword to remove
   * @return true when the keyword was present and has been removed.
   */
  public boolean remove(String kw) {
    boolean removed = keywords.remove(kw);
    if (removed)
      reRef.set(null);
    return removed;
  }

  /**
   * Return the keywords present as a navigatable set sorted in alphabetical
   * order (case insensitive).
   */
  public NavigableSet<String> getSet() {
    return keywords;
  }

  /**
   * Return the keywords present as a navigatable set sorted in alphabetical
   * order (case insensitive) where all values are greater or equal to the given
   * kw. This can be used to implement pageing.
   * 
   * @param kw
   *          lower bound value for the keywords returned.
   * @return a alphabetical set of keywords with the lower value equal or
   *         greater than 'kw'.
   */
  public NavigableSet<String> tailSet(String kw) {
    return keywords.tailSet(kw);
  }

  /**
   * Return true when the collection includes no keywords.
   */
  public boolean isEmpty() {
    return keywords.isEmpty();
  }
}
