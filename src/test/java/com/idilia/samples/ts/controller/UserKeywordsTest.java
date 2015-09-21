package com.idilia.samples.ts.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.regex.Pattern;

import org.junit.Test;

public class UserKeywordsTest {

  @Test
  public void testUserKws() {
    
    Keywords kws = new Keywords();
    kws.add("seed");
    assertEquals("seed", kws.tailSet("").iterator().next());
    
    Pattern re = kws.getRegexPattern();
    assertNotNull(re);
    assertTrue(re.matcher("seed").find());
    assertFalse(re.matcher("seeds").find());
    
    kws.remove("seed");
    assertTrue(kws.isEmpty());
    assertNull(kws.getRegexPattern());
  }
}
