dojo._xdResourceLoaded({
depends: [["provide", "dojo.AdapterRegistry"],
["provide", "dojo.io.script"],
["provide", "dojox.cometd._base"],
["provide", "dojox.cometd"]],
defineResource: function(dojo){/*
	Copyright (c) 2004-2007, The Dojo Foundation
	All Rights Reserved.

	Licensed under the Academic Free License version 2.1 or above OR the
	modified BSD license. For more information on Dojo licensing, see:

		http://dojotoolkit.org/book/dojo-book-0-9/introduction/licensing
*/

/*
	This is a compiled version of Dojo, built for deployment and not for
	development. To get an editable version, please visit:

		http://dojotoolkit.org

	for documentation and information on getting the source.
*/

if(!dojo._hasResource["dojo.AdapterRegistry"]){ //_hasResource checks added by build. Do not use _hasResource directly in your code.
dojo._hasResource["dojo.AdapterRegistry"] = true;
dojo.provide("dojo.AdapterRegistry");

dojo.AdapterRegistry = function(/*Boolean?*/ returnWrappers){
	//	summary:
	//		A registry to make contextual calling/searching easier.
	//	description:
	//		Objects of this class keep list of arrays in the form [name, check,
	//		wrap, directReturn] that are used to determine what the contextual
	//		result of a set of checked arguments is. All check/wrap functions
	//		in this registry should be of the same arity.
	//	example:
	//	|	// create a new registry
	//	|	var reg = new dojo.AdapterRegistry();
	//	|	reg.register("handleString",
	//	|		dojo.isString,
	//	|		function(str){
	//	|			// do something with the string here
	//	|		}
	//	|	);
	//	|	reg.register("handleArr",
	//	|		dojo.isArray,
	//	|		function(arr){
	//	|			// do something with the array here
	//	|		}
	//	|	);
	//	|
	//	|	// now we can pass reg.match() *either* an array or a string and
	//	|	// the value we pass will get handled by the right function
	//	|	reg.match("someValue"); // will call the first function
	//	|	reg.match(["someValue"]); // will call the second

	this.pairs = [];
	this.returnWrappers = returnWrappers || false; // Boolean
}

dojo.extend(dojo.AdapterRegistry, {
	register: function(/*String*/ name, /*Function*/ check, /*Function*/ wrap, /*Boolean?*/ directReturn, /*Boolean?*/ override){
		//	summary: 
		//		register a check function to determine if the wrap function or
		//		object gets selected
		//	name:
		//		a way to identify this matcher.
		//	check:
		//		a function that arguments are passed to from the adapter's
		//		match() function.  The check function should return true if the
		//		given arguments are appropriate for the wrap function.
		//	directReturn:
		//		If directReturn is true, the value passed in for wrap will be
		//		returned instead of being called. Alternately, the
		//		AdapterRegistry can be set globally to "return not call" using
		//		the returnWrappers property. Either way, this behavior allows
		//		the registry to act as a "search" function instead of a
		//		function interception library.
		//	override:
		//		If override is given and true, the check function will be given
		//		highest priority. Otherwise, it will be the lowest priority
		//		adapter.
		this.pairs[((override) ? "unshift" : "push")]([name, check, wrap, directReturn]);
	},

	match: function(/* ... */){
		// summary:
		//		Find an adapter for the given arguments. If no suitable adapter
		//		is found, throws an exception. match() accepts any number of
		//		arguments, all of which are passed to all matching functions
		//		from the registered pairs.
		for(var i = 0; i < this.pairs.length; i++){
			var pair = this.pairs[i];
			if(pair[1].apply(this, arguments)){
				if((pair[3])||(this.returnWrappers)){
					return pair[2];
				}else{
					return pair[2].apply(this, arguments);
				}
			}
		}
		throw new Error("No match found");
	},

	unregister: function(name){
		// summary: Remove a named adapter from the registry

		// FIXME: this is kind of a dumb way to handle this. On a large
		// registry this will be slow-ish and we can use the name as a lookup
		// should we choose to trade memory for speed.
		for(var i = 0; i < this.pairs.length; i++){
			var pair = this.pairs[i];
			if(pair[0] == name){
				this.pairs.splice(i, 1);
				return true;
			}
		}
		return false;
	}
});

}

if(!dojo._hasResource["dojo.io.script"]){ //_hasResource checks added by build. Do not use _hasResource directly in your code.
dojo._hasResource["dojo.io.script"] = true;
dojo.provide("dojo.io.script");

/*=====
dojo.io.script.__ioArgs = function(kwArgs){
	//	summary:
	//		All the properties described in the dojo.__ioArgs type, apply to this
	//		type as well, EXCEPT "handleAs". It is not applicable to
	//		dojo.io.script.get() calls, since it is implied by the usage of
	//		"callbackParamName" (response will be a JSONP call returning JSON)
	//		or "checkString" (response is pure JavaScript defined in
	//		the body of the script that was attached). The following additional
	//		properties are allowed for dojo.io.script.get():
	//	callbackParamName: String
	//		The URL parameter name that indicates the JSONP callback string.
	//		For instance, when using Yahoo JSONP calls it is normally, 
	//		callbackParamName: "callback". For AOL JSONP calls it is normally 
	//		callbackParamName: "c".
	//	checkString: String
	//		A string of JavaScript that when evaluated like so: 
	//		"typeof(" + checkString + ") != 'undefined'"
	//		being true means that the script fetched has been loaded. 
	//		Do not use this if doing a JSONP type of call (use callbackParamName instead).
}
=====*/

dojo.io.script = {
	get: function(/*dojo.io.script.__ioArgs*/args){
		//	summary:
		//		sends a get request using a dynamically created script tag.
		var dfd = this._makeScriptDeferred(args);
		var ioArgs = dfd.ioArgs;
		dojo._ioAddQueryToUrl(ioArgs);

		this.attach(ioArgs.id, ioArgs.url);
		dojo._ioWatch(dfd, this._validCheck, this._ioCheck, this._resHandle);
		return dfd;
	},

	attach: function(/*String*/id, /*String*/url){
		//	summary:
		//		creates a new <script> tag pointing to the specified URL and
		//		adds it to the document.
		//	description:
		//		Attaches the script element to the DOM.  Use this method if you
		//		just want to attach a script to the DOM and do not care when or
		//		if it loads.
		var element = dojo.doc.createElement("script");
		element.type = "text/javascript";
		element.src = url;
		element.id = id;
		dojo.doc.getElementsByTagName("head")[0].appendChild(element);
	},

	remove: function(/*String*/id){
		//summary: removes the script element with the given id.
		dojo._destroyElement(dojo.byId(id));
		
		//Remove the jsonp callback on dojo.io.script, if it exists.
		if(this["jsonp_" + id]){
			delete this["jsonp_" + id];
		}
	},

	_makeScriptDeferred: function(/*Object*/args){
		//summary: 
		//		sets up a Deferred object for an IO request.
		var dfd = dojo._ioSetArgs(args, this._deferredCancel, this._deferredOk, this._deferredError);

		var ioArgs = dfd.ioArgs;
		ioArgs.id = "dojoIoScript" + (this._counter++);
		ioArgs.canDelete = false;

		//Special setup for jsonp case
		if(args.callbackParamName){
			//Add the jsonp parameter.
			ioArgs.query = ioArgs.query || "";
			if(ioArgs.query.length > 0){
				ioArgs.query += "&";
			}
			ioArgs.query += args.callbackParamName + "=dojo.io.script.jsonp_" + ioArgs.id + "._jsonpCallback";

			//Setup the Deferred to have the jsonp callback.
			ioArgs.canDelete = true;
			dfd._jsonpCallback = this._jsonpCallback;
			this["jsonp_" + ioArgs.id] = dfd;
		}
		return dfd; // dojo.Deferred
	},
	
	_deferredCancel: function(/*Deferred*/dfd){
		//summary: canceller function for dojo._ioSetArgs call.

		//DO NOT use "this" and expect it to be dojo.io.script.
		dfd.canceled = true;
		if(dfd.ioArgs.canDelete){
			dojo.io.script._deadScripts.push(dfd.ioArgs.id);
		}
	},

	_deferredOk: function(/*Deferred*/dfd){
		//summary: okHandler function for dojo._ioSetArgs call.

		//DO NOT use "this" and expect it to be dojo.io.script.

		//Add script to list of things that can be removed.		
		if(dfd.ioArgs.canDelete){
			dojo.io.script._deadScripts.push(dfd.ioArgs.id);
		}

		if(dfd.ioArgs.json){
			//Make sure to *not* remove the json property from the
			//Deferred, so that the Deferred can still function correctly
			//after the response is received.
			return dfd.ioArgs.json;
		}else{
			//FIXME: cannot return the dfd here, otherwise that stops
			//the callback chain in Deferred. So return the ioArgs instead.
			//This doesn't feel right.
			return dfd.ioArgs;
		}
	},
	
	_deferredError: function(/*Error*/error, /*Deferred*/dfd){
		//summary: errHandler function for dojo._ioSetArgs call.

		if(dfd.ioArgs.canDelete){
			//DO NOT use "this" and expect it to be dojo.io.script.
			if(error.dojoType == "timeout"){
				//For timeouts, remove the script element immediately to
				//avoid a response from it coming back later and causing trouble.
				dojo.io.script.remove(dfd.ioArgs.id);
			}else{
				dojo.io.script._deadScripts.push(dfd.ioArgs.id);
			}
		}
		console.debug("dojo.io.script error", error);
		return error;
	},

	_deadScripts: [],
	_counter: 1,

	_validCheck: function(/*Deferred*/dfd){
		//summary: inflight check function to see if dfd is still valid.

		//Do script cleanup here. We wait for one inflight pass
		//to make sure we don't get any weird things by trying to remove a script
		//tag that is part of the call chain (IE 6 has been known to
		//crash in that case).
		var _self = dojo.io.script;
		var deadScripts = _self._deadScripts;
		if(deadScripts && deadScripts.length > 0){
			for(var i = 0; i < deadScripts.length; i++){
				//Remove the script tag
				_self.remove(deadScripts[i]);
			}
			dojo.io.script._deadScripts = [];
		}

		return true;
	},

	_ioCheck: function(/*Deferred*/dfd){
		//summary: inflight check function to see if IO finished.

		//Check for finished jsonp
		if(dfd.ioArgs.json){
			return true;
		}

		//Check for finished "checkString" case.
		var checkString = dfd.ioArgs.args.checkString;
		if(checkString && eval("typeof(" + checkString + ") != 'undefined'")){
			return true;
		}

		return false;
	},

	_resHandle: function(/*Deferred*/dfd){
		//summary: inflight function to handle a completed response.
		if(dojo.io.script._ioCheck(dfd)){
			dfd.callback(dfd);
		}else{
			//This path should never happen since the only way we can get
			//to _resHandle is if _ioCheck is true.
			dfd.errback(new Error("inconceivable dojo.io.script._resHandle error"));
		}
	},

	_jsonpCallback: function(/*JSON Object*/json){
		//summary: 
		//		generic handler for jsonp callback. A pointer to this function
		//		is used for all jsonp callbacks.  NOTE: the "this" in this
		//		function will be the Deferred object that represents the script
		//		request.
		this.ioArgs.json = json;
	}
}

}

if(!dojo._hasResource["dojox.cometd._base"]){ //_hasResource checks added by build. Do not use _hasResource directly in your code.
dojo._hasResource["dojox.cometd._base"] = true;
dojo.provide("dojox.cometd._base");




/*
 * this file defines Comet protocol client. Actual message transport is
 * deferred to one of several connection type implementations. The default is a
 * long-polling implementation. A single global object named "dojox.cometd" is
 * used to mediate for these connection types in order to provide a stable
 * interface.
 *
 * extensions modules may be loaded (eg "dojox.cometd.timestamp", that use
 * the cometd._extendInList and cometd._extendOutList fields to provide functions
 * that extend and handling incoming and outgoing messages.
 */

dojox.cometd = new function(){
	
	// cometd states:
 	this.DISCONNECTED="DISCONNECTED";	// _initialized==false 	&& _connected==false
 	this.CONNECTING="CONNECTING";		// _initialized==true	&& _connected==false (handshake sent)
 	this.CONNECTED="CONNECTED";		// _initialized==true	&& _connected==true (first successful connect)
 	this.DISCONNECTING="DISCONNECING";	// _initialized==false 	&& _connected==true (disconnect sent)
 	
	this._initialized = false;
	this._connected = false;
	this._polling = false;

	this.connectionTypes = new dojo.AdapterRegistry(true);

	this.version="1.0";
	this.minimumVersion="0.9";
	this.clientId=null;
	this.messageId=0;
	this.batch=0;

	this._isXD = false;
	this.handshakeReturn=null;
	this.currentTransport=null;
	this.url = null;
	this.lastMessage=null;
	this._messageQ=[];
	this.handleAs="json-comment-optional";
	this._advice={};
	this._maxInterval=30000;
	this._backoffInterval=1000;
	this._deferredSubscribes={};
	this._deferredUnsubscribes={};
	this._subscriptions=[];
	this._extendInList=[];	// List of functions invoked before delivering messages
	this._extendOutList=[];	// List of functions invoked before sending messages

	this.state = function() {
		return this._initialized?(this._connected?this.CONNECTED:this.CONNECTING):(this._connected?this.DISCONNECTING:this.DISCONNECTED);
	}

	this.init = function(	/*String*/	root,
				/*Object|null */ props,
				/*Object|null */ bargs){	// return: dojo.Deferred
		//	summary:
		//		Initialize the cometd implementation of the Bayeux protocol
		//	description:
		//		Initialize the cometd implementation of the Bayeux protocol by
		//		sending a handshake message. The cometd state will be changed to CONNECTING
		//		until a handshake response is received and the first successful connect message
		//		has returned.
		//		The protocol state changes may be monitored
		//		by subscribing to the dojo topic "/cometd/meta" where events are
		//		published in the form {cometd:this,action:"handshake",successful:true,state:this.state()}
		//	root:
		//		The URL of the cometd server. If the root is absolute, the host
		//		is examined to determine if xd transport is needed. Otherwise the
		//		same domain is assumed.
		//	props:
		//		An optional object that is used as the basis of the handshake message
		//	bargs:
		//		An optional object of bind args mixed in with the send of the handshake
		//	example:
		//	|	dojox.cometd.init("/cometd");
		//	|	dojox.cometd.init("http://xdHost/cometd",{ext:{user:"fred",pwd:"secret"}});


		// FIXME: if the root isn't from the same host, we should automatically
		// try to select an XD-capable transport
		props = props||{};
		// go ask the short bus server what we can support
		props.version = this.version;
		props.minimumVersion = this.minimumVersion;
		props.channel = "/meta/handshake";
		props.id = ""+this.messageId++;

		this.url = root||djConfig["cometdRoot"];
		if(!this.url){
			console.debug("no cometd root specified in djConfig and no root passed");
			return null;
		}

		// Are we x-domain? borrowed from dojo.uri.Uri in lieu of fixed host and port properties
		var regexp = "^(([^:/?#]+):)?(//([^/?#]*))?([^?#]*)(\\?([^#]*))?(#(.*))?$";
		var parts = (""+window.location).match(new RegExp(regexp));
		if(parts[4]){
			var tmp = parts[4].split(":");
			var thisHost = tmp[0];
			var thisPort = tmp[1]||"80"; // FIXME: match 443

			parts = this.url.match(new RegExp(regexp));
			if(parts[4]){
				tmp = parts[4].split(":");
				var urlHost = tmp[0];
				var urlPort = tmp[1]||"80";
				this._isXD = ((urlHost != thisHost)||(urlPort != thisPort));
			}
		}

		if(!this._isXD){
			if(props.ext){
				if(props.ext["json-comment-filtered"]!==true && props.ext["json-comment-filtered"]!==false){
					props.ext["json-comment-filtered"] = true;
				}
			}else{
				props.ext = { "json-comment-filtered": true };
			}
		}

		props=this._extendOut(props);

		var bindArgs = {
			url: this.url,
			handleAs: this.handleAs,
			content: { "message": dojo.toJson([props]) },
			load: dojo.hitch(this,function(msg){
				this._finishInit(msg);
			}),
			error: dojo.hitch(this,function(e){
				console.debug("handshake error!:",e);
				this._finishInit([{}]);
			})
		};

		if(bargs){
			dojo.mixin(bindArgs, bargs);
		}
		this._props=props;
		for(var tname in this._subscriptions){
			for(var sub in this._subscriptions[tname]){
				if(this._subscriptions[tname][sub].topic){
		 			dojo.unsubscribe(this._subscriptions[tname][sub].topic);
				}
			}
		}
		this._messageQ = [];
		this._subscriptions = [];
		this._initialized=true;
		this.batch=0;
		this.startBatch();
		
		var r;
		// if xdomain, then we assume jsonp for handshake
		if(this._isXD){
			bindArgs.callbackParamName="jsonp";
			r= dojo.io.script.get(bindArgs);
		} else
			r = dojo.xhrPost(bindArgs);
		dojo.publish("/cometd/meta", [{cometd:this,action:"handshake",successful:true,state:this.state()}]);
		return r;
	}
	
	
	this.publish = function(/*String*/channel, /*Object */data, /*Object|null */properties){
		// summary:
		//		publishes the passed message to the cometd server for delivery
		//		on the specified topic
		// channel:
		//		the destination channel for the message
		// data:
		//		a JSON object containing the message "payload"
		// properties:
		//		Optional. Other meta-data to be mixed into the top-level of the
		//		message
		var message = {
			data: data,
			channel: channel
		};
		if(properties){
			dojo.mixin(message, properties);
		}
		this._sendMessage(message);
	}

	
	this.subscribe = function(	/*String */	channel,
					/*Object */	objOrFunc,
					/*String */	funcName){ // return: dojo.Deferred
		// summary:
		//		inform the server of this client's interest in channel
		// channel:
		//		name of the cometd channel to subscribe to
		// objOrFunc:
		//		an object scope for funcName or the name or reference to a
		//		function to be called when messages are delivered to the
		//		channel
		// funcName:
		//		the second half of the objOrFunc/funcName pair for identifying
		//		a callback function to notifiy upon channel message delivery

		if(objOrFunc){
			var tname = "/cometd"+channel;
			var subs=this._subscriptions[tname];
			if(!subs || subs.length==0){
				subs=[];
				this._sendMessage({
					channel: "/meta/subscribe",
					subscription: channel
				});
				
				var _ds = this._deferredSubscribes;
				_ds[channel] = new dojo.Deferred();
				if(_ds[channel]){
					_ds[channel].cancel();
					delete _ds[channel];
				}
			}
			
			for (var i in subs){
				if (subs[i].objOrFunc===objOrFunc&&(!subs[i].funcName&&!funcName||subs[i].funcName==funcName))
					return null;
			}
			
			var topic = dojo.subscribe(tname, objOrFunc, funcName);
			subs.push({ 
				topic: topic, 
				objOrFunc: objOrFunc, 
				funcName: funcName
			});
			this._subscriptions[tname] =subs;
		}
		return this._deferredSubscribes[channel];
	}



	this.unsubscribe = function(	/*string*/	channel,
					/*object|null*/ objOrFunc,
					/*string|null*/ funcName){
		// summary:
		//		inform the server of this client's disinterest in channel
		// channel:
		//		name of the cometd channel to unsubscribe from
		// objOrFunc:
		//		an object scope for funcName or the name or reference to a
		//		function to be called when messages are delivered to the
		//		channel. If null then all subscribers to the channel are unsubscribed.
		// funcName:
		//		the second half of the objOrFunc/funcName pair for identifying
		//		a callback function to notifiy upon channel message delivery
		
		var tname = "/cometd"+channel;
		var subs=this._subscriptions[tname];
		if(!subs || subs.length==0){
			return null;
		}

		var s=0;
		for(var i in subs){
			var sb=subs[i];
			if( (!objOrFunc) ||
				(
					sb.objOrFunc===objOrFunc &&
					(!sb.funcName && !funcName || sb.funcName==funcName)
				)
			){
				dojo.unsubscribe(subs[i].topic);
				delete subs[i];
			}else{
				s++;
			}
		}
		
		if(s==0){
			delete this._subscriptions[tname];
			this._sendMessage({
				channel: "/meta/unsubscribe",
				subscription: channel
			});
			this._deferredUnsubscribes[channel] = new dojo.Deferred();
			if (this._deferredSubscribes[channel]){
				this._deferredSubscribes[channel].cancel();
				delete this._deferredSubscribes[channel];
			}
		}
		return this._deferredUnsubscribes[channel];
	}
	
	
	this.disconnect = function(){
		//	summary:
		//		Disconnect from the server.
		//	description:
		//		Disconnect from the server by sending a disconnect message
		//	example:
		//	|	dojox.cometd.disconnect();

		for(var tname in this._subscriptions){
			for(var sub in this._subscriptions[tname]){
				if(this._subscriptions[tname][sub].topic){
					dojo.unsubscribe(this._subscriptions[tname][sub].topic);
				}
			}
		}
		this._subscriptions = [];
		this._messageQ = [];
		if(this._initialized && this.currentTransport){
			this._initialized=false;
			this.currentTransport.disconnect();
		}
		if(!this._polling) {
			this._connected=false;
			dojo.publish("/cometd/meta", [{cometd:this,action:"connect",successful:false,state:this.state()}]);
		}
		this._initialized=false;
		dojo.publish("/cometd/meta", [{cometd:this,action:"disconnect",successful:true,state:this.state()}]);
	}

	
	// public extension points
	
	this.subscribed = function(	/*String*/channel, /*Object*/message){ }

	this.unsubscribed = function(/*String*/channel, /*Object*/message){ }


	// private methods (TODO name all with leading _)

	this.tunnelInit = function(childLocation, childDomain){
		// placeholder - replaced by _finishInit
	}
	
	this.tunnelCollapse = function(){
		// placeholder - replaced by _finishInit
	}
	
	this._backoff = function(){
		if(!this._advice || !this._advice.interval){
			this._advice={reconnect:"retry",interval:0}; // TODO Is this good advice?
		}
		if(this._advice.interval<this._maxInterval){
			this._advice.interval+=this._backoffInterval;
		}
	}

	this._finishInit = function(data){
		//	summary:
		//		Handle the handshake return from the server and initialize
		//		connection if all is OK
		data = data[0];
		this.handshakeReturn = data;
		
		// remember any advice
		if(data["advice"]){
			this._advice = data.advice;
		}

		var successful=data.successful?data.successful:false;
		
		// check version
		if(data.version < this.minimumVersion){
			console.debug("cometd protocol version mismatch. We wanted", this.minimumVersion, "but got", data.version);
			successful=false;
			this._advice.reconnect="none";
		}
		
		// If all OK
		if(successful){
			// pick a transport
			this.currentTransport = this.connectionTypes.match(
				data.supportedConnectionTypes,
				data.version,
				this._isXD
			);
			// initialize the transport
			this.currentTransport._cometd = this;
			this.currentTransport.version = data.version;
			this.clientId = data.clientId;
			this.tunnelInit = dojo.hitch(this.currentTransport, "tunnelInit");
			this.tunnelCollapse = dojo.hitch(this.currentTransport, "tunnelCollapse");
			this.currentTransport.startup(data);
		}

		dojo.publish("/cometd/meta", [{cometd:this,action:"handshook",successful:successful,state:this.state()}]);

		// If there is a problem
		if(!successful){
			console.debug("cometd init failed");
			this._backoff();
			// follow advice
			if(this._advice && this._advice["reconnect"]=="none"){
				console.debug("cometd reconnect: none");
			}else if(this._advice && this._advice["interval"] && this._advice.interval>0 ){
				setTimeout(
					dojo.hitch(this, function(){ this.init(cometd.url,this._props); }),
					this._advice.interval
				);
			}else{
				this.init(this.url,this._props);
			}
		}
	}

	this._extendIn = function(message){
		// Handle extensions for inbound messages
		var m=message;
		dojo.forEach(dojox.cometd._extendInList, function(f){
			var n=f(m);
			if(n)m=n;
		});
		return m;
	}

	this._extendOut= function(message){
		// Handle extensions for inbound messages
		var m=message;
		dojo.forEach(dojox.cometd._extendOutList, function(f){
			var n=f(m);
			if(n)m=n;
		});
		return m;
	}


	this.deliver = function(messages){
		dojo.forEach(messages, this._deliver, this);
		return messages;
	}

	this._deliver = function(message){
		// dipatch events along the specified path
		
		message=this._extendIn(message);

		if(!message["channel"]){
			if(message["success"] !== true){
				console.debug("cometd error: no channel for message!", message);
				return;
			}
		}
		this.lastMessage = message;

		if(message.advice){
			this._advice = message.advice; // TODO maybe merge?
		}

		// check to see if we got a /meta channel message that we care about
		var deferred=null;
		if(	(message["channel"]) &&
			(message.channel.length > 5)&&
			(message.channel.substr(0, 5) == "/meta")){
			// check for various meta topic actions that we need to respond to
			switch(message.channel){
				case "/meta/connect":
					if(message.successful && !this._connected){
						this._connected = this._initialized;
						this.endBatch();
					} else if(!this._initialized){
						this._connected = false; // finish disconnect
					}
					dojo.publish("/cometd/meta",[{cometd:this,action:"connect",successful:message.successful,state:this.state()}]);
					break;
				case "/meta/subscribe":
					deferred = this._deferredSubscribes[message.subscription];
					if(!message.successful){
						if(deferred){
							deferred.errback(new Error(message.error));
						}
						return;
					}
					dojox.cometd.subscribed(message.subscription, message);
					if(deferred){
						deferred.callback(true);
					}
					break;
				case "/meta/unsubscribe":
					deferred = this._deferredUnsubscribes[message.subscription];
					if(!message.successful){
						if(deferred){
							deferred.errback(new Error(message.error));
						}
						return;
					}
					this.unsubscribed(message.subscription, message);
					if(deferred){
						deferred.callback(true);
					}
					break;
			}
		}
		
		// send the message down for processing by the transport
		this.currentTransport.deliver(message);

		if(message.data){
			// dispatch the message to any locally subscribed listeners
			try {
				var tname = "/cometd"+message.channel;
				dojo.publish(tname, [ message ]);
			}catch(e){
				console.debug(e);
			}
		}
	}

	this._sendMessage = function(/* object */ message){
		if(this.currentTransport && this._connected && this.batch==0){
			return this.currentTransport.sendMessages([message]);
		}
		else{
			this._messageQ.push(message);
			return null;
		}
	}

	this.startBatch = function(){
		this.batch++;
	}

	this.endBatch = function(){
		if(--this.batch <= 0 && this.currentTransport && this._connected){
			this.batch=0;

			var messages=this._messageQ;
			this._messageQ=[];
			if(messages.length>0){
				this.currentTransport.sendMessages(messages);
			}
		}
	}
	
	this._onUnload = function(){
		// make this the last of the onUnload method
		dojo.addOnUnload(dojox.cometd,"disconnect");
	}
}

/*
transport objects MUST expose the following methods:
	- check
	- startup
	- sendMessages
	- deliver
	- disconnect
optional, standard but transport dependent methods are:
	- tunnelCollapse
	- tunnelInit

Transports SHOULD be namespaced under the cometd object and transports MUST
register themselves with cometd.connectionTypes

here's a stub transport defintion:

cometd.blahTransport = new function(){
	this._connectionType="my-polling";
	this._cometd=null;
	this.lastTimestamp = null;

	this.check = function(types, version, xdomain){
		// summary:
		//		determines whether or not this transport is suitable given a
		//		list of transport types that the server supports
		return dojo.lang.inArray(types, "blah");
	}

	this.startup = function(){
		if(dojox.cometd._polling){ return; }
		// FIXME: fill in startup routine here
		dojox.cometd._polling = true;
	}

	this.sendMessages = function(message){
		// FIXME: fill in message array sending logic
	}

	this.deliver = function(message){
	}

	this.disconnect = function(){
	}
}
cometd.connectionTypes.register("blah", cometd.blahTransport.check, cometd.blahTransport);
*/

dojox.cometd.longPollTransport = new function(){
	this._connectionType="long-polling";
	this._cometd=null;

	this.check = function(types, version, xdomain){
		return ((!xdomain)&&(dojo.indexOf(types, "long-polling") >= 0));
	}

	this.tunnelInit = function(){
		var message = {
			channel:	"/meta/connect",
			clientId:	this._cometd.clientId,
			connectionType: this._connectionType,
			id:	""+this._cometd.messageId++
		};
		message=this._cometd._extendOut(message);
		this.openTunnelWith({message: dojo.toJson([message])});
	}

	this.tunnelCollapse = function(){
		// TODO handle transport specific advice
		
		if(!this._cometd._initialized){ return; }
			
		if(this._cometd._advice){
			if(this._cometd._advice["reconnect"]=="none"){
				return;
			}
			if(	(this._cometd._advice["interval"])&&
				(this._cometd._advice.interval>0) ){
				setTimeout(dojo.hitch(this,function(){ this._connect(); }),this._cometd._advice.interval);
			}else{
				this._connect();
			}
		}else{
			this._connect();
		}
	}

	this._connect = function(){
		if(!this._cometd._initialized){ return; }
		if(this._cometd._polling) {
			console.debug("wait for poll to complete or fail");
			return;
		}
			
		if(	(this._cometd._advice) &&
			(this._cometd._advice["reconnect"]=="handshake")
		){
			this._cometd._connected=false;
			this._initialized = false;
			this._cometd.init(this._cometd.url,this._cometd._props);
 		}else if(this._cometd._connected){
			var message={
				channel:	"/meta/connect",
				connectionType: this._connectionType,
				clientId:	this._cometd.clientId,
				id:	""+this._cometd.messageId++
			};
			message=this._cometd._extendOut(message);
			this.openTunnelWith({message: dojo.toJson([message])});
		}
	}

	this.deliver = function(message){
		// Nothing to do
	}

	this.openTunnelWith = function(content, url){
		this._cometd._polling = true;
		var d = dojo.xhrPost({
			url: (url||this._cometd.url),
			content: content,
			handleAs: this._cometd.handleAs,
			load: dojo.hitch(this, function(data){
				this._cometd._polling = false;
				this._cometd.deliver(data);
				this.tunnelCollapse();
			}),
			error: dojo.hitch(this, function(err){
				this._cometd._polling = false;
				console.debug("tunnel opening failed:", err);
				dojo.publish("/cometd/meta", [{cometd:this._cometd,action:"connect",successful:false,state:this._cometd.state()}]);
				this._cometd._backoff();
				this.tunnelCollapse();
			})
		});
	}

	this.sendMessages = function(messages){
		for(var i=0; i<messages.length; i++){
			messages[i].clientId = this._cometd.clientId;
			messages[i].id = ""+this._cometd.messageId++;
			messages[i]=this._cometd._extendOut(messages[i]);
		}
		return dojo.xhrPost({
			url: this._cometd.url||djConfig["cometdRoot"],
			handleAs: this._cometd.handleAs,
			load: dojo.hitch(this._cometd, "deliver"),
			content: {
				message: dojo.toJson(messages)
			}
		});
	}

	this.startup = function(handshakeData){
		if(this._cometd._connected){ return; }
		this.tunnelInit();
	}

	this.disconnect = function(){
		var message={
			channel:	"/meta/disconnect",
			clientId:	this._cometd.clientId,
			id:	""+this._cometd.messageId++
		};
		message=this._cometd._extendOut(message);
		dojo.xhrPost({
			url: this._cometd.url||djConfig["cometdRoot"],
			handleAs: this._cometd.handleAs,
			content: {
				message: dojo.toJson([message])
			}
		});
	}
}

dojox.cometd.callbackPollTransport = new function(){
	this._connectionType = "callback-polling";
	this._cometd = null;

	this.check = function(types, version, xdomain){
		// we handle x-domain!
		return (dojo.indexOf(types, "callback-polling") >= 0);
	}

	this.tunnelInit = function(){
		var message = {
			channel:	"/meta/connect",
			clientId:	this._cometd.clientId,
			connectionType: this._connectionType,
			id:	""+this._cometd.messageId++
		};
		message = this._cometd._extendOut(message);		
		this.openTunnelWith({
			message: dojo.toJson([message])
		});
	}

	this.tunnelCollapse = dojox.cometd.longPollTransport.tunnelCollapse;
	this._connect = dojox.cometd.longPollTransport._connect;
	this.deliver = dojox.cometd.longPollTransport.deliver;

	this.openTunnelWith = function(content, url){
		this._cometd._polling = true;
		dojo.io.script.get({
			load: dojo.hitch(this, function(data){
				this._cometd._polling = false;
				this._cometd.deliver(data);
				this.tunnelCollapse();
			}),
			error: dojo.hitch(this, function(err){
				this._cometd._polling = false;
				console.debug("tunnel opening failed:", err);
				dojo.publish("/cometd/meta", [{cometd:this._cometd,action:"connect",successful:false,state:this._cometd.state()}]);
				this._cometd._backoff();
				this.tunnelCollapse();
			}),
			url: (url||this._cometd.url),
			content: content,
			callbackParamName: "jsonp"
		});
	}

	this.sendMessages = function(/*array*/ messages){
		for(var i=0; i<messages.length; i++){
			messages[i].clientId = this._cometd.clientId;
			messages[i].id = ""+this._cometd.messageId++;
			messages[i]=this._cometd._extendOut(messages[i]);
		}
		var bindArgs = {
			url: this._cometd.url||djConfig["cometdRoot"],
			load: dojo.hitch(this._cometd, "deliver"),
			callbackParamName: "jsonp",
			content: { message: dojo.toJson( messages ) }
		};
		return dojo.io.script.get(bindArgs);
	}

	this.startup = function(handshakeData){
		if(this._cometd._connected){ return; }
		this.tunnelInit();
	}

	this.disconnect = dojox.cometd.longPollTransport.disconnect;
	
	this.disconnect = function(){
		var message={
			channel:"/meta/disconnect",
			clientId:this._cometd.clientId,
			id:""+this._cometd.messageId++
		};
		message=this._cometd._extendOut(message);		
		dojo.io.script.get({
			url: this._cometd.url||djConfig["cometdRoot"],
			callbackParamName: "jsonp",
			content: {
				message: dojo.toJson([message])
			}
		});
	}
}
dojox.cometd.connectionTypes.register("long-polling", dojox.cometd.longPollTransport.check, dojox.cometd.longPollTransport);
dojox.cometd.connectionTypes.register("callback-polling", dojox.cometd.callbackPollTransport.check, dojox.cometd.callbackPollTransport);

dojo.addOnUnload(dojox.cometd,"_onUnload");

}

if(!dojo._hasResource["dojox.cometd"]){ //_hasResource checks added by build. Do not use _hasResource directly in your code.
dojo._hasResource["dojox.cometd"] = true;
// stub loader for the cometd module since no implementation code is allowed to live in top-level files
dojo.provide("dojox.cometd");


}


}});
