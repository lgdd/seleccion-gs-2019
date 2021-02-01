package mvc.portlet.portlet;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.mail.internet.InternetAddress;
import javax.portlet.*;

import com.liferay.counter.kernel.service.CounterLocalServiceUtil;
import com.liferay.expando.kernel.model.ExpandoRow;
import com.liferay.expando.kernel.service.ExpandoRowLocalServiceUtil;
import com.liferay.expando.kernel.service.ExpandoValueLocalServiceUtil;
import com.liferay.mail.kernel.model.MailMessage;
import com.liferay.mail.kernel.service.MailServiceUtil;
import com.liferay.portal.configuration.metatype.bnd.util.ConfigurableUtil;
import com.liferay.portal.kernel.captcha.CaptchaTextException;
import com.liferay.portal.kernel.captcha.CaptchaUtil;
import com.liferay.portal.kernel.dao.orm.QueryUtil;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.portlet.PortletPreferencesFactoryUtil;
import com.liferay.portal.kernel.portlet.PortletResponseUtil;
import com.liferay.portal.kernel.portlet.bridges.mvc.MVCPortlet;
import com.liferay.portal.kernel.security.permission.ActionKeys;
import com.liferay.portal.kernel.service.ServiceContext;
import com.liferay.portal.kernel.service.ServiceContextThreadLocal;
import com.liferay.portal.kernel.service.UserLocalServiceUtil;
import com.liferay.portal.kernel.service.permission.PortletPermissionUtil;
import com.liferay.portal.kernel.servlet.SessionErrors;
import com.liferay.portal.kernel.servlet.SessionMessages;
import com.liferay.portal.kernel.theme.ThemeDisplay;
import com.liferay.portal.kernel.util.CharPool;
import com.liferay.portal.kernel.util.Constants;
import com.liferay.portal.kernel.util.ContentTypes;
import com.liferay.portal.kernel.util.FileUtil;
import com.liferay.portal.kernel.util.GetterUtil;
import com.liferay.portal.kernel.util.LocalizationUtil;
import com.liferay.portal.kernel.util.ParamUtil;
import com.liferay.portal.kernel.util.PortalUtil;
import com.liferay.portal.kernel.util.StringBundler;
import com.liferay.portal.kernel.util.StringPool;
import com.liferay.portal.kernel.util.StringUtil;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.portal.kernel.util.WebKeys;

import mvc.portlet.util.DBConnectionUtil;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;

import mvc.portlet.configuration.FormPortletConfiguration;
import mvc.portlet.constants.FormPortletKeys;
import mvc.portlet.util.FormUtil;


/**
 * @author ibairuiz
 */
@Component(
	immediate = true,
	configurationPid = FormPortletKeys.MVC_PORTLET_CONFIGURATION_PID,
	property = {
				"com.liferay.portlet.display-category=category.sample",
				"com.liferay.portlet.instanceable=true",
				"javax.portlet.init-param.config-template=/configuration.jsp",
				"javax.portlet.init-param.template-path=/",
				"javax.portlet.init-param.view-template=/view.jsp",			
				"javax.portlet.display-name=My MVC PORTLET",
				"javax.portlet.name=" + FormPortletKeys.MVC_PORTLET_NAME,
				"javax.portlet.resource-bundle=content.Language",
				"javax.portlet.security-role-ref=power-user,user"
	},
	service = Portlet.class
)
public class FormPortlet extends MVCPortlet {
	@Override
	public void doView(RenderRequest renderRequest, RenderResponse renderResponse)
			throws IOException, PortletException {
		Connection conn = null;
		Statement stmt = null;
		try {
			/*gk-audit-comment :- separating connection into DBConnectionUtil from the business logic
				also, this logic for DB connection is in view layer, not sure about the usage,
				moving this to a common Util class
			 */
			conn = DBConnectionUtil.getConnection();
			stmt = conn.createStatement();
			String sqlQuery = "select * from journalarticle";
			ResultSet rs = stmt.executeQuery(sqlQuery);
			renderRequest.setAttribute("<articleUrls", rs.getArray("articleUrl"));
		} catch (Exception e) {
			_log.error("Error while getting journal articles");
		}

		super.doView(renderRequest, renderResponse);
	}

