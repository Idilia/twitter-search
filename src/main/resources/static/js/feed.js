/**
 * Module to manage the results area:
 * <ul>
 * <li>  switching feeds between matching and discarded documents
 * <li>  growing the feed on demand (infinite scroll)
 * <li>  user keyword selection from the text in a document
 * </ul>
 * Listens to custom events:
 * <ul>
 * <li>  newExpr: A new search expression is being entered. Clears everything.
 * <li>  newFeed: Creates the feed. The event data is the initial HTML.
 * <li>  senseSelStart: Sense selection is ongoing, clears everything
 * <li>  newKeyword, remKeyword: Finds the keyword in the displayed documents and
 *          update their status
 * </ul>
 * Sends custom events:
 * <ul>
 * <li>  selFeed: The selected feed. Sent whenever we toggle between KEPT/DISCARD. {type: <KEPT|DISCARD}.
 * </ul>
 */

idilia = window.idilia || {};
idilia.ts = window.idilia.ts || {};

idilia.ts.feed = function() {

  /**
   * Object for a feed. We have two instances: kept, rejected
   */
  var Feed = {
      
      /** Area with the actual feed */
      $feed : undefined,
      
      /** Type of feed. One of 'KEPT' or 'DISCARDED' */
      feedType : undefined,
      
      /** scroll offset in window of top displayed tweet */
      yOffset : 0,

      /** true when the feed was initialized with initial content */
      initialized : false,
      
      /** Constructor */
      construct : function(type, $ctr) {
        this.feedType = type;
        this.$feed = $ctr;
      },
      
      /** Record the current position so that we can switch */
      recordPosition : function() {
        this.yOffset = $(window).scrollTop();
      },
      
      /** Clear the current feed */
      clear : function() {
        this.yOffset = 0;
        this.initialized = undefined;
        this.$feed.empty();
      },
      
      
      /**
       * Helper to starting showing a feed. This activates infinite scrolling for
       * it.
       * 
       * @param data
       *          html initial content for the feed, including link for next group
       *          of results
       */
      init : function(data) {
        this.initialized = true;
        this.$feed.html(data);
        this.$feed.iscroll({
          loadingHtml : '<div class="tweet-width" style="text-align:center;padding-top:10px"><img src="images/loading.gif" alt="Loading" /></div>',
          nextSelector : 'a.feed-next',
          contentSelector : '> *',
          $content : this.$feed,
          padding : 20,
          callback : refreshStats,
          debug : false
        });
      },
      
      start : function() {
        this.$feed.data('iscroll').resume();
      },
      
      stop : function() {
        this.$feed.data('iscroll').pause();
      },
      
      /** Start showing the feed */
      show : function() {
        /* Show the feed and scroll the window to last position in feed */
        this.$feed.show();
        $(window).scrollTop(this.yOffset);
        this.start();
      },
      
      /** Hide the feed */
      hide : function() {
        this.stop();
        
        /* Record our current position so that we can come back to it and hide the feed */
        this.recordPosition();
        this.$feed.hide();
      } 
  };
  
  var keptFeed = Object.create(Feed);
  var discFeed = Object.create(Feed);
  
  var actFeed = 'KEPT';
  
  /** Area with the results, feed toggle buttons */
  var $feedCtr = null;

  var getFeed = function(ft) {
    return actFeed == 'DISCARDED' ? discFeed : keptFeed;  
  };
  
  /**
   * Handler when a new search expression is being submitted or when the tagging
   * menu is open Clears all results.
   */
  var newExprEH = function(event, data) {
    $feedCtr.hide();
    $("#feed-mgmt").hide();
    keptFeed.clear();
    discFeed.clear();
  };

  /**
   * Handler when a new feed becomes available. Start polling
   */
  var newFeedEH = function(event, data) {
    keptFeed.init(data);
    keptFeed.show();
    $feedCtr.show();
    $("button[data-feed-type=KEPT]").hide();
    $("button[data-feed-type=DISCARDED]").show();
    $(document).trigger("selFeed", {type: 'KEPT'});
  };


  /**
   * Event handler to switch between discarded and kept document feeds
   */
  var selectFeedEH = function(event) {
    /* get the feed type from the attr */
    var $ctrl = $(this);
    var feedType = $ctrl.attr("data-feed-type");
    
    var othFeed = feedType == "KEPT" ? discFeed : keptFeed;
    othFeed.hide();
    
    var feed = feedType == "KEPT" ? keptFeed : discFeed;
    if (feed.initialized === true) {
      feed.show();
    } else {
      $.get("feed/" + feedType + "/start").done(function(data) {
        feed.init(data);
        feed.show();
      });
    }
    
    this.actFeed = feed;
    $(document).trigger("selFeed", {type: feedType});

    $("#feed-view-ctrls button").toggle();
    event.preventDefault();
  };

  /**
   * Helper to refresh the statistics displayed on the page
   */
  var refreshStats = function() {
    $.get("feed/stats").done(function(data) {
      updateStats(data);
    });
  };

  /**
   * Helper to update the statistics.
   * 
   * @param data: {
   *          kept: <int>, discarded: <int>, snr: <string> }
   */
  var updateStats = function(data) {
    var kept = data['kept'], discarded = data['discarded'];
    $(".nb-kept-results").html(kept);
    $(".nb-discarded-results").html(discarded);
    $(".total-results").html(parseInt(discarded, 10) + parseInt(kept, 10));
    $(".results-snr").html(data['snr']);
    $("#feed-mgmt").show();
    if (kept + discarded > 0) {
      $(".result-status").show();
    }
  };

  /**
   * Return tweet classification based on number of user keywords. This policy
   * must match the same policy implemented in the server in FeedDocument.
   * <ul>
   * <li>Return > 0 if a positive keyword present
   * <li>Return < 0 if a negative keyword present
   * <li>Return 0 otherwise (no keyword)
   * </ul>
   * 
   * @param $tweet
   *          jQuery object for the tweet element (.tweet)
   * @return int < 0 for discarding, > 0 for keeping, 0 when can't tell
   */
  var caclClassification = function($tweet) {
    var posKws = $tweet.data("pos-kws") || '\t';
    var negKws = $tweet.data("neg-kws") || '\t';
    if (posKws !== '\t') {
      return 1;
    } else if (negKws !== '\t') {
      return -1;
    } else if ($tweet.hasClass("eval-keep")) {
      return 1;
    } else if ($tweet.hasClass("eval-discard")) {
      return -1;
    } else {
      return 0;
    }
  };

  /**
   * Set the correct class to display the kept or reject icons based on the
   * number of user keywords. Returns int for ajusting the feed statistics.
   * 
   * @param $tweet
   *          jQuery object for the tweet element (.tweet)
   * @param cls
   *          tweet classification code as computed by
   *          {@link #caclClassification}.
   * @return int: 0 if no change done or changed to unknown, -1 if changed to
   *         discard, +1 if changed to keep
   */
  var setClassificationSign = function($tweet, cls) {
    if (cls === 0 && !$tweet.hasClass('status-unknown')) {
      $tweet.removeClass('status-discard status-keep').addClass('status-unknown');
      return 0;
    } else if (cls > 0 && !$tweet.hasClass('status-keep')) {
      $tweet.removeClass('status-discard status-unknown').addClass('status-keep');
      return 1;
    } else if (cls < 0 && !$tweet.hasClass('status-discard')) {
      $tweet.removeClass('status-keep status-unknown').addClass('status-discard');
      return -1;
    }
    return 0;
  };

  /**
   * Traverse all the tweets and remove the user keyword where it had been
   * found. This looks in the data attribute located with the .tweet element.
   * Recompute the tweet classification.
   * 
   * @param kwType
   *          type of keyword: POSITIVE or NEGATIVE
   * @param kw
   *          text of keyword
   * @return int: number of tweets that changed classification conclusively.
   */
  var remKeyword = function(kwType, kw) {
    var moved = 0;
    var re = new RegExp('\t' + kw + '\t', 'i');
    var attrName = kwType === 'POSITIVE' ? 'pos-kws' : 'neg-kws';
    $("#feed-containers .tweet").each(function(ndx, tweet) {
      var $tweet = $(tweet);
      var kws = $tweet.data(attrName);
      if (kws !== undefined) {
        var repl = kws.replace(re, '\t');
        if (repl != kws) {
          $tweet.data(attrName, repl);
          moved = moved + setClassificationSign($tweet, caclClassification($tweet));
        }
      }
    });
    return moved;
  };

  /**
   * Traverse all the tweets and attempt to match the keyword in their text.
   * When it does, record it in a data attribute and recompute the tweet
   * classification.
   * 
   * @param kwType
   *          type of keyword: POSITIVE or NEGATIVE
   * @param kw
   *          text of keyword
   * @return int: number of tweets that changed classification conclusively.
   */
  var newKeyword = function(kwType, kw) {
    
    var reS = (/^\w/.test(kw) ? "\\b" : "") + kw.replace(/([$'()[{|?+*\.^])/g, "\\$1") + (/\w$/.test(kw) ? "\\b" : ""); 
    var re = new RegExp(reS, 'im');
    var attrName = kwType === 'POSITIVE' ? 'pos-kws' : 'neg-kws';
    var moved = remKeyword(kwType === 'POSITIVE' ? 'NEGATIVE' : 'POSITIVE', kw);
    $("#feed-containers .tweet").each(function(ndx, tweet) {
      var $tweet = $(tweet);
      if (re.test($tweet.find('.text').text())) {
        var kws = $tweet.data(attrName) || '\t';
        kws = kws + kw + '\t';
        $tweet.data(attrName, kws);
        moved = moved + setClassificationSign($tweet, caclClassification($tweet));
      }
    });
    return moved;
  };

  /**
   * Handler invoked when a user keyword is added or removed. We want to go
   * through the existing documents in the feed and change their status if
   * applicable. Then refresh the stats.
   * 
   * @param event:
   *          customer event newKeyword or remKeyword
   * @param data:
   *          an object { kw: <text of kw>, type: (POSITIVE|NEGATIVE) }
   */
  var keywordEH = function(event, data) {

    var moved = event.type == 'newKeyword' ? newKeyword(data.type, data.kw) : remKeyword(data.type,
        data.kw);

    /*
     * We want to update the stats. The server already updated for those not
     * already displayed. Send it the change of the feed so that it can take
     * that into account in its calculations.
     */
    if (moved !== 0) {
      $.post("feed/updStats", {
        feedType : 'KEPT',
        diff : moved
      }).done(function(data) {
        updateStats(data);
      });
    } else {
      /* Get updated stats based on feed data still in the server. */
      refreshStats();
    }
  };

  /**
   * Initialize the module
   */
  var init = function() {
    /* our data members */
    $feedCtr = $("#feed-containers").hide();
    keptFeed.construct("KEPT", $("#feed-containers #feed-container-KEPT .feed").first());
    discFeed.construct("DISCARDED", $("#feed-containers #feed-container-DISCARDED .feed").first());

    /* hide areas not shown until we start displaying a feed */
    $("#feed-mgmt").hide();
    $("button[data-feed-type=KEPT]").hide();

    /* Create a popover to explain the status of each tweet */
    $(document).popover({
      trigger : 'hover',
      delay : 100,
      container : 'body',
      selector : '.status-icon',
      html : true,
      content : function() {
        /*
         * Take the content from static content generated in application.html
         * with the name of the tweet status class + "-popup"
         */
        var $icon = $(this);
        var $tweet = $icon.closest(".tweet");
        var cls = $tweet.attr('class').match(/status-\w+\b/)[0];
        var posKw = $tweet.data('pos-kws') || '\t';
        var negKw = $tweet.data('neg-kws') || '\t';
        if (posKw !== '\t' || negKw !== '\t')  {
          cls = cls + '-kw';
        }
        var $id = $('#' + cls + '-popup');
        if (posKw !== '\t') {
          $id.find('.words').html(posKw.trim().replace('\t', ', '));
        } else if (negKw != '\t') {
          $id.find('.words').html(negKw.trim().replace('\t', ', '));
        }
        return $id.html();
      }
    });

    /* Start listening to events from other modules */
    $(document).
      on("newExpr senseSelStart", newExprEH).
      on("newFeed", newFeedEH).
      on("newKeyword remKeyword", keywordEH);

    /* install handler for selecting a feed */
    $(document).on("click", ".feed-sel", selectFeedEH);
  };

  return {
    init : init
  };

}();
