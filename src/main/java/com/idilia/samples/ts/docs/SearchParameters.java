package com.idilia.samples.ts.docs;

import java.time.ZonedDateTime;
import java.util.List;

/**
 * Possible parameters for a search to retrieve tweets. These are modeled after
 * the "Avanced Search" fields at https://twitter.com/search-advanced
 */
public interface SearchParameters {

  /**
   * Return the query string for performing a search. Includes word specifiers
   * for phrases, negated terms, etc.
   */
  public String getQuery();

  /**
   * When restricting the search to selected from users, the list of allowed
   * senders
   * 
   * @return a space seperated lists of users or null if none
   */
  public List<String> getFromAccounts();

  /**
   * When restricting the search to selected to users, the list of allowed
   * recipients
   * 
   * @return a space seperated lists of users or null if none
   */
  public List<String> getToAccounts();

  /**
   * When restricting the search to tweets that mention specific users, the list
   * of mentionned users
   * 
   * @return a space seperated lists of users or null if none
   */
  public List<String> getReferredAccounts();

  /**
   * When restricting the search to tweets send after a specific date.
   * 
   * @return earliest date for tweets to return or null if none
   */
  public ZonedDateTime getFromDate();

  /**
   * When restricting the search to tweets sent before a specific date.
   * 
   * @return latest date for tweets to return or null if none
   */
  public ZonedDateTime getToDate();

  /**
   * Indicate if tweets are to be restricted to those with a positive
   * connotation.
   * 
   * @return true when selected, false otherwise.
   */
  public boolean isPositive();

  /**
   * Indicate if tweets are to be restricted to those with a negative
   * connotation.
   * 
   * @return true when selected, false otherwise.
   */
  public boolean isNegative();

  /**
   * Restrict selection to tweets that are questions.
   * 
   * @return true when search is limited to question tweets
   */
  public boolean isQuestion();

  /**
   * When set, retweets are not returned by the search.
   * 
   * @return true if retweets are not returned, false to return all tweets
   */
  public boolean isIncludeRetweets();
}
