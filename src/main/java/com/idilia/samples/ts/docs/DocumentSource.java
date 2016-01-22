package com.idilia.samples.ts.docs;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Interface for a service that can provide documents that are
 * potential matches of a search expression.
 *
 */
public interface DocumentSource {

  /**
   * Asynchronous service to retrieve a number of documents.
   * @param searchToken a search token providing the information for
   *   performing the search.
   * @param maxCnt the maximum number of documents to return
   * @return a CompletableFuture that is signaled once the documents are available.
   */
  CompletableFuture<List<? extends Document>> getNextDocuments(SearchToken searchToken, int maxCnt);
  
  /**
   * Create a search token to be interpreted by the document source
   * service when retrieving documents for the initial request
   * and then subsequent requests for additional documents.
   * @param searchExpression string of the search expression.
   * @return a search token to be interpreted by the document source.
   */
  SearchToken createSearchToken(String searchExpression);
  
  /**
   * Create search token from another one to resume the search after
   * the already returned documents.
   * @param searchExpression string of the search expression.
   * @param token an existing token where we can read the last returned location
   */
  SearchToken extendSearch(String searchExpression, SearchToken token);
}
