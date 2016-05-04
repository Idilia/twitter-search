package com.idilia.samples.ts.idilia;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.idilia.samples.ts.docs.Document;
import com.idilia.samples.ts.docs.FilteringParameters;
import com.idilia.services.base.IdiliaClientException;
import com.idilia.services.base.IdiliaCredentials;
import com.idilia.services.text.AsyncClient;
import com.idilia.services.text.MatchingEvalRequest;
import com.idilia.services.text.MatchingEvalResponse;
import com.idilia.tagging.Sense;

/**
 * Client for the matching/eval API provided by Idilia
 *
 */
public class MatchingEvalService {

  /**
   * Create a client that uses the provided credentials.
   */
  public MatchingEvalService(IdiliaCredentials creds) {
    matchClient = new AsyncClient(creds);
  }

  /**
   * Issue an asynchronous request to classify the given documents.
   * 
   * @param senses meanings of the words of the search expression as obtained
   *        from the tagging menu plugin.
   * @param docs documents to classify
   * @param customerId id of the customer. Used to enable customer-specific
   *        senses.
   * @return a MatchingEvalResponse which contains a list of return codes for
   *         each document in the same order as the input list and the matching
   *         status of the given senses
   * @throws IdiliaClientException thrown when encountering an error by the API
   *         call to Idilia's matching eval service
   */
  public CompletableFuture<MatchingEvalResponse> matchAsync(List<Sense> senses,
      FilteringParameters filterParms, final List<? extends Document> docs, UUID customerId) throws IdiliaClientException {

    MatchingEvalRequest req = new MatchingEvalRequest();
    req.setExpression(senses);
    req.setCustomerId(customerId);
    req.setRequireTerm(filterParms.isDiscardOnMissingWords() ? MatchingEvalRequest.RequireTerm.yes : MatchingEvalRequest.RequireTerm.onDisjunctionOnly);
    List<String> texts = new ArrayList<>(docs.size());
    for (Document doc : docs)
      texts.add(doc.getText());
    req.setDocuments(texts);
    return matchClient.matchingEvalAsync(req);
  }

  /** Idilia aysnc text service client */
  private AsyncClient matchClient;
}
