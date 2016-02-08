package com.idilia.samples.ts.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.regex.Pattern;
import org.junit.Test;

public class KeywordsTest {

  @Test
  public void testRe() {
    Keywords kws = new Keywords();
    
    kws.add("#one");
    kws.add("@two");
    kws.add("three-four");
    
    Pattern re = kws.getRegexPattern();
    assertEquals("(\\Q#one\\E\\b|\\Q@two\\E\\b|\\b\\Qthree-four\\E\\b)", re.toString());
    
    assertTrue(re.matcher("three-four").find());
    assertFalse(re.matcher("three-fours").find());
    assertTrue(re.matcher("#one").find());
    assertFalse(re.matcher("one").find());
  }
}
