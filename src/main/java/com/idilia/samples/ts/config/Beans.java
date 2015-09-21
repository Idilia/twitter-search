package com.idilia.samples.ts.config;

import java.security.InvalidKeyException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import com.idilia.samples.ts.docs.DocumentSource;
import com.idilia.samples.ts.idilia.MatchingEvalService;
import com.idilia.samples.ts.idilia.TaggingMenuService;
import com.idilia.samples.ts.twitter.OAuthCredentials;
import com.idilia.samples.ts.twitter.TwitterDocumentSource;
import com.idilia.services.base.IdiliaCredentials;

/**
 * Spring bean configuration.
 * Define the beans used with @Autowired annotations that
 * are not discovered automatically.
 */
@Configuration
public class Beans {

  @Autowired
  Environment env;
  
  @Bean
  OAuthCredentials oauthCredentials() {
    return new OAuthCredentials(
        env.getProperty("twitterOAuthConsumerKey"),
        env.getProperty("twitterOAuthConsumerSecret"),
        env.getProperty("twitterOAuthToken"),
        env.getProperty("twitterOAuthTokenSecret"));
  }
  
  @Bean
  IdiliaCredentials idiliaCredentials() throws InvalidKeyException {
    return new IdiliaCredentials(
        env.getProperty("idiliaAccessKey"),
        env.getProperty("idiliaPrivateKey"));
  }
  
  @Bean
  DocumentSource documentSource() {
    return new TwitterDocumentSource(oauthCredentials());
  }
  
  @Bean
  MatchingEvalService docMatchingService() throws InvalidKeyException {
    return new MatchingEvalService(idiliaCredentials());
  }
  
  @Bean
  TaggingMenuService taggingMenuService() throws InvalidKeyException {
    return new TaggingMenuService(
        idiliaCredentials(),
        env.getProperty("senseCardTemplate"));
  }
  
}
