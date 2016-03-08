package com.idilia.samples.ts.controller;

import java.util.List;
import java.util.stream.Collectors;

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
import org.springframework.web.context.request.async.DeferredResult;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import com.idilia.samples.ts.twitter.TwitterHttpAsyncClient.TwitterRateLimitingException;
import com.idilia.services.text.MatchingEvalResponse.SkModelStatus;

/**
 * Controller to provide the document feeds (kept and discarded) and overall
 * statistics
 */
@Controller
@SessionAttributes({ "user", "search" })
public class FeedController {

  private final Logger logger = LoggerFactory.getLogger(this.getClass());

  /**
   * Operation to start a feed.
   * <p>
   * This returns a template which generates a message with the API usage of the
   * meanings of each search term and a link to fetch the first documents. This
   * link is processed by the autocomplete plugin and immediately requests the
   * next documents (See feed{feedType}/next).
   * 
   * @param search Ongoing search
   * @param feedType feed requested
   * @param minCnt minimum number of documents to return. Used to create next
   *        link.
   * @param maxCnt maximum number of documents to return. Used to create next
   *        link.
   * @param model Spring model
   * @return DeferredResult with the name of the rendering template
   */
  @RequestMapping("feed/{feedType}/start")
  public DeferredResult<String> feedStart(
      final @ModelAttribute Search search,
      final @PathVariable("feedType") FeedType feedType,
      final @RequestParam(value = "count", defaultValue = "1") Integer minCnt,
      final @RequestParam(value = "maxCnt", defaultValue = "5") Integer maxCnt,
      Model model) {

    logger.debug("request for first results in feed: " + feedType.toString());

    final DeferredResult<String> dfRes = new DeferredResult<>((long) 5 * 60 * 1000);
    model.addAttribute("url", getNextLink(feedType, minCnt, maxCnt).toUriString());
    model.addAttribute("isKept", feedType == FeedType.KEPT);
    final String tmplt = "feed/feedStarter :: content";

    if (feedType == FeedType.KEPT) {
      /*
       * On the kept feed, we output a message to indicate if some meanings were
       * not used. We have to wait for the first API result to become available.
       */
      search.getSksEvalStatus().whenComplete((List<SkModelStatus> sksEvalStatus, Throwable t) -> {
        if (t != null) {
          dfRes.setErrorResult(t);
        } else {
          String msg = skStatusToMessage(sksEvalStatus);
          if (msg != null) {
            model.addAttribute("classAppend", "alert-warning skstatus-info");
            model.addAttribute("msg", msg);
          }
          dfRes.setResult(tmplt);
        }
      });
    } else {
      /*
       * No message to output, we can complete the deferred result immediately
       */
      dfRes.setResult(tmplt);
    }

    return dfRes;
  }
  
  /**
   * Helper to create a message from the meaning status returned by the
   * matching/eval service.
   * @param sksEvalStatus member of the MatchEvalResponse. Indicates which
   *        sensekeys were used during matching.
   * @return a message with the words where the meaning was not used.
   */
  String skStatusToMessage(List<SkModelStatus> sksEvalStatus) {
    StringBuffer sb = new StringBuffer();
    
    /* Gather the words where the meaning was not used */
    List<String> unused = sksEvalStatus.stream().filter(s -> !s.wasUsed()).map(SkModelStatus::getText)
        .collect(Collectors.toList());

    if (unused.size() == 1)
      sb.append("Meaning-based search is unavailable for \"" + unused.get(0) + "\". Falling back to the word itself.");
    else if (unused.size() > 1) {
      sb.append("Meaning-based search is unavailable for \"");
      sb.append(unused.stream().collect(Collectors.joining(", ")));
      sb.append("\". Falling back to the words.");
    }
    
    /** 
     * Where the SkModelStatus::getCode is 4 or 5, the meaning is used but we expect 
     * little discrimination of results:
     * <li> 4 - selected meaning is rare and most documents are discarded. Those
     *     not discarded are mostly inconclusive. That's visible so no message required.
     * <li> 5 - selected meaning is almost always used. Most documents are kept
     *     and conclusive and good. No need for a message.
     */
    
    return sb.length() == 0 ? null : sb.toString();
  }

