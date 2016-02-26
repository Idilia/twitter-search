package com.idilia.samples.ts.config;

import java.io.File;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.util.Collections;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
  
  /** The OAuth credentials used with the Twitter search API 
   * @throws IOException 
   * @throws JsonMappingException 
   * @throws JsonParseException */
  List<OAuthCredentials> oauthCredentials() throws JsonParseException, JsonMappingException, IOException {
    /* 
     * Attempt to retrieve the credentials from a file. The file is
     * a JSON array of objects with properties: consumerKey, consumerSecret, token, tokenSecret.
     */
    if (env.getProperty("twitterOAuthTokensFile") != null) {
      ObjectMapper mapper = new ObjectMapper();
      List<OAuthCredentials> creds = mapper.readValue(
          new File(env.getProperty("twitterOAuthTokensFile")), new TypeReference<List<OAuthCredentials>>(){});
      return creds;
    } else
      return Collections.singletonList(new OAuthCredentials(
          env.getProperty("twitterOAuthConsumerKey"),
          env.getProperty("twitterOAuthConsumerSecret"),
          env.getProperty("twitterOAuthToken"),
          env.getProperty("twitterOAuthTokenSecret")));
  }
  
  
  /** The Idilia credentials used with the Idilia API */
  @Bean
  IdiliaCredentials idiliaCredentials() throws InvalidKeyException {
    return new IdiliaCredentials(
        env.getProperty("idiliaAccessKey"),
        env.getProperty("idiliaPrivateKey"));
  }
  
  
  /** 
   * The implementation for the abstract document source uses an
   * implementation that uses the Twitter search API 
   * @throws IOException 
   * @throws JsonMappingException 
   * @throws JsonParseException 
   */
  @Bean
  DocumentSource documentSource() throws JsonParseException, JsonMappingException, IOException {
    return new TwitterDocumentSource(oauthCredentials());
  }
  
  
  /**
   * The document matching service uses an implementation that
   * uses the Idilia text eval API and configured with Idilia credentials.
   */
  @Bean
  MatchingEvalService docMatchingService() throws InvalidKeyException {
    return new MatchingEvalService(idiliaCredentials());
  }

  /**
   * Tagging menus (setting senses of words in a search expression) uses
   * a TaggingMenuService configured with the Idilia credentials
   * and the template in use.
   */
  @Bean
  TaggingMenuService taggingMenuService() throws InvalidKeyException {
    return new TaggingMenuService(
        idiliaCredentials(),
        env.getProperty("senseCardTemplate"));
  }
  
}
