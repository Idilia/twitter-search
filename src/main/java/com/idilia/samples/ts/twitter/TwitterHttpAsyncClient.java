package com.idilia.samples.ts.twitter;

import java.io.Closeable;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

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
    public TwitterClientException(String msg) { super(msg); }
    public TwitterClientException(Throwable t) { super(t); }
  }
  
  /** Apache HTTP async client */
  private final CloseableHttpAsyncClient httpClient_ ;
  
  /** Thread object cleaning up the HTTP expired connections */
  private final IdleConnectionEvictor connEvictor_;
  
  /** OAuth consumer used to sign the requests */
  private final OAuthConsumer oauthConsumer;
  
  private final ObjectMapper jsonMapper_ = new ObjectMapper();
  
  /**
   * Constructor. Configure an async Apache HTTP client with default parameters
   * except provide default timeouts to make sure we eventually timeout.
   */
  TwitterHttpAsyncClient(OAuthCredentials creds) {
    
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
    
    
    /* Initialize OAuth. */
    oauthConsumer = new CommonsHttpOAuthConsumer(creds.consumerKey, creds.consumerSecret); 
    oauthConsumer.setTokenWithSecret(creds.token, creds.tokenSecret);
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
   * @param searchToken provides the query to use. And is updated for the next query.
   * @param cnt maximum number of documents to retrieve
   * @throws TwitterClientException on any error
   */
  CompletableFuture<List<? extends Document>> search(final TwitterSearchToken searchToken, int cnt) throws TwitterClientException {
  
    StringBuilder qrySb = new StringBuilder(256);
    qrySb.append("https://api.twitter.com/1.1/search/tweets.json");
    if (searchToken.getNextResults() != null)
      qrySb.append(searchToken.getNextResults());
    else {
      qrySb.append("?q=");
      try {
        qrySb.append(URLEncoder.encode(searchToken.getExp(), StandardCharsets.UTF_8.toString()));
      } catch (UnsupportedEncodingException e) {
      }
      qrySb.append("&count=").append(cnt);
      qrySb.append("&include_entities=false&lang=en&result_type=recent");
      if (searchToken.getMaxId() != null)
        qrySb.append("&max_id=").append(searchToken.getMaxId() - 1);
    }
    
    final CompletableFuture<List<? extends Document>> future = new CompletableFuture<>();
    HttpGet httpGet = new HttpGet(qrySb.toString());
    try {
      oauthConsumer.sign(httpGet);
    } catch (OAuthException e) {
      throw new TwitterClientException(e);
    }

    HttpClientContext ctxt = HttpClientContext.create();
    httpClient_.execute(httpGet, ctxt, new FutureCallback<HttpResponse>() {
      
      @SuppressWarnings("unchecked")
      @Override
      public void completed(final HttpResponse response) {
        if (response.getFirstHeader("Content-Type").getValue().contains("json")) {
          try {
            BufferedHttpEntity bRxEntity = new BufferedHttpEntity(response.getEntity());
            Map<String, Object> msgResp = jsonMapper_.readValue(bRxEntity.getContent(), new TypeReference<Map<String, Object>>() {});
            
            /* Read errors if any and when some, stop with exception */
            if (msgResp.get("errors") != null) {
              Object o = ((List<Object>) msgResp.get("errors")).get(0);
              LinkedHashMap<String,Object> cv = (LinkedHashMap<String,Object>) o;
              Integer code = (Integer) cv.get("code");
              String msg = (String) cv.get("message");
              future.completeExceptionally(new TwitterClientException(
                  String.format("Twitter API error: %d, %s", code, msg)));
            }
            
            /* Read the link for the next results. It is not always available
             * so we have a fallback to the max_id parameter. */
            LinkedHashMap<String,Object> metaData = 
                (LinkedHashMap<String,Object>) msgResp.get("search_metadata");
            if (metaData != null)
              searchToken.setNextResults((String) metaData.get("next_results"));
            
            /* Read the tweets returned */
            List<Object> apiTweets = (List<Object>) msgResp.get("statuses");
            if (apiTweets != null) {
              List<Tweet> tweets = new ArrayList<>(apiTweets.size());
              for (Object o: apiTweets) {
                LinkedHashMap<String,Object> cv = (LinkedHashMap<String,Object>) o;
                Tweet t = new Tweet(cv);
                searchToken.setMaxId(t.getNumericId());
                tweets.add(new Tweet(cv));
              }
              future.complete(tweets);
            } else
              future.complete(Collections.emptyList());
            
          } catch (Exception e) {
            searchToken.setFinished();
            future.completeExceptionally(new TwitterClientException(e));
          }
        } else {
          searchToken.setFinished();
          future.completeExceptionally(new TwitterClientException("Unexpected content type in response"));
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
   * A thread that periodically removes expired connections from the connection pool.
   * This is straight from an example in
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
