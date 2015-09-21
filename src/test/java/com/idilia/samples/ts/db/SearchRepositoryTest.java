package com.idilia.samples.ts.db;

import static org.junit.Assert.assertEquals;

import java.util.Collections;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.idilia.samples.ts.Application;
import com.idilia.tagging.Sense;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = {Application.class})
public class SearchRepositoryTest {

  @Autowired
  UserRepository userRepo;
  
  @Autowired
  SearchRepository searchRepo;
  
  @Autowired
  SearchDbService searchSvc;
  
  @Test
  public void testCreate() {
    
    User u = User.create();
    u = userRepo.save(u);
    u.setIsNew(false);

    List<Sense> senses = Collections.singletonList(new Sense(0, 1, "text", "text/N1"));
    Search s = new Search(u, "one");
    s.setSenses(senses);
    searchSvc.save(s);
    
    s = new Search(u, "two");
    s.setSenses(senses);
    searchSvc.save(s);
   
    s = new Search(u, "three");
    s.setSenses(senses);
    searchSvc.save(s);
    
    // Search by user and expression
    {
      List<Search> searches = searchRepo.findByUserAndExpression(u, "two");
      assertEquals(1, searches.size());
      assertEquals("two", searches.get(0).getExpression());
    }
    
    // Search by expression for any user
    {
      List<Search> searches = searchRepo.findByExpression("two");
      assertEquals(1, searches.size());
      assertEquals("two", searches.get(0).getExpression());
    }
    
    // Search by ordering. Should return in inverse order as added
    {
      List<SearchExpr> searches = searchRepo.findRecentExpressions(u, 10);
      assertEquals(3, searches.size());
      assertEquals("three", searches.get(0).expr);
      assertEquals (senses, searches.get(0).senses);
    }
    
    // Retrieve the search text for a user
    {
      List<SearchExpr> exprs = searchRepo.findUserExpressionsStartingWith(u, "t");
      assertEquals(2, exprs.size());
      SearchExpr t = (SearchExpr) exprs.get(0);
      assertEquals ("two", t.expr);
    }
    
  }
}
