<%@ include file="/init.jsp" %>
<%@ page import="java.sql.Connection"%>
<%@ page import="java.sql.DriverManager"%>
<%@ page import="java.sql.Statement"%>
<%@ page import="java.sql.ResultSet"%>
<%@ page import="java.util.ArrayList"%>
<%@ page import="java.util.HashSet"%>
<%@ page import="java.util.LinkedHashMap"%>
<%@ page import="java.util.List"%>
<%@ page import="java.util.Map"%>
<%@ page import="java.util.Set"%>

<aui:form action="<%= sendActionURL %>" method="post" name="fm">
	<aui:input
		name="command"
		type="hidden"
		value="save"
	/>

	<aui:fieldset>

<%
Connection conn = null;
Statement stmt = null;
try {
		Class.forName("com.mysql.jdbc.Driver");
		conn = DriverManager.getConnection("jdbc:mysql://localhost:3306/demo_omnichannel71", "root", "root");
		stmt = conn.createStatement();
		String sqlQuery = "select * from journalarticle";
		ResultSet rs = stmt.executeQuery(sqlQuery);
	int i = 0;
		while(rs.next()){
			i++;
		String t = rs.getString("urlTitle");
		String field = "field" + i;
%><h1>URL: <%=t%></h1><aui:input name="<%=field%>" placeholder="<%=t%>" type="text"/><%}			  

					} 
					catch (Exception e) {
							System.out.println("error");
					}
%>	


	</aui:fieldset>


	<aui:button type="submit">Save</aui:button>

</aui:form><liferay-portlet:actionURL
	var="sendActionURL"
/>