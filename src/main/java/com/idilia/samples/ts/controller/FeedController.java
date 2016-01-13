package com.idilia.samples.ts.controller;

import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.SessionAttributes;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Controller to provide the document feeds (kept and discarded) and overall
 * statistics
 */
@Controller
@SessionAttributes({ "user", "userSearch" })
public class FeedController {

  private final Logger logger = LoggerFactory.getLogger(this.getClass());

  
  /**
   * Operation to start a feed.
   * <p>
   * This returns a template which generates nothing except a link to the next
   * documents. This link is processed by the autocomplete plugin and
   * immediately requests the next documents (See feed{feedType}/next).
   * 
   * @param search
   *          Ongoing search
   * @param feedType
   *          feed requested
   * @param minCnt
   *          minimum number of documents to return. Used to create next link.
   * @param maxCnt
   *          maximum number of documents to return. Used to create next link.
   * @param model
   *          Spring model
   * @return template to generate the HTML
   */
  @RequestMapping("feed/{feedType}/start")
  public String feedStart(@ModelAttribute UserSearch search,
      final @PathVariable("feedType") FeedType feedType,
      final @RequestParam(value = "count", defaultValue = "1") Integer minCnt,
      final @RequestParam(value = "maxCnt", defaultValue = "5") Integer maxCnt,
      Model model) {

    logger.debug("request for first results in feed: " + feedType.toString());
    model.addAttribute("url", getNextLink(feedType, minCnt, maxCnt).toUriString());
    model.addAttribute("isKept", feedType == FeedType.KEPT);
    return "feed/feedStarter :: content";
  }

  /**
   * Helper to assemble the link to pull the next results for the feed
   * 
   * @param feedType
   *          feed requested
   * @param minCnt
   *          minimum number of documents to return. Used to create next link.
   * @param maxCnt
   *          maximum number of documents to return. Used to create next link.
   * @return a UriComponents suitable to provide to the views
   */
  private UriComponents getNextLink(FeedType feedType, int minCnt, int maxCnt) {
    UriComponents url = UriComponentsBuilder.newInstance()
        .path("/feed/{ft}/next")
        .queryParam("minCnt", minCnt)
        .queryParam("maxCnt", maxCnt)
        .build()
        .expand(feedType);
    return url;
  }
  
  /**
   * Operation to return the next documents in a feed.
   * <p>
   * Returns the HTML for the documents requested and the link for the next
   * group of documents. The result is computed asynchronously, hence the
   * returned DeferredResult.
   * 
   * @param search
   *          Ongoing search
   * @param feedType
   *          feed requested
   * @param minCnt
   *          minimum number of documents to return
   * @param maxCnt
   *          maximum number of documents to return
   * @param model
   *          Spring model
   * @return Completable future with the name of the rendering template
   */
  @RequestMapping("feed/{feedType}/next")
  public CompletableFuture<String> feedNext(@ModelAttribute UserSearch search,
      final @PathVariable("feedType") FeedType feedType,
      final @RequestParam(value = "count", defaultValue = "1") Integer minCnt,
      final @RequestParam(value = "maxCnt", defaultValue = "5") Integer maxCnt,
      final Model model) {

    logger.debug("request for next results in feed: " + feedType.toString()
        + " minCount " + minCnt + " maxCnt " + maxCnt);

    model.addAttribute("isKept", feedType == FeedType.KEPT);

    /*
     * Request the next group of results from the UserSearch. This is an
     * asynchronous call that we handle to
     * process the returned documents and any exception thrown.
     */
    return search.getDocumentsAsync(feedType, minCnt, maxCnt).thenApply(docs -> {

      model.addAttribute("docs", docs);

      if (!docs.isEmpty()) {

        // We found tweets
        model.addAttribute("url", getNextLink(feedType, minCnt, maxCnt).toUriString());
        return "feed/feedNext :: content";

      } else if (search.getFeed(FeedType.KEPT).getNumAssigned()
          + search.getFeed(FeedType.DISCARDED).getNumAssigned() == 0) {

        // There are no tweets at all
        return "feed/noTweets";

      } else if (search.getFeed(FeedType.KEPT).getNumAssigned() == 0
          && feedType == FeedType.KEPT) {

        // There are only discarded tweets
        model.addAttribute("classAppend", "result-comment");
        model.addAttribute("msg", "All the tweets have been discarded.");
        return "feed/info :: content";

      } else if (search.getFeed(FeedType.DISCARDED).getNumAssigned() == 0
          && feedType == FeedType.DISCARDED) {

        // There are only kept tweets
        model.addAttribute("classAppend", "result-comment");
        model.addAttribute("msg", "All the tweets have been kept.");
        return "feed/info :: content";

      } else {

        // There are no more tweets
        model.addAttribute("classAppend", "alert alert-info");
        model.addAttribute("msg", "No more tweets.");
        return "feed/info :: content";
      }
    }).exceptionally(ex -> {
      logger.error("Encountered exception when fetching documents", ex);
      model.addAttribute("classAppend", "alert alert-danger");
      model.addAttribute("msg", "Encountered a problem: "
          + ex.getCause().getMessage());
      return "feed/info :: content";
    });
  }

  
  /**
   * Class for returning the the client the statistics on kept/rejected
   * documents. Returned as a JSON object.
   */
  static class ResultStats {
    public int kept;
    public int discarded;
    public String snr;

    public ResultStats(int kept, int discarded, String snr) {
      this.kept = kept;
      this.discarded = discarded;
      this.snr = snr;
    }
  }

  
  /**
   * Get stats on current search and returns a json object to enable the client
   * populate html parts
   * 
   * @param search
   *          ongoing search
   * @param model
   *          Spring model
   * @return object with the statistics
   */
  @RequestMapping("feed/stats")
  @ResponseBody
  public ResultStats getFeedStats(@ModelAttribute UserSearch search, Model model) {
    Integer kept = search.getFeed(FeedType.KEPT).getNumAssigned();
    Integer discarded = search.getFeed(FeedType.DISCARDED).getNumAssigned();
    String snr = null;
    if (discarded == 0)
      snr = "100%";
    else
      snr = String.format("%d%%", Math.round(kept * 100 / (double) (discarded + kept)));

    return new ResultStats(kept, discarded, snr);
  }

  
  /**
   * Allow the client to update the feed stats based on changes done in the
   * documents present in the client. (We forget them once returned.) So if a
   * user keyword is added or removed and that causes some documents to change
   * classification, this lets us know about it and we can adjust our feed
   * statistics.
   * 
   * @param feedType
   *          one of the feed
   * @param diff
   *          delta to apply to the feed. The other feed subtracts this value.
   * @param search
   *          current user search
   * @param model
   *          Spring model
   * @return updated stats object
   */
  @RequestMapping(value = "feed/updStats", method = RequestMethod.POST)
  @ResponseBody
  public ResultStats updateFeedStats(
      final @RequestParam("feedType") FeedType feedType,
      final @RequestParam("diff") int diff, @ModelAttribute UserSearch search,
      Model model) {

    Feed fd = search.getFeed(feedType);
    Feed oFd = search.getFeed(feedType == FeedType.KEPT ? FeedType.DISCARDED
        : FeedType.KEPT);
    fd.adjNumAssigned(diff);
    oFd.adjNumAssigned(-1 * diff);
    return getFeedStats(search, model);
  }

}
