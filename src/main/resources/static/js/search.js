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
 * <li> form for submitting the senses: #senses-form
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
        $("#senses-form").show();

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
   * Handler when clicking into the query search box Resets to a state for a new
   * search.
   */
  var queryEntryEH = function(event) {
    /* Ensure that we don't have anything showing in the sense results */
    $("#query-senses").hide();

    /* The primary action becomes the search icon */
    $("#search-button").removeClass("btn-default").addClass("btn-primary");

    $(document).trigger("newExpr");
  };

  /**
   * Handler invoked when the text expression is submitted. Tell the server to
   * get sense information and on the callback inform the listeners.
   */
  var newExprEH = function(event) {
    event.preventDefault();
    if ($("#search-query").val().trim() !== "") {
      var $form = $("#search-form");
      var formData = $form.serialize();
      $("html").addClass("busy");
      $.post($form.attr("action"), formData).done(function(data) {
        /*
         * Replace the search area with the new content that includes the sense
         * selection for the words. Activate the sense menu.
         */
        $("#search-container").html(data);
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
    }
  };

  /**
   * Handler invoked when the sense selection is finalized. We submit to the
   * server and inform listeners.
   */
  var newSensesEH = function(event) {
    event.preventDefault();
    /* Read the results from the tagging menu and send them as a JSON object to the server */
    var senses = $("#search-words").children().first().data("taggingMenu").sensesAsObjects();
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
    });
  };

  /**
   * Handler triggered when sense selection starts. Inform listeners. They
   * typically clear their results obtained from a previous sense.
   */
  var senseSelStartEH = function(event) {
    $(document).trigger("senseSelStart");
  };

  /**
   * Module initialization
   */
  var init = function() {

    $("#search-container").
      on("click", "#query", queryEntryEH).
      on("submit", "#search-form", newExprEH).
      on("click", "#search-button", newExprEH).
      on("submit", "#senses-form", newSensesEH).
      on("click", ".idl-menu-word", senseSelStartEH);

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
