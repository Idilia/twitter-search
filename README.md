Twitter Search
==============

## Overview
This is a sample application for using the Idilia social media filtering APIs and components. The application uses the meanings of the words when searching for relevant tweets. By using both user keywords and the Idilia text/matching/eval API, the user can obtain a feed of tweets that contains mostly relevant documents.

It demonstrates the following:
* Using Idilia's kb/tagging_menu API on the server side to obtain the tagging menu information required by the jquery_tagging_menu plugin.
* Using Idilia's jquery_tagging_menu javascript plugin on the client side for allowing a user to select the meanings of the words of a search expression;
* Using Idilia's text/matching/eval API on the server side to filter social media messages using the sense information obtained.

In addition to the above, this application also implements the following best practices:
* Each user is assigned a unique UUID that is used with Idilia's API to enable a user to create a new meaning for a word if needed. This meaning is only visible by that user;
* The application tries to limit the number of word sense selections required by a user using several strategies:
  * It remembers past searches and their word senses and recalls those senses when the search is done again. This is implemented using an autocomplete function in the client;
  * If another user has done the same search in the past, the same senses are recalled;
  * The application keeps track of the senses in recent searches by the user and re-uses them when dealing with a new search expression;
* A user may add keywords to improve the tweet classification by selecting words that always keep and always reject a document. These are used prior to requesting document classification using Idilia's API. For providing this capability, the UI enables the user to access both the documents kept and the documents rejected.

The capabilities are demonstrated in the context of a Twitter search application. The tweets are retrieved using the Twitter search API.

## Implementation
The project is implemented as a [Spring](http://spring.io/) application using JPA. However the Idilia API does not rely on Spring and can be used with other frameworks. The database configured is an in-memory database to facilitate testing.

If you are not familiar with Spring, here are some of its capabilities used by this application:
* It maps requests to event handlers, recovering path and query arguments as needed.
* It manages sessions and objects stored in the session. We use two such objects: User and UserSearch.
* When a string is returned from a handler, it is the combination of the name of a view file in resources/templates and the name of a fragment within that file ("file :: fragment"). We've implemented those templates using [Thymeleaf](http://www.thymeleaf.org/).
* The configuration of the servlet is managed by Spring Boot which configures everything using conventions.

The project also uses the Idilia Java SDK. This project is also on GitHub is is pulled automatically by maven.

### Server Implementation

The code is organized as:
Package | Description
------- | -----------
com.idilia.samples.ts | Single class Application used to start the application
com.idilia.samples.ts.config | Configuration files for JPA and the beans. Those are automatically discovered by Spring Boot and loaded.
com.idilia.samples.ts.controller | Implementation for the controllers responding to the client requests
com.idilia.samples.ts.db | JPA implementation of the database
com.idilia.samples.ts.docs | Interface layer to abstract the documents, where we get them from, and running searches on the document source
com.idilia.samples.ts.idilia | Idilia API access
com.idilia.samples.ts.twitter | Implementation for the interfaces of com.idilia.samples.ts.docs that implement tweets and obtaining them using the Twitter search API.

#### Database Objects
The two database objects are:
* User: A user that runs searches. These are automatically created when first encountered. There is no login as we store the user id in a cookie. The absence of the cookie triggers a new user to be created. The user id is a UUID to enable customer-specific senses in Idilia.
* Search: A search belonging to a user. This includes the text of the search, the senses for the words, and the user keywords.

#### Controller
The application always starts with a request to "/" which detects the user, allocates a new one if necessary, and records it in the session. The returned HTML includes the application visual elements including the search box. Most of the visual components are hidden by the javascript.

The next step is for the user to submit a search expression. That is processed to return a tagging menu to the client for the user to select the meaning of each word. The tagging menu is obtained using asynchronous processing supported by the Idilia Java SDK.

The next step is for the user to submit the sense information. This is automatically recovered into a List<Sense> expected by the text/matching/eval API. The response is a redirect to obtain the first results of the search.

Results for the search are generated by class UserSearch. It returns a Future to the controller that is set when documents are available. To obtain documents, the UserSearch uses the DocumentSource and MatchingEvalService. Both of these also implement asynchronous APIs. The UserSearch normally obtains more documents than requested by the browser, classifies them, and records them into the appropriate Feed.

UserSearch pro-actively runs requests to fetch and classify documents when none are left.

Document classification is performed first by user keywords and then using the Idilia API if no keywords were found. The classification policy implemented keeps a document as soon as one positive keyword is found, then rejects it if a negative keyword is found. When no keywords, it relies on the return code from the Idilia classification API.

The documents are retrieved from the Twitter Search API using a single authentication token configured in application.properties. We provide an initial value for this token. It is rate limited by Twitter to a few searches per minute. This implementation is therefore not suitable for a real deployment because rate limit exceptions are encountered when multiple users are running searches or when repeately searching for rare word meanings.

### Client Implementation
The client is implemented as a Javascript applications with a few modules that communicate with each other using custom events. The plugin dependencies are managed by [bower](http://bower.io/) and automatically loaded by maven when building the project.

Modules:
* application.js: Initializes the other modules
* search.js: Manages the search box and sense tagging of the search expression. Uses [typeahead.js](https://twitter.github.io/typeahead.js/) to fetch previous searches from the user.
* feed.js: Manages the results area. Uses [jscroll](http://jscroll.com/) to implement infinite scrolling on the displayed feed, switching between the kept and rejected documents, and showing statistics on the document classification.
* keywords.js: Manages adding user keywords to improve the classification. Keywords are added when the user selects a segment of text in the feed and tags it as an expression that keeps or discards a document. The text is submitted to the server and the appropriate list of keywords is refreshed. It also enables removing a keyword from a list.

## Running
You will need maven 3.1 or better and java 8 installed.
* Obtain project keys from Idilia at www.idilia.com/developer/my-projects
* Clone the project: git clone https://github.com/Idilia/twitter-search.git
* Run: mvn spring-boot:run -DidiliaAccessKey=<access key> -DidiliaPrivateKey=<privateKey>
* Navigate to [http://localhost:8780](http://localhost:8780) to access the application.