package com.idilia.samples.ts.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

/**
 * Controller to manage the fetching and editing of the user keywords
 */
@Controller
@SessionAttributes({ "user", "userSearch" })
public class KeywordsController {

  private final Logger logger = LoggerFactory.getLogger(this.getClass());

  
  /**
   * Return the user keywords for the current search. Both the keep (positive)
   * and discard (negative) keywords.
   * 
   * @param search
   *          Active search
   * @return the name of the template that renders the keywords
   */
  @RequestMapping("keywords/all")
  public String getAllKeywords(@ModelAttribute UserSearch search, Model model) {

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
   * @param search
   *          Active search
   * @param kwType
   *          keyword list to edit
   * @return the name of the template that renders the keywords
   */
  @RequestMapping("keywords/{kwType}/list")
  public String getKeywords(@ModelAttribute UserSearch search,
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
   * @param search
   *          Active search
   * @param kwType
   *          keyword list to edit
   * @param keyword
   *          keyword to remove
   * @param model
   *          Spring model
   * @return redirection to the keywords list operation
   * @throws KeywordUnchangeException
   *           if the keyword is not added because it was already present.
   */
  @RequestMapping(value = "keywords/{kwType}/new", method = RequestMethod.POST)
  public String newUserKeyword(@ModelAttribute UserSearch search,
      @PathVariable("kwType") KeywordType kwType,
      @RequestParam("k") String keyword, Model model) {

    logger.debug(String.format("Add keyword [%s] to keyword list type: %s",
        keyword, kwType.toString()));

    if (!search.addKeyword(kwType, keyword))
      throw new KeywordUnchangeException();

    // Return both lists because both are edited if found in the other list
    return "redirect:/keywords/all";
  }

  
  /**
   * Remove a user keyword
   * <p>
   * 
   * @param search
   *          Active search
   * @param kwType
   *          keyword list to edit
   * @param keyword
   *          keyword to remove
   * @param model
   *          Spring model
   * @return redirection to the keywords list operation
   * @throws KeywordUnchangeException
   *           if the keyword cannot be removed
   */
  @RequestMapping(value = "keywords/{kwType}/remove", method = RequestMethod.POST)
  public String removeUserKeyword(@ModelAttribute UserSearch search,
      @PathVariable("kwType") KeywordType kwType,
      @RequestParam("k") String keyword, Model model)
      throws KeywordUnchangeException {

    logger.debug(String.format("Remove keyword [%s] to keyword list type: %s",
        keyword, kwType.toString()));

    if (!search.removeKeyword(kwType, keyword))
      throw new KeywordUnchangeException();

    // Return updated list of keywords. This goes to /keywords/{kwType}/list
    return "redirect:list";
  }

}
