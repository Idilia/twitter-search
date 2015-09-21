package com.idilia.samples.ts.controller;

/**
 * Type for the classification keywords entered by the user.
 *
 */
public enum KeywordType {

  /** A keyword used to conclusively classify a document as matching the query */
  POSITIVE,
  
  /** A keyword used to conclusively classify a document as not matching the query */
  NEGATIVE,
}
