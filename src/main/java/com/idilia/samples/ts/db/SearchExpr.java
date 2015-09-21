package com.idilia.samples.ts.db;

import java.util.List;

import com.idilia.tagging.Sense;

/**
 * Helper class used to extract selected fields of the Search object
 * when running JPA queries
 */
public class SearchExpr {
  
  /**
   * @see Search.id
   */
  public Long id;
  
  /**
   * @see Search.expression
   */
  public String expr;
  
  
  public List<Sense> senses;
  
  /**
   * Constructor. Invoked from within JPA when returning results of a query
   */
  public SearchExpr(Long id, String expr) {
    this.id = id;
    this.expr = expr;
  }
  
  public SearchExpr(Long id, String expr, List<Sense> senses) {
    this.id = id;
    this.expr = expr;
    this.senses = senses;
  }
}
