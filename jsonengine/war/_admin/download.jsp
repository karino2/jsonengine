<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="com.google.appengine.api.users.User" %>
<%@ page import="com.google.appengine.api.users.UserService" %>
<%@ page import="com.google.appengine.api.users.UserServiceFactory" %>
<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
<title>BBS sample by jsonengine</title>
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

function getSrts(){
 ajaxGet("/_je/srt", function (result) {
	var sel = $('#srtList');
	for(var i = 0; i <result.length; i++){
		sel.append($('<option>').attr({value: result[i]._docId}).text(result[i].srtTitle));
	}
	btnStartEnable(true);
 });
}

function btnStartEnable(isEnable) {
	if(isEnable) {
			$("#btnSrtChoose").removeAttr("disabled");
	} else {
			$("#btnSrtChoose").attr("disabled", true);
	}
}

function onGenerateLink() {
	var srtId = $('#srtList option:selected')[0].value;
	var span =$("#linkSpan");
	span.empty();
	span.append('<a href="/admin/download?srtId='+ srtId+ '">download srt</a>');
}

getSrts();

</script>
</head>

<body>
<h1>Subtitles</h1>
  <div class="input-append">
    <select class="span5" id="srtList"></select>
    <input id="btnSrtChoose" class="btn" type="button" value="Generate Download" disabled onclick="onGenerateLink()">
	<span id="linkSpan"></span>
  </div>
</body>

</html>
