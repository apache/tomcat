dojo.provide("dojox.cometd.session");
dojo.require("dojox.cometd");

dojox.cometd.session= new function(){
        this._session=null;

        this._in=function(msg){
                var channel=msg.channel;
                if (channel=="/service/ext/session"){
                        this._session=msg.data;
                }
                return msg;
        }

        this._out=function(msg){
                var channel=msg.channel;
                if (channel=="/meta/handshake" && this._session!=null){
                        if (!msg.ext)
                                msg.ext={};
                        msg.ext.session=this._session;
                }
                return msg;
        }
};

dojox.cometd._extendInList.push(dojo.hitch(dojox.cometd.session,"_in"));
dojox.cometd._extendOutList.push(dojo.hitch(dojox.cometd.session,"_out"));