package com.idilia.samples.ts.twitter;

import java.io.Closeable;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.idilia.samples.ts.docs.Document;
import com.idilia.samples.ts.docs.DocumentSource;
import com.idilia.samples.ts.docs.SearchToken;

/**
 * An implementation of a DocumentSource that fetches Tweet (implements Document) from
 * the Twitter Search API.
 */
public class TwitterDocumentSource implements DocumentSource, Closeable {

  /**
   * Create a source using the specified OAuth credentials for authentication
   * @param creds OAuth credentials
   */
  public TwitterDocumentSource(OAuthCredentials creds) {
    twitterClient = new TwitterHttpAsyncClient(creds);
  }
  
  @Override
  public CompletableFuture<List<? extends Document>> getNextDocuments(SearchToken searchToken, int cnt) {
    final TwitterSearchToken tst = (TwitterSearchToken) searchToken;
    if (tst.isFinished())
      return CompletableFuture.completedFuture(Collections.emptyList());
      
    tst.incrIterations();
    return twitterClient.search(tst, cnt);
  }

  @Override
  public SearchToken createSearchToken(String searchExpression) {
    // Create a search token that will limit the maximum number of request
    // for a search to maxIterations. If we did not limit, we could easily
    // exhaust the quota for the used oauth credentials.
    return new TwitterSearchToken(searchExpression, maxIterations);
  }

  @Override
  public void close() throws IOException {
    twitterClient.close();
  }
  
  /** The HTTP client that we use to reach the Twitter API */
  private final TwitterHttpAsyncClient twitterClient;
  
  /** Maximum number of requests for the same search */
  final private static int maxIterations = 50;
}
