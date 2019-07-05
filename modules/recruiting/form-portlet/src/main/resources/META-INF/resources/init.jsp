<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>

<%@ taglib uri="http://java.sun.com/portlet_2_0" prefix="portlet" %>

<%@ taglib uri="http://liferay.com/tld/aui" prefix="aui" %>
<%@ taglib uri="http://liferay.com/tld/portlet" prefix="liferay-portlet" %>
<%@ taglib uri="http://liferay.com/tld/theme" prefix="liferay-theme" %>
<%@ taglib uri="http://liferay.com/tld/ui" prefix="liferay-ui" %>
<%@ taglib prefix="clay" uri="http://liferay.com/tld/clay" %>

<%@ page import="mvc.portlet.configuration.FormPortletConfiguration" %>
<%@ page import="mvc.portlet.configuration.FormPortletDisplayContext" %>

<%@ page import="com.liferay.portal.kernel.util.StringPool" %>
<%@ page import="com.liferay.portal.kernel.util.Validator" %>

<liferay-theme:defineObjects />

<portlet:defineObjects />
<%
    FormPortletDisplayContext formPortletDisplayContext = new FormPortletDisplayContext(renderRequest);
   	FormPortletConfiguration formPortletConfiguration = formPortletDisplayContext.getFormPortletConfiguration();

	String csvSeparator = StringPool.BLANK;
	boolean isDataFilePathChangeable = false;
	String dataRootDir = StringPool.BLANK;
    String emailFromAddress = StringPool.BLANK;
    String emailFromName = StringPool.BLANK;
    boolean isValidationScriptEnabled = false;

    if (Validator.isNotNull(formPortletConfiguration)) {
		csvSeparator = formPortletConfiguration.csvSeparator();
		isDataFilePathChangeable = formPortletConfiguration.isDataFilePathChangeable();
		dataRootDir = formPortletConfiguration.dataRootDir();
		emailFromAddress = formPortletConfiguration.emailFromAddress();
		emailFromName = formPortletConfiguration.emailFromName();
		isValidationScriptEnabled = formPortletConfiguration.isValidationScriptEnabled();
	}
%>