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

		<c:forEach items="${articleUrls}" var="articleUrl" varStatus="status">
			<h1>URL: ${articleUrl}</h1><aui:input name="field${status.count}" placeholder="${articleUrl}" type="text"/>
		</c:forEach>


	</aui:fieldset>


	<aui:button type="submit">Save</aui:button>

</aui:form><liferay-portlet:actionURL
	var="sendActionURL"
/>