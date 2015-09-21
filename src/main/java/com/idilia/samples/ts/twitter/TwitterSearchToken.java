package com.idilia.samples.ts.twitter;

import com.idilia.samples.ts.docs.SearchToken;

/**
 * Implementation of the SearchToken that allows performing successive
 * searches on the Twitter search API.
 */
public class TwitterSearchToken implements SearchToken {

  /** 
   * Search expression that uses the Twitter syntax
   */
  private String exp;
  
  /**
   *  Number of successive requests performed on the API for this search expression
   */
  int iterations = 0;
  
  /**
   *  Maximum number of requests allowed 
   */
  int maxIterations;
  
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
   * @param exp search expression
   * @param maxIterations maximum number of iterations allowed for the search
   */
  public TwitterSearchToken(String exp, int maxIterations) {
    this.exp = exp;
    this.maxIterations = maxIterations;
  }

  public final String getExp() {
    return exp;
  }

  public void setNextResults(String s) {
    this.nextResults = s;
  }
  
  public String getNextResults() {
    return this.nextResults;
  }
  
  /** Set the id of the lowest tweet that we received. We use this
   * value as the value (-1) of the max_id API parameter
   * @param id id of a tweet
   */
  public void setMaxId(Long id) {
    if (this.maxId == null || id < this.maxId)
      this.maxId = id;
  }
  
  public Long getMaxId() {
    return maxId;
  }
  
  public final void incrIterations() {
    ++this.iterations;
  }
  
  public final void setFinished() {
    this.iterations = maxIterations;
  }

  @Override
  public boolean isFinished() {
    return iterations >= maxIterations;
  }
}
