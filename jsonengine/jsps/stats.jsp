<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="com.google.appengine.api.users.User" %>
<%@ page import="com.google.appengine.api.users.UserService" %>
<%@ page import="com.google.appengine.api.users.UserServiceFactory" %>
<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
<%
    UserService userService = UserServiceFactory.getUserService();
    User user = userService.getCurrentUser();
    if (user == null) {
		response.sendRedirect(userService.createLoginURL(request.getRequestURI()));
	}
%>
<script type="text/javascript">
var g_user = "<%= user.getEmail() %>";
</script>
<!-- 
	************************************************************
	********* below here is the same as jsp version. ***********
	************************************************************
-->

<link href="http://twitter.github.com/bootstrap/assets/css/bootstrap.css" rel="stylesheet">
<style>
body {
  padding-top: 60px; /* 60px to make the container go all the way to the bottom of the topbar */
}

textarea {
  width: 100%;
}

#subtitleHolder {
  padding-top: 10px;
}

div.status {
  display: none;
  position: fixed;
  right: 40px;
  top: 50px;
}


</style>
<title>つぶやくようにCoursera和訳 web Ver. Stats</title>

<script src="http://ajax.googleapis.com/ajax/libs/jquery/1.5/jquery.min.js" type="text/javascript"></script>
<script type="text/javascript">

function isLocal() {
   return typeof(g_test_srt) != "undefined";
}

function ajaxPost(url, param, onSuccess, postType){
   if(isLocal()) {
	   return dummyPost(url, param, onSuccess, postType);
   } else {
	   return $.post(url, param, onSuccess, postType);
   }
}

function ajaxGet(url, onSuccess){
	if(isLocal()) {
		dummyGet(url, onSuccess);
	} else {
		$.get(url, onSuccess);
	}
}

function ajaxGeneral(param) {
	if(isLocal()) {
		dummyAjax(param);
	} else {
		$.ajax(param);
	}
}

var TEXT_PER_AREA =20;

function createAreaMap(json){ 
   return {
       getSrtId: function() {return json["srtId"]; },
	   getDocId: function() {return json["_docId"]; },
	   getArea: function(idx){ return json["a"+idx] },
	   isMine: function(idx) {
	       var area = this.getArea(idx);
			if(area[1] == g_user)
				return true;
			return false;
		},
	   isEmpty: function(idx){
	       var area = this.getArea(idx);
		   if(area[0] == "e")
				return true;
			if(area[1] == g_user)
				return true;
			var now = (new Date()).getTime();
			if(now - area[2] > 60*60*1000)
				return true;
			return false;
		},
		unbook: function(idx) {
			var area =this.getArea(idx);
			area[0] = "e";
			area[1] = "";
			area[2] = (new Date()).getTime();
		},
		book: function(idx){
			var area =this.getArea(idx);
			area[0] = "a";
			area[1] =g_user;
			area[2] = (new Date()).getTime();
		},
		setDone: function(idx){
			var area =this.getArea(idx);
			area[0] = "d";
			area[1] = "";
			area[2] = (new Date()).getTime();
		},
		getTextNum: function() { return json["textNum"]; },
		getAreaNum: function() {
			return 1+ Math.floor((this.getTextNum()-1) / TEXT_PER_AREA);
		},
		lastAreaTextNum: function() {
			return this.getTextNum() % TEXT_PER_AREA;
		},
		areaRegion: function(areaIndex) {
			var firstAreaIndex = 1+(areaIndex-1)*TEXT_PER_AREA;
			var lastAreaIndex = this.getAreaNum();
			if(areaIndex == lastAreaIndex) {
				return {begin: firstAreaIndex, end: this.lastAreaTextNum() };
			}
			return {begin: firstAreaIndex, end: firstAreaIndex-1+TEXT_PER_AREA};
		},
   };
}

function createAreaMapList(jsons){
   return {
      getMap: function(srtId){
		  for(var i =0; i < jsons.length; i++){
		      if(jsons[i].srtId ==srtId)
			      return createAreaMap(jsons[i]);
		  }
		  return undefined;
	  }, 
	  getLength: function() {return jsons.length; }
	  };
}

var g_areaMapList;
var g_areaMap;

function calcComps(texts, region) {
	var comp = 0;
	for(var idx =region.begin; idx <=region.end; idx++) {
		var text = texts[idx-1];
		if(text.target != "")
			comp++;
	}
	return comp;
}

