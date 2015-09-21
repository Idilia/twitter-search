package com.idilia.samples.ts.idilia;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.net.HttpURLConnection;
import java.util.concurrent.CompletableFuture;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.idilia.samples.ts.config.Beans;
import com.idilia.services.kb.TaggingMenuResponse;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = Beans.class)
public class TaggingMenuServiceTest {

  @Autowired
  TaggingMenuService menuSvc;
  
  @Test
  public void testExprConversion() {
    
    // Single word
    assertEquals("word", menuSvc.convertBooleanExp("word"));
    
    // Two words
    assertEquals("two words", menuSvc.convertBooleanExp("two words"));
    
    // Escape of punctuation
    assertEquals(
        "<span data-idl-fsk=\"ina\">\"</span>dog food<span data-idl-fsk=\"ina\">\"</span>",
        menuSvc.convertBooleanExp("\"dog food\""));
  }
  
  @Test
  public void testGetMenu() {
    CompletableFuture<TaggingMenuResponse> tmFtr = menuSvc.getTaggingMenu("word", null /* UUID */);
    TaggingMenuResponse tm = tmFtr.join();
    assertEquals(HttpURLConnection.HTTP_OK, tm.getStatus());
    assertFalse(tm.menu.isEmpty());
    assertFalse(tm.text.isEmpty());
  }
}
