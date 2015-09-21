/*
 * Main javascript module.
 * 
 */
idilia = window.idilia || {};
idilia.ts = window.idilia.ts || {};

$(document).ready(function() {
  /* Initialize the other modules */
  idilia.ts.feed.init();
  idilia.ts.search.init();
  idilia.ts.keywords.init();
});

/*
 * Checks if http return code indicates that session has been invalidated. If
 * session is invalidated, we display an error message.
 */
$.ajaxSetup({
  error : function(xhr, status) {
    if (xhr.status == 408) {
      $("#session-expired").show();
      return false;
    }
  }
});