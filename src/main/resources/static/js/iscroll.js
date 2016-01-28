/**
 * jQuery plugin for infinite scrolling.
 * What is special here is that we can have multiple
 * feeds in the same document including feeds in modals.
 * 
 * The plugin is instantiated around an element that contains
 * the infinite feed. It can be pretty much any element.
 * 
 * The html should look something like this:
 *  <div>some text</div>
 *  <div>some text</div>
 *  <a>link to pull more content</>
 *  
 * When the window in which the content is shown is not filled,
 * the plugin pulls the content using the link. It then replaces
 * the link with the received content.
 * 
 * The plugin keeps filling the window while the received content
 * includes another link.
 */

(function($, window, document) {

  /**
   * Main class
   */

  var InfiniteScoll = {

    /** jQuery element on which we are attached */
    $elem : undefined,

    /** options */
    options : undefined,

    States : {
      RUNNING : 1,
      FETCHING : 2,
      STOPPED : 3
    },
    state : undefined,

    /* Functions */

    /** Initialization */
    init : function(options, el) {
      var base = this;
      base.$elem = $(el);
      base.options = $.extend({}, $.fn.iscroll.options, options);
      base._create();
    },

    /**
     * Public method to cleanup 
     */
    destroy : function() {
      this.pause();
      this.$elem.removeData("iscroll");
    },

    /**
     * Public method the pause the infinite scroll
     */
    pause : function() {
      var base = this;
      base.options.$viewport.off("scroll.iscroll");
      base.state = base.States.STOPPED;
      base._debug("Unlinked scroll event");
    },

    /**
     * Public method to resume the infinite scroll.
     */
    resume : function() {
      if (this.state == this.States.STOPPED) {
        this._bindScroll();
        this.state = this.States.RUNNING;
        this._scroll();
      }
    },

    /*
     * Private methods
     */

    /** Constructor */
    _create : function() {
      var base = this;
      if (base.options.$content.find(this.options.nextSelector).length === 0) {
        base.state = base.States.STOPPED;
        base._debug("No link for initial content");
        return;
      }
      base.state = base.States.RUNNING;
      base._bindScroll();
      base._scroll();
    },

    /**
     * Helper to bind to the scroll event that is signaled as the
     * content scrolls in the view port
     */
    _bindScroll : function() {
      var base = this;
      base.options.$viewport.on('scroll.iscroll', function(event) {
        base._scroll();
      });
    },

    /** Helper function to load more content when required */
    _scroll : function() {
      var base = this;
      if (base.state == base.States.RUNNING && base._atBottom()) {
        base._load();
      }
    },

    /** 
     * Loads more content from the server
     * @return the jQuery promise signaled completion or null if no link
     */
    _load : function() {
      var base = this;
      base.state = base.States.FETCHING;

      /* Get the next link. If we can't find it, we are done. */
      var $link = base.options.$content.find(this.options.nextSelector);
      if ($link.length != 1) {
        base._debug("No link for additional content");
        base.pause();
        return null;
      }

      var href = $link.attr("href");
      base._debug("Loading more content from: " + href);

      $link.parent().append(base.options.loadingHtml);

      return $.get(href, null).always(function() {
        /* Remove the "inserting..." */
        $link.parent().children().last().remove();
      }).done(function(data) {
        base._debug("Got additional content");
        /* Replace the link with the rx content */
        var $data = $(data);
        if (base.options.contentSelector !== undefined) {
          $data = $data.find(base.options.contentSelector);
        }
        $link.replaceWith($data);
        base.options.callback($data);
        if (base.state === base.States.FETCHING) {
          base.state = base.States.RUNNING;
          base._scroll();
        }
      }).fail(function() {
        base._debug("Failed to fetch content. Stopping.");
        base.pause();
        base.state = base.States.STOPPED;
      });
    },

    /** 
     * Return true when the cursor is at the bottom of the viewport
     */
    _atBottom : function() {
      var base = this;
      var vpIsWindow = base.options.$viewport[0] === window;
      var ctTop = base.options.$content.offset().top;
      var ctHgt = base.options.$content.height();
      var vpTop = vpIsWindow ? $(window).scrollTop() : base.options.$viewport.offset().top;
      var vpHgt = base.options.$viewport.height();
      var ctBot = ctTop + ctHgt;
      var vpBot = vpTop + vpHgt;
      var distBot = ctBot - vpBot;
      base._debug("Distance to bottom: " + distBot);
      return distBot - base.options.padding <= 0;
    },

    _debug : function(m) {
      if (this.options.debug === true) {
        console.log(m);
      }
    }
  };
  

  /** 
   * Add to jQuery namespace method to create infinite scolling with options
   */
  $.fn.iscroll = function(options) {
    return this.each(function() {
      var $elem = $(this);
      var inst = $elem.data("iscroll");
      if (inst === undefined) {
        inst = Object.create(InfiniteScoll);
        $elem.data("iscroll", inst);
      }
      inst.init(options, this);
    });
  };
  

  /** 
   * Add to jQuery namespace the default options.
   */
  $.fn.iscroll.options = {
    debug : false,
    loadingHtml : 'Loading...',

    /** 
     * The jQuery element in which data is added. Shown in the viewport.
     *  Must be specified.
     */
    $content : undefined,

    /**
     * The jQuery element that is scrollable. Normally $(window) but it can
     * base a scrollable parent of $content
     */
    $viewport : $(window),

    /** jQuery selector for retrieving in $content the next URL to fetch */
    nextSelector : 'a:last',

    /** Number of pixels near the bottom of the viewport that triggers a fetch */
    padding : 50,

    /** 
     * selector to extract content received when loading. By default all content
     * is appended to $content. To unwrap a top level div, use value '> *'
     */
    contentSelector : undefined,

    /** 
     * Callback when content is loaded. Argument is jQuery object for content
     * received from server and already appended to $content
     */
    callback : function($added) {}
  };

}(jQuery, window, document));