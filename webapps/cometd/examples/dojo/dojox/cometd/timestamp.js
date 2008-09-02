if(!dojo._hasResource["dojox.cometd.timestamp"]){ //_hasResource checks added by build. Do not use _hasResource directly in your code.
dojo._hasResource["dojox.cometd.timestamp"] = true;
dojo.provide("dojox.cometd.timestamp");
dojo.require("dojox.cometd");

// A cometd extension that adds a timestamp to every message
dojox.cometd._extendOutList.push(function(msg){msg.timestamp=new Date().toUTCString();return msg});

}
