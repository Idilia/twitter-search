package com.idilia.samples.ts.db;

import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

/**
 * Define a repository to be instantiated by Spring to manipulate Search
 * persistent objects.
 *
 */
public interface DbSearchRepository extends CrudRepository<DbSearch, Long>, DbSearchRepositoryCustom {

  /**
   * Retrieve the expressions previously ran by the user without recovering any
   * of the rest of its data. Returns a list of SearchExpr with the properties
   * "id" and "expr" extracted from the Search object.
   */
  @Query(value = "SELECT new com.idilia.samples.ts.db.SearchExpr(s.id, s.expression) FROM DbSearch s WHERE s.user = :user AND s.expression LIKE :prefix%")
  List<SearchExpr> findUserExpressionsStartingWith(@Param("user") User user, @Param("prefix") String prefix);

  /**
   * Used to retrieve the senses and keywords for a previous search by this
   * user.
   */
  List<DbSearch> findByUserAndExpression(User user, String expression);

  /**
   * Used to find a search with this expression from any user when we attempt to
   * initialize sense information.
   */
  List<DbSearch> findByExpression(String expression);

}