  /**
   * Helper to assemble the link to pull the next results for the feed
   * 
   * @param feedType feed requested
   * @param minCnt minimum number of documents to return. Used to create next
   *        link.
   * @param maxCnt maximum number of documents to return. Used to create next
   *        link.
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
   * @param search Ongoing search
   * @param feedType feed requested
   * @param minCnt minimum number of documents to return
   * @param maxCnt maximum number of documents to return
   * @param model Spring model
   * @return Completable future with the name of the rendering template
   */
  @RequestMapping("feed/{feedType}/next")
  public DeferredResult<String> feedNext(@ModelAttribute Search search,
      final @PathVariable("feedType") FeedType feedType,
      final @RequestParam(value = "count", defaultValue = "1") Integer minCnt,
      final @RequestParam(value = "maxCnt", defaultValue = "5") Integer maxCnt,
      final Model model) {

    logger.debug("request for next results in feed: " + feedType.toString()
        + " minCount " + minCnt + " maxCnt " + maxCnt);

    model.addAttribute("isKept", feedType == FeedType.KEPT);

    /*
     * We return a DeferredResult instead of a CompletionFuture because we can
     * specify a longer than normal timeout. This is necessary when we have to
     * search for a long time for a rare sense.
     */
    final DeferredResult<String> dfRes = new DeferredResult<>((long) 5 * 60 * 1000);

    /*
     * Request the next group of results from the Search. This is an
     * asynchronous call that we handle to process the returned documents and
     * any exception thrown.
     */
    search.getDocumentsAsync(feedType, minCnt, maxCnt).thenApply(docs -> {

      model.addAttribute("docs", docs);

      if (!docs.isEmpty()) {

        // We found tweets
        model.addAttribute("url", getNextLink(feedType, minCnt, maxCnt).toUriString());
        return "feed/feedNext :: content";

      } else if (search.getFeed(FeedType.KEPT).getNumAssigned()
          + search.getFeed(FeedType.DISCARDED).getNumAssigned() == 0) {

        // There are no tweets at all
        return "feed/noTweets :: content";

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

    }).exceptionally(cex -> {
      RuntimeException ex = (RuntimeException) cex.getCause();
      logger.error("Encountered exception when fetching documents", ex);
      model.addAttribute("classAppend", "alert alert-danger");
      if (ex instanceof TwitterRateLimitingException)
        model.addAttribute("msg", "Twitter search API rate limit exceeded.");
      else
        model.addAttribute("msg", "Encountered a problem: " + ex.getCause().getMessage());
      return "feed/info :: content";

    }).whenComplete((tmplt, ex) -> {
      dfRes.setResult(tmplt);
    });

    return dfRes;
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
   * @param search ongoing search
   * @param model Spring model
   * @return object with the statistics
   */
  @RequestMapping("feed/stats")
  @ResponseBody
  public ResultStats getFeedStats(@ModelAttribute Search search, Model model) {
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
   * @param feedType one of the feed
   * @param diff delta to apply to the feed. The other feed subtracts this
   *        value.
   * @param search current user search
   * @param model Spring model
   * @return updated stats object
   */
  @RequestMapping(value = "feed/updStats", method = RequestMethod.POST)
  @ResponseBody
  public ResultStats updateFeedStats(
      final @RequestParam("feedType") FeedType feedType,
      final @RequestParam("diff") int diff, @ModelAttribute Search search,
      Model model) {

    Feed fd = search.getFeed(feedType);
    Feed oFd = search.getFeed(feedType == FeedType.KEPT ? FeedType.DISCARDED
        : FeedType.KEPT);
    fd.adjNumAssigned(diff);
    oFd.adjNumAssigned(-1 * diff);
    return getFeedStats(search, model);
  }

}
