dojo.provide("dojox.cometd.timesync");
dojo.require("dojox.cometd");

/**
 * this file provides the time synchronization extension to cometd.
 * Timesync allows the client and server to exchange time information on every 
 * handshake and connect message so that the client may calculate an approximate
 * offset from it's own clock epoch to that of the server.
 *
 * With each handshake or connect, the extension sends timestamps within the 
 * ext field like: {ext:{timesync:{tc:12345567890},...},...}
 * where ts is the timestamp in ms since 1970 of when the message was sent.
 *
 * A cometd server that supports timesync, should respond with and ext field
 * like: {ext:{timesync:{tc:12345567890,ts:1234567900,p:123},...},...}
 * where ts is the timestamp sent by the client, te is the timestamp on the server
 * of when the message was received and p is the poll duration in ms - ie the
 * time the server took before sending the response.
 *
 * On receipt of the response, the client is able to use current time to determine
 * the total trip time, from which p is subtracted to determine an approximate
 * two way network traversal time. The assumption is made that the network is
 * symmetric for traversal time, so the offset between the two clocks is 
 * ts-tc-(now-tc-p)/2. In practise networks (and the cometd client/server software)
 * is never perfectly symmetric, so accuracy is limited by the difference, which 
 * can be 10s of milliseconds.
 *
 * In order to smooth over any transient fluctuations, the extension keeps a sliding
 * average of the offsets received. By default this is over 10 messages, but this can
 * be changed with the dojox.cometd.timesync._window element.
 */
dojox.cometd.timesync= new function(){

	this._window=10;// The window size for the sliding average of offset samples.
	this.offset=0;	// The offset in ms between the clients clock and the servers clock. Add this to the local
			// time epoch to obtain server time.
	this.samples=0; // The number of samples used to calculate the offset. If 0, the offset is not valid.
	
	this.getServerTime=function(){ // return: long
		// Summary:
		//	Calculate the current time on the server
		// 
		return new Date().getTime()+this.offset;
	}
	
	this.getServerDate=function(){ // return: Date
		// Summary:
		//	Calculate the current time on the server
		// 
		return new Date(this.getServerTime());
	}
	
	this.setTimeout=function(/*function*/call,/*long|Date*/atTimeOrDate){
		// Summary:
		//	Set a timeout function relative to server time
		// call:
		//	the function to call when the timeout occurs
		// atTimeOrTime: 
		//	a long timestamp or a Date representing the server time at
		//	which the timeout should occur.
		
		var ts=(atTimeOrDate instanceof Date)?atTimeOrDate.getTime():(0+atTimeOrDate);
		var tc=ts-this.offset;
		var interval=tc-new Date().getTime();
		if (interval<=0)
			interval=1;
		return setTimeout(call,interval);
	}

	this._in=function(/*Object*/msg){
		// Summary:
		//	Handle incoming messages for the timesync extension.
		// description:
		//	Look for ext:{timesync:{}} field and calculate offset if present.
		// msg: 
		//	The incoming bayeux message
		
		var channel=msg.channel;
		if (channel=="/meta/handshake" || channel=="/meta/connect"){
			if (msg.ext && msg.ext.timesync){
				var sync=msg.ext.timesync;
				var now=new Date().getTime();
				var offset=sync.ts-sync.tc-(now-sync.tc-sync.p)/2;
				
				if (this.samples++==0)
					this.offset=offset;
				else
					this.offset=(this.offset*(this._window-1)+offset)/this._window;
			}
		}
		return msg;
	}

	this._out=function(msg){
		// Summary:
		//	Handle outgoing messages for the timesync extension.
		// description:
		//	Look for handshake and connect messages and add the ext:{timesync:{}} fields
		// msg: 
		//	The outgoing bayeux message
		
		var channel=msg.channel;
		if (channel=="/meta/handshake" || channel=="/meta/connect"){
			var now=new Date().getTime();
			if (!msg.ext)
				msg.ext={};
			msg.ext.timesync={tc: now};
		}
		return msg;
	}
};

dojox.cometd._extendInList.push(dojo.hitch(dojox.cometd.timesync,"_in"));
dojox.cometd._extendOutList.push(dojo.hitch(dojox.cometd.timesync,"_out"));
