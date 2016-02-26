package com.idilia.samples.ts.db;

import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.TypedQuery;

/**
 * Implementation for the non-Spring functions in the SearchRepository
 */
public class DbSearchRepositoryImpl implements DbSearchRepositoryCustom {

  @PersistenceContext
  private EntityManager em;

  @Override
  public List<SearchExpr> findRecentExpressions(User user, int cnt) {
    // Create a typed query from the named query defined at with the table
    // definition in Search.java. Unfortunately we can't specify the number
    // of records as a parameter; hence the implementation here.
    TypedQuery<SearchExpr> q = em.createNamedQuery("Search.findUserRecent", SearchExpr.class);
    q.setParameter("user", user);
    q.setMaxResults(cnt);
    return q.getResultList();
  }

}
