package com.idilia.samples.ts.docs;

/**
 * Interface of a token used by a document source to represent a search
 * and its state (e.g., initial search, subsequent pages of results, etc.)
 */
public interface SearchToken {

  /**
   * Return true when the document source does not have more documents or
   * does not want to retrieve more documents for the search.
   */
  boolean isFinished();
}
