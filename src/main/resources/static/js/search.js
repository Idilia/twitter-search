/**
 * Module to manages the search area which consists of the textual search
 * expression and its senses.
 * 
 * Generates custome events:
 * <ul>
 * <li>
 * <li> newExpr: A new search expression is entered.
 * <li> senseSelStart: Sense selection is starting.
 * <li> newFeed: The senses info was submitted and feed data should become
 * available. The event data is the HTML rx to initiate the feed
 * </ul>
 * 
 * Managed elements:
 * <ul>
 * <li> form for entering the search expr: #search-query
 * <li> form for submitting the senses (initiate search): #senses-form
 * <li> area for presenting the senses: #search-words
 * </ul>
 * 
 * All these elements are stored in a #search-container which content is
 * replaced when starting a new search.
 */

idilia = window.idilia || {};
idilia.ts = window.idilia.ts || {};

idilia.ts.search = function() {

  /** The Bloodhound engine from typeahead that we are using for autocomplete */
  var exprFetch = undefined;

  /**
   * Helper to instantiate the sense tagging menu for the search expression
   */
  var renderTaggingMenu = function() {
    var tm = $("#search-words").children().first().taggingMenu({
      menus : $("#search-menus"),
      useToolTip : false,
      closeOnSelect : true,
      hideUntaggable : true,
      wordsContent : "tile",
      senseMenuOptions : {
        view : "grid"
      },
      sensesel : function() {

        /* Set the focus on the search button */
        $("#senses-form button").focus();

        return true;
      }
    }).data("taggingMenu");
    $("#query-senses").show();
    $("#senses-form").show();
  };

  /**
   * Helper to initialize the autocomplete function for the search query field
   * This enables re-using previous searches and their senses.
   */
  var initAutocompleteUI = function() {
    $('#search-query').typeahead({
      classNames : {
        menu : 'dropdown-menu' /* bootstrap class */
      }
    }, {
      name : 'expr',
      display : 'expr',
      source : exprFetch
    });
  };

  /**
   * Clear the sense area and tell listeners to remove feeds
   */
  var clearForNewRequest = function() {
    /* Ensure that we don't have anything showing in the sense results */
    $("#query-senses").hide();

    /* Inform listeners */
    $(document).trigger("newExpr");
  };
  
  /**
   * Handler when clicking into the query search box Resets to a state for a new
   * search.
   */
  var queryEntryEH = function(event) {
    clearForNewRequest();

    /* The primary action becomes the search icon */
    $("#search-button").removeClass("btn-default").addClass("btn-primary");
  };
  
  /**
   * Handler when opening or closing the advanced search panel.
   */
  var advancedSearchEH = function(event) {
      /* Toggle stuff, including the form */
      $("#search-button").toggleClass("btn-primary");
      $("#adv-search-fields").toggle(200);
      
      if ($(this).parent().toggleClass("dropup").hasClass("dropup")) {
        /* Opening action */
        
        /* Do the same actions as if clicking in the main query field input */
        queryEntryEH(event);
        
        /* If word fields are changed, this invalidates the main query field input */
        $("#adv-search-words input").change( function () {
          $("#search-query").val('');
        });
        
        /* Submitting the form using the adv search button */
        $("#adv-search-submit").click(function (event) {
          /* Move the words content to the search query box. */
          var expr = convertAdvancedSearchWordsToExpr();
          if (expr) {
            $("#search-query").val(expr);
          }
        });
      } else {
        /* Closing action by rolling up the button */
        var expr = convertAdvancedSearchWordsToExpr();
        if (expr) {
          $("#search-query").val(expr);
        }
      }
      
      event.preventDefault();
  };
  
  
  /** 
   * Helper to return the string value equivalent to the multiple words fields in the
   * advanced search form.
   * @return null if nothing in fields, or a twitter syntax expression
   */
  var convertAdvancedSearchWordsToExpr = function() {
    var q = $("#allWords").val() || '';
    
    var p = $("#phrase").val() || '';
    if (p !== '') {
      q = q + ' "' + p + '"'
    };
    
    var any = $("#anyWords").val() || '';
    if (any !== '') {
      var anys = any.split(' ');
      var addedOne = false;
      for (var i = 0; i < anys.length; ++i) {
        var a = anys[i].trim();
        if (a.length > 0) {
          if (addedOne) {
            q = q + " OR " + a;
          } else {
            q = q + ' ' + a;
            addedOne = true;
          }
        }
      }
    }
    
    var none = $("#noneWords").val() || '';
    if (none !== '') {
      var nones = none.split(' ');
      for (var i = 0; i < nones.length; ++i) {
        var n = nones[i].trim();
        if (n.length > 0) {
          q = q + " -" + n;
        }
      }
    }
    
    var tag = $("#hashTags").val() || '';
    if (tag !== '') {
      var tags = tag.split(' ');
      for (var i = 0; i < tags.length; ++i) {
        var n = tags[i].trim();
        if (n.length > 0) {
          if (n.charAt(0) == '#') {
            q = q + " " + n;
          } else {
            q = q + " #" + n;
          }
        }
      }
    }
    
    return q.length === 0 ? null : q;
  };

  
  /**
   * Handler invoked when the text expression is submitted. Tell the server to
   * get sense information and on the callback inform the listeners.
   */
  var newExprEH = function(event) {
    event.preventDefault();

    clearForNewRequest();
    
    var $form = $("#search-form");
    var formData = $form.serialize();
    $("html").addClass("busy");
    $.post($form.attr("action"), formData).done(function(data) {
      /*
       * Replace the search area with the new content that includes the sense
       * selection for the words. Activate the sense menu.
       */
      $("#search-container").html(data);
      $(".idl-tile-any .idl-def p").html("Search for any meaning");
      
      renderTaggingMenu();

      /*
       * Now that we have a submitted an expression the primary action is now
       * to proceed
       */
      $("#search-button").removeClass("btn-primary").addClass("btn-default");

      /* Set the focus on the search button */
      $("#senses-form button").focus();

      /* We just reloaded the search box so re-init autocomplete */
      initAutocompleteUI();
    }).fail(function(jqXHR, textStatus) {
      alert("Server failed to accept new search");
    }).always(function() {
      $("html").removeClass("busy");
    });
  };

  /**
   * Handler invoked when the sense selection is finalized. We submit to the
   * server and inform listeners.
   */
  var newSensesEH = function(event) {
    event.preventDefault();
    /* Read the results from the tagging menu and send them as a JSON object to the server */
    var senses = $("#search-words").children().first().data("taggingMenu").sensesAsObjects();
    $("html").addClass("busy");
    $.ajax({
      data : JSON.stringify(senses),
      method : "POST",
      contentType : 'application/json; charset=utf-8',
      url : $("#senses-form").attr("action")
    }).done(function(data) {
      /* Hide the element submitting to prevent re-submit */
      $("#senses-form").hide();

      /*
       * Inform observers that we now have a new feed started. Data is the HTML
       * to initiate the feed display.
       */
      $(document).trigger("newFeed", data);
    }).fail(function(jqXHR, textStatus) {
      alert("Server failed to process expression senses");
    }).always(function() {
      $("html").removeClass("busy");
    });
  };

  /**
   * Handler triggered when sense selection starts. Inform listeners. They
   * typically clear their results obtained from a previous sense.
   */
  var senseSelStartEH = function(event) {
    $(document).trigger("senseSelStart");
    $("#senses-form").show();
  };

  /**
   * Module initialization
   */
  var init = function() {

    $("#search-container").
      on("click", "#search-query", queryEntryEH).
      on("submit", "#search-form", newExprEH).
      on("click", "#search-button", newExprEH).
      on("submit", "#senses-form", newSensesEH).
      on("click", ".idl-menu-word", senseSelStartEH).
      on("click", "#adv-search-button", advancedSearchEH);

    /*
     * Initialize the autocomplete remote engine This uses the "/history" method
     * to obtain past search expressions starting with one or more letters. The
     * response to the request is an array of JSON objects: { id: <some unique
     * number>, expr: <text> }
     */
    exprFetch = new Bloodhound({
      datumTokenizer : Bloodhound.tokenizers.obj.whitespace('expr'),
      queryTokenizer : Bloodhound.tokenizers.whitespace,
      identify : function(datum) {
        return datum['id'];
      },
      remote : {
        url : 'history?q=%QUERY',
        wildcard : '%QUERY'
      }
    });

    initAutocompleteUI();
  };

  return {
    init : init
  };

}();
