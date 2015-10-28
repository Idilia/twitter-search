package com.idilia.samples.ts.controller;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.HttpSessionRequiredException;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.SessionAttributes;
import org.springframework.web.context.request.async.DeferredResult;

import com.idilia.samples.ts.db.Search;
import com.idilia.samples.ts.db.SearchDbService;
import com.idilia.samples.ts.db.SearchExpr;
import com.idilia.samples.ts.db.User;
import com.idilia.samples.ts.db.UserRepository;
import com.idilia.samples.ts.docs.DocumentSource;
import com.idilia.samples.ts.idilia.MatchingEvalService;
import com.idilia.samples.ts.idilia.TaggingMenuService;
import com.idilia.services.kb.TaggingMenuResponse;
import com.idilia.tagging.AprioriTagger;
import com.idilia.tagging.AprioriTaggerBuilder;
import com.idilia.tagging.Sense;

@Controller
@SessionAttributes({ "user", "userSearch" })
public class SearchController {

  @Autowired
  private UserRepository userRepo;

  @Autowired
  private SearchDbService searchDbSvc;

  @Autowired
  private DocumentSource docSrc;

  @Autowired
  private MatchingEvalService matchSvc;

  @Autowired
  private TaggingMenuService taggingMenuSvc;

  private final Logger logger = LoggerFactory.getLogger(this.getClass());

  /**
   * This function adds to the model the session attribute User for the active
   * user. The user id is stored in a cookie with name "uid". If the cookie is
   * absent or its value does not match a current user, a new user is allocated
   * and the cookie value is set.
   * 
   * @param userIdS
   *          Current value for the cookie. Empty when cookie is absent.
   * @param response
   *          Updated with the cookie.
   * @return User retrieved or allocated for the session.
   */
  private User getUser(String userIdS, HttpServletResponse response) {

    /* Recover the user from the cookie value */
    User user = null;
    UUID uId = null;
    if (userIdS != null) {
      try {
        uId = UUID.fromString(userIdS);
        List<User> users = userRepo.findById(uId);
        if (!users.isEmpty())
          user = users.get(0);
      } catch (IllegalArgumentException e) {
      }
    }

    /* Create the user when not found */
    if (user == null) {
      user = User.create();
      if (uId != null)
        user.setId(uId);
      user = userRepo.save(user);
      logger.debug("Created new user " + user.toString());
    }

    // Create or refresh the cookie
    Cookie c = new Cookie("uid", user.getId().toString());
    c.setMaxAge(60 * 60 * 24 * 365); // expire in one year
    response.addCookie(c);

    return user;
  }

  /**
   * Whenever the session expires, the session attribute "user" and "userSearch"
   * are removed. If another request is made where those attribute are expected
   * to exist (e.g., /search), then this exception is raised.
   * 
   * @throws IOException
   */
  @ExceptionHandler(HttpSessionRequiredException.class)
  @ResponseBody
  public void handleExpiredSession(HttpServletResponse response) throws IOException {

    logger.debug("Session expired");

    /*
     * Set an error code detected by the javascript (application.js) This causes
     * a message to be displayed by the client.
     */
    response.sendError(HttpStatus.REQUEST_TIMEOUT.value());
  }

  /**
   * Home mapping.
   * <p>
   * Recover the user from the cookie and store it as a persistent attribute of
   * the model. Also create a blank search and also add it to the model. Return
   * the name of the template that displays the search box.
   * <p>
   * 
   * @return name of template to display
   */
  @RequestMapping("/")
  public String home(@CookieValue(value = "uid", defaultValue = "") String userIdS,
      HttpServletResponse response, Model model) {

    logger.debug("Request for home page");

    /*
     * Add the two session attributes that we expect to always find. The initial
     * value for the UserSearch is an empty search string.
     */
    User user = getUser(userIdS, response);
    model.addAttribute(user);
    model.addAttribute(new UserSearch(user, "", docSrc, matchSvc));

    /* Return the template with the search box */
    return "application";
  }

  /**
   * Mapping to obtain history of previous searches.
   * <p>
   * Retrieve previous searches for the user.
   * 
   * @param user
   *          Active user
   * @param prefix
   *          prefix for the search
   * @param model
   *          Updated model
   * @return list of search expressions previously performed starting with the
   *         prefix
   */
  @RequestMapping("history")
  @ResponseBody
  public List<SearchExpr> expressions(@ModelAttribute User user, @RequestParam("q") String prefix,
      Model model) {

    logger.debug("Request previous searches (history) for prefix: " + prefix);

    List<SearchExpr> exprs = searchDbSvc.getRepo().findUserExpressionsStartingWith(user, prefix);
    return exprs;
  }

