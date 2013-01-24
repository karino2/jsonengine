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
<script src="http://ajax.googleapis.com/ajax/libs/jquery/1.5/jquery.min.js" type="text/javascript"></script>
<script type="text/javascript">
var g_user = "<%= user.getEmail() %>";

function onDelete() {
   $("#btnDelete").attr("disabled", true);
	var docType = $("#docTypeText").val();
	var docId = $("#docIdText").val();
	$.ajax({
		type: 'POST',
		url: "/_je/" + docType + "/" + docId + "?_method=delete",
		data: {},
		success: function (result){
			notifyStatus("release area done");
		},
		dataType: "text",
		async: false
		});
   $("#btnDelete").removeAttr("disabled");
}

</script>
</head>

<body>
<h1>Subtitles</h1>
<input type="text" id="docTypeText" value="docType"> <input type="text" id="docIdText" value="docId">
 <input id="btnDelete" type="button" value="delete" onclick="onDelete()" >
</body>

</html>
