<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>

<%@page import="com.liferay.journal.service.persistence.JournalArticleUtil"%>
<%@page import="com.liferay.journal.model.JournalArticle"%>
<%@ include file="/init.jsp"%>

<liferay-portlet:actionURL var="sendActionURL" />

<aui:form action="${sendActionURL}" method="post" name="fm">
	<aui:input name="command" type="hidden" value="save" />

	<aui:fieldset>
		<c:forEach varStatus="i" var="article" items="${JournalArticleUtil.findAll()}">
			<h1>URL: ${article.urlTitle}</h1>
			<aui:input name="${'field' +  i.index}" placeholder="${article.urlTitle}" type="text" />	
		</c:forEach>
	</aui:fieldset>

	<aui:button type="submit">Save</aui:button>

</aui:form>
