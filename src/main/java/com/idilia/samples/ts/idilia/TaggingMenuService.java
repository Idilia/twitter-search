package com.idilia.samples.ts.idilia;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

import com.idilia.services.base.IdiliaClientException;
import com.idilia.services.base.IdiliaCredentials;
import com.idilia.services.kb.TaggingMenuRequest;
import com.idilia.services.kb.TaggingMenuResponse;
import com.idilia.services.text.DisambiguateRequest;
import com.idilia.tagging.Sense;

/**
 * Service for obtaining a tagging menu for a search expression
 *
 */
public class TaggingMenuService {

  /**
   * Client for obtaining the sense information needed to generate the menu.
   */
  final com.idilia.services.text.AsyncClient txtClient;

  /**
   * Client to generate the menu
   */
  final com.idilia.services.kb.AsyncClient kbClient;

  /**
   * Template defining the format of the menu cards.
   */
  final String senseCardTemplate;

  /**
   * This regex matches the terms where we don't want the user to perform sense
   * sense selection. Those terms are:
   * <ul>
   * <li>double quotes used to impose word sequence
   * <li>parenthesis used to form groups
   * <li>keyword OR
   * <li>words rejected by preceeding them with a minus
   * </ul>
   */
  private final Pattern oneRe = Pattern.compile("(\"|\\(|\\)|(?<= )OR(?= )|-\\w+)");

  /**
   * Create the service
   * 
   * @param creds
   *          idilia credentials
   * @param sensecardTmpl
   *          template to use when generating the menu. Aligned with the
   *          jquery_tagging_menu version used by the project.
   */
  public TaggingMenuService(IdiliaCredentials creds, String sensecardTmpl) {
    this.txtClient = new com.idilia.services.text.AsyncClient(creds);
    this.kbClient = new com.idilia.services.kb.AsyncClient(creds);
    this.senseCardTemplate = sensecardTmpl;
  }

  /**
   * Transform the expression with boolean search operators to prevent the
   * tagging menu from attempting to generate senses on those words. Also
   * excludes terms preceded by a minus sign, parenthesis, and double quotes
   * 
   * @param exp
   *          Original expression
   * @return Modified expression where operators are wrapped to ensure that
   *         ignored
   */
  public String convertBooleanExp(String exp) {

    StringBuilder sb = new StringBuilder(exp.length() + 10);
    sb.append(oneRe.matcher(exp).replaceAll("<span data-idl-fsk=\"ina\">$1</span>"));
    return sb.toString();
  }

  /**
   * Return a tagging menu for the text given.
   * 
   * @param text
   *          the text where we want to assign word meanings. The text may be
   *          html formatted to force specific senses or prevent senses to be
   *          assigned on some words.
   * @param customerId
   *          id of the customer. Used to enable customer-specific senses.
   * @return a TaggingMenuResponse that contains html for the text and for the
   *         sense menus.
   * @throws IdiliaClientException
   *           when the menu cannot be obtained successfully for any reason.
   */
  public CompletableFuture<TaggingMenuResponse> getTaggingMenu(String text, UUID customerId)
      throws IdiliaClientException {

    DisambiguateRequest disReq = new DisambiguateRequest();
    disReq.setText(text, "text/query-html", StandardCharsets.UTF_8);
    return getTaggingMenu(disReq, customerId);
  }

  /**
   * Return a tagging menu for the senses given.
   * 
   * @param senses
   *          the senses to use for seeding the tagging menu.
   * @param customerId
   *          id of the customer. Used to enable customer-specific senses.
   * @return a TaggingMenuResponse that contains html for the text and for the
   *         sense menus.
   * @throws IdiliaClientException
   *           when the menu cannot be obtained successfully for any reason.
   */
  public CompletableFuture<TaggingMenuResponse> getTaggingMenu(List<Sense> senses, UUID customerId)
      throws IdiliaClientException {
    
    StringBuilder sb = new StringBuilder(senses.size() * 64);
    for (Sense sense: senses) {
      if (sense.getFsk() != null) {
        /* Constrain the menu to have the sense available */
        sb.append("<span data-idl-fsk=\"").append(sense.getFsk().replace("\"", "&quot;")).append("\">");
        sb.append(sense.getText());
        sb.append("</span>");
      } else
        /* When there is no sense, it means the word is something that we decided to ignore
         * E.g., boolean operators, negative terms, etc. */
        sb.append("<span data-idl-fsk=\"ina\">").append(sense.getText()).append("</span>");
      if (sense.isSpcAft())
        sb.append(' ');
    }

    String text = sb.toString();
    DisambiguateRequest disReq = new DisambiguateRequest();
    disReq.setText(text, "text/query-html", StandardCharsets.UTF_8);
    return getTaggingMenu(disReq, customerId);
  }

  /**
   * Helper function to obtain the tagging menu once the DisambiguateRequest has
   * been constructed.
   * 
   * @param disReq
   *          disambiguate request that contains the text to process
   * @param customerId
   *          id of the customer. Used to enable customer-specific senses.
   * @return a CompletableFuture that is set once the menu is available
   * @throws IdiliaClientException
   *           when the menu cannot be obtained successfully for any reason.
   */
  private CompletableFuture<TaggingMenuResponse> getTaggingMenu(DisambiguateRequest disReq,
      UUID customerId) throws IdiliaClientException {

    /* This enables senses specific to the customer */
    if (customerId != null)
      disReq.setCustomerId(customerId);

    /* That's the format of results expected by the tagging menu API */
    disReq.setResultMime("application/x-tf+xml+gz");

    /*
     * Return a CompletableFuture that returns when both the first stage
     * (getting sense information) and the second stage (converting the sense
     * information into a tagging menu) have been completed.
     */

    return txtClient.disambiguateAsync(disReq).thenCompose(
        /* DisambiguateResponse */ disResp -> {
          /*
           * Lambda function to convert convert disResp of type
           * DisambiguateResponse to a TaggingMenuResponse by invoking the KB
           * tagging menu API.
           */
          TaggingMenuRequest menuReq = new TaggingMenuRequest();
          menuReq.setTf(disResp.getResult()).setTemplate(senseCardTemplate).setAddAnySense()
              .setFilters("noDynamic");

          if (customerId != null)
            menuReq.setAddCreateSense().setFilters("noDynamic noOther").setCustomerId(customerId);

          return kbClient.taggingMenuAsync(menuReq);
        });
  }

}