function calcStats(texts, areaMap) {
	var textNum =areaMap.getTextNum();
	var now = (new Date()).getTime();
	var stats = {d: now, a:[]};
	var lastArea = areaMap.getAreaNum();
	var comp;
	for(var i = 1; i <= lastArea; i++) {
		comp = calcComps(texts, areaMap.areaRegion(i));
		stats.a[i] =comp;
	}
	return stats;
}

function onTextsComming(texts){
	texts.sort(function(a, b) { if(a.textId > b.textId) return 1; if(a.textId < b.textId) return -1; return 0; });
	
	var stats = calcStats(texts, g_areaMap);
	var obj = {s: stats, _docId: g_areaMap.getDocId()};
	var jsonparam = { _doc: JSON.stringify(obj) };	

	ajaxPost("/_je/areaMap", jsonparam, function (result){
		// console.log(jsonparam);
	}, "json");
	
	
	var count = 0;
	for(var i =0; i < texts.length; i++) {
		if(texts[i].target != "")
			count++;
	}
	var statResult = count + "/" + texts.length  + " " + Math.floor((100*count)/texts.length) + "%";
	$('#resultDiv').html(statResult );

	var obj = {_docId: g_areaMap.getSrtId(), stats: statResult};
	var jsonparam = {_doc: JSON.stringify(obj)};
	ajaxPost("/_je/srt", jsonparam, function(result) { console.log(jsonparam);}, "json");
}

function onCalcStatsStart() {
	notifyStatus("calc...", STATUS_STICK);
	var srtId = $('#srtList option:selected')[0].value;
	ajaxGet("/_je/areaMap", function (areaMaps) {
		g_areaMapList =createAreaMapList(areaMaps);
		g_areaMap = g_areaMapList.getMap(srtId);
	
		ajaxGet("/_je/text?cond=srtId.eq." + srtId, function (result) {
			onTextsComming(result);
			notifyStatus("done", STATUS_TIMED);
		});
	});
}

function getSrts(){
 debugLog("get srts...");
 // var BASE = "http://subtitleliketweets.appspot.com";
 ajaxGet("/_je/srt", function (result) {
    notifyStatus("", STATUS_HIDE);
	var sel = $('#srtList');
	for(var i = 0; i <result.length; i++){
		sel.append($('<option>').attr({value: result[i]._docId}).text(result[i].srtTitle));
	}
	btnStartEnable(true);
 });
}


$(function(){
	notifyStatus("setup...", STATUS_STICK);
	getSrts();
 });

function debugLog(msg){
	if (console) {
		console.log(msg);        
	}
}

var STATUS_STICK =1;
var STATUS_TIMED =2;
var STATUS_HIDE =3;
var g_status = STATUS_HIDE;

function notifyDone() {
	if(g_status == STATUS_TIMED){
		$("div.status").fadeOut(1000);
		g_status =STATUS_HIDE
	}
}

function notifyWaitLong() {
	setTimeout(notifyDone, 1000);
}

function notifyStatus(msg, typ){
	$("div.status").html(msg);
	if(typ == STATUS_STICK) {
		g_status = STATUS_STICK;
		$("div.status").html(msg).fadeIn(1000);
		return;
	}
	if(typ == STATUS_TIMED) {
		g_status = STATUS_TIMED;
		$("div.status").html(msg).fadeIn(1000, notifyWaitLong);
		return;
	}
	if(typ == STATUS_HIDE){
		g_status = STATUS_HIDE;
		$("div.status").fadeOut(1000);
	}
}

function btnStartEnable(isEnable) {
	if(isEnable) {
			$("#btnSrtChoose").removeAttr("disabled");
	} else {
			$("#btnSrtChoose").attr("disabled", true);
	}
}

</script>
</head>

<body>
<div class="navbar navbar-inverse navbar-fixed-top">
  <div class="navbar-inner">
    <div class="container">
      <a class="btn btn-navbar" data-toggle="collapse" data-target=".nav-collapse">
        <span class="icon-bar"></span>
        <span class="icon-bar"></span>
        <span class="icon-bar"></span>
      </a>
      <div class="nav-collapse collapse">
        <ul class="nav">
		  <li><a href="subtitle.jsp">Home</a></li>
          <li class="active"><a href="#">Stats</a></li>
          <li><a href="notice.html">Notice</a></li>
        </ul>
      </div><!--/.nav-collapse -->
    </div>
  </div>
</div>
<div class="status label label-info">status</div>
<div class="container">
  <div class="input-append">
    <select class="span5" id="srtList"></select>
    <input id="btnSrtChoose" class="btn" type="button" value="統計を計算" disabled onclick="onCalcStatsStart()">
  </div>
  <div id="resultDiv">
  </div>
</div>
</body>

</html>
