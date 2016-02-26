package com.idilia.samples.ts.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.SessionAttributes;
import org.springframework.web.context.request.async.DeferredResult;

import com.idilia.samples.ts.db.DbSearchService;
import com.idilia.samples.ts.db.User;
import com.idilia.samples.ts.docs.DocumentSource;
import com.idilia.samples.ts.idilia.MatchingEvalService;
import com.idilia.samples.ts.twitter.TwitterHttpAsyncClient.TwitterRateLimitingException;

/**
 * Controller to manage the fetching and editing of the user keywords
 */
@Controller
@SessionAttributes({ "searchForm", "user", "search", "kwSearch" })
public class KeywordsController {

  @Autowired
  private DocumentSource docSrc;

  @Autowired
  private MatchingEvalService matchSvc;

  @Autowired
  private DbSearchService searchDbSvc;

  private final Logger logger = LoggerFactory.getLogger(this.getClass());

  /**
   * Return the user keywords for the current search. Both the keep (positive)
   * and discard (negative) keywords.
   * 
   * @param search Active search
   * @return the name of the template that renders the keywords
   */
  @RequestMapping("keywords/all")
  public String getAllKeywords(@ModelAttribute Search search, Model model) {

    logger.debug("request for all keywords");

    model.addAttribute("positive", search.getKeywords(KeywordType.POSITIVE)
        .getSet());
    model.addAttribute("negative", search.getKeywords(KeywordType.NEGATIVE)
        .getSet());
    return "keywords/all :: content";
  }

  /**
   * Return the user keywords (keep or discard) for the current search.
   * 
   * @param search Active search
   * @param kwType keyword list to edit
   * @return the name of the template that renders the keywords
   */
  @RequestMapping("keywords/{kwType}/list")
  public String getKeywords(@ModelAttribute Search search,
      @PathVariable("kwType") KeywordType kwType,
      @RequestParam(value = "first", defaultValue = "") String firstKw,
      Model model) {

    logger.debug("request for keywords type: " + kwType.toString());

    model.addAttribute("keywords", search.getKeywords(kwType).getSet());
    model.addAttribute("kwType", kwType);
    return "keywords/keywords :: content";
  }

  /**
   * Exception thrown when a user keyword addition or removal request results in
   * no change to the keyword list. On the javascript side we see this as an
   * empty document and do not replace the keyword list.
   */
  @SuppressWarnings("serial")
  @ResponseStatus(value = HttpStatus.NOT_MODIFIED)
  static class KeywordUnchangeException extends RuntimeException {
  };

  /**
   * Add a new user keyword.
   * <p>
   * 
   * @param search Active search
   * @param kwType keyword list to edit
   * @param keyword keyword to remove
   * @param model Spring model
   * @return redirection to the keywords list operation
   * @throws KeywordUnchangeException if the keyword is not added because it was
   *         already present.
   */
  @RequestMapping(value = "keywords/{kwType}/new", method = RequestMethod.POST)
  public String newUserKeyword(@ModelAttribute Search search,
      @PathVariable("kwType") KeywordType kwType,
      @RequestParam("k") String keyword, Model model) {

    logger.debug(String.format("Add keyword [%s] to keyword list type: %s",
        keyword, kwType.toString()));

    if (!search.addKeyword(kwType, keyword))
      throw new KeywordUnchangeException();
    else
      searchDbSvc.save(search.toDb());

    // Return both lists because both are edited if found in the other list
    return "redirect:/keywords/all";
  }

  /**
   * Remove a user keyword
   * <p>
   * 
   * @param search Active search
   * @param kwType keyword list to edit
   * @param keyword keyword to remove
   * @param model Spring model
   * @return redirection to the keywords list operation
   * @throws KeywordUnchangeException if the keyword cannot be removed
   */
  @RequestMapping(value = "keywords/{kwType}/remove", method = RequestMethod.POST)
  public String removeUserKeyword(@ModelAttribute Search search,
      @PathVariable("kwType") KeywordType kwType,
      @RequestParam("k") String keyword, Model model)
          throws KeywordUnchangeException {

    logger.debug(String.format("Remove keyword [%s] to keyword list type: %s",
        keyword, kwType.toString()));

    if (!search.removeKeyword(kwType, keyword))
      throw new KeywordUnchangeException();
    else
      searchDbSvc.save(search.toDb());

    // Return updated list of keywords. This goes to /keywords/{kwType}/list
    return "redirect:list";
  }

