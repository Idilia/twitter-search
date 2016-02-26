package com.idilia.samples.ts.controller;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.idilia.samples.ts.docs.FilteringParameters;
import com.idilia.samples.ts.docs.SearchParameters;

public class SearchForm implements SearchParameters, FilteringParameters {

  /** Search parameters */
  private String query;

  private List<String> fromAccounts;
  private List<String> toAccounts;
  private List<String> referredAccounts;

  private ZonedDateTime fromDate;
  private ZonedDateTime toDate;

  private boolean positive = false;
  private boolean negative = false;
  private boolean question = false;
  private boolean includeRetweets = false;

  private String allWords;
  private String phrase;
  private String anyWords;
  private String noneWords;
  private String hashTags;

  
  /** Filtering parameters */

  private boolean discardInconclusive = false;
  private boolean rejectOnMissingWords = true;

  
  
  /** 
   * Implement SearchParameters interface
   */
  
  @Override
  public String getQuery() {
    if ((query == null || query.isEmpty()) && isAdvancedSearch())
      return getAdvancedSearchExpr();
    else
      return query;
  }

  public void setQuery(String query) {
    this.query = query;
  }

  /**
   * Return true when the search parameters indicate that an advanced search.
   */
  boolean isAdvancedSearch() {
    return (allWords != null && !allWords.isEmpty()) ||
        (phrase != null && !phrase.isEmpty()) ||
        (anyWords != null && !anyWords.isEmpty()) ||
        (noneWords != null && !noneWords.isEmpty()) ||
        (hashTags != null && !hashTags.isEmpty());
  }

  /**
   * Assemble a search string from the advanced search fields
   * 
   * @return string used as the query for an advanced search
   */
  private String getAdvancedSearchExpr() {

    // Construct the query from the various advanced search fields
    StringBuilder sb = new StringBuilder();
    if (allWords != null && !allWords.isEmpty())
      sb.append(allWords);

    if (phrase != null && !phrase.isEmpty())
      sb.append(" \"").append(phrase).append("\"");

    if (anyWords != null && !anyWords.isEmpty()) {
      int sbLen = sb.length();
      for (String w : anyWords.split(" "))
        if (!w.isEmpty())
          sb.append(" ").append(w).append(" OR ");
      if (sb.length() != sbLen)
        sb.setLength(sb.length() - 4);
    }

    if (noneWords != null && !noneWords.isEmpty()) {
      for (String w : noneWords.split(" "))
        if (!w.isEmpty())
          sb.append(" -").append(w);
    }

    if (hashTags != null && !hashTags.isEmpty()) {
      for (String w : hashTags.split(" "))
        if (!w.isEmpty()) {
          sb.append(" #");
          if (w.charAt(0) == '#')
            sb.append(w, 1, w.length());
          else
            sb.append(w);
        }
    }
    return sb.toString();
  }

  public final String getAllWords() {
    return allWords;
  }

  public final void setAllWords(String allWords) {
    this.allWords = allWords;
  }

  public final String getPhrase() {
    return phrase;
  }

  public final void setPhrase(String phrase) {
    this.phrase = phrase;
  }

  public final String getAnyWords() {
    return anyWords;
  }

  public final void setAnyWords(String anyWords) {
    this.anyWords = anyWords;
  }

  public final String getNoneWords() {
    return noneWords;
  }

  public final void setNoneWords(String noneWords) {
    this.noneWords = noneWords;
  }

  public final String getHashTags() {
    return hashTags;
  }

  public final void setHashTags(String hashTags) {
    this.hashTags = hashTags;
  }

  @Override
  public List<String> getFromAccounts() {
    return fromAccounts;
  }

  public final void setFromAccounts(String accounts) {
    fromAccounts = spaceSplitter.splitAsStream(accounts).
        filter(s -> !s.isEmpty()).collect(Collectors.toList());
  }

  public final String getFromAccountsAsString() {
    if (fromAccounts == null)
      return null;
    return fromAccounts.stream().collect(Collectors.joining(", "));
  }

  @Override
  public List<String> getToAccounts() {
    return toAccounts;
  }

  public final void setToAccounts(String accounts) {
    toAccounts = spaceSplitter.splitAsStream(accounts).
        filter(s -> !s.isEmpty()).collect(Collectors.toList());
  }

  public final String getToAccountsAsString() {
    if (toAccounts == null)
      return null;
    return toAccounts.stream().collect(Collectors.joining(", "));
  }

  @Override
  public List<String> getReferredAccounts() {
    return referredAccounts;
  }

  public final void setReferredAccounts(String accounts) {
    referredAccounts = spaceSplitter.splitAsStream(accounts).
        filter(s -> !s.isEmpty()).collect(Collectors.toList());
  }

  public final String getReferredAccountsAsString() {
    if (referredAccounts == null)
      return null;
    return referredAccounts.stream().collect(Collectors.joining(", "));
  }

  @Override
  public ZonedDateTime getFromDate() {
    return fromDate;
  }

  public String getFromDateAsString() {
    if (fromDate == null)
      return null;
    return fromDate.toLocalDateTime().format(dbStrFmt);
  }

  public final void setFromDate(String dateStr) {
    fromDate = null;
    if (dateStr != null && !dateStr.isEmpty()) {
      LocalDate dt = LocalDate.parse(dateStr, dbStrFmt);
      fromDate = ZonedDateTime.of(dt.atStartOfDay(), utcZone);
    }
  }

  @Override
  public ZonedDateTime getToDate() {
    return toDate;
  }

  public String getToDateAsString() {
    if (toDate == null)
      return null;
    return toDate.toLocalDateTime().format(dbStrFmt);
  }

  public final void setToDate(String dateStr) {
    toDate = null;
    if (dateStr != null && !dateStr.isEmpty()) {
      LocalDate dt = LocalDate.parse(dateStr, dbStrFmt);
      toDate = ZonedDateTime.of(dt.atStartOfDay(), utcZone);
    }
  }

  @Override
  public boolean isPositive() {
    return positive;
  }

  public final void setPositive(boolean positive) {
    this.positive = positive;
  }

  @Override
  public boolean isNegative() {
    return negative;
  }

  public final void setNegative(boolean negative) {
    this.negative = negative;
  }

  @Override
  public boolean isQuestion() {
    return question;
  }

  public final void setQuestion(boolean question) {
    this.question = question;
  }

  @Override
  public boolean isIncludeRetweets() {
    return includeRetweets;
  }
  
  
  /** 
   * Implement FilteringParameters interface
   */
  

  public final void setIncludeRetweets(boolean includeRetweets) {
    this.includeRetweets = includeRetweets;
  }

  @Override
  public final boolean isDiscardInconclusive() {
    return this.discardInconclusive;
  }

  public final void setDiscardInconclusive(boolean discardInconclusive) {
    this.discardInconclusive = discardInconclusive;
  }

  @Override
  public boolean isRejectedOnMissingWords() {
    return rejectOnMissingWords;
  }

  public final void setRejectedOnMissingWords(boolean rejectOnMissingWords) {
    this.rejectOnMissingWords = rejectOnMissingWords;
  }

  
  private static final Pattern spaceSplitter = Pattern.compile(" ");
  private static ZoneId utcZone = ZoneId.of("UTC");
  private static DateTimeFormatter dbStrFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
}
