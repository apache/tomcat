dojo.require("dojox.cometd");
dojo.require("dojox.cometd.timestamp");

var room = {
    _last: "",
    _username: null,
    _connected: true,

    join: function(name){
        
        if(name == null || name.length==0 ){
            alert('Please enter a username!');
        }else{
		
            dojox.cometd.init(new String(document.location).replace(/http:\/\/[^\/]*/,'').replace(/\/examples\/.*$/,'')+"/cometd");
            // dojox.cometd.init("http://127.0.0.2:8080/cometd");
            this._connected=true;
	    
            this._username=name;
            dojo.byId('join').className='hidden';
            dojo.byId('joined').className='';
            dojo.byId('phrase').focus();
	    
            // subscribe and join
	    dojox.cometd.startBatch();
            dojox.cometd.subscribe("/chat/demo", room, "_chat");
            dojox.cometd.publish("/chat/demo", { user: room._username, join: true, chat : room._username+" has joined"});
	    dojox.cometd.endBatch();
	    
            // handle cometd failures while in the room
            room._meta=dojo.subscribe("/cometd/meta",dojo.hitch(this,function(event){
                console.debug(event);   
                if (event.action=="handshake"){
	            room._chat({data:{join:true,user:"SERVER",chat:"reinitialized"}});
                    dojox.cometd.subscribe("/chat/demo", room, "_chat");
                } else if (event.action=="connect") {
		    if (event.successful && !this._connected)
                        room._chat({data:{leave:true,user:"SERVER",chat:"reconnected!"}});
		    if (!event.successful && this._connected)
                        room._chat({data:{leave:true,user:"SERVER",chat:"disconnected!"}});
		    this._connected=event.successful;
	        }
            }));
        }
    },

    leave: function(){
        if (room._username==null)
            return;
	    
	if (room._meta)
            dojo.unsubscribe(room._meta);
	room._meta=null;
	
	dojox.cometd.startBatch();
        dojox.cometd.unsubscribe("/chat/demo", room, "_chat");
        dojox.cometd.publish("/chat/demo", { user: room._username, leave: true, chat : room._username+" has left"});
	dojox.cometd.endBatch();

        // switch the input form
        dojo.byId('join').className='';
        dojo.byId('joined').className='hidden';
        dojo.byId('username').focus();
        room._username=null;
        dojox.cometd.disconnect();
    },
      
    chat: function(text){
        if(!text || !text.length){ return false; }
        dojox.cometd.publish("/chat/demo", { user: room._username, chat: text});
    },

    _chat: function(message){
        var chat=dojo.byId('chat');
        if(!message.data){
            alert("bad message format "+message);
            return;
        }
        var from=message.data.user;
        var special=message.data.join || message.data.leave;
        var text=message.data.chat;
        if(!text){ return; }

        if( !special && from == room._last ){
            from="...";
        }else{
            room._last=from;
            from+=":";
        }

        if(special){
            chat.innerHTML += "<span class=\"alert\"><span class=\"from\">"+from+"&nbsp;</span><span class=\"text\">"+text+"</span></span><br/>";
            room._last="";
        }else{
            chat.innerHTML += "<span class=\"from\">"+from+"&nbsp;</span><span class=\"text\">"+text+"</span><br/>";
        } 
        chat.scrollTop = chat.scrollHeight - chat.clientHeight;    
    },
  
  _init: function(){
        dojo.byId('join').className='';
        dojo.byId('joined').className='hidden';
        dojo.byId('username').focus();
	
        var element=dojo.byId('username');
        element.setAttribute("autocomplete","OFF"); 
        dojo.connect(element, "onkeyup", function(e){   
            if(e.keyCode == dojo.keys.ENTER){
                room.join(dojo.byId('username').value);
                return false;
            }
            return true;
	});
  
        dojo.connect(dojo.byId('joinB'), "onclick", function(e){
            room.join(dojo.byId('username').value);
            e.preventDefault();
	});
  
        element=dojo.byId('phrase');
        element.setAttribute("autocomplete","OFF");
        dojo.connect(element, "onkeyup", function(e){   
            if(e.keyCode == dojo.keys.ENTER){
                room.chat(dojo.byId('phrase').value);
                dojo.byId('phrase').value='';
                e.preventDefault();
            }
	});
  
	dojo.connect(dojo.byId('sendB'), "onkeyup", function(e){   
	    room.chat(dojo.byId('phrase').value);
            dojo.byId('phrase').value='';
	});
	dojo.connect(dojo.byId('leaveB'), "onclick", room, "leave");
    } 
};

dojo.addOnLoad(room, "_init");
dojo.addOnUnload(room,"leave");
