package com.idilia.samples.ts.twitter;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Record the crendentials for OAuth 2 authentication as an application.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class OAuthCredentials {

  public String consumerKey, consumerSecret;
  public String token, tokenSecret;

  public OAuthCredentials(String consumerKey, String consumerSecret, String token, String tokenSecret) {
    this.consumerKey = consumerKey;
    this.consumerSecret = consumerSecret;
    this.token = token;
    this.tokenSecret = tokenSecret;
  }

  /** For JSON recovery */
  protected OAuthCredentials() {
  }
}
