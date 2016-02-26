package com.idilia.samples.ts.twitter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringEscapeUtils;

import com.idilia.samples.ts.docs.Document;

/**
 * An implementation of Document that represents a Tweet as obtained from the
 * Twitter search API.
 */
public class Tweet implements Document {

  static abstract class Entity {
    int start;
    int end;

    Entity(int start, int end) {
      this.start = start;
      this.end = end;
    }

    abstract String format(String src);
  }

  static class HashTag extends Entity {
    String text;

    HashTag(String text, int start, int end) {
      super(start, end);
      this.text = text;
    }

    @Override
    String format(String src) {
      return "<a target=\"_blank\" class=\"tag\" href=\"https://twitter.com/hashtag/" + text
          + "?src=hash\")><span class=\"hash\">#</span><span class=\"hashtext\">" + text + "</span></a>";
    }
  }

  static class UserMention extends Entity {
    String name;

    UserMention(String name, int start, int end) {
      super(start, end);
      this.name = name;
    }

    @Override
    String format(String src) {
      return "<a target=\"_blank\" class=\"tag\" href=\"https://twitter.com/" + name
          + "\"><span class=\"at-sign\">@</span><span class=\"username\">" + name + "</span></a>";
    }
  }

  static class TCOLink extends Entity {
    String tco;
    String expUrl;

    TCOLink(String from, String to) {
      super(-1, -1);
      this.tco = from;
      this.expUrl = to;
    }

    @Override
    String format(String src) {
      return src.replaceAll(tco, expUrl);
    }
  }

  private long id;
  private String text;
  private String screenName;
  private String profileImgUrl;
  private String name;
  private String createdAt;
  private List<HashTag> hashTags;
  private List<TCOLink> tCoLinks;
  private List<UserMention> userMentions;
  private boolean retweet;

  public Tweet(long id, String text) {
    this.id = id;
    this.text = text;
  }

  /**
   * Construct from a map of properties created when recovering the JSON result
   * of the Twitter Search API.
   * 
   * @param map a map of property/value pairs for a tweet
   */
  @SuppressWarnings("unchecked")
  public Tweet(Map<String, Object> map) {
    this.id = (Long) map.get("id");
    this.text = StringEscapeUtils.unescapeXml((String) map.get("text"));
    this.createdAt = (String) map.get("created_at");
    // this.createdAt = this.createdAt.replace(" GMT.*$/", "");
    this.retweet = map.get("retweeted_status") != null;

    Map<String, Object> user = (Map<String, Object>) map.get("user");
    if (user != null) {
      this.profileImgUrl = (String) user.get("profile_image_url_https");
      this.name = (String) user.get("name");
      this.screenName = (String) user.get("screen_name");
    }

    Map<String, Object> entities = (Map<String, Object>) map.get("entities");
    if (entities != null) {

      // Recover the url entities
      List<Object> urls = (List<Object>) entities.get("urls");
      if (urls != null && !urls.isEmpty()) {
        tCoLinks = new ArrayList<>(urls.size());
        for (Object urlMapO : urls) {
          Map<String, Object> urlMap = (Map<String, Object>) urlMapO;
          String url = (String) urlMap.get("url");
          String dest = (String) urlMap.get("expanded_url");
          if (url != null && dest != null)
            tCoLinks.add(new TCOLink(url, dest));
        }
      }

      // Recover the hash tags
      List<Object> tags = (List<Object>) entities.get("hashtags");
      if (tags != null && !tags.isEmpty()) {
        hashTags = new ArrayList<>(tags.size());
        for (Object tagMapO : tags) {
          Map<String, Object> tagMap = (Map<String, Object>) tagMapO;
          String text = (String) tagMap.get("text");
          List<Object> indicesO = (List<Object>) tagMap.get("indices");
          int start = (Integer) indicesO.get(0);
          int end = (Integer) indicesO.get(1);
          hashTags.add(new HashTag(text, start, end));
        }
      }

      // Recover the user mentions
      List<Object> ums = (List<Object>) entities.get("user_mentions");
      if (ums != null && !ums.isEmpty()) {
        userMentions = new ArrayList<>(ums.size());
        for (Object umO : ums) {
          Map<String, Object> umMap = (Map<String, Object>) umO;
          String name = (String) umMap.get("screen_name");
          List<Object> indicesO = (List<Object>) umMap.get("indices");
          int start = (Integer) indicesO.get(0);
          int end = (Integer) indicesO.get(1);
          userMentions.add(new UserMention(name, start, end));
        }

      }
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

  public final List<HashTag> getHashTags() {
    return hashTags;
  }

  public final List<TCOLink> gettCoLinks() {
    return tCoLinks;
  }

  public final List<UserMention> getUserMentions() {
    return userMentions;
  }

  public final boolean isReTweet() {
    return retweet;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof Tweet))
      return false;
    Tweet other = (Tweet) o;
    return id == other.id;
  }

  @Override
  public String toString() {
    return text;
  }
}
