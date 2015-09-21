package com.idilia.samples.ts.db;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class SearchDbService {

  @Autowired 
  private SearchRepository searchRepo;

  
  public SearchRepository getRepo() {
    return searchRepo;
  }
  
  public Search save(Search s) {
    s.setRanAt();
    return searchRepo.save(s);
  }
}