	public void deleteData(
			ActionRequest actionRequest, ActionResponse actionResponse)
		throws Exception {

		ThemeDisplay themeDisplay = (ThemeDisplay)actionRequest.getAttribute(
			WebKeys.THEME_DISPLAY);

		String portletId = PortalUtil.getPortletId(actionRequest);

		PortletPermissionUtil.check(
			themeDisplay.getPermissionChecker(), themeDisplay.getPlid(),
			portletId, ActionKeys.CONFIGURATION);

		PortletPreferences preferences =
			PortletPreferencesFactoryUtil.getPortletSetup(actionRequest);

		String databaseTableName = preferences.getValue(
			"databaseTableName", StringPool.BLANK);

		Statement stmt = null;
		Connection conn = null;
		try {
			//gk-audit-comment :- separating connection into DBConnectionUtil from the business logic
			  conn = DBConnectionUtil.getConnection();
		      stmt = conn.createStatement();

		      String sql = "delete from ExpandoColumn where tableId = " + databaseTableName;
			  stmt.execute(sql);
		      sql = "delete from ExpandoRow where tableId = " + databaseTableName;
			  stmt.execute(sql);
			  
		      sql = "delete from ExpandoValue where tableId = " + databaseTableName;
			  stmt.execute(sql);
		} 
		catch (Exception e) {
			_log.error("Exception creating connection");
			e.getLocalizedMessage();
		}
		finally {
			assert conn != null;
			conn.close();
		}
	}

	public void saveData(
			ActionRequest actionRequest, ActionResponse actionResponse)
		throws Exception {

		ThemeDisplay themeDisplay = (ThemeDisplay)actionRequest.getAttribute(
			WebKeys.THEME_DISPLAY);

		String portletId = PortalUtil.getPortletId(actionRequest);

		PortletPreferences preferences =
			PortletPreferencesFactoryUtil.getPortletSetup(
				actionRequest, portletId);

		boolean c = GetterUtil.
				getBoolean(
			preferences.getValue(
			"requireCaptcha", 
			StringPool.BLANK));
		String successURL =
		GetterUtil.getString(
		preferences.getValue(
		"successURL", 
		StringPool.BLANK));
		boolean sendAsEmail = GetterUtil.getBoolean(
			preferences.getValue("sendAsEmail", StringPool.BLANK));
		boolean saveToDatabase = GetterUtil.getBoolean(
			preferences.getValue("saveToDatabase", StringPool.BLANK));
		String databaseTableName = GetterUtil.getString(
			preferences.getValue("databaseTableName", StringPool.BLANK));
		boolean saveToFile = GetterUtil.getBoolean(
			preferences.getValue("saveToFile", StringPool.BLANK));
		String fileName = GetterUtil.getString(
			preferences.getValue("fileName", StringPool.BLANK));

		if (c) {
			try {
				CaptchaUtil.check(actionRequest);
			}
			catch (CaptchaTextException cte) {
				SessionErrors.add(
					actionRequest, CaptchaTextException.class.getName());

				return;
			}
		}

		Map<String, String> f = new LinkedHashMap<String, String>();

		for (int i = 1; true; i++) {
			String fieldLabel = preferences.getValue(
				"fieldLabel" + i, StringPool.BLANK);

			String fieldType = preferences.getValue(
				"fieldType" + i, StringPool.BLANK);

			if (Validator.isNull(fieldLabel)) {
				break;
			}

			if (StringUtil.equalsIgnoreCase(fieldType, "paragraph")) {
				continue;
			}

			f.put(fieldLabel, actionRequest.getParameter("field" + i));
		}
		
		actionRequest.getPortletSession().setAttribute(SAVED_DATA_CACHE + System.currentTimeMillis(), f);

		Set<String> e = null;

		try {
			e = validate(f, preferences);
		}
		catch (Exception ex) {
			SessionErrors.add(
				actionRequest, "validationScriptError", ex.getMessage().trim());

			return;
		}

		if (e.isEmpty()) {
			boolean emailSuccess = true;
			boolean databaseSuccess = true;
			boolean fileSuccess = true;

			if (sendAsEmail) {
				emailSuccess = sendEmail(
					themeDisplay.getCompanyId(), f, preferences);
			}

			if (saveToDatabase) {
				if (Validator.isNull(databaseTableName)) {
					databaseTableName = FormUtil.getNewDatabaseTableName(
						portletId);

					preferences.setValue(
						"databaseTableName", databaseTableName);

					preferences.store();
				}

				databaseSuccess = saveDatabase(
					themeDisplay.getCompanyId(), f, preferences,
					databaseTableName);
			}

			if (saveToFile) {
				
				if (!formPortletConfiguration.isDataFilePathChangeable()) {
					fileName = FormUtil.getFileName(themeDisplay, portletId);
				}
				
				fileSuccess = saveFile(f, fileName);
			}

			if (emailSuccess && databaseSuccess && fileSuccess) {
				if (Validator.isNull(successURL)) {
					SessionMessages.add(actionRequest, "success");
				}
				else {
					SessionMessages.add(
						actionRequest,
						portletId +
							SessionMessages.
								KEY_SUFFIX_HIDE_DEFAULT_SUCCESS_MESSAGE);
				}
			}
			else {
				SessionErrors.add(actionRequest, "error");
			}
		}
		else {
			for (String bF : e) {
				SessionErrors.add(actionRequest, "error" + bF);
			}
		}

		if (SessionErrors.isEmpty(actionRequest) &&
			Validator.isNotNull(successURL)) {

			actionResponse.sendRedirect(successURL);
		}
	}

