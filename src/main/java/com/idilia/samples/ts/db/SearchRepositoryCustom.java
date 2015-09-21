package com.idilia.samples.ts.db;

import java.util.List;

/**
 * Interface for the queries added to the repository that Spring cannot
 * instantiate itself.
 */
public interface SearchRepositoryCustom {

  /**
   * Return the most recent searches performed by a user
   * @param user user filter
   * @param cnt  number of results to return
   * @return list of the recent searches in decreasing age.
   */
  List<SearchExpr> findRecentExpressions(User user, int cnt);
  
}