  /**
   * For a user keyword addition, the process is the following: The user
   * highlights an expression in a visible tweet. The javascript creates a
   * dialog box that contains the tweets in the browser feed that contain that
   * keyword and invokes feed/{type}/preview to pull the tweets still in the
   * server that contain the keyword. The response includes those tweets and a
   * "next" link to fetch more.
   */

  /**
   * Invoked to get the tweets that contain a specific keyword and already
   * available in a feed. Also includes a link to start pulling more content for
   * this keyword
   * 
   * @param search the existing ongoing search
   * @param feedType type of feed being displayed on the client side
   * @param keyword the keyword highlighted being considered as a user keyword
   * @return view displaying the tweets matching the keyword and a link to pull
   *         more
   */
  @RequestMapping("keywords/{feedType}/preview")
  public String keywordPreview(
      @ModelAttribute Search search,
      final @PathVariable("feedType") FeedType feedType,
      @RequestParam("k") String keyword,
      final Model model) {
    model.addAttribute("docs", search.getFeed(feedType).getMatching(keyword));
    model.addAttribute("isKept", feedType == FeedType.KEPT);
    model.addAttribute("url", "keywords/" + feedType + "/search/start");
    model.addAttribute("keyword", keyword);
    return "keywords/searchFeed :: content";
  }

  /**
   * Handler to request a search with the current search expression and an
   * additional keyword.
   * 
   * @param user user in context
   * @param search Active search
   * @param feedType type of feed currently displayed in the client
   * @param keyword a candidate keyword
   * @return DeferredResult with the name of the rendering template
   */
  @RequestMapping(value = "keywords/{feedType}/search/start", method = RequestMethod.GET)
  public DeferredResult<String> searchKeyword(
      @ModelAttribute User user,
      @ModelAttribute Search search,
      @ModelAttribute SearchForm searchForm,
      final @PathVariable("feedType") FeedType feedType,
      @RequestParam("k") String keyword, Model model) {

    logger.debug("search with senses + keyword: " + keyword);

    /*
     * Create a new Search from the current one. Make it fetch some documents
     * and return the appropriate feed based on the keyword type.
     */
    String expr = search.getExpression() + " \"" + keyword + "\"";
    Search kwSearch = new Search(user, expr, docSrc, matchSvc);
    kwSearch.setExpressionSenses(search.getExpressionSenses());
    kwSearch.setDocumentFilter(keyword);
    kwSearch.start(docSrc.extendSearch(searchForm, search.getSearchToken(), expr), searchForm);
    model.addAttribute("kwSearch", kwSearch);

    return searchKeywordNext(kwSearch, feedType, model);
  }

  /**
   * Handler to pull in additional documents for the current search expression
   * and an additional keyword.
   * 
   * @param kwSearch the search specific to the keyword as created in
   *        {@link #searchKeyword}
   * @param feedType type of feed currently displayed in the client
   * @return DeferredResult with the name of the rendering template
   */
  @RequestMapping(value = "keywords/{feedType}/search/next", method = RequestMethod.GET)
  public DeferredResult<String> searchKeywordNext(
      @ModelAttribute("kwSearch") Search kwSearch,
      final @PathVariable("feedType") FeedType feedType,
      Model model) {

    /*
     * Don't return the CompletableFuture directly because we want to extend the
     * default timeout to something longer because we may need to search for a
     * long time when the search senses are rare.
     */
    final DeferredResult<String> dfRes = new DeferredResult<>((long) 5 * 60 * 1000);

    kwSearch.getDocumentsAsync(feedType, 1, 5).thenApply(docs -> {

      model.addAttribute("isKept", feedType == FeedType.KEPT);

      if (docs.isEmpty()) {
        model.addAttribute("classAppend", "alert alert-info");
        model.addAttribute("msg", "No more tweets.");
        return "feed/info :: content";
      } else {
        model.addAttribute("docs", docs);
        model.addAttribute("url", "keywords/" + feedType + "/search/next");
        return "feed/feedNext :: content";
      }

    }).exceptionally(cex -> {
      RuntimeException ex = (RuntimeException) cex.getCause();
      logger.error("Encountered exception when fetching documents", ex);
      if (ex instanceof TwitterRateLimitingException)
        model.addAttribute("msg", "Twitter search API rate limit exceeded.");
      else
        model.addAttribute("msg", "Encountered a problem: " + ex.getCause().getMessage());
      model.addAttribute("classAppend", "alert alert-danger");
      return "feed/info :: content";

    }).whenComplete((tmplt, ex) -> {
      dfRes.setResult(tmplt);
    });

    return dfRes;
  }

}
