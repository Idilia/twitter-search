package com.idilia.samples.ts.controller;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.LoggerFactory;

import com.idilia.samples.ts.db.Search;
import com.idilia.samples.ts.db.User;
import com.idilia.samples.ts.docs.Document;
import com.idilia.samples.ts.docs.DocumentSource;
import com.idilia.samples.ts.docs.SearchToken;
import com.idilia.samples.ts.idilia.MatchingEvalService;
import com.idilia.tagging.Sense;

/**
 * Class for the ongoing active search. It records the results and is capable of
 * generating the results as needed using the services provided when
 * constructing.
 *
 */
public class UserSearch {

  /**
   * The persistent object which backs-up this object
   */
  private Search dbSearch;

  /**
   * A document source used to fetch unclassified documents
   */
  private DocumentSource docSrc;

  /**
   * A document matching (i.e., classifying) service that can be used to divide
   * the documents fetched.
   */
  private MatchingEvalService matchSvc;

  /**
   * A search token stored transparently here and used by the DocumentSource to
   * retrieve documents.
   */
  private AtomicReference<SearchToken> searchTokenRef;

  /**
   * Keywords entered by the user to conclusively identify a document as
   * matching the query
   */
  Keywords kwsKeep;

  /**
   * Keywords entered by the user to conclusively identify a document as not
   * matching the query
   */
  Keywords kwsDiscard;

  /**
   * The documents that match the query or inconclusive
   */
  final private Feed matching;

  /**
   * The documents that could conclusively be rejected
   */
  final private Feed rejected;

  /**
   * The last fetch request from the user. Null initially
   */
  private DocRequest docRequest;

  /**
   * A future for a document fetch/classify job (FetchJob) that this object runs
   * when it needs more documents to assign to the feeds.
   */
  private CompletableFuture<Boolean> jobRunning = CompletableFuture.completedFuture(Boolean.FALSE);

  private UserSearch() {
    this.matching = new Feed(FeedType.KEPT, -1); // unlimited size
    this.rejected = new Feed(FeedType.DISCARDED, -1);
  }

  /**
   * Constructor used when creating a new search for a user.
   * 
   * @param expression
   *          text of the search expression
   * @param docSrc
   *          document source from which we can pull candidate documents
   * @param matchSvc
   *          matching service used to classify documents
   */
  public UserSearch(User user, String expression, DocumentSource docSrc,
      MatchingEvalService matchSvc) {
    this();
    this.dbSearch = new Search(user, expression);
    this.docSrc = docSrc;
    this.matchSvc = matchSvc;
    this.kwsKeep = new Keywords();
    this.kwsDiscard = new Keywords();
  }

  /**
   * Constructor used when performing a search that was previously done and
   * recorded in the database. The DB info includes the user keywords and the
   * word meanings for the search.
   * 
   * @param dbSrc
   *          db record for the last time the search was performed
   * @param docSrc
   *          document source from which we can pull candidate documents
   * @param matchSvc
   *          matching service used to classify documents
   */
  public UserSearch(Search dbSrc, DocumentSource docSrc, MatchingEvalService matchSvc) {
    this();
    this.dbSearch = dbSrc;
    this.docSrc = docSrc;
    this.matchSvc = matchSvc;

    // Recover the keywords used for classification
    if (dbSrc.getPositiveKeywords() != null)
      this.kwsKeep = new Keywords(dbSrc.getPositiveKeywords());
    else
      this.kwsKeep = new Keywords();

    if (dbSrc.getNegativeKeywords() != null)
      this.kwsDiscard = new Keywords(dbSrc.getNegativeKeywords());
    else
      this.kwsDiscard = new Keywords();
  }

  /**
   * Return the search expression (textual form)
   */
  public String getExpression() {
    return this.dbSearch.getExpression();
  }

  /**
   * Set the senses for the words of the search expression.
   * 
   * @param senses
   *          senses obtained from the tagging menu plugin.
   */
  public void setExpressionSenses(List<Sense> senses) {
    if (getExpressionSenses() != null && !getExpressionSenses().equals(senses)) {
      kwsKeep = new Keywords();
      kwsDiscard = new Keywords();
      jobRunning.cancel(false);
    }
    dbSearch.setSenses(senses);
  }

  /**
   * Reset the processing to start a new search
   */
  public void start() {
    jobRunning.cancel(false);
    searchTokenRef = new AtomicReference<SearchToken>(docSrc.createSearchToken(getExpression()));
    matching.empty();
    rejected.empty();
  }

