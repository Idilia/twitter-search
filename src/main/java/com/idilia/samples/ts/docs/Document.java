package com.idilia.samples.ts.docs;

/**
 * Interface for all the types of documents that can be fetched
 * and classified.
 *
 */
public interface Document {
  
  
  /**
   * Return the raw text of the document.
   */
  String getText();
  
  /**
   * Return the id of the document. This is used for ordering documents
   * when moving them from one feed to another.
   */
  String getId();
}