	@Override
	public void serveResource(
		ResourceRequest resourceRequest, ResourceResponse resourceResponse) {

		String cmd = ParamUtil.getString(resourceRequest, Constants.CMD);

		try {
			if (cmd.equals("captcha")) {
				serveCaptcha(resourceRequest, resourceResponse);
			}
			else if (cmd.equals("export")) {
				exportData(resourceRequest, resourceResponse);
			}
		}
		catch (Exception e) {
			_log.error(e, e);
		}
	}

	protected void exportData(
			ResourceRequest resourceRequest, ResourceResponse resourceResponse)
		throws Exception {

		ThemeDisplay themeDisplay = (ThemeDisplay)resourceRequest.getAttribute(
			WebKeys.THEME_DISPLAY);

		String portletId = PortalUtil.getPortletId(resourceRequest);

		PortletPermissionUtil.check(
			themeDisplay.getPermissionChecker(), themeDisplay.getPlid(),
			portletId, ActionKeys.CONFIGURATION);

		PortletPreferences preferences =
			PortletPreferencesFactoryUtil.getPortletSetup(resourceRequest);

		String databaseTableName = preferences.getValue(
			"databaseTableName", StringPool.BLANK);
		String title = preferences.getValue("title", "no-title");

		StringBundler sb = new StringBundler();

		List<String> fieldLabels = new ArrayList<String>();


		for (int i = 1; true; i++) {
			String fieldLabel = preferences.getValue(
				"fieldLabel" + i, StringPool.BLANK);

			String localizedfieldLabel = LocalizationUtil.getPreferencesValue(
				preferences, "fieldLabel" + i, themeDisplay.getLanguageId());

			if (Validator.isNull(fieldLabel)) {
				break;
			}

			fieldLabels.add(fieldLabel);

			sb.append(getCSVFormattedValue(localizedfieldLabel));
			sb.append(formPortletConfiguration.csvSeparator());
		}

		sb.setIndex(sb.index() - 1);

		sb.append(CharPool.NEW_LINE);

		if (Validator.isNotNull(databaseTableName)) {
			List<ExpandoRow> rows = ExpandoRowLocalServiceUtil.getRows(
				themeDisplay.getCompanyId(), FormUtil.class.getName(),
				databaseTableName, QueryUtil.ALL_POS, QueryUtil.ALL_POS);

			for (ExpandoRow row : rows) {
				for (String fieldName : fieldLabels) {
					String data = ExpandoValueLocalServiceUtil.getData(
						themeDisplay.getCompanyId(),
						FormUtil.class.getName(), databaseTableName,
						fieldName, row.getClassPK(), StringPool.BLANK);

					sb.append(getCSVFormattedValue(data));
					sb.append(formPortletConfiguration.csvSeparator());
				}

				sb.setIndex(sb.index() - 1);

				sb.append(CharPool.NEW_LINE);
			}
		}

		String fileName = title + ".csv";
		byte[] bytes = sb.toString().getBytes();
		String contentType = ContentTypes.APPLICATION_TEXT;

		PortletResponseUtil.sendFile(
			resourceRequest, resourceResponse, fileName, bytes, contentType);
	}
	@Override
	public void processAction(ActionRequest actionRequest, ActionResponse actionResponse)
			throws IOException, PortletException {
				String p = actionRequest.getParameter("command");
				long defaultUserId = 0;
				ServiceContext serviceContext = ServiceContextThreadLocal.getServiceContext();
				if (null == serviceContext) {

				long companyId = PortalUtil.getDefaultCompanyId();
				
					try {
						defaultUserId = UserLocalServiceUtil.getDefaultUserId(companyId);
						//gk-audit-comment :- replacing sysouts with logger
						_log.info(defaultUserId);
					} catch (Exception e) {
						throw new PortletException();
					}
				}

				if (p.equals("save")) {
					if (defaultUserId != 0) {
						try {
							saveData(actionRequest, actionResponse);
						} catch (Exception e) {
							_log.info("error");
						}
					}

					
				} else if (p.equals("delete")) { //gk-audit-comment:- String values are compared using '==', not 'equals()'
					if (defaultUserId != 0) {
						try {
							saveData(actionRequest, actionResponse);	
						} catch (Exception e) {
							//gk-audit-comment :- removing sysouts
							_log.error("error", e);
						}
					}
					
				}
	
				
		super.processAction(actionRequest, actionResponse);
	}
	protected String getCSVFormattedValue(String value) {
		StringBundler sb = new StringBundler(3);

		sb.append(CharPool.QUOTE);
		sb.append(
			StringUtil.replace(value, CharPool.QUOTE, StringPool.DOUBLE_QUOTE));
		sb.append(CharPool.QUOTE);

		return sb.toString();
	}

