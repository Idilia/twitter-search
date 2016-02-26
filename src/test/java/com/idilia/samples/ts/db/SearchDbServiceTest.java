package com.idilia.samples.ts.db;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.Collections;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.idilia.samples.ts.Application;
import com.idilia.tagging.Sense;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = {Application.class})
public class SearchDbServiceTest {

  @Autowired
  UserRepository userRepo;
  
  @Autowired
  DbSearchService searchSvc;
  
  @Test
  public void testCreate() {
    User u = User.create();
    u = userRepo.save(u);
    u.setIsNew(false);
    
    DbSearch s = new DbSearch(u, "apple");
    s.setSenses(Collections.singletonList(new Sense(0, 1, "apple", "Apple/N8")));
    DbSearch s2 = searchSvc.save(s);
    assertNotNull(s2.getSenses());
    
    DbSearch s3 = searchSvc.getRepo().findOne(s2.getId());
    assertEquals(s2, s3);
    assertNotNull(s3.getSenses());
  }
  
  @Test
  public void testUpdate() {
    
    User u = User.create();
    u = userRepo.save(u);
    u.setIsNew(false);
    
    DbSearch s = new DbSearch(u, "apple");
    s = searchSvc.save(s);
    assertNotNull(s.getId());
    
    DbSearch s2 = searchSvc.getRepo().findOne(s.getId());
    assertEquals(s.getExpression(), s2.getExpression());
    
    s.setSenses(Collections.singletonList(new Sense(0, 1, "apple", "Apple/N8")));
    s = searchSvc.save(s);
    assertNotNull(s.getSenses());
    
    s2 = searchSvc.getRepo().findOne(s.getId());
    assertEquals(s, s2);
    assertArrayEquals(s.getSenses().toArray(), s2.getSenses().toArray());
  }
  
}
