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

textarea.dirty {
  background-color: #996666;
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
<title>つぶやくようにCoursera和訳 web Ver.</title>
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



function onJqueryReady() {
	$(window).unload(function() {
		freeArea(g_areaIndex, true);
	});
	notifyStatus("setup...", STATUS_STICK);
	getSrts();
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
var g_srtId;
var g_areaIndex =-1;

function btnStartEnable(isEnable) {
	if(isEnable) {
			$("#btnSrtChoose").removeAttr("disabled");
	} else {
			$("#btnSrtChoose").attr("disabled", true);
	}
}

function onChoose() {
	notifyStatus("connecting...", STATUS_STICK);
	if(g_areaIndex != -1){
		freeArea(g_areaIndex, true);
		g_areaIndex = -1;
	}
	btnStartEnable(false);
	g_srtId =$('#srtList option:selected')[0].value;
	updateAreaMapAndSetupAreaAndTexts();
}

function areaIndexToRegion(areaIndex){
	return {begin: 1+(areaIndex-1)*TEXT_PER_AREA, end: areaIndex*TEXT_PER_AREA }
}

function areaIndexToRegionWithHeaderFooter(areaIndex){
	if(areaIndex == 1) {
		return {begin: 1, end: (2+areaIndex*TEXT_PER_AREA) }	
	}
	return {begin: -1+(areaIndex-1)*TEXT_PER_AREA, end: 2+areaIndex*TEXT_PER_AREA }
}

function isOutsideArea(text) {
	var region = areaIndexToRegion(g_areaIndex);
	return (text.textId < region.begin) || (text.textId > region.end);
}

function shuffle(begin, end) {
	var ind = [];
	for(var i =begin; i <=end; i++) {
		ind.push(i);
	}
	var res = [];
	while(ind.length > 0) {
		var i = Math.floor(Math.random()*ind.length)
		res.push(ind[i]);
		ind.splice(i, 1);
	}
	return res;
}

function filterSrtId(texts, srtId){
	var res = [];
	for(var i = 0; i < texts.length; i++) {
		if(texts[i].srtId == srtId)
			res.push(texts[i]);
	}
	return res;
}

function onJump() {
	notifyStatus("network...", STATUS_STICK);
	updateAreaMapAndSetupAreaAndTexts();
}

function submitText(docId, targetText, onAfter) {
	var obj = {target: targetText, _docId: docId};
	notifyStatus("submitting...", STATUS_STICK);
	var jsonparam = { _doc: JSON.stringify(obj) };	

	ajaxPost("/_je/text", jsonparam, function (result){
		onAfter();
		notifyStatus("submit done.", STATUS_TIMED);
	}, "json");
}

function isInsideArea(text) {
	return !isOutsideArea(text);
}

function isReallyEmpty(texts){
	for(var i =0; i < texts.length; i++) {
		if(texts[i].target == "" && isInsideArea(texts[i]))
			return true;
	}
	return false;
}

function onTextAreaChange() {
	var tarea = $(this);
	if(tarea.val() != tarea.attr("_server"))
		$(this).addClass("dirty");
}



function onTextsComming(result) {
	var texts = filterSrtId(result, g_srtId);
	if(!isReallyEmpty(texts)) {
		changeDone(g_areaIndex);
		g_areaIndex = -1;
		updateAreaMapAndSetupAreaAndTexts();
		return;
	}
	texts.sort(function(a, b) { if(a.textId > b.textId) return 1; if(a.textId < b.textId) return -1; return 0; });
	
	
	var bldr = [];
	var holder = $('#subtitleHolder');
	holder.empty();
	holder.append($('<ul class="pager"><li class="next"><a id="btnJump" href="javascript:void(0)" disabled onclick="onJump()">別の場所を翻訳 &rarr;</a></li></ul>'));
	for(var i = 0; i <texts.length; i++){
		var div = $('<div/>').addClass("row");
		div.attr("_docId", texts[i]._docId);
		var targetTextArea = $('<textarea />').addClass("target").val(texts[i].target).attr("_server", texts[i].target);
		if(isOutsideArea(texts[i])){
			 targetTextArea.attr("disabled", "disabled");
		} else {
			 targetTextArea.change(onTextAreaChange);
		}
		var target = $('<div/>').addClass("span11").append(targetTextArea);
		var original = $('<div/>').addClass("span12").append($('<textarea />').addClass("original").val(texts[i].original).attr("disabled", "disabled"));
		var submit = $('<a href="javascript:void(0)" class="btn"><i class="icon-ok"></i></a>').click(function() {
			var par =$(this).parent().parent();
			var targetTextArea = par.find(".target");
			var newVal =targetTextArea.val();
			submitText(par.attr("_docId"), par.find(".target").val(), function() {targetTextArea.attr("_server", newVal); targetTextArea.removeClass("dirty"); });
		});
		div.append(original);
		div.append(target);
		div.append($('<div/>').addClass("span1").append(submit));
		holder.append(div);
	}
	notifyStatus("", STATUS_HIDE);
}

function updateAreaMapAndSetupAreaAndTexts() {
	ajaxGet("/_je/areaMap", function (result) {
		g_areaMapList =createAreaMapList(result);
		g_areaMap = g_areaMapList.getMap(g_srtId);
		setupAreaAndTexts();
	 });
}

function setupAreaAndTexts() {
	var id =findEmptyIndex(g_areaIndex);	
	if(id == -1) {
		notifyStatus("TODO: area full", STATUS_STICK);
		return;
	}
	freeArea(g_areaIndex, true);
	bookArea(id);
	g_areaIndex = id;
	enableAreaRelatedButton(true);

	debugLog("retrieve texts");
	var region = areaIndexToRegionWithHeaderFooter(g_areaIndex);
	ajaxGet("/_je/text?cond=textId.ge." + region.begin +"&cond=textId.le."+ region.end, function (result) {
		onTextsComming(result);
		notifyStatus("done", STATUS_TIMED);
	});

}

function findEmptyIndex(avoidId){
	for(var i = 1; i <= g_areaMap.getAreaNum(); i++) {
		if(g_areaMap.isMine(i) && i !=avoidId)
			return i;
	}
   var shuffleIndex =shuffle(1, g_areaMap.getAreaNum());
   for(var i = 0; i < shuffleIndex.length; i++){
      var j =shuffleIndex[i];
      if(g_areaMap.isEmpty(j) && j != avoidId)
		  return j;
   }
   return -1;
}

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

function onFreeClick() {
	freeArea(g_areaIndex);
	g_areaIndex = -1;
	enableAreaRelatedButton(false);
	btnStartEnable(true);
	$('#subtitleHolder').empty();	
}

var g_deb;

function updateArea(areaIndex, success, sync){
	var obj = {};
	obj["a"+areaIndex] = g_areaMap.getArea(areaIndex);
	obj._docId = g_areaMap.getDocId();
	g_deb = obj;
	var jsonparam = { _doc: JSON.stringify(obj) };
	ajaxGeneral({
		type: 'POST',
		url: "/_je/areaMap",
		data: jsonparam,
		dataType: 'json',
		success: function (result){
			debugLog("release area done");
		},
		async: !sync
		});
}

function freeArea(id, sync) {
	if(id == -1) {
		return;
	}
	g_areaMap.unbook(id);
	updateArea(id, function (result){
			debugLog("release area done");
		}, sync);
}

function changeDone(areaIndex) {
	g_areaMap.setDone(areaIndex);
	updateArea(areaIndex, function (result){
		// silent
		});	
}

function bookArea(id){
	g_areaMap.book(id);
	updateArea(id, function (result){
		debugLog("book done");
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


function enableAreaRelatedButton(isEnabled){
  if(isEnabled){
    $("#btnReleaseArea").removeAttr("disabled");
	$("#btnJump").removeAttr("disabled");
  } else {
    $("#btnReleaseArea").attr("disabled", true);
	$("#btnJump").attr("disabled", true);
  }
}

function onChangeSrt() {
	btnStartEnable(true);
}


</script>
</head>

<body onload="onJqueryReady()">
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
          <li class="active"><a href="#home">Home</a></li>
          <li><a href="stats.jsp">Stats</a></li>
          <li><a href="notice.html">Notice</a></li>
        </ul>
      </div><!--/.nav-collapse -->
    </div>
  </div>
</div>
<div class="status label label-info">status</div>
<div class="container" id="homeDiv">
  <input id="btnReleaseArea" type="button" onclick="onFreeClick()" disabled value="和訳を終える" class="pull-right btn btn-primary">
  <div class="input-append">
    <select class="span5" onchange="onChangeSrt()" id="srtList"></select>
    <input id="btnSrtChoose" class="btn" type="button" value="和訳開始" disabled onclick="onChoose()">
  </div>
  <div id="subtitleHolder">
  </div>
  <hr>
</div>
</body>

</html>
