package com.idilia.samples.ts.docs;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Interface for a service that can provide documents that are potential matches
 * of a search expression.
 *
 */
public interface DocumentSource {

  /**
   * Asynchronous service to retrieve a number of documents.
   * 
   * @param searchToken a search token providing the information for performing
   *        the search.
   * @param maxCnt the maximum number of documents to return
   * @return a CompletableFuture that is signaled once the documents are
   *         available.
   */
  CompletableFuture<List<? extends Document>> getNextDocuments(SearchToken searchToken, int maxCnt);

  /**
   * Create a search token to be interpreted by the document source service when
   * retrieving documents for the initial request and then subsequent requests
   * for additional documents.
   * 
   * @param searchParms parameters support by the search API.
   * @return a search token to be interpreted by the document source.
   */
  SearchToken createSearchToken(SearchParameters searchParms);

  /**
   * Create search token from another one to resume the search after the already
   * returned documents.
   * 
   * @param token an existing token where we can read the last returned location
   * @param expr expression to use for the new search
   * @param searchExpression string of the search expression.
   */
  SearchToken extendSearch(SearchParameters searchParms, SearchToken token, String expr);
}
