package com.idilia.samples.ts.twitter;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.apache.http.HttpResponse;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.entity.BufferedHttpEntity;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.apache.http.impl.nio.conn.PoolingNHttpClientConnectionManager;
import org.apache.http.impl.nio.reactor.DefaultConnectingIOReactor;
import org.apache.http.nio.conn.NHttpClientConnectionManager;
import org.apache.http.nio.reactor.ConnectingIOReactor;
import org.apache.http.nio.reactor.IOReactorException;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.idilia.samples.ts.docs.Document;

import oauth.signpost.OAuthConsumer;
import oauth.signpost.commonshttp.CommonsHttpOAuthConsumer;
import oauth.signpost.exception.OAuthException;

public class TwitterHttpAsyncClient implements Closeable {

  @SuppressWarnings("serial")
  public static class TwitterClientException extends RuntimeException {
    TwitterClientException() {
    }

    TwitterClientException(String msg) {
      super(msg);
    }

    TwitterClientException(Throwable t) {
      super(t);
    }
  }

  @SuppressWarnings("serial")
  public static class TwitterRateLimitingException extends TwitterClientException {
    TwitterRateLimitingException() {
    }
  }

  /** Apache HTTP async client */
  private final CloseableHttpAsyncClient httpClient_;

  /** Thread object cleaning up the HTTP expired connections */
  private final IdleConnectionEvictor connEvictor_;

  /** OAuth consumer used to sign the requests */
  private ArrayBlockingQueue<OAuthConsumer> oauthConsumers;

  private final ObjectMapper jsonMapper_ = new ObjectMapper();

  /**
   * Constructor. Configure an async Apache HTTP client with default parameters
   * except provide default timeouts to make sure we eventually timeout.
   */
  TwitterHttpAsyncClient() {

    /* Initialize the Http async client */
    ConnectingIOReactor ioReactor;
    try {
      ioReactor = new DefaultConnectingIOReactor();
    } catch (IOReactorException e) {
      throw new TwitterClientException(e);
    }

    final int maxConns = 100;
    PoolingNHttpClientConnectionManager cm = new PoolingNHttpClientConnectionManager(ioReactor);
    cm.setMaxTotal(maxConns);

    final int maxConnectTimeMs = 60 * 1000;
    RequestConfig requestConfig = RequestConfig.custom()
        .setSocketTimeout(maxConnectTimeMs)
        .setConnectTimeout(maxConnectTimeMs).build();

    httpClient_ = HttpAsyncClients.custom()
        .setConnectionManager(cm)
        .setDefaultRequestConfig(requestConfig)
        .build();

    httpClient_.start();

    /* Initialize the evictor for old connections */
    connEvictor_ = new IdleConnectionEvictor(cm);
    connEvictor_.start();
  }

  /**
   * Constructor. Configure an async Apache HTTP client with default parameters
   * except provide default timeouts to make sure we eventually timeout.
   */
  TwitterHttpAsyncClient(Collection<OAuthCredentials> creds) {
    this();

    /* Initialize OAuth. */
    List<OAuthConsumer> consumers = creds.stream().map(cred -> {
      OAuthConsumer oauthConsumer = new CommonsHttpOAuthConsumer(cred.consumerKey, cred.consumerSecret);
      oauthConsumer.setTokenWithSecret(cred.token, cred.tokenSecret);
      return oauthConsumer;
    }).collect(Collectors.toList());

    oauthConsumers = new ArrayBlockingQueue<>(consumers.size(), false, consumers);
  }

  /**
   * Constructor. Configure an async Apache HTTP client with default parameters
   * except provide default timeouts to make sure we eventually timeout.
   */
  TwitterHttpAsyncClient(OAuthCredentials creds) {
    this(Collections.singletonList(creds));
  }

  @Override
  public void close() throws IOException {
    connEvictor_.shutdown();
    try {
      connEvictor_.join();
    } catch (InterruptedException e) {
    }
    httpClient_.close();
  }

