package com.idilia.samples.ts.db;

import java.util.Objects;
import java.util.UUID;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Transient;

import org.springframework.data.domain.Persistable;

import com.fasterxml.uuid.Generators;

/**
 * Persistent object for a user that owns searches and also possibly has
 * user-specific word meanings.
 * 
 * Note that although the user owns searches, were not doing that mapping here
 * using JPA because we don't make use of it.
 */
@Entity
public class User implements Persistable<UUID> {

  /**
   * Id for the user. Defined as a UUID because that's what the Idilia API uses
   * for identifying a "customer" that owns customer-specific senses.
   */
  @Id
  @Column(name = "uuid")
  private UUID id;

  /** Helper boolean to implement the Persistable interface */
  @Transient
  boolean isNew_ = false;

  User() {
  }

  /**
   * Create a new user. An unique UUID is generated and the record is flagged as
   * new to cause JPA to insert it (as opposed to update it) on the next save.
   */
  public static User create() {
    User user = new User();
    user.id = Generators.timeBasedGenerator().generate();
    user.isNew_ = true;
    return user;
  }

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  /**
   * Used by spring to determine is persisting or saving. We need to override
   * because the id is allocated up front
   */
  @Override
  public boolean isNew() {
    return isNew_;
  }

  public final void setIsNew(boolean b) {
    this.isNew_ = b;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof User))
      return false;
    User other = (User) o;
    return Objects.equals(id, other.id);
  }

  @Override
  public int hashCode() {
    return id.hashCode();
  }

  @Override
  public String toString() {
    return id != null ? id.toString() : "NULL";
  }

  private static final long serialVersionUID = 1L;
}