  /**
   * Return the meanings for each word of the search expression
   */
  public List<Sense> getExpressionSenses() {
    return dbSearch.getSenses();
  }

  /**
   * Return list of user keywords for the type given
   * 
   * @param kwType
   *          type of keywords to return
   * @return list of keywords or empty list when none
   */
  public Keywords getKeywords(KeywordType kwType) {
    return kwType == KeywordType.NEGATIVE ? kwsDiscard : kwsKeep;
  }

  /**
   * Return the feed requested.
   * 
   * @param feedType
   *          feed desired
   * @return Feed object for either the matching or the rejected documents.
   */
  public Feed getFeed(FeedType feedType) {
    return feedType == FeedType.DISCARDED ? rejected : matching;
  }

  /**
   * Add a new user keyword for automatic classification. Existing documents
   * containing the keyword are moved to the correct feed (discarded for a
   * negative keyword, kept for a positive keyword).
   * 
   * @param kwType
   *          Whether the keyword is for matching or rejecting
   * @param kw
   *          string of the keyword
   * @return true if the keyword was added (i.e., did not already exist)
   */
  public boolean addKeyword(KeywordType kwType, String kw) {
    // Add to the specified list. If a change, update the feeds
    boolean added = getKeywords(kwType).add(kw);
    if (added) {

      // Remove it from the other keyword list in case it was there.
      removeKeyword(kwType == KeywordType.NEGATIVE ? KeywordType.POSITIVE : KeywordType.NEGATIVE,
          kw);

      /*
       * Attempt to match it in both feeds. This can result in some documents
       * moving (e.g., adding a positive keyword and finding it in the discarded
       * feed moves the document to the kept feed.
       */
      List<FeedDocument> fromRej = rejected.addUserKeyword(kwType, kw);
      rejected.addAll(matching.addUserKeyword(kwType, kw));
      matching.addAll(fromRej);
    }
    return added;
  }

  /**
   * Remove a keyword from the list used for automatic classification. Should
   * removing a keyword modify the document classification, it it moved to the
   * correct feed. If the resulting classification is neutral, then compute it.
   * <p>
   * 
   * @param kwType
   *          Whether the keyword is for matching or rejecting
   * @param kw
   *          string of the keyword
   * @return true if the keyword existed
   */
  public boolean removeKeyword(KeywordType kwType, String kw) {
    boolean removed = getKeywords(kwType).remove(kw);
    if (removed) {
      /* Remove the keyword from docs in both feeds */
      List<FeedDocument> fromRej = rejected.removeUserKeyword(kwType, kw);
      List<FeedDocument> fromMatching = matching.removeUserKeyword(kwType, kw);

      /*
       * From the docs obtained, some will now be inconclusive. Those are ran
       * through the Idilia classifying service. Move the others to their new
       * feed.
       */
      List<Document> toReclass = new ArrayList<>(fromRej.size() + fromMatching.size());
      for (FeedDocument d : fromRej) {
        if (d.getClassificationFromUserKeywords() == 0)
          toReclass.add(d.getDoc());
        else
          matching.add(d);
      }

      for (FeedDocument d : fromMatching) {
        if (d.getClassificationFromUserKeywords() == 0)
          toReclass.add(d.getDoc());
        else
          rejected.add(d);
      }

      if (!toReclass.isEmpty()) {
        try {
          List<Integer> res = matchSvc.matchAsync(getExpressionSenses(), toReclass).join();
          for (int i = 0; i < res.size(); ++i) {
            Document d = toReclass.get(i);
            if (res.get(i) < 0)
              rejected.add(new FeedDocument(d));
            else
              matching.add(new FeedDocument(d));
          }
        } catch (CompletionException | CancellationException e) {
          /* Unexpected exception. Just drop those documents */
          LoggerFactory.getLogger(this.getClass()).error("Failed to reclassify", e);
        }
      }
    }
    return removed;
  }

  /**
   * Return the fraction of documents retrieved from the document source that
   * match the search expression.
   */
  public double signalToNoiseRatio() {
    if (rejected.getNumAssigned() > 0)
      return matching.getNumAssigned() / (double) rejected.getNumAssigned();
    else
      return 1.0;
  }

