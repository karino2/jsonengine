<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="com.google.appengine.api.users.User" %>
<%@ page import="com.google.appengine.api.users.UserService" %>
<%@ page import="com.google.appengine.api.users.UserServiceFactory" %>
<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
<title>TobinQ script admin page</title>
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
<script src="http://ajax.googleapis.com/ajax/libs/jquery/1.4.1/jquery.min.js" type="text/javascript"></script>
<script type="text/javascript">

function isLocal() {
   return typeof(g_test_scripts) != "undefined";
}


function ajaxPost(url, param, onSuccess, postType){
   if(isLocal()) {
	   return dummyPost(url, param, onSuccess, postType);
   } else {
	   var ajx = $.post(url, param, onSuccess, postType);
	   ajx.fail(function() {alert("conflict or error"); });
	   return ajx;
   }
}

function ajaxGet(url, params, onSuccess){
	if(isLocal()) {
		dummyGet(url, params, onSuccess);
	} else {
		$.get(url, params, onSuccess);
	}
}


$(function() {
  query();
});


function clearFields() {
  $("#msgTitle").val("");
  $("#msgDescription").val("");
  $("#msgScript").val("");
}

function postNewScript() {

  disableButton(true);
  ajaxPost("/_je/tobinqscripts", { title: $("#msgTitle").val(),  _description:  $("#msgDescription").val(), _script:$("#msgScript").val()},
 function (result){
    clearFields();
    query();
  }, "text");
}

// get all posts and list them
function query() {
  
  // build query params
  disableButton(true);
  params = { sort: "_createdAt.desc", limit: 100};
  
  // query for the messages
  ajaxGet("/_je/tobinqscripts", params, function (result) {
    $("#codeList").empty();
    for (i = 0; i < result.length; i++) {
	  var div = $("<div/>");
	  div.attr("_docId", result[i]._docId);
	  div.attr("_updatedAt", result[i]._updatedAt);	  
	  var titleElem = $("<input/>").attr({ type: 'text', size: 30}).addClass("title").val(result[i].title);
	  var descriptionElem =$("<textarea/>").attr({cols: 40, rows:4}).addClass("description").val(result[i]._description);
	  var scriptElem =$("<textarea/>").attr({cols: 40, rows:4}).addClass("script").val(result[i]._script);
	  var editButton = $("<input/>").attr({type: "button"}).addClass("edit").val("Edit").click(function() {
			var par =$(this).parent();
			debElement =par;
			var title = par.find(".title").val();
			var description = par.find(".description").val();
			var script = par.find(".script").val();
			var docId = par.attr("_docId");
			var updatedAt = par.attr("_updatedAt");
			disableSubButtons(par, true);
			ajaxPost("/_je/tobinqscripts/"+docId, {title: title, _description: description, _script: script, _checkUpdatesAfter: updatedAt}, function(result) {
				query();
				}, "text");

		});
	  var deleteButton = $("<input/>").attr({type:"button"}).addClass("delete").val("Delete").click(function(){
			var par =$(this).parent();
			var docId = par.attr("_docId");
			var updatedAt = par.attr("_updatedAt");
		    disableSubButtons(par, true);
			ajaxPost("/_je/tobinqscripts/"+docId+"?_method=delete", {_checkUpdatesAfter: updatedAt}, function (result){	
				query();
				  }, "text");
		});
		
		div.append(titleElem);
		div.append(descriptionElem);
		div.append(scriptElem);
		div.append(editButton);
		div.append(deleteButton);
	  
      $("#codeList").append(div);
    }
    disableButton(false);
  });
}

function disableSubButtons(par, isDisabled){
  if (isDisabled) {
	  par.find(".delete").attr("disabled", true)
	  par.find(".edit").attr("disabled", true)
   } else {
      alert("NYI!");
   }
}


function disableButton(isDisabled) {
  if (isDisabled) {
    $("#sendBtn").attr("disabled", true);
  } else {
    $("#sendBtn").removeAttr("disabled");
  }
}

</script>
</head>

<body>
<h1>TobinQ Codes admin</h1>
<table>
	<tr><td>title:</td><td><input id="msgTitle" type="text" value=""  size="50"></td></tr>
	<tr><td>description:</td><td><textarea id="msgDescription" rows="10" cols="100"></textarea></td></tr>
	<tr><td>code:</td><td><textarea id="msgScript" rows="10" cols="100"></textarea></td></tr>
	<tr><td colSpan=2><input id="sendBtn" type="button" value="Post" onclick="postNewScript();" ></td></tr>
</table>
<hr>
<div id="codeList"></div><br>
<br>
<hr>
</body>

</html>