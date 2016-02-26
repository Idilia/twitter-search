package com.idilia.samples.ts.twitter;

import java.io.Closeable;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import com.idilia.samples.ts.docs.Document;
import com.idilia.samples.ts.docs.DocumentSource;
import com.idilia.samples.ts.docs.SearchParameters;
import com.idilia.samples.ts.docs.SearchToken;

/**
 * An implementation of a DocumentSource that fetches Tweet (implements
 * Document) from the Twitter Search API.
 */
public class TwitterDocumentSource implements DocumentSource, Closeable {

  /**
   * Create a source using the specified OAuth credentials for authentication
   * 
   * @param creds OAuth credentials
   */
  public TwitterDocumentSource(List<OAuthCredentials> creds) {
    twitterClient = new TwitterHttpAsyncClient(creds);
  }

  @Override
  public CompletableFuture<List<? extends Document>> getNextDocuments(SearchToken searchToken, int cnt) {
    final TwitterSearchToken tst = (TwitterSearchToken) searchToken;
    if (tst.isFinished())
      return CompletableFuture.completedFuture(Collections.emptyList());

    tst.incrIterations();
    return twitterClient.search(tst, cnt).thenApply((List<? extends Document> docs) -> {
      if (tst.includeRetweets())
        return docs;
      else
        return docs.stream().filter(d -> !((Tweet) d).isReTweet()).collect(Collectors.toList());
    });
  }

  @Override
  public SearchToken createSearchToken(SearchParameters searchParms) {
    // Create a search token that will limit the maximum number of request
    // for a search to maxIterations. If we did not limit, we could easily
    // exhaust the quota for the used oauth credentials.
    return new TwitterSearchToken(searchParms, searchParms.getQuery(), maxIterations);
  }

  @Override
  public void close() throws IOException {
    twitterClient.close();
  }

  @Override
  public SearchToken extendSearch(SearchParameters searchParms, SearchToken token, String expr) {
    TwitterSearchToken st = (TwitterSearchToken) token;
    TwitterSearchToken res = new TwitterSearchToken(searchParms, expr, maxIterations);
    res.setMaxId(st.getMaxId());
    res.setNextResults(null);
    return res;
  }

  /** The HTTP client that we use to reach the Twitter API */
  private final TwitterHttpAsyncClient twitterClient;

  /** Maximum number of requests for the same search */
  final private static int maxIterations = 50;

}