  /**
   * Return an updated persistent object to record the search.
   * 
   * @return database object to persist
   */
  Search toDb() {
    dbSearch.setPositiveKeywords(new ArrayList<>(kwsKeep.getSet()));
    dbSearch.setNegativeKeywords(new ArrayList<>(kwsDiscard.getSet()));
    return dbSearch;
  }

  /**
   * Retrieve documents from this search.
   * <p>
   * This pulls the document from the source, filters them into the matching or
   * discarded feeds, and returns documents from the requested feed.
   * <p>
   * Subsequent requests are immediatably fillable when the requested feed has
   * sufficient documents to meet the request. Or when the search is exhausted
   * and not more results are available. In that case an empty collection is
   * assigned to the future.
   * <p>
   * 
   * @param feedType
   *          feed from which to retrieve documents
   * @param minCnt
   *          minimum number of documents to retrieve
   * @param maxCnt
   *          maximum number of documents to retrieve
   * @return a completable future that is set once results become available.
   */
  public CompletableFuture<List<FeedDocument>> getDocumentsAsync(FeedType feedType, int minCnt,
      int maxCnt) {
    /* We should always have senses and never a pending request */
    assert (getExpressionSenses() != null);
    assert (docRequest == null || docRequest.isDone());

    final CompletableFuture<List<FeedDocument>> future = new CompletableFuture<>();
    Feed feed = getFeed(feedType);

    if (searchTokenRef.get().isFinished()) {
      /*
       * Search is finished. Return whatever we have or none if feed is empty
       */
      future.complete(feed.getNext(maxCnt));
    } else if (feed.getNumAvailable() >= minCnt) {
      /*
       * We have enough to satisfy the request. Do it. But if not enough left
       * afterwards, then get some more proactively.
       */
      future.complete(feed.getNext(maxCnt));
      if (feed.getNumAvailable() < minCnt && !isJobRunning())
        startFetchJob();
    } else {
      /*
       * Not enough document available. Initiate fetching and classifying
       */
      docRequest = new DocRequest(future, feed, minCnt, maxCnt);
      if (!isJobRunning())
        startFetchJob();
    }

    return future;
  }

  /**
   * Sychronous version of getDocumentsAsync
   * 
   * @throws CompletionException
   *           that wraps the true Exception
   */
  public List<FeedDocument> getDocuments(FeedType feedType, int minCnt, int maxCnt)
      throws CompletionException {
    CompletableFuture<List<FeedDocument>> future = getDocumentsAsync(feedType, minCnt, maxCnt);
    return future.join();
  }

  /**
   * Returns true when our internal fetch/classify job is running.
   */
  private boolean isJobRunning() {
    return !jobRunning.isDone();
  }

  /**
   * Starts the internal fetch/classify job
   * <p>
   * This creates an asynchronous pipeline that launches the job and upon its
   * completion, signals any pending user request.
   */
  private void startFetchJob() {
    jobRunning = new FetchJob()
        .start()
        .whenComplete(
            (rc, ex) -> {
              if (ex != null)
                docRequest.future.completeExceptionally(ex);
              else {
                /*
                 * Job ended normally. Attempt to satisfy a pending user
                 * request. If that's not possible or nothing left, then start
                 * another job.
                 */
                if ((!docRequest.isDone() && !signalPendingRequest())
                    || (!searchTokenRef.get().isFinished() && docRequest.feed.getNumAvailable() < docRequest.minCnt))
                  startFetchJob();
              }
            });
  }

  /**
   * Checks if a user request is pending and attempts to statisfy it.
   * 
   * @returns true when the request was signaled (either because the search is
   *          done or because we had enough documents.
   */
  private boolean signalPendingRequest() {
    if (docRequest.feed.getNumAvailable() >= docRequest.minCnt || searchTokenRef.get().isFinished()) {
      docRequest.future.complete(docRequest.feed.getNext(docRequest.maxCnt));
      return true;
    } else
      return false;
  }

  /**
   * Helper class to record the parameters of a user request for documents.
   */
  static private class DocRequest {
    /** The future to signal when the request is complete */
    final CompletableFuture<List<FeedDocument>> future;

    /** The feed from which to return results */
    final Feed feed;

    /** Mininum and maximum number of documents to return in the future */
    final int minCnt, maxCnt;

    DocRequest(CompletableFuture<List<FeedDocument>> future, Feed fd, int minCnt, int maxCnt) {
      this.future = future;
      this.feed = fd;
      this.minCnt = minCnt;
      this.maxCnt = maxCnt;
    }

