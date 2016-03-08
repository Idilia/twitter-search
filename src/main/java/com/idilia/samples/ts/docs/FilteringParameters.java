package com.idilia.samples.ts.docs;

/**
 * Interface for parameters that influent the filtering of documents returned by
 * the search
 */
public interface FilteringParameters {

  /**
   * Indicates if document that cannot be classified as negative or positive are
   * put in the kept feed or the discard feed.
   * 
   * @return true when ambiguous documents are to be discarded
   */
  boolean isDiscardInconclusive();

  /**
   * Indicates how to handle a document when the words in the document do not
   * include some mandatory terms from the search expression.
   */
  boolean isDiscardOnMissingWords();
}
