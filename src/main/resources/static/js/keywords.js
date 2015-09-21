/**
 * Module to manage the user keywords. The addition occurs when an expression is
 * selected from a document in the feed. The removal occurs when a keyword is
 * "cancelled" from the keyword list area.
 * <p>
 * Listens to custom events:
 * <ul>
 * <li>  newExpr, senseSelStart: A new search expression is being entered. Clears
 *          everything.
 * <li>  newFeed: On an available feed, fetch the user keywords
 * </ul>
 * Sends custom events:
 * <ul>
 * <li>  newKeyword: A new keyword was selected in the text of a tweet { kw: ,
 *          type: POSITIVE|NEGATIVE }
 * <li>  remKeyword: A keyword was removed from the keyword list { kw: , type:
 *          POSITIVE|NEGATIVE }
 * </ul>
 */

idilia = window.idilia || {};
idilia.ts = window.idilia.ts || {};

idilia.ts.keywords = function() {

  /** Area with the results */
  var $kwsCtr = null;

  /** Word selected from the text of a tweet */
  var keyword = null;

  /** State of mouse button used during selection */
  var mouseDown = false;

  /**
   * Handler when detecting a mouse down event in the feed area
   */
  var mouseDownEH = function(event) {
    mouseDown = true;
  };

  /**
   * Handler when detecting mouse up event anywhere. Open up the pop-up so that
   * the user can indicate if a positive or negative keyword
   */
  var mouseUpEH = function(event) {
    if (!mouseDown) {
      return;
    }
    mouseDown = false;

    /* Read the text currently selected and open a modal to request where to add */
    keyword = readSelected();
    if (keyword) {
      $("#keyword-candidate").html(keyword);
      var $mod = $("#keyword-candidate-popup");
      var dh = $mod.height(), dw = $mod.width();
      var left = Math.max(0, event.clientX - dw / 2);
      var top = event.clientY - dh - 20;
      $mod.css({
        left : left + "px",
        top : top + "px"
      });
      $mod.modal('show');
    }
  };

  /**
   * Helper function to read the selected text.
   * Ensure that all within one tweet...
   * 
   * @return text of the keyword (highlighted text)
   */
  var readSelected = function() {
    var selected = "";
    if (typeof window.getSelection != "undefined") {
      var sel = window.getSelection();
      if (sel.rangeCount) {
        var range = sel.getRangeAt(0);
        var $parent = $(range.commonAncestorContainer.parentNode);
        if ($parent.closest(".tweet").length > 0) {
          var txt = document.createElement('div');
          txt.appendChild(range.cloneContents());
          return $(txt).text().trim();
        }
      }
    }
    return selected;
  };

  /**
   * Event handler the user selects the button in the popop that identifies the
   * type of keyword. We close the popup, tell the server which will respond
   * with an updated list of keywords. Finally we trigger the custom event
   * "newKeyword"
   * 
   * @param event.data
   *          is one of POSITIVE,NEGATIVE
   */
  var kwSelEH = function(event) {
    $("#keyword-candidate-popup").modal('hide');
    var $kwList = $(event.data === 'POSITIVE' ? "#kws-positive" : "#kws-negative");
    $.post("keywords/" + event.data + "/new", {
      k : keyword
    }).done(function(data) {
      /*
       * server responds with an updated list of keywords or nothing when we
       * already had it
       */
      if (data !== undefined) {
        $("#kws-container").html(data);
        $(document).trigger("newKeyword", {
          kw : keyword,
          type : event.data
        });
      }
    });

    event.preventDefault();
  };

  /**
   * Event handler when a keyword is deleted. This comes from clicking on the
   * keyword in the keyword list. Inform the server and then send the custom
   * event.
   */
  var kwRemEH = function(event) {
    var $kwBut = $(this);
    var keyword = $kwBut.children(".kw-text").text();
    var $kwList = $kwBut.closest(".kws-list");
    var kwType = $kwList.attr("id") === "kws-positive" ? 'POSITIVE' : 'NEGATIVE';

    $.post("keywords/" + kwType + "/remove", {
      k : keyword
    }).done(function(data) {
      /*
       * server responds with an updated list of keywords or nothing if it did
       * not exist
       */
      if (data !== undefined) {
        $kwList.replaceWith(data);
        $(document).trigger("remKeyword", {
          kw : keyword,
          type : kwType
        });
      }
    }).fail(function(jqXHR, textStatus) {
      alert("Server failed to remove keyword");
    });

    event.preventDefault();
  };

  /**
   * Event handler when a new feed is initialized
   * Activate our display area and request from the server the user keywords
   * in use.
   */
  var newFeedEH = function(event, data) {
    $kwsCtr.html("");
    $kwsCtr.show();

    /*
     * Request the positive and negative keywords for the search expression used
     * by the feed
     */
    $.get("keywords/all", function(data) {
      $("#kws-container").html(data);
    });
    event.preventDefault();
  };

  /**
   * Initialization for the module.
   */
  var init = function() {
    /* local members */
    $kwsCtr = $("#kws-container");
    
    $kwsCtr.hide();

    /*
     * Install events to detect mouse selection events in the feed. That is used
     * to record selection of new keywords.
     */
    $(document).on("mouseup", mouseUpEH);
    $(document).on("mousedown", "#feed-inner", mouseDownEH);

    /*
     * This is the modal that we show when the text of a keyword is selected and
     * the event handlers for this modal.
     */
    $("#keyword-candidate-popup").modal({
      show : false
    });
    
    /* Handlers for the two buttons in the keyword popup */
    $("#positive-kwd-sel").on("click", null, "POSITIVE", kwSelEH);
    $("#negative-kwd-sel").on("click", null, "NEGATIVE", kwSelEH);

    /* Handler when a keyword is deleted */
    $kwsCtr.on("click", ".kw-remove", kwRemEH);

    /*
     * These signals are generated in search.js and indicate that working on a
     * new query or new senses. Clear our results.
     */
    $(document).on("newExpr senseSelStart", function(event) {
      $kwsCtr.hide();
    });

    /* This is signal indicates that a feed is now available for an expression. */
    $(document).on("newFeed", newFeedEH);
  };

  return {
    init : init
  };

}();
