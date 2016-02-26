package com.idilia.samples.ts.twitter;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import com.idilia.samples.ts.docs.SearchParameters;
import com.idilia.samples.ts.docs.SearchToken;

/**
 * Implementation of the SearchToken that allows performing successive searches
 * on the Twitter search API.
 */
public class TwitterSearchToken implements SearchToken {

  /**
   * Search expression that uses the Twitter syntax
   */
  private String exp;

  /**
   * Number of successive requests performed on the API for this search
   * expression
   */
  int iterations = 0;

  /**
   * Maximum number of requests allowed
   */
  final int maxIterations;

  /**
   * Whether to include retweets in the results
   */
  final boolean includeRetweets;

  /**
   * Request parameters for the next search expression
   */
  String nextResults;

  /**
   * Id of the last tweet received
   */
  Long maxId;

  /**
   * Constructor. Initializes for the first request.
   * 
   * @param searchParms form with the search parameters
   * @param maxIterations maximum number of iterations allowed for the search
   */
  public TwitterSearchToken(SearchParameters searchParms, String query, int maxIterations) {
    this.maxIterations = maxIterations;
    this.includeRetweets = searchParms.isIncludeRetweets();

    StringBuilder sb = new StringBuilder(128);
    sb.append(query);

    if (searchParms.getFromAccounts() != null)
      for (String acnt : searchParms.getFromAccounts())
        sb.append(" from:").append(acnt);

    if (searchParms.getToAccounts() != null)
      for (String acnt : searchParms.getToAccounts())
        sb.append(" to:").append(acnt);

    if (searchParms.getReferredAccounts() != null)
      for (String acnt : searchParms.getReferredAccounts())
        if (acnt.startsWith("@"))
          sb.append(" ").append(acnt);
        else
          sb.append(" @").append(acnt);

    if (searchParms.getFromDate() != null)
      sb.append(" since:")
          .append(searchParms.getFromDate().withZoneSameInstant(utcZone).toLocalDateTime().format(dbStrFmt));

    if (searchParms.getToDate() != null)
      sb.append(" until:")
          .append(searchParms.getToDate().withZoneSameInstant(utcZone).toLocalDateTime().format(dbStrFmt));

    if (searchParms.isPositive())
      sb.append(" :)");
    if (searchParms.isNegative())
      sb.append(" :(");
    if (searchParms.isQuestion())
      sb.append(" ?");

    try {
      this.exp = "?q=" + URLEncoder.encode(sb.toString(), StandardCharsets.UTF_8.toString())
          + "&include_entities=true&lang=en&result_type=recent";
    } catch (UnsupportedEncodingException e) {
    }
  }

  String getFirstResults() {
    return exp;
  }

  void setNextResults(String s) {
    this.nextResults = s;
  }

  String getNextResults() {
    return this.nextResults;
  }

  /**
   * Set the id of the lowest tweet that we received. We use this value as the
   * value (-1) of the max_id API parameter
   * 
   * @param id id of a tweet
   */
  void setMaxId(Long id) {
    if (this.maxId == null || id < this.maxId)
      this.maxId = id;
  }

  Long getMaxId() {
    return maxId;
  }

  final void incrIterations() {
    ++this.iterations;
  }

  final void setFinished() {
    this.iterations = maxIterations;
  }

  final boolean includeRetweets() {
    return this.includeRetweets;
  }

  @Override
  public boolean isFinished() {
    return iterations >= maxIterations;
  }

  static ZoneId utcZone = ZoneId.of("UTC");
  static DateTimeFormatter dbStrFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
}