    /**
     * Returns true when the user request is completed (i.e., the future was
     * signaled with available documents.
     */
    boolean isDone() {
      return future.isDone();
    }
  }

  /**
   * Class for the internal job that we run to fetch/classify documents. It uses
   * the document source to pull documents. It does a preliminary document
   * classification using the user keywords. The other documents are then
   * classified using the Idilia matching/eval API.
   */
  private class FetchJob {

    /**
     * Start a fetch/classify job. This constructs an asynchronous pipeline and
     * returns with a future signaled when that pipeline completes.
     * 
     * @return a future with a boolean value set to true when the pipeline
     *         completes normally.
     */
    CompletableFuture<Boolean> start() {
      /*
       * Create an asychronous processing chain. This function returns
       * immediately but the tasks are scheduled in the background
       */

      /* First step: pull 100 documents from the document source */
      return docSrc.getNextDocuments(searchTokenRef.get(), 100)

        .thenCompose(/* List<Document> */ds -> {
  
          /* Save the rx documents */
          this.docs = new ArrayList<>(ds.size());
          for (Document d : ds)
            this.docs.add(new FeedDocument(d));
  
          /*
           * Second step: Do a preliminary document screening based on the user
           * keywords
           */
          this.kwRes = classifyUsingUserKeywords(docs);
  
          /* Third step: Use the Idilia API to classify the remaining unknown docs */
          List<Document> forApi = new ArrayList<>(docs.size());
          for (int i = 0; i < docs.size(); ++i)
            if (kwRes.get(i) == 0)
              forApi.add(docs.get(i).getDoc());
          return matchSvc.matchAsync(getExpressionSenses(), forApi);
  
        }).thenApply(/* List<Integer> */apiRes -> {
  
          /*
           * Final step: Record the documents into their feed. Maintain the
           * original order
           */
          int apiIdx = 0;
          for (int docIdx = 0; docIdx < docs.size(); ++docIdx) {
            if (kwRes.get(docIdx) < 0)
              rejected.add(docs.get(docIdx));
            else if (kwRes.get(docIdx) > 0)
              matching.add(docs.get(docIdx));
            else {
              if (apiRes.get(apiIdx) < 0)
                rejected.add(docs.get(docIdx));
              else {
                // both matching and indeterminate go to matching feed
                matching.add(docs.get(docIdx)); 
              }
              ++apiIdx;
            }
          }
          return Boolean.TRUE;
        });
    }

    /**
     * Helper function to classify documents using the user keywords. Updates in
     * the FeedDocument the positive and negative keywords found. Returns <0, 0,
     * >0 for each document when not matching, unknown, or matching. The value
     * is set based on the number of keyords found.
     * 
     * @param a
     *          list of document to analyze
     * @return a list of return codes for each document in the same order as the
     *         input list
     */
    private List<Integer> classifyUsingUserKeywords(List<FeedDocument> docs) {
      Pattern manMatch = kwsKeep.getRegexPattern();
      Pattern manDiscard = kwsDiscard.getRegexPattern();

      if (manMatch == null && manDiscard == null)
        return Collections.nCopies(docs.size(), 0);

      List<Integer> rcs = new ArrayList<>(docs.size());
      for (FeedDocument doc : docs) {

        /* Find all possible positive user keywords in the document */
        Matcher matcherKeep = manMatch == null ? null : manMatch.matcher(doc.getText());
        boolean manKeep = matcherKeep == null ? false : matcherKeep.find();
        if (manKeep) {
          do {
            doc.addKeyword(KeywordType.POSITIVE, matcherKeep.group());
          } while (matcherKeep.find());
        }

        /* Find all possible negative user keywords in the document */
        Matcher matcherDisc = manDiscard == null ? null : manDiscard.matcher(doc.getText());
        boolean manDisc = matcherDisc == null ? false : matcherDisc.find();
        if (manDisc) {
          do {
            doc.addKeyword(KeywordType.NEGATIVE, matcherDisc.group());
          } while (matcherDisc.find());
        }

        rcs.add(doc.getClassificationFromUserKeywords());
      }
      return rcs;
    }

    /** The list of documents returned by the document service */
    List<FeedDocument> docs;

    /** The matching codes computed by the user keywords */
    List<Integer> kwRes;
  }

  @Override
  public String toString() {
    return getExpression();
  }
}
