package com.idilia.samples.ts.twitter;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Class to render the tweet elements when generating the HTML for it
 *
 */
public final class TweetFormatter {

  /**
   * Return a "user friendly" formatted date.
   * 
   * @param t
   *          tweet to process
   * @return a string representing relative time between the current time and
   *         the tweet creation.
   */
  static public String renderDate(Tweet tweet) {

    Date createdAt = null;
    try {
      createdAt = twtDateFmt.parse(tweet.getCreatedAt());
    } catch (ParseException e) {
      return tweet.getCreatedAt();
    }

    Date now = new Date();
    long diff = now.getTime() - createdAt.getTime();
    long diffSecs = diff / 1000;
    if (diffSecs < 60)
      return String.format("%ds", diffSecs);
    if (diffSecs < 3600)
      return String.format("%dm", diffSecs / 60);
    if (diffSecs < 3600 * 24)
      return String.format("%dh", diffSecs / 3600);

    return outDateFmt.format(createdAt);
  }

  /**
   * Return html formatted text for the body of the tweet.
   */
  static public String renderBody(Tweet tweet) {
    
    String t = tweet.getText();

//    List<Tweet.Entity> entities = new ArrayList<>();
//    if (tweet.getHashTags() != null)
//      entities.addAll(tweet.getHashTags());
//    if (tweet.gettCoLinks() != null)
//      entities.addAll(tweet.gettCoLinks());
//    if (tweet.getUserMentions() != null)
//      entities.addAll(tweet.getUserMentions());
//    
//    // Sort in decreasing order
//    entities.sort((e1, e2) -> Integer.compare(e1.end, e2.end));
//    
//    // Now that we have sorted the entities in decreasing order
//    // perform the replacements
//    StringBuilder res = new StringBuilder();
//    int lastEnd = 0;
//    for (Tweet.Entity e: entities) {
//      if (e.start >= 0) {
//        res.append(StringEscapeUtils.escapeHtml(tweet.getText().substring(lastEnd, e.start)));
//        res.append(e.format(tweet.getText()));
//        lastEnd = e.end;
//      }
//    }
//    res.append(StringEscapeUtils.escapeHtml(tweet.getText().substring(lastEnd, tweet.getText().length())));
//    
//    String t = res.toString();
//    for (Tweet.Entity e: entities)
//      if (e.end < 0)
//        t = e.format(t);
  
    // Put links where http
    Matcher m = httpRe.matcher(t);
    t = m.replaceAll("<a target=\"_blank\" class=\"link\" href=\"$1$2\">$2</a>");

    // Link to hashtags
    m = tagsRe.matcher(t);
    t = m
        .replaceAll("<a target=\"_blank\" class=\"tag\" href=\"https://twitter.com/hashtag/$1?src=hash\")><span class=\"hash\">#</span><span class=\"hashtext\">$1</span></a>");

    // Link to other users
    m = userRe.matcher(t);
    t = m
        .replaceAll("<a target=\"_blank\" class=\"tag\" href=\"https://twitter.com/$1\"><span class=\"at-sign\">@</span><span class=\"username\">$1</span></a>");

    return t;
  }
  
  /*
   * Date formatters for converting to/from the tweet date to the visual representation
   */
  private static final SimpleDateFormat twtDateFmt, outDateFmt;
  static {
    twtDateFmt = new SimpleDateFormat("EEE MMM dd HH:mm:ss ZZZZZ yyyy");
    twtDateFmt.setLenient(true);
    outDateFmt = new SimpleDateFormat("MMM d");
  }

  /**
   * Regex to convert links and insert links to hashtags and user profiles
   */
  final static private Pattern httpRe = Pattern.compile("(https?:\\/\\/)([^\\s]*)");
  final static private Pattern tagsRe = Pattern.compile("(?<!&)#(\\w*)");
  final static private Pattern userRe = Pattern.compile("@(\\w*)");

}
