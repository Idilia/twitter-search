package com.idilia.samples.ts.db;

import java.util.Collections;
import java.util.Date;
import java.util.List;

import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import javax.validation.constraints.NotNull;

import com.idilia.tagging.Sense;

/**
 * Database object for remembering a search and its parameters.
 *
 */
@Entity
@Table(name = "searches", indexes = { @Index(name = "expr_ndx", columnList = "expression") })
@NamedQueries({
    @NamedQuery(name = "Search.findUserRecent", query = "SELECT new com.idilia.samples.ts.db.SearchExpr(s.id, s.expression, s.senses) FROM DbSearch s WHERE s.user = :user AND s.senses IS NOT NULL ORDER BY s.ranAt DESC")
})
public class DbSearch {

  @Id
  @GeneratedValue
  private Long id;

  /** Owner of the search */
  @ManyToOne
  @JoinColumn(name = "user_id")
  private User user;

  /** Text of the search expression */
  @NotNull
  @Column(name = "expression")
  private String expression;

  /**
   * Keywords used to conclusively identify a document as matching the search
   * expression
   */
  @Column(name = "kws_pos")
  @Convert(converter = KeywordsConverter.class)
  private List<String> positiveKeywords;

  /**
   * Keywords used to conclusively identify a document as not matching the
   * search expression
   */
  @Column(name = "kws_neg")
  @Convert(converter = KeywordsConverter.class)
  private List<String> negativeKeywords;

  /** The meanings for the words of the search expression */
  @Column(name = "senses", length = 2048)
  @Convert(converter = SensesConverter.class)
  private List<Sense> senses;

  /** Last usage of the search */
  @Column(name = "ran_at")
  @Temporal(TemporalType.TIMESTAMP)
  private Date ranAt;

  protected DbSearch() {
  } /* for JPA */

  /**
   * Constructor
   * 
   * @param user User owning this search
   * @param expr Search expression (text)
   */
  public DbSearch(User user, String expr) {
    this.user = user;
    this.expression = expr;
  }

  public final Long getId() {
    return id;
  }

  public final void setId(Long id) {
    this.id = id;
  }

  public final User getUser() {
    return user;
  }

  /**
   * Return the text of the search expression.
   */
  public final String getExpression() {
    return expression;
  }

  /**
   * Get the keywords that trigger an immediate keep of the document.
   * 
   * @return non mutable list of keywords or null if none
   */
  public final List<String> getPositiveKeywords() {
    return positiveKeywords;
  }

  public final void setPositiveKeywords(List<String> positiveKeywords) {
    this.positiveKeywords = positiveKeywords;
  }

  /**
   * Get the keywords that trigger an immediate rejection of the document.
   * 
   * @return non mutable list of keywords or null if none
   */
  public final List<String> getNegativeKeywords() {
    return negativeKeywords;
  }

  public final void setNegativeKeywords(List<String> negativeKeywords) {
    this.negativeKeywords = negativeKeywords;
  }

  /**
   * Get the list of senses assigned to the words in the expression.
   * 
   * @return null when information is not available yet.
   */
  public final List<Sense> getSenses() {
    return senses == null ? null : Collections.unmodifiableList(senses);
  }

  /**
   * Set the senses as determined by the tagging menu for words of this search
   * 
   * @param senses The results obtained by invoking "sensesAsObjects" on the
   *        tagging menu.
   */
  public final void setSenses(List<Sense> senses) {
    this.senses = senses;
  }

  final void setRanAt() {
    this.ranAt = new Date();
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof DbSearch))
      return false;
    DbSearch other = (DbSearch) o;
    return expression.equals(other.expression) &&
        true;
  }

  @Override
  public int hashCode() {
    return expression.hashCode();
  }

  @Override
  public String toString() {
    return expression;
  }

}
