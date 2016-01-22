package com.idilia.samples.ts.idilia;

import static org.junit.Assert.assertArrayEquals;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.idilia.samples.ts.config.Beans;
import com.idilia.samples.ts.twitter.Tweet;
import com.idilia.tagging.Sense;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = Beans.class)
public class DocMatchingServiceTest {

  @Autowired
  MatchingEvalService matchingSvc;
  
  @Test
  public void testMatch() {
    UUID customerId = null;
    List<Tweet> tweets = new ArrayList<>();
    tweets.add(new Tweet(0, "I love to do the laundry with tide."));
    tweets.add(new Tweet(1, "tide"));
    tweets.add(new Tweet(2, "The tide was high when he was at the beach"));
    List<Sense> senses = Collections.singletonList(new Sense(0, 1, "tide", "Tide/N8"));
    List<Integer> docRes = matchingSvc.matchAsync(senses, tweets, customerId).join();
    Integer docResA[] = new Integer[3];
    assertArrayEquals(new Integer[]{1,0,-1}, docRes.toArray(docResA));
  }
  

}
