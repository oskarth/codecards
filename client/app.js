//////////////////////////////////////////////////////////////////////
// API
// Post requests and get the latest state.
//////////////////////////////////////////////////////////////////////

var API = function(commands) {
  this.apiroot = "https://codecards.replaceme.com/api"; //REPLACE
}

API.prototype.postReq = function(command) {
  //console.log("COMMAND POST", JSON.stringify(command));
  var req = new XMLHttpRequest();

  req.open("POST", this.apiroot + "/cards", true);
  req.onerror = function(e) {
    debug(app.commands.delay);
    delay = Math.round(app.commands.delay/1000);
    // TODO: Remove this
    flashNotification("error", "Unable to sync with server, will retry in " + delay + " seconds", app.commands.delay);
    app.commands.retry(command);
  };
  req.addEventListener("load", function() {
    if (req.status >= 200 && req.status < 400) {
      debug("SERVER RESP:", this.responseText);
      app.commands.acknowledge(command);
    } else if (req.status == 400) {
      debug("Bad command:", this.responseText);
    } else if (req.status == 403) {
      flashNotification("error", "Access denied, have you signed up for an account?", 5000);
    }else {
      debug("Unable to post card", req.status, "\n", req);
    }
  });
  req.setRequestHeader('Content-Type', 'application/json');
  req.send(JSON.stringify(command));
}

API.prototype.fetchState = function(waiting) {
  var apiroot = this.apiroot;
  if (waiting == true) {
    var hideNotification = flashNotification("ok", "Fetching state...");
  }

  setTimeout(function () {
    __this = this;

    var req = new XMLHttpRequest();
    req.withCredentials = true;
    req.onerror = function(e) {
      flashNotification("error", "Unable to sync with server");
      app.commands.backoff();
    };
    req.addEventListener("load", function() {
      if (waiting == true) { hideNotification(); }
      if (req.status >= 200 && req.status < 400) {
        //console.log("FETCH STATE", JSON.parse(this.responseText));

        response = JSON.parse(this.responseText);

        myCards           = response["my-cards"];
        scheduled         = response["scheduled"];
        publicCards       = response["public-cards"];
        publicCollections = response["public-collections"];
        myCollections     = response["my-collections"];

        app.scheduled     = queueFetchedCards(scheduled);
        app.publicCards   = publicCards;
        app.myCards       = myCards;
        app.myCollections = myCollections;
        app.publicCollections = publicCollections;

        updateReviewCount();
        updateMyCardCount();

      } else {
        debug("Unable to fetch latest state", req.status, "\n", req);
        flashNotification("error", "Something went wrong fetching latest state");
      }
    }, 100);
    req.open("GET", apiroot + "/state");
    req.setRequestHeader("Authorization", app.session.token);
    req.send();

    app.api.fetchState();
  }, 5000); // XXX
}


//////////////////////////////////////////////////////////////////////
// Commands
// Deal with commands sent to server.
//
// TODO: Simplify this in terms of the various queues, etc.
//////////////////////////////////////////////////////////////////////

var CommandQueue = function() {
  //debug("init commands");
  this.waiting  = JSON.parse(localStorage.getItem("waiting-queue"))  || [];
  this.progress = JSON.parse(localStorage.getItem("progress-queue")) || [];
  this.dead     = JSON.parse(localStorage.getItem("dead-queue"))     || [];
  this.delay    = 200;
}

CommandQueue.prototype.add = function(command) {
  debug("COMMAND ADD", JSON.stringify(command));
  this.waiting = this.waiting.concat(command);
  this.sync();
}

CommandQueue.prototype.kill = function(command) {
  debug("COMMAND KILL", JSON.stringify(command));
  flashNotification("bad", "Unable to reach server after multiple retries, progress not synced", 10000);
  this.dead = this.dead.concat(command);
  this.sync();
}

CommandQueue.prototype.pushCommands = function() {
  var _this = this;
  setTimeout(function () {
    while (_this.waiting.length != 0) {
      command = _this.waiting[0]; // ???
      // XXX: Bad behavior
      _this.waiting = _this.waiting.slice(1, _this.waiting.length);
      _this.progress = _this.progress.concat(command);
      _this.sync();
      app.api.postReq(command);
    }
    _this.pushCommands();
  }, _this.delay);
}

CommandQueue.prototype.acknowledge = function(command) {
  // Resetting delay as serer connection is working
  this.delay = 100;

  this.progress = this.progress.filter(function(item, idx) {
    return item.id != command.id;
  });
}

