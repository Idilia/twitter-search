package com.idilia.samples.ts.controller;

import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSessionEvent;
import javax.servlet.http.HttpSessionListener;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.idilia.samples.ts.db.SearchDbService;

/**
 * Create an HTTP session listener to persist the last query
 * performed by the user.
 * Normally the queries are persisted when a new one is entered.
 * This avoids having to update the database everytime a new
 * user keyword is added.
 */
@Component
public class SessionListener implements HttpSessionListener {

  @Autowired
  SearchDbService searchSvc;
  
  @Override
  public void sessionCreated(HttpSessionEvent se) {
  }

  @Override
  public void sessionDestroyed(HttpSessionEvent se) {
    /* Save most recent search before it is wiped out */
    HttpSession session = se.getSession();
    Object searchO = session.getAttribute("userSearch");
    if (searchO != null) {
      UserSearch search = (UserSearch) searchO;
      searchSvc.save(search.toDb());
    }
  }

}
