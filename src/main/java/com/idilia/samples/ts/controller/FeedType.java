package com.idilia.samples.ts.controller;

public enum FeedType {

  /**
   * Feed with the documents that were kept because either matching the search
   * query or inconclusive.
   */
  KEPT,

  /** Feed with documents that could conclusively be discarded */
  DISCARDED,
}
