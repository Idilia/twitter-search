package com.idilia.samples.ts.db;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.idilia.samples.ts.Application;
@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = {Application.class})
public class UserRepositoryTest {

  @Autowired
  UserRepository repo;
  
  @Test
  public void testPersistence() {
    
    User u = User.create();
    u = repo.save(u);
    
    User u2 = repo.findOne(u.getId());
    assertNotNull(u2);
    assertFalse(u2.isNew());
    
    User u3 = repo.save(u2);
    assertEquals(u2, u3);
    
    List<User> users = repo.findById(u.getId());
    assertEquals(1, users.size());
    assertEquals(u, users.get(0));
  }
}
