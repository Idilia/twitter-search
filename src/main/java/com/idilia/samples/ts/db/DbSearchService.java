package com.idilia.samples.ts.db;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class DbSearchService {

  @Autowired
  private DbSearchRepository searchRepo;

  public DbSearchRepository getRepo() {
    return searchRepo;
  }

  public DbSearch save(DbSearch s) {
    s.setRanAt();
    return searchRepo.save(s);
  }
}