  /**
   * Handler when the search expression has been entered.
   * <p>
   * Obtain a sense tagging menu for the expression. Uses multiple strategies to
   * recover the sense information whenever possible.
   * 
   * @param user
   *          Active user
   * @param expr
   *          string with the search expression entered
   * @param model
   *          Spring model
   * @return
   */
  @RequestMapping("search")
  public DeferredResult<String> search(@ModelAttribute User user,
      @ModelAttribute UserSearch search, @RequestParam("q") String expr, Model model) {

    logger.debug("Request for a new search expression: " + expr);

    if (!expr.equals(search.getExpression())) {

      /* Record the previous search in the database for later recall */
      if (search.getExpressionSenses() != null)
        searchDbSvc.save(search.toDb());

      /*
       * We're now going to generate a tagging menu for this expression. To make
       * the task easier for the end-user, we're going to set the senses
       * information if we can: - first use a previous search by this user -
       * second use the same previous search by another user
       */

      List<Search> userPrevs = searchDbSvc.getRepo().findByUserAndExpression(user, expr);
      if (!userPrevs.isEmpty())
        search = new UserSearch(userPrevs.get(0), docSrc, matchSvc);
      else {
        search = new UserSearch(user, expr, docSrc, matchSvc);
        List<Search> prevs = searchDbSvc.getRepo().findByExpression(expr);
        if (!prevs.isEmpty())
          search.setExpressionSenses(prevs.get(0).getSenses());
      }
      model.addAttribute(search);
    }

    /*
     * Future that we create here and set to the asynchronous processing
     * required to convert the search expression into a sense tagging menu.
     */
    CompletableFuture<TaggingMenuResponse> tmFtr = null;

    if (search.getExpressionSenses() != null) {
      /* We have the senses. Build a tagging menu that is initialized with them. */
      tmFtr = taggingMenuSvc.getTaggingMenu(search.getExpressionSenses(), user.getId());
    } else {
      /*
       * First time running this search. Attempt to automatically set its senses
       * using the senses recently used.
       */
      List<Sense> senses = new ArrayList<Sense>();
      for (SearchExpr se : searchDbSvc.getRepo().findRecentExpressions(user, 10))
        senses.addAll(se.senses);
      AprioriTagger tagger = new AprioriTaggerBuilder(senses).setCaseInsensitive(true)
          .setMatchesPluralNouns(true).build();
      String tmText = taggingMenuSvc.convertBooleanExp(expr);
      tmText = tagger.tag(tmText);
      tmFtr = taggingMenuSvc.getTaggingMenu(tmText, user.getId());
    }

    /*
     * Spring does not understand CompletableFuture but it has an equivalent
     * DeferredResult. When our CompletableFuture is set upon completion of the
     * menu, set the deferred result. This triggers applying the template and
     * HTML generation.
     */
    final DeferredResult<String> result = new DeferredResult<>(60 * 1000);

    tmFtr.whenComplete((/* TaggingMenuResponse */tm, ex) -> {
      if (ex != null) {
        /*
         * This will normally be an IdiliaClientException wrapped inside a
         * CompletionException
         */
        result.setErrorResult(new TaggingMenuException());
        logger.error("Failed to generate tagging menu", ex);
      } else {
        model.addAttribute("tm", tm);
        result.setResult("search/searchWithSenses :: content");
      }
    });

    return result;
  }

  /** Exception thrown when we fail to generate a tagging menu */
  @SuppressWarnings("serial")
  @ResponseStatus(value = HttpStatus.SERVICE_UNAVAILABLE)
  static class TaggingMenuException extends RuntimeException {
  }

  /**
   * Handler to record the selected senses for the search expression.
   * <p>
   * Record the senses and start the search. After this the client can start
   * polling for results.
   * 
   * @param search
   *          Active search
   * @param senses
   *          A collection of Sense objects that were read by the client as the
   *          values of the tagging menu. Receives as the JSON body of the
   *          request.
   * @param model
   *          Spring model
   * 
   * @return a redirect to the method that starts a feed display
   */
  @RequestMapping(value = "searchSenses", method = RequestMethod.POST, consumes = "application/json; charset=utf-8")
  public String searchSenses(@ModelAttribute UserSearch search,
      @RequestBody final ArrayList<Sense> senses, Model model) {

    logger.debug("search with senses: " + senses.toString());

    search.setExpressionSenses(senses);
    search.start();

    // Return the initial content for the feed for kept documents
    return "redirect:/feed/" + FeedType.KEPT + "/start";
  }

}