CommandQueue.prototype.retry = function(command) {
  debug("Retrying command, backing off");
  this.backoff();

  this.progress = this.progress.filter(function(item, idx) {
    return item.id != command.id;
  });

  // XXX: Race condition with reviews in wrong order
  command["retries"] = (command["retries"] || 0) + 1;
  if (command["retries"] > 5) {
    this.kill(command);
  } else {
    this.add(command);
  }
}

CommandQueue.prototype.backoff = function() {
  debug("New delay is", this.delay);
  this.delay = Math.round(this.delay * 2);
}

CommandQueue.prototype.sync = function() {
//  debug("commands sync");
  localStorage.setItem("waiting-queue", JSON.stringify(this.waiting));
  localStorage.setItem("progress-queue", JSON.stringify(this.progress));
  localStorage.setItem("dead-queue", JSON.stringify(this.dead));
}

//////////////////////////////////////////////////////////////////////
// Notifications
// Show notifications at the top of the screen.
//////////////////////////////////////////////////////////////////////

var Notification = function(type) {
  var hidable = true;
  document.getElementById("notify").style.color = "black";

  if (type == "error") {
    document.getElementById("notify").style.background = "yellow";
  } else if (type == "bad") {
    document.getElementById("notify").style.background = "red";
  } else {
    document.getElementById("notify").style.background = "#19a974";
  }
}

function flashNotification(type, text, time) {
  var hidable = true;

  //debug("Flash notification", text);
  document.getElementById("notify").style.color = "black";
  if (type == "error") {
    document.getElementById("notify").style.background = "yellow";
  } else if (type == "bad") {
    document.getElementById("notify").style.background = "red";
  } else {
    document.getElementById("notify").style.background = "#19a974";
  }

  document.getElementById("notify").textContent = text;
  showElement("notify", "block");

  function hideNotification() {
    document.getElementById("notify").style.background = "white";
    document.getElementById("notify").style.color = "white";
    hidable = false;
  }

  //XX
  setTimeout(function() {
    if (hidable == true) {
      hideNotification();
    }
  }, 2 * (time || 1500));

  return hideNotification;
}

Notification.prototype.show = function(text) {
  debug("Notifications show", text);
  hidable = false;
  document.getElementById("notify").textContent = text;
  showElement("notify", "block");
  setTimeout(function() {
    hidable = true;
  }, 500);
}

Notification.prototype.hide = function() {
  debug("Notifications hidable", hidable);
  self = this;
  if (hidable == true) {
    document.getElementById("notify").textContent = "...";
    document.getElementById("notify").style.background = "white";
    document.getElementById("notify").style.color = "white";
  } else {
    setTimeout(function() {
      self.hide();
    }, 500);
  }
}

//////////////////////////////////////////////////////////////////////
// App
// I don't know how to structure this.
//////////////////////////////////////////////////////////////////////

var App = function() {
  this.commands = new CommandQueue();
  this.api = new API();
  this.currentCard = null; // current active card
  this.currentCollection = null; // current active collection
  this.scheduled  = [];    // current scheduled cards
  this.filtered  = [];    // filtered current scheduled cards
  this.myCards  = [];      // all my cards
  this.publicCards  = [];  // curated public cards
  this.session = null;
  this.currentPage = "";   // XXX: Very WIP right now

  this.stripeHandler = this.setupStripeHandler();
  this.setupListeners();
  this.setupCodeMirror();
  this.initOrLoadSession();
  this.maybeAuth();

  this.api.fetchState();

  this.commands.pushCommands();

  this.actionsTaken = 0;

  this.goHome();

  _this = this;
}

// Setup functions

App.prototype.setupStripeHandler = function() {
  var stripeHandler = StripeCheckout.configure({
    // TODO: Fix this
    key: 'pk_live_QQQREPLACEME',
    image: 'https://stripe.com/img/documentation/checkout/marketplace.png',
    locale: 'auto',
    token: function(token) {
      // XXX: More detail?
      mixpanel.track("Checkout", {'amount (cents)': 1000});
      /// XXX: Last variable is amount in cents
      ga('send', 'event', 'Command', 'checkout', 'amount', 1000);
      var id = genUUID();
      var command = {id: id,
                     type: "checkout",
                     token: app.session.token,
                     data: {email: token.email,
                            id: token.id,
                            livemode: token.livemode,
                            // XXX: Other data not captured
                            // XXX: Change here too
                            amount: 1000}};
      app.commands.add(command);
      flashNotification("good", "Thank you! You can now login", 5000);
    }
  });
  return stripeHandler;
}