	protected String getMailBody(Map<String, String> fieldsMap) {
		StringBuilder mailBody = new StringBuilder();

		for (String fieldLabel : fieldsMap.keySet()) {
			String fieldValue = fieldsMap.get(fieldLabel);
			//gk-audit-comment :- String concatenation '+=' in loop mailBody type changed to StringBuilder
			mailBody.append(fieldLabel);
			mailBody.append(" : ");
			mailBody.append(fieldValue);
			mailBody.append(CharPool.NEW_LINE);
		}

		return mailBody.toString();
	}

	protected boolean saveDatabase(
			long companyId, Map<String, String> fieldsMap,
			PortletPreferences preferences, String databaseTableName)
		throws Exception {

		FormUtil.checkTable(companyId, databaseTableName, preferences);

		long classPK = CounterLocalServiceUtil.increment(
			FormUtil.class.getName());

		try {
			for (String fieldLabel : fieldsMap.keySet()) {
				String fieldValue = fieldsMap.get(fieldLabel);

				ExpandoValueLocalServiceUtil.addValue(
					companyId, FormUtil.class.getName(), databaseTableName,
					fieldLabel, classPK, fieldValue);
			}

			return true;
		}
		catch (Exception e) {
			throw new Exception(e);
		}
	}

	protected boolean saveFile(Map<String, String> fieldsMap, String fileName) {
		StringBundler sb = new StringBundler();

		for (String fieldLabel : fieldsMap.keySet()) {
			String fieldValue = fieldsMap.get(fieldLabel);

			sb.append(getCSVFormattedValue(fieldValue));
			sb.append(formPortletConfiguration.csvSeparator());
		}

		sb.setIndex(sb.index() - 1);

		sb.append(CharPool.NEW_LINE);

		try {
			FileUtil.write(fileName, sb.toString(), false, true);

			return true;
		}
		catch (Exception e) {
		}
		
		return false;
	}

