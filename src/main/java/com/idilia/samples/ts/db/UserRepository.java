package com.idilia.samples.ts.db;

import java.util.List;
import java.util.UUID;

import org.springframework.data.repository.CrudRepository;

/**
 * Define a repository to be instantiated by Spring to manipulate User
 * persistent objects.
 *
 */
public interface UserRepository extends CrudRepository<User, UUID> {

  List<User> findById(UUID uId);

}