App.prototype.setupCodeMirror = function() {
  var questionEditor = CodeMirror.fromTextArea(
    document.getElementById("questionBox"), {
      theme: "default",
      mode: 'shell'
    });

  var answerEditor = CodeMirror.fromTextArea(
    document.getElementById("answerBox"), {
      theme: "default",
      mode: 'shell'
    });
}

App.prototype.maybeAuth = function() {
  var fullHash = window.location.hash;
  var id = genUUID();
  var hash = fullHash.match(/#auth\?e=([A-Za-z0-9=]*)&t=([A-Za-z0-9]*)/);
  if (hash != null && hash.length == 3) {
    mixpanel.track("Auth");
    ga('send', 'event', 'Command', 'auth');
    var command = {id: id,
                   type: "auth",
                   token: this.session.token,
                   data: {"encoded-email": hash[1],
                          "encoded-otp":   hash[2],
                          token:           this.session.token}};
    this.commands.add(command);

    $("goLogin").innerHTML = "Log out";
    $("goSettings").disabled = false;
    newSession = {token: this.session.token,
                  logged_in: true,
                  "encoded-email": hash[1]};
    localStorage.setItem("session", JSON.stringify(newSession));
    this.session = newSession;
  }
}

App.prototype.initOrLoadSession = function() {
  var session = JSON.parse(localStorage.getItem("session"));
  if (session == null) {
    debug("No session set yet");
    newSession = {token: genUUID(), logged_in: false};
    localStorage.setItem("session", JSON.stringify(newSession));
    this.session = newSession;
  } else {
    this.session = session;
    debug("Session already set", this.session);

    if (this.session.logged_in) {
      $("goLogin").innerHTML = "Log out";
      $("goSettings").disabled = false;
    }

  }
}

App.prototype.setupListeners = function() {
  _this = this;

  // More coarse, either 0 or *5.
  // XXX: What about remove? That's delete on card.
  // Actually, we can piggieback on response=0 being delete for now.
  onClick("answerButtonEdit", function()   {
    // TODO: Editable card
    // Current card?
    // QQQ
    app.goEdit();
    console.log("EDIT CARD");
    // app.postReview(0);
  });
  onClick("answerButtonRemove", function()   { app.postReview(0) });
  onClick("answerButtonDontKnow", function() { app.postReview(1) });
  onClick("answerButtonKnow", function()     { app.postReview(4) });

  onClick("goHome", _this.goHome);
  onClick("goAdd", _this.goAdd);
  onClick("goAdd2", _this.goAdd);
  onClick("goReview", _this.goReview);
  onClick("goCollections", _this.goCollections);
  onClick("goMyCards", _this.goMyCards);
  onClick("goSettings", _this.goSettings);
  onClick("goLogin", _this.goLogin);
  onClick("loginButton", _this.login);
  onClick("updateSettingsButton", _this.updateSettings);

  $("signupButton").addEventListener('click', _this.goPayment);
  $("signupButton2").addEventListener('click', _this.goPayment);
  //$("purchaseButton").addEventListener('click', _this.goPurchase);

  // only if we are on review page... this is getting awfully messy.
  $("selectOptionCollection").addEventListener('change', _this.maybeChangeCollection);

  // XXX: For Stripe, ehm
  window.addEventListener('popstate', function() {
    _this.stripeHandler.close();
  });

  onClick("showButton", _this.showAnswer);
  onClick("addCardAndReview", _this.addCardReview);
  onClick("addCardAndAnother", _this.addCardAnother);
  onClick("editCard", _this.editCard);
  //onClick("addCard", _this.addCard);

  onClick("createCollection", _this.createCollection);
  //  onClick("addClojureCards", _this.addClojureCards);
}

// Navigation

App.prototype.goHome = function() {
  //this.currentPage = "home"; // eh...
  ga('send', 'event', 'Navigation', 'Home');
  mixpanel.track("Go home");
  resetLayout();
  showElement("home", "block");
}

App.prototype.goAdd = function() {
  app.currentPage = "add";
  ga('send', 'event', 'Navigation', 'Add');
  mixpanel.track("Go add");
  resetLayout();
  questionEditor = getCodeMirrorDiv("#questionBox");
  answerEditor = getCodeMirrorDiv("#answerBox");

  populateCollectionsDropdown();

  // TODO: These should be placeholders
  questionEditor.CodeMirror.setValue("# Add your question here");
  setTimeout(function() { questionEditor.CodeMirror.refresh(); });

  answerEditor.CodeMirror.setValue("# Add your answer here");
  setTimeout(function() { answerEditor.CodeMirror.refresh(); });

  showElement("addCardButtons", "flex");
  showElement("addCards", "block");
  showElement("selectCollection", "block");
  getCodeMirrorDiv("#questionBox").style.display = "block";
  getCodeMirrorDiv("#answerBox").style.display = "block";
}

App.prototype.goEdit = function() {
  app.currentPage = "edit";
  ga('send', 'event', 'Navigation', 'Edit');
  mixpanel.track("Go edit");
  resetLayout();
  //questionEditor = getCodeMirrorDiv("#questionBox");
  //answerEditor = getCodeMirrorDiv("#answerBox");

  //populateCollectionsDropdown();

  // TODO: These should be placeholders
  //questionEditor.CodeMirror.setValue("# Add your question here");
  //setTimeout(function() { questionEditor.CodeMirror.refresh(); });

  //answerEditor.CodeMirror.setValue("# Add your answer here");
  //setTimeout(function() { answerEditor.CodeMirror.refresh(); });

  // showElement("addCardButtons", "flex");
  // showElement("addCards", "block");

  showElement("editCardButtons", "flex");
  showElement("editCards", "block");

  //showElement("selectCollection", "block");
  getCodeMirrorDiv("#questionBox").style.display = "block";
  getCodeMirrorDiv("#answerBox").style.display = "block";

  // XXX
}

App.prototype.goReviewCollection = function(last_try) {

  app.currentPage = "review-collection";
  ga('send', 'event', 'Navigation', 'Review');
  mixpanel.track("Go review");

  resetLayout();
  showElement("reviewCards", "block");
  showElement("showButton", "block");

  getCodeMirrorDiv("#questionBox").style.display = "block";
  getCodeMirrorDiv("#answerBox").style.display = "none";

  popCard(last_try);
}

App.prototype.goReview = function() {
  app.currentPage = "review";
  ga('send', 'event', 'Navigation', 'Review');
  mixpanel.track("Go review");

  resetLayout();
  showElement("reviewCards", "block");
  showElement("goReviewStart", "block");
  showElement("myCollectionsListReview", "block");

  populateCollectionsListReview();
}

App.prototype.goCollections = function() {
  app.currentPage = "collections";
  resetLayout();
  cards = app.publicCards;
  myCollections = app.myCollections;
  publicCollections = [];

  // Filter out only public publicCollections
  for (var i = 0; i < app.publicCollections.length; i++) {
    collection = app.publicCollections[i];
    console.log(collection.public);
    if (collection.public == true) {
      publicCollections = publicCollections.concat(collection);
    }
  }

  // Populate my collections
  populateCollectionsList(myCollections, "myCollectionsList");

  // Populate public collections
  populateCollectionsList(publicCollections, "publicCollectionsList");

  // This is not a bad idea, but to do it nicely
  // NOTE: Disabling this for now, too cluttered
  // XXX: Hacky way to populate inspiration block
  $("publicCards").innerHTML = "";
  for (var i = 0; i < cards.length; i++) {
    var questionText = document.createTextNode(cards[i].question);
    var questionPre = document.createElement('pre');
    questionPre.className += " publicQuestion";
    questionPre.appendChild(questionText);

    var answerText = document.createTextNode(cards[i].answer);
    var answerPre = document.createElement('pre');
    answerPre.className += " publicAnswer";
    answerPre.appendChild(answerText);

    var publicCardDiv = document.createElement('div');
    publicCardDiv.className += " publicCard";
    publicCardDiv.appendChild(questionPre);
    publicCardDiv.appendChild(answerPre);

    var addPublicCardContainerDiv = document.createElement('div');
    addPublicCardContainerDiv.className += " addPublicCardContainer";

    var publicCardContainerDiv = document.createElement('div');
    publicCardContainerDiv.className += " publicCardContainer";
    publicCardContainerDiv.appendChild(publicCardDiv);
    publicCardContainerDiv.appendChild(addPublicCardContainerDiv);

    $("publicCards").appendChild(publicCardContainerDiv);
  }

  showElement("myCollectionsList", "block");
  showElement("allCollectionsContainer", "block");
  showElement("myCollections", "block");

}

App.prototype.goMyCards = function() {
  app.currentPage = "cards";
  resetLayout();
  cards = app.myCards;

  // Reset state
  $("myCards").innerHTML = "";

  for (var i = 0; i < cards.length; i++) {
    var questionText = document.createTextNode(cards[i].question);
    var questionPre = document.createElement('pre');
    questionPre.className += " myQuestion";
    questionPre.appendChild(questionText);

    var answerText = document.createTextNode(cards[i].answer);
    var answerPre = document.createElement('pre');
    answerPre.className += " myAnswer";
    answerPre.appendChild(answerText);

    var myCardDiv = document.createElement('div');
    myCardDiv.className += " myCard";
    myCardDiv.appendChild(questionPre);
    myCardDiv.appendChild(answerPre);

    var addMyCardContainerDiv = document.createElement('div');
    addMyCardContainerDiv.className += " addMyCardContainer";

    var myCardContainerDiv = document.createElement('div');
    myCardContainerDiv.className += " myCardContainer";
    myCardContainerDiv.appendChild(myCardDiv);
    myCardContainerDiv.appendChild(addMyCardContainerDiv);

    $("myCards").appendChild(myCardContainerDiv);
  }
  showElement("myCardsContainer", "block");

}

App.prototype.goReviewsDone = function() {
  resetLayout();
  showElement("reviewsDone", "block");

  setTimeout(function() {
    console.log("retry");
  _this.goReview(true);
  }, 2000);
}

App.prototype.goReviewsReallyDone = function() {
  resetLayout();
  showElement("reviewsReallyDone", "block");
}

App.prototype.goSettings = function() {
  app.currentPage = "settings";
  resetLayout();
  mixpanel.track("Go settings");
  ga('send', 'event', 'Navigation', 'Settings');
  showElement("settings", "block");
}

App.prototype.goLogin = function() {
  app.currentPage = "login";
  mixpanel.track("Go login");
  ga('send', 'event', 'Navigation', 'Login');
  resetLayout();
  if (app.session.logged_in) {
    console.log("logout");
    newSession = {token: genUUID(), logged_in: false};
    localStorage.setItem("session", JSON.stringify(newSession));
    $("goLogin").innerHTML = "Login";
    $("goSettings").disabled = true;
    app.goHome();

  } else {
    getCodeMirrorDiv("#questionBox").style.display = "none";
    hideElement("showButton");
    hideElement("home");
    showElement("login", "block");
  }
}

App.prototype.goPayment = function(e) {
  mixpanel.track("Go pay", {'amount (cents)': 1000});
  ga('send', 'event', 'Navigation', 'Payment');
  _this.stripeHandler.open({
    name: 'Code Cards',
    description: 'One-time fee',
    currency: 'USD',
    zipCode: true,
    // XXX: If you change this, change this in two other places!
    amount: 1000
  });
  e.preventDefault();

  // resetLayout();
  // showElement("payment", "block");
}

// Other stuff

App.prototype.maybeChangeCollection = function () {
  // update current collection id?
  // why revie :Sjk
  // Change review scope
  if (app.currentPage == "review") {
    var selIndex = $("selectOptionCollection").selectedIndex;
    var option = $("selectOptionCollection").children[selIndex];
    var collId = null;
    if (option != undefined || option == null) {
      collId = option.value;
    }

    app.setFiltered(collId);
  }
}

App.prototype.postReview = function(response) {
  var commandId = genUUID();
  var cardId = this.currentCard.id;
  this.currentCard = null; // reset current card
  mixpanel.track("Review");
  ga('send', 'event', 'Command', 'review', response);
  updateActionsTaken();
  var command = {id: commandId,
                 type: "review",
                 token: this.session.token,
                 data: {id: cardId, response: response}};
  this.commands.add(command);
  this.goReviewCollection();
}

App.prototype.postCard = function(question, answer, id) {
  var commandId = genUUID();
  var cardId = null;

  if (id != undefined && id != null) {
    cardId = id;
  } else {
    cardId = genUUID();
  }

  var selIndex = $("selectOptionCollection").selectedIndex;

  var option = $("selectOptionCollection").children[selIndex];
  var collId = null;
  if (option != undefined) {
    collId = option.value;
  }

  // here
  console.log("UPDATE CURR COLL", this.currentCollection);
  this.currentCollection = collId;

  updateActionsTaken();

  mixpanel.track("Card");
  ga('send', 'event', 'Command', 'card');
  card = {id: cardId,
          question: question,
          answer: answer,
          collection: collId};
  var command = {id: commandId,
                 type: "card",
                 token: this.session.token,
                 data: card};
  this.commands.add(command);
}

App.prototype.showAnswer = function() {
  editor = getCodeMirrorDiv("#answerBox");
  editor.style.display = "block";
  setTimeout(function() { editor.CodeMirror.refresh() });

  document.getElementById("showButton").style.display     = "none";
  document.getElementById("answerButtons").style.display  = "flex";
  document.getElementById("answerButtons2").style.display  = "flex";
}

App.prototype.login = function() {
  var id = genUUID();

  email = document.getElementById("emailInput").value;

  mixpanel.track("Login");
  ga('send', 'event', 'Command', 'register');
  var command = {id: id,
                 type: "register",
                 token: app.session.token,
                 data: {email: email
                       }};

  app.commands.add(command);
  flashNotification("good", "Check your inbox for a login link (might take a minute)", 5000);

  app.goHome();
}

App.prototype.updateSettings = function() {
  var id = genUUID();

  notifications = $("sendEmailsInput").checked;

  mixpanel.track("Updated settings");
  ga('send', 'event', 'Command', 'update-settings');
  var command = {id: id,
                 type: "update-settings",
                 token: app.session.token,
                 data: {"encoded-email": app.session["encoded-email"],
                        "notifications?": notifications}};

  app.commands.add(command);
  flashNotification("good", "Your settings have been updated", 5000);

  app.goHome();
}

App.prototype.setFiltered = function(collId) {
  console.log("setFilt");
  var filtered = [];
  //console.log("getFiltered", collId);

  if (collId == undefined || collId == null) {
    console.log("default collection");
    filtered = app.scheduled;
  } else {
    for (var i = 0; i < app.scheduled.length; i++) {
      console.log("coll", app.scheduled[i].collection);
      if (app.scheduled[i].collection == collId) {
        filtered = filtered.concat(app.scheduled[i]);
      }
    }
  }

  console.log("FILTERED", filtered);
  app.filtered = filtered;
}

App.prototype.collScheduleCount = function(collId) {
  var count = 0;

  if (collId == undefined || collId == null) {
    count = app.scheduled.length; // incl all
  } else {
    for (var i = 0; i < app.scheduled.length; i++) {
      if (app.scheduled[i].collection == collId) {
        count += 1;
      }
    }
  }

  return count;
}

App.prototype.addCardReview = function() {
  _this.addCard();
//  _this.api.fetchCards(true);
  //_this.goReviewCollection(app.currentCollection);
  _this.goReview();
  // goreviewcoll?
  // QQQ
}

App.prototype.editCard = function() {
  _this.addCard(app.currentCard.id);
  _this.goReview();
}

App.prototype.addCollection = function(collId) {
  console.log("addCollection", collId);
  var id = genUUID();

  mixpanel.track("Add Collection");
  mixpanel.track("Add collection");
  ga('send', 'event', 'Command', 'add-collection');
  var command = {id: id,
                 type: "add-collection",
                 token: _this.session.token,
                 data: {id: collId}};
  _this.commands.add(command);

  flashNotification("good", "Adding collection! You can always remove the ones you don't like. Downloading...", 2000);
  setTimeout(function() {
    _this.goReview();
  }, 2000);
}

// XXX: Deprecated
App.prototype.addClojureCards = function() {

  var id = genUUID();

  mixpanel.track("Add Collection");
  mixpanel.track("Add Clojure collection");
  ga('send', 'event', 'Command', 'add-collection');
  var command = {id: id,
                 type: "add-collection",
                 token: _this.session.token,
                 data: {id: 1}};
  _this.commands.add(command);

  flashNotification("good", "10 Clojure cards coming your way! You can always remove the ones you don't like. Downloading...", 2000);
  setTimeout(function() {
    _this.goReview();
  }, 2000);
}

App.prototype.addCardAnother = function() {
  _this.addCard();
  _this.goAdd();
//  setTimeout(function() { _this.api.fetchCards(); }, 100);
}

// With id it should just update right
App.prototype.addCard = function(id) {
  var question = getCodeMirrorDiv("#questionBox").CodeMirror.getValue();
  var answer = getCodeMirrorDiv("#answerBox").CodeMirror.getValue();

  _this.postCard(question, answer, id);
}

App.prototype.createCollection = function() {
  collId = genUUID();
  collName = $("newCollectionName").value;
  _this.postCollection(collId, collName);
}

App.prototype.postCollection = function(collId, collName) {
  var commandId = genUUID();
  updateActionsTaken();

  mixpanel.track("Collection");
  ga('send', 'event', 'Command', 'collection');
  collection = {id: collId, name: collName, public: true};
  var command = {id: commandId,
                 type: "collection",
                 token: this.session.token,
                 data: collection};
  this.commands.add(command);
  flashNotification("good", "Created collection", 2000);
}

//////////////////////////////////////////////////////////////////////
// Actions
// Things that purely have side effects. (?)
//////////////////////////////////////////////////////////////////////

function resetLayout() {
  hideElement("home");
  hideElement("login");
  hideElement("home");
  hideElement("answerButtons");
  hideElement("answerButtons2");
  hideElement("login");
  hideElement("settings");
  hideElement("addCards");
  hideElement("reviewCards");
  hideElement("selectCollection");
  hideElement("addCardButtons");
  hideElement("showButton");
  hideElement("reviewsDone");
  hideElement("reviewsReallyDone");
  hideElement("editCards");
  hideElement("editCardButtons");
  hideElement("allCollectionsContainer");
  hideElement("myCollectionsList");
  hideElement("myCollectionsListReview");
  hideElement("myCardsContainer");
  hideElement("notReadyPayment");
  getCodeMirrorDiv("#questionBox").style.display = "none";
  getCodeMirrorDiv("#answerBox").style.display = "none";

  //app.currentPage = "";
}

function populateCollectionsDropdown() {
  var myCollections = app.myCollections || [];
  $("selectOptionCollection").innerHTML = "";

  // Add empty coll?
  var optionText = document.createTextNode("Default");
  var optionElem = document.createElement("option");
  optionElem.value = null;
  optionElem.appendChild(optionText);
  $("selectOptionCollection").appendChild(optionElem);

  for (var i = 0; i < myCollections.length; i++) {
    var curr = myCollections[i];
    optionText = document.createTextNode(curr.name);
    optionElem = document.createElement("option");
    optionElem.value = curr.id;
    optionElem.appendChild(optionText);
    $("selectOptionCollection").appendChild(optionElem);
  }
}

// XXX: Very similar to collectionElement
function populateCollectionsList(collections, elem) {
  $(elem).innerHTML = "";

  var nameText = document.createTextNode("Default");
  var count = 0;
  if (app.myCards !== undefined) {
    count = app.myCards.length || 0;
  }

  for (var i = 0; i < collections.length; i++) {
    coll = collections[i];
    nameText = document.createTextNode(collections[i].name);
    count = 0;
    if (collections[i].cards !== undefined) {
      count = collections[i].cards.length || 0;
    }

    countText = document.createTextNode(" (" + count +" cards)");
    console.log(nameText, countText);

    collB = document.createElement('b');
    collB.appendChild(nameText);

    buttonText = document.createTextNode("Add");
    collButton = document.createElement('button');

    collPar = document.createElement('p');
    collPar.appendChild(collB);
    collPar.appendChild(countText);

    collButton.className += "collectionButton";
    collButton.appendChild(buttonText);
    collButton.id = "collectionButton-" + coll.id;

    clearfix = document.createElement('span');
    clearfix.className += "clearfix";

    collDiv = document.createElement('div');
    collDiv.className += "collection";
    collDiv.appendChild(collPar);

    if (elem == "publicCollectionsList") {
      collDiv.appendChild(collButton);
    }

    $(elem).appendChild(collDiv);
    $(elem).appendChild(clearfix);

    // XXX: Event listener, way too much going on here
    if (elem == "publicCollectionsList") {
      buttonSelector = "collectionButton-" + coll.id;
      selElem = document.getElementById(buttonSelector);
      selElem.addEventListener('click', function() {
        var id = coll.id;
        return function(e) {
          app.addCollection(id);
        };
      }(), false);
    }

  }
}

function collectionElement(text, id, cards, scheduleCount) {
  var nameText = document.createTextNode(text);
  var count = 0;
  if (cards !== undefined && cards !== null) {
    count = cards.length || 0;
  }
  var collId = "";
  if (id !== undefined && id !== null) {
    collId = id;
  }

  var countText = document.createTextNode(" (" + scheduleCount + " due)");

  var collB = document.createElement('b');
  collB.appendChild(nameText);

  var buttonText = document.createTextNode("Review");

  var collButton = document.createElement('button');
  if (scheduleCount == 0) {
    collButton.disabled = true;
  }
  collButton.className += "collectionButton";
  collButton.appendChild(buttonText);
  collButton.id = "collectionButton-" + collId;

  var collPar = document.createElement('p');
  collPar.appendChild(collB);
  collPar.appendChild(countText);

  var collDiv = document.createElement('div');
  collDiv.className += "collection";

  collDiv.appendChild(collPar);
  collDiv.appendChild(collButton);

  return collDiv;
}

function populateCollectionsListReview() {
  $("myCollectionsListReview").innerHTML = "";

  // Default collection first
  defaultElem = collectionElement("Default",
                                  null,
                                  app.myCards,
                                  app.scheduled.length);
  $("myCollectionsListReview").appendChild(defaultElem);
  // Needs to be mounted to setup event listener I believe
  elem = document.getElementById("collectionButton-");
  elem.addEventListener('click', function() {
    var id = "";
    return function(e) {
      console.log("REVIEW COLL default", id);
      app.setFiltered(null); // default?
      app.goReviewCollection();
    };
  }(), false);

  for (var i = 0; i < myCollections.length; i++) {
    coll = myCollections[i];
    var elem = collectionElement(coll.name,
                                 coll.id,
                                 coll.cards,
                                 app.collScheduleCount(coll.id));
    $("myCollectionsListReview").appendChild(elem);

    buttonSelector = "collectionButton-" + coll.id;
    elem = document.getElementById(buttonSelector);
    elem.addEventListener('click', function() {
      var id = coll.id;
      return function(e) {
        console.log("REVIEW COLL", id);
        app.setFiltered(id);
        app.goReviewCollection();
      };
    }(), false);

  }
}

function popCard(last_try) {
  // XXX: Problem with current etc?
  if (app.filtered.length == 0) {
    app.setFiltered(null); // reset to default collection?
    flashNotification("good", "No cards left to review in this collection!", 2000);
    if (last_try == true) {
      app.goReviewsReallyDone();
    } else {
      app.goReviewsDone();
    }

  } else {
    // Think this is fine?
    curr = app.filtered.shift();

    app.currentCard = curr;
    console.log("CURR CARD ID", curr.id);
    getCodeMirrorDiv("#questionBox").CodeMirror.setValue(curr["question"]);
    getCodeMirrorDiv("#answerBox").CodeMirror.setValue(curr["answer"]);
  }
  // XXX: race condition?
  updateReviewCount();
}

function updateActionsTaken() {
  app.actionsTaken += 1;
  if (app.actionsTaken >= 3 && !app.session.logged_in) {
    showElement("signupBanner", "block");
    mixpanel.track("Show signup banner", {'amount (cents)': 1000});
  }
}

function queueFetchedCards(fetchedCards) {
  var queuedCards = [];

  for (var i = 0; i < fetchedCards.length; i++) {
    if (app.currentCard !== null && fetchedCards[i].id === app.currentCard.id) {
    } else {
      queuedCards = queuedCards.concat(fetchedCards[i]);
    };
  }
  return queuedCards;

}

function updateReviewCount() {
  // XXX: So ugly
  $("goCollections").disabled = false;
  if (app.scheduled.length == 0) {
    $("goReview").disabled = true;
    $("goReview").innerHTML = "Review";

  } else {
    $("goReview").disabled = false;
    $("goReview").innerHTML = "Review " + "(" + app.scheduled.length + ")";
  }

  if (app.currentPage == "review") {
    // XXX: Omg.
    app.goReview();
  }
}

function updateMyCardCount() {
  //XXX
  $("goCollections").disabled = false;
  if (app.myCards.length == 0) {
    $("goMyCards").disabled = true;
    $("goMyCards").innerHTML = "My Cards";

  } else {
    $("goMyCards").disabled = false;
    $("goMyCards").innerHTML = "My Cards " + "(" + app.myCards.length + ")";
  }
}

window.onload = function() {
  app = new App();
}

//////////////////////////////////////////////////////////////////////
// Utils
//
//////////////////////////////////////////////////////////////////////

function genUUID() {
  // https://stackoverflow.com/questions/105034/create-guid-uuid-in-javascript
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function(c) {
    var r = Math.random()*16|0, v = c == 'x' ? r : (r&0x3|0x8);
    return v.toString(16);
  });
}

function todaysDate() {
  return (new Date()).toISOString().slice(0,10);
}

function onClick(id, fn) {
  elem = document.getElementById(id);
  elem.addEventListener('click', function() {
    fn();
  }, false);
}

function setElementValue(id, value) {
  document.getElementById(id).value = value;
}

function hideElement(id) {
  document.getElementById(id).style.display = "none";
}

function showElement(id, display) {
  document.getElementById(id).style.display = display;
}

function $(id) {
  return document.getElementById(id);
}

function getCodeMirrorDiv(selector) {
  return document.querySelector(selector).nextSibling;
}

function getCodeMirrorFromSelector(selector) {
  editor = document.querySelector(selector).nextSibling;
  return editor.CodeMirror;
}

function debug() {
  debugMode = false;
  var debugStr = "DEBUG:";
  if (debugMode == true) {
    for (var i = 0; i < arguments.length; i++) {
      debugStr += " " + arguments[i];
    }
    console.log(debugStr);
  }
}