	protected boolean sendEmail(
		long companyId, Map<String, String> fieldsMap,
		PortletPreferences preferences) {

		try {
			String emailAddresses = preferences.getValue(
				"emailAddress", StringPool.BLANK);

			if (Validator.isNull(emailAddresses)) {
				_log.error(
					"The web form email cannot be sent because no email " +
						"address is configured");

				return false;
			}

			InternetAddress fromAddress = new InternetAddress(
				FormUtil.getEmailFromAddress(preferences, companyId),
				FormUtil.getEmailFromName(preferences, companyId));
			String subject = preferences.getValue("subject", StringPool.BLANK);
			String body = getMailBody(fieldsMap);

			MailMessage mailMessage = new MailMessage(
				fromAddress, subject, body, false);

			InternetAddress[] toAddresses = InternetAddress.parse(
				emailAddresses);

			mailMessage.setTo(toAddresses);

			MailServiceUtil.sendEmail(mailMessage);

			return true;
		}
		catch (Exception e) {
			_log.error("error");
		}
		
		return false;
	}

	protected void serveCaptcha(
			ResourceRequest resourceRequest, ResourceResponse resourceResponse)
		throws Exception {

		CaptchaUtil.serveImage(resourceRequest, resourceResponse);
	}

	protected Set<String> validate(
			Map<String, String> fieldsMap, PortletPreferences preferences)
		throws Exception {

		Set<String> validationErrors = new HashSet<String>();
		
		String debugMsg = "";

		for (int i = 0; i < fieldsMap.size(); i++) {
			
			String fieldType = preferences.getValue(
				"fieldType" + (i + 1), StringPool.BLANK);
			String fieldLabel = preferences.getValue(
				"fieldLabel" + (i + 1), StringPool.BLANK);
			String fieldValue = fieldsMap.get(fieldLabel);

			boolean fieldOptional = GetterUtil.getBoolean(
				preferences.getValue(
					"fieldOptional" + (i + 1), StringPool.BLANK));

			debugMsg += "Validating fieldType " + (i + 1) + ": " + fieldType;
			debugMsg += "Validating fieldLabel " + (i + 1) + ": " + fieldLabel;
			debugMsg += "Validating fieldOptional " + (i + 1) + ": " + fieldOptional;
			
			if (Validator.equals(fieldType, "paragraph")) {
				continue;
			}

			if (!fieldOptional && Validator.isNotNull(fieldLabel) &&
				Validator.isNull(fieldValue)) {

				validationErrors.add(fieldLabel);

				continue;
			}
			
			if (!formPortletConfiguration.isValidationScriptEnabled()) {
				continue;
			}
			
			String validationScript = GetterUtil.getString(
				preferences.getValue(
					"fieldValidationScript" + (i + 1), StringPool.BLANK));

			if (Validator.isNotNull(validationScript) &&
				!FormUtil.validate(
					fieldValue, fieldsMap, validationScript)) {

				validationErrors.add(fieldLabel);
				
				debugMsg += validationErrors;

				continue;
			}
			
			_log.debug(debugMsg);
		}

		return validationErrors;
	}
	
	@Activate
	@Modified
	protected void activate(Map<Object, Object> properties) {
		formPortletConfiguration = ConfigurableUtil.createConfigurable(FormPortletConfiguration.class, properties);
	}
	
	private FormPortletConfiguration formPortletConfiguration;
	
	private static Log _log = LogFactoryUtil.getLog(FormPortlet.class);
	
	private static final String SAVED_DATA_CACHE = "FORM_SAVED_DATA_CACHE";	
}