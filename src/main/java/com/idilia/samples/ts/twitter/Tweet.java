package com.idilia.samples.ts.twitter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringEscapeUtils;

import com.idilia.samples.ts.docs.Document;

/**
 * An implementation of Document that represents a Tweet as obtained
 * from the Twitter search API.
 */
public class Tweet implements Document {

  private long id;
  private String text;
  private String screenName;
  private String profileImgUrl;
  private String name;
  private String createdAt;
  private List<String> hashTags;
  
  public Tweet(long id, String text) {
    this.id = id;
    this.text = text;
  }
  
  /**
   * Construct from a map of properties created when recovering the JSON
   * result of the Twitter Search API.
   * @param map a map of property/value pairs for a tweet
   */
  @SuppressWarnings("unchecked")
  public Tweet(Map<String,Object> map) {
    this.id = (Long) map.get("id");
    this.text = StringEscapeUtils.unescapeXml((String) map.get("text"));
    this.createdAt = (String) map.get("created_at");
    //this.createdAt = this.createdAt.replace(" GMT.*$/", "");
    
    List<Object> tags = (List<Object>) map.get("hashtags");
    if (tags != null && !tags.isEmpty()) {
      hashTags = new ArrayList<>(tags.size());
      for (Object tO: tags)
        hashTags.add((String) ((Map<String, Object>) tO).get("text"));
    } else
      hashTags = Collections.emptyList();
    
    Map<String, Object> user = (Map<String, Object>) map.get("user");
    if (user != null) {
      this.profileImgUrl = (String) user.get("profile_image_url_https");
      this.name = (String) user.get("name");
      this.screenName = (String) user.get("screen_name");
    }
  }
  
  @Override
  public String getText() {
    return text;
  }

  @Override
  public final String getId() {
    return Long.toString(id);
  }

  public final long getNumericId() {
    return id;
  }
  
  public final String getScreenName() {
    return screenName;
  }

  public final void setScreenName(String screenName) {
    this.screenName = screenName;
  }

  public final String getProfileImgUrl() {
    return profileImgUrl;
  }

  public final void setProfileImgUrl(String profileImgUrl) {
    this.profileImgUrl = profileImgUrl;
  }

  public final String getName() {
    return name;
  }

  public final void setName(String name) {
    this.name = name;
  }

  public final String getCreatedAt() {
    return createdAt;
  }

  public final void setCreatedAt(String createdAt) {
    this.createdAt = createdAt;
  }
  
  public final List<String> getHashTags() {
    return hashTags;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof Tweet)) return false;
    Tweet other = (Tweet) o;
    return id == other.id;
  }
  
  @Override
  public String toString() {
    return text;
  }
}