  /**
   * Perform a search using the Twitter API
   * 
   * @param searchToken provides the query to use. And is updated for the next
   *        query.
   * @param cnt maximum number of documents to retrieve
   * @throws TwitterRateLimitingException if encountering a rate limit error
   *         with the API
   * @throws TwitterClientException on any other error
   */
  CompletableFuture<List<? extends Document>> search(final TwitterSearchToken searchToken, int cnt)
      throws TwitterClientException {

    final StringBuilder qrySb = new StringBuilder(256);
    qrySb.append("https://api.twitter.com/1.1/search/tweets.json");
    if (searchToken.getNextResults() != null)
      qrySb.append(searchToken.getNextResults());
    else {
      qrySb.append(searchToken.getFirstResults());
      qrySb.append("&count=").append(cnt);
      if (searchToken.getMaxId() != null)
        qrySb.append("&max_id=").append(searchToken.getMaxId() - 1);
    }

    final CompletableFuture<List<? extends Document>> future = new CompletableFuture<>();
    HttpGet httpGet = new HttpGet(qrySb.toString());

    /* Get a signing token */
    OAuthConsumer oauthConsumer = null;
    try {
      oauthConsumer = oauthConsumers.take();
    } catch (InterruptedException e) {
      throw new TwitterClientException(e);
    }

    /* Sign the request and requeue the signing token */
    try {
      oauthConsumer.sign(httpGet);
    } catch (OAuthException e) {
      throw new TwitterClientException(e);
    } finally {
      oauthConsumers.add(oauthConsumer);
    }

    HttpClientContext ctxt = HttpClientContext.create();
    httpClient_.execute(httpGet, ctxt, new FutureCallback<HttpResponse>() {

      @SuppressWarnings("unchecked")
      @Override
      public void completed(final HttpResponse response) {
        String ct = response.getFirstHeader("Content-Type").getValue();
        if (ct.contains("json")) {
          try {
            BufferedHttpEntity bRxEntity = new BufferedHttpEntity(response.getEntity());
            Map<String, Object> msgResp = jsonMapper_.readValue(bRxEntity.getContent(),
                new TypeReference<Map<String, Object>>() {
            });

            /* Read errors if any and when some, stop with exception */
            if (msgResp.get("errors") != null) {
              Object o = ((List<Object>) msgResp.get("errors")).get(0);
              LinkedHashMap<String, Object> cv = (LinkedHashMap<String, Object>) o;
              Integer code = (Integer) cv.get("code");
              String msg = (String) cv.get("message");
              if (code == 88)
                future.completeExceptionally(new TwitterRateLimitingException());
              else
                future.completeExceptionally(new TwitterClientException(
                    String.format("Twitter API error: %d, %s", code, msg)));
            }

            /* Read the tweets returned */
            List<Object> apiTweets = (List<Object>) msgResp.get("statuses");
            List<Tweet> tweets = Collections.emptyList();
            if (apiTweets != null) {
              tweets = new ArrayList<>(apiTweets.size());
              for (Object o : apiTweets) {
                LinkedHashMap<String, Object> cv = (LinkedHashMap<String, Object>) o;
                Tweet t = new Tweet(cv);
                searchToken.setMaxId(t.getNumericId());
                tweets.add(new Tweet(cv));
              }
            }

            /*
             * Read the link for the next results. It is not always available so
             * we have a fallback to the max_id parameter when were able to read
             * tweets.
             */
            LinkedHashMap<String, Object> metaData = (LinkedHashMap<String, Object>) msgResp.get("search_metadata");
            String nextRes = metaData != null ? (String) metaData.get("next_results") : null;
            if (tweets.isEmpty() && (nextRes == null || nextRes.equals(searchToken.getNextResults())))
              searchToken.setFinished();
            else
              searchToken.setNextResults(nextRes);

            future.complete(tweets);

          } catch (Exception e) {
            searchToken.setFinished();
            future.completeExceptionally(new TwitterClientException(e));
          }
        } else {
          searchToken.setFinished();
          future.completeExceptionally(new TwitterClientException("Unexpected content type in response:" + ct));
        }
      }

      @Override
      public void failed(final Exception ex) {
        searchToken.setFinished();
        future.completeExceptionally(new TwitterClientException(ex));
      }

      @Override
      public void cancelled() {
        searchToken.setFinished();
        future.cancel(false);
      }

    });
    return future;
  }

  /**
   * A thread that periodically removes expired connections from the connection
   * pool. This is straight from an example in
   * https://hc.apache.org/httpcomponents-asyncclient-dev/examples.html
   */
  static class IdleConnectionEvictor extends Thread {

    /** The connection manager used by the async client */
    private final NHttpClientConnectionManager connMgr;

    /** Set to true when time to stop */
    private volatile boolean shutdown;

    public IdleConnectionEvictor(NHttpClientConnectionManager connMgr) {
      super();
      this.connMgr = connMgr;
    };

    @Override
    public void run() {
      try {
        while (!shutdown) {
          synchronized (this) {
            wait(5000);
            connMgr.closeExpiredConnections();
            connMgr.closeIdleConnections(5, TimeUnit.SECONDS);
          }
        }
      } catch (InterruptedException ex) {
      } finally {
      }
    }

    public void shutdown() {
      shutdown = true;
      synchronized (this) {
        notifyAll();
      }
    }
  }
}
