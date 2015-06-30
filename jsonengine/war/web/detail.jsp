<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="com.google.appengine.api.users.User" %>
<%@ page import="com.google.appengine.api.users.UserService" %>
<%@ page import="com.google.appengine.api.users.UserServiceFactory" %>

<%@ page import="java.util.List" %>
<%@ page import="java.lang.StringBuffer" %>
<%@ page import="com.jsonengine.model.JEDoc" %>

<%@ page import="com.jsonengine.service.crud.CRUDRequest" %>
<%@ page import="com.jsonengine.service.crud.CRUDService" %>

<%@ page import="com.google.appengine.api.datastore.Transaction" %>
<%@ page import="org.slim3.datastore.Datastore" %>
<%@ page import="com.jsonengine.common.JENotFoundException" %>
<%@ page import="com.jsonengine.common.JEConflictException" %>


<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
<title>統計グラフ!</title>
<%!
String htmlEscape(String body){
	return body.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
}
String formatBody(String body) {
	return htmlEscape(body).replace("\n", "<br>");
}

JEDoc getJEDoc(CRUDService serv, CRUDRequest cr) {
    final Transaction tx = Datastore.beginTransaction();

	try{
            final JEDoc jeDoc = serv.getJEDoc(tx, cr);
            Datastore.commit(tx);
			return jeDoc;
     } catch (JEConflictException e) {
        throw new RuntimeException(e);
     } catch (JENotFoundException e) {
	 	return null;
     }

}

%>
<%
String id = request.getParameter("id");

CRUDRequest jeReq = new CRUDRequest();
CRUDService service = new CRUDService();
jeReq.setDocType("tobinqscripts");
jeReq.setDocId(id);



String title = "チャートが見つかりませんでした。";
String graphId = "";
String description = "";
String baseEncodedData = "";


JEDoc doc = getJEDoc(service, jeReq);

boolean error = doc ==null;

if(!error){

	title = (String)doc.getDocValues().get("title");
	graphId = (String)doc.getDocValues().get("graphId");
	description = (String)doc.getDocValues().get("_description");


	if(graphId != null && !graphId.equals("")) {
		CRUDRequest graphReq = new CRUDRequest();
		graphReq.setDocType("graphs");
		graphReq.setDocId(graphId);

	
		JEDoc graphDoc = getJEDoc(service, graphReq);
		if(graphDoc != null) {
				baseEncodedData = "data:image/png;base64," + (String)graphDoc.getDocValues().get("_encodedData");
		}
	}
}	


%>

<script src="http://ajax.googleapis.com/ajax/libs/jquery/1.4.1/jquery.min.js" type="text/javascript"></script>
<style type="text/css">
img.chartImgClass {
  float: left;
  margin-right: 2em;
  margin-bottom: 1em;
  width: 400px;
  height: 400px;
}
</style>

</head>

<body>
<h1><%= htmlEscape(title) %></h1>
<hr>
<% if(error) { %>
チャートが見つかりませんでした。指定されたチャートは削除されたか、URLが間違っています。
<% } else { %>
<img class="chartImgClass" id="chartImg" src="<%= baseEncodedData%>">
<div id="descriptionDiv">
<%= this.formatBody(description) %>
</div>
<% } %>
</body>

</html>