package mvc.portlet.portlet;

import static com.liferay.petra.string.CharPool.NEW_LINE;
import static com.liferay.petra.string.CharPool.QUOTE;
import static com.liferay.petra.string.StringPool.BLANK;
import static com.liferay.petra.string.StringPool.DOUBLE_QUOTE;
import static com.liferay.portal.kernel.servlet.SessionMessages.KEY_SUFFIX_HIDE_DEFAULT_SUCCESS_MESSAGE;
import static java.util.stream.Collectors.joining;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.mail.internet.InternetAddress;
import javax.portlet.ActionRequest;
import javax.portlet.ActionResponse;
import javax.portlet.Portlet;
import javax.portlet.PortletException;
import javax.portlet.PortletPreferences;
import javax.portlet.ReadOnlyException;
import javax.portlet.ResourceRequest;
import javax.portlet.ResourceResponse;
import javax.portlet.ValidatorException;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;

import com.liferay.counter.kernel.service.CounterLocalServiceUtil;
import com.liferay.expando.kernel.model.ExpandoRow;
import com.liferay.expando.kernel.service.ExpandoRowLocalServiceUtil;
import com.liferay.expando.kernel.service.ExpandoTableLocalServiceUtil;
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
import com.liferay.portal.kernel.util.Constants;
import com.liferay.portal.kernel.util.ContentTypes;
import com.liferay.portal.kernel.util.FileUtil;
import com.liferay.portal.kernel.util.GetterUtil;
import com.liferay.portal.kernel.util.LocalizationUtil;
import com.liferay.portal.kernel.util.ParamUtil;
import com.liferay.portal.kernel.util.PortalUtil;
import com.liferay.portal.kernel.util.StringBundler;
import com.liferay.portal.kernel.util.StringUtil;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.portal.kernel.util.WebKeys;

import mvc.portlet.configuration.FormPortletConfiguration;
import mvc.portlet.constants.FormPortletKeys;
import mvc.portlet.util.FormUtil;

/**
 * @author ibairuiz
 */

@Component(immediate = true, configurationPid = FormPortletKeys.MVC_PORTLET_CONFIGURATION_PID, property = {
		"com.liferay.portlet.display-category=category.sample", "com.liferay.portlet.instanceable=true",
		"javax.portlet.init-param.config-template=/configuration.jsp", "javax.portlet.init-param.template-path=/",
		"javax.portlet.init-param.view-template=/view.jsp", "javax.portlet.display-name=My MVC PORTLET",
		"javax.portlet.name=" + FormPortletKeys.MVC_PORTLET_NAME, "javax.portlet.resource-bundle=content.Language",
		"javax.portlet.security-role-ref=power-user,user" }, service = Portlet.class)
public class FormPortlet extends MVCPortlet {

	private static final Log _log = LogFactoryUtil.getLog(FormPortlet.class);
	private static final String SAVED_DATA_CACHE = "FORM_SAVED_DATA_CACHE";

	private static final String COMMAND_SAVE = "save";
	private static final String COMMAND_DELETE = "delete";

	private FormPortletConfiguration formPortletConfiguration;

	@Activate
	@Modified
	protected void activate(Map<Object, Object> properties) {
		formPortletConfiguration = ConfigurableUtil.createConfigurable(FormPortletConfiguration.class, properties);
	}

	public void deleteData(ActionRequest actionRequest, ActionResponse actionResponse) throws PortalException {
		ThemeDisplay themeDisplay = (ThemeDisplay) actionRequest.getAttribute(WebKeys.THEME_DISPLAY);
		String portletId = PortalUtil.getPortletId(actionRequest);

		PortletPermissionUtil.check(themeDisplay.getPermissionChecker(), themeDisplay.getPlid(), portletId,
				ActionKeys.CONFIGURATION);

		PortletPreferences preferences = PortletPreferencesFactoryUtil.getPortletSetup(actionRequest);
		String tableName = preferences.getValue("databaseTableName", BLANK);

		if (!BLANK.equals(tableName)) {
			FormUtil.deleteTable(themeDisplay.getCompanyId(), tableName);
		}
	}

	public void saveData(ActionRequest actionRequest, ActionResponse actionResponse)
			throws PortalException, IOException, ReadOnlyException, ValidatorException {

		ThemeDisplay themeDisplay = (ThemeDisplay) actionRequest.getAttribute(WebKeys.THEME_DISPLAY);
		long companyId = themeDisplay.getCompanyId();
		String portletId = PortalUtil.getPortletId(actionRequest);

		PortletPreferences preferences = PortletPreferencesFactoryUtil.getPortletSetup(actionRequest, portletId);

		boolean requireCaptcha = GetterUtil.getBoolean(preferences.getValue("requireCaptcha", BLANK));
		String successURL = GetterUtil.getString(preferences.getValue("successURL", BLANK));

		if (requireCaptcha) {
			try {
				CaptchaUtil.check(actionRequest);
			} catch (CaptchaTextException cte) {
				SessionErrors.add(actionRequest, CaptchaTextException.class.getName());
				return;
			}
		}

		Map<String, String> fields = new LinkedHashMap<>();

		for (int i = 1; true; i++) {

			String fieldLabel = preferences.getValue("fieldLabel" + i, BLANK);
			String fieldType = preferences.getValue("fieldType" + i, BLANK);

			if (Validator.isNull(fieldLabel)) {
				break;
			}

			if (StringUtil.equalsIgnoreCase(fieldType, "paragraph")) {
				continue;
			}

			fields.put(fieldLabel, actionRequest.getRenderParameters().getValue("field" + i));
		}

		actionRequest.getPortletSession().setAttribute(SAVED_DATA_CACHE + System.currentTimeMillis(), fields);

		try {
			validate(fields, preferences).forEach(error -> SessionErrors.add(actionRequest, "error" + error));
		} catch (Exception ex) {
			SessionErrors.add(actionRequest, "validationScriptError", ex.getMessage().trim());
		}

		if (!SessionErrors.isEmpty(actionRequest)) {
			return;
		}

		boolean emailSuccess = true;
		boolean databaseSuccess = true;
		boolean fileSuccess = true;

		boolean sendAsEmail = GetterUtil.getBoolean(preferences.getValue("sendAsEmail", BLANK));

		if (sendAsEmail) {
			emailSuccess = sendEmail(companyId, fields, preferences);
		}

		boolean saveToDatabase = GetterUtil.getBoolean(preferences.getValue("saveToDatabase", BLANK));

		if (saveToDatabase) {
			String databaseTableName = GetterUtil.getString(preferences.getValue("databaseTableName", BLANK));
			if (Validator.isNull(databaseTableName)) {
				databaseTableName = FormUtil.getNewDatabaseTableName(portletId);
				preferences.setValue("databaseTableName", databaseTableName);
				preferences.store();
			}

			databaseSuccess = saveDatabase(companyId, fields, preferences, databaseTableName);
		}

		boolean saveToFile = GetterUtil.getBoolean(preferences.getValue("saveToFile", BLANK));

		if (saveToFile) {
			String fileName = GetterUtil.getString(preferences.getValue("fileName", BLANK));
			if (!formPortletConfiguration.isDataFilePathChangeable()) {
				fileName = FormUtil.getFileName(themeDisplay, portletId);
			}

			fileSuccess = saveFile(fields, fileName);
		}

		if (emailSuccess && databaseSuccess && fileSuccess) {
			String msg = Validator.isNull(successURL) ? "success" : portletId + KEY_SUFFIX_HIDE_DEFAULT_SUCCESS_MESSAGE;
			SessionMessages.add(actionRequest, msg);
		} else {
			SessionErrors.add(actionRequest, "error");
		}

		if (SessionErrors.isEmpty(actionRequest) && Validator.isNotNull(successURL)) {
			actionResponse.sendRedirect(successURL);
		}
	}

	/**
	 * 
	 */

	@Override
	public void serveResource(ResourceRequest resourceRequest, ResourceResponse resourceResponse) {

		String cmd = ParamUtil.getString(resourceRequest, Constants.CMD);

		try {
			if (cmd.equals("captcha")) {
				serveCaptcha(resourceRequest, resourceResponse);
			} else if (cmd.equals("export")) {
				exportData(resourceRequest, resourceResponse);
			}
		} catch (Exception e) {
			_log.error(e, e);
		}
	}

	protected void exportData(ResourceRequest resourceRequest, ResourceResponse resourceResponse) throws Exception {

		ThemeDisplay themeDisplay = (ThemeDisplay) resourceRequest.getAttribute(WebKeys.THEME_DISPLAY);

		String portletId = PortalUtil.getPortletId(resourceRequest);

		PortletPermissionUtil.check(themeDisplay.getPermissionChecker(), themeDisplay.getPlid(), portletId,
				ActionKeys.CONFIGURATION);

		PortletPreferences preferences = PortletPreferencesFactoryUtil.getPortletSetup(resourceRequest);

		String databaseTableName = preferences.getValue("databaseTableName", BLANK);
		String title = preferences.getValue("title", "no-title");

		StringBundler sb = new StringBundler();

		List<String> fieldLabels = new ArrayList<>();

		for (int i = 1; true; i++) {
			String fieldLabel = preferences.getValue("fieldLabel" + i, BLANK);

			String localizedfieldLabel = LocalizationUtil.getPreferencesValue(preferences, "fieldLabel" + i,
					themeDisplay.getLanguageId());

			if (Validator.isNull(fieldLabel)) {
				break;
			}

			fieldLabels.add(fieldLabel);

			sb.append(getCSVFormattedValue(localizedfieldLabel));
			sb.append(formPortletConfiguration.csvSeparator());
		}

		sb.setIndex(sb.index() - 1);

		sb.append(NEW_LINE);

		if (Validator.isNotNull(databaseTableName)) {
			List<ExpandoRow> rows = ExpandoRowLocalServiceUtil.getRows(themeDisplay.getCompanyId(),
					FormUtil.class.getName(), databaseTableName, QueryUtil.ALL_POS, QueryUtil.ALL_POS);

			for (ExpandoRow row : rows) {
				for (String fieldName : fieldLabels) {
					String data = ExpandoValueLocalServiceUtil.getData(themeDisplay.getCompanyId(),
							FormUtil.class.getName(), databaseTableName, fieldName, row.getClassPK(), BLANK);

					sb.append(getCSVFormattedValue(data));
					sb.append(formPortletConfiguration.csvSeparator());
				}

				sb.setIndex(sb.index() - 1);
				sb.append(NEW_LINE);
			}
		}

		String fileName = title + ".csv";
		byte[] bytes = sb.toString().getBytes();
		String contentType = ContentTypes.APPLICATION_TEXT;

		PortletResponseUtil.sendFile(resourceRequest, resourceResponse, fileName, bytes, contentType);
	}

	@Override
	public void processAction(ActionRequest actionRequest, ActionResponse actionResponse)
			throws IOException, PortletException {

		String command = ParamUtil.getString(actionRequest, "command");
		ServiceContext serviceContext = ServiceContextThreadLocal.getServiceContext();
		if (null == serviceContext) {

			try {
				switch (command) {
				case COMMAND_SAVE:
					saveData(actionRequest, actionResponse);
					break;
				case COMMAND_DELETE:
					deleteData(actionRequest, actionResponse);
					break;
				default:
					break;
				}

			} catch (Exception e) {
				throw new PortletException(e.getMessage(), e);
			}
		}

		super.processAction(actionRequest, actionResponse);
	}

	protected String getCSVFormattedValue(String value) {
		StringBundler sb = new StringBundler(3);
		sb.append(QUOTE);
		sb.append(StringUtil.replace(value, QUOTE, DOUBLE_QUOTE));
		sb.append(QUOTE);
		return sb.toString();
	}

	protected String getMailBody(Map<String, String> fieldsMap) {
		String mailBody = "";

		for (String fieldLabel : fieldsMap.keySet()) {
			String fieldValue = fieldsMap.get(fieldLabel);
			mailBody += fieldLabel + " : " + fieldValue;
			mailBody += NEW_LINE;
		}

		return mailBody;
	}

	protected boolean saveDatabase(long companyId, Map<String, String> fieldsMap, PortletPreferences preferences,
			String databaseTableName) throws PortalException {

		FormUtil.checkTable(companyId, databaseTableName, preferences);
		long classPK = CounterLocalServiceUtil.increment(FormUtil.class.getName());
		String formClassName = FormUtil.class.getName();

		for (String fieldLabel : fieldsMap.keySet()) {
			String fieldValue = fieldsMap.get(fieldLabel);

			ExpandoValueLocalServiceUtil.addValue(companyId, formClassName, databaseTableName, fieldLabel, classPK,
					fieldValue);
		}

		return true;
	}

	protected boolean saveFile(Map<String, String> fieldsMap, String fileName) throws IOException {
		StringBuilder sb = new StringBuilder();
		sb.append(fieldsMap.keySet().stream().map(this::getCSVFormattedValue)
				.collect(joining(formPortletConfiguration.csvSeparator())));

		sb.append(NEW_LINE);
		FileUtil.write(fileName, sb.toString(), false, true);
		return true;
	}

	protected boolean sendEmail(long companyId, Map<String, String> fieldsMap, PortletPreferences preferences)
			throws PortalException {

		String emailAddresses = preferences.getValue("emailAddress", BLANK);

		if (Validator.isNull(emailAddresses)) {
			_log.error("The web form email cannot be sent because no email address is configured");
			return false;
		}

		String emailfromAddress = FormUtil.getEmailFromAddress(preferences, companyId);
		String emailFromName = FormUtil.getEmailFromName(preferences, companyId);

		try {
			InternetAddress fromAddress = new InternetAddress(emailfromAddress, emailFromName);
			String subject = preferences.getValue("subject", BLANK);
			String body = getMailBody(fieldsMap);

			MailMessage mailMessage = new MailMessage(fromAddress, subject, body, false);
			InternetAddress[] toAddresses = InternetAddress.parse(emailAddresses);
			mailMessage.setTo(toAddresses);
			MailServiceUtil.sendEmail(mailMessage);
		} catch (Exception e) {
			throw new PortalException(e.getMessage(), e);
		}
		return true;
	}

	protected void serveCaptcha(ResourceRequest resourceRequest, ResourceResponse resourceResponse) throws Exception {
		CaptchaUtil.serveImage(resourceRequest, resourceResponse);
	}

	protected Set<String> validate(Map<String, String> fieldsMap, PortletPreferences preferences) throws Exception {

		Set<String> validationErrors = new HashSet<>();
		String debugMsg = "";

		for (int i = 0; i < fieldsMap.size(); i++) {

			String fieldType = preferences.getValue("fieldType" + (i + 1), BLANK);
			String fieldLabel = preferences.getValue("fieldLabel" + (i + 1), BLANK);
			String fieldValue = fieldsMap.get(fieldLabel);

			boolean fieldOptional = GetterUtil.getBoolean(preferences.getValue("fieldOptional" + (i + 1), BLANK));

			debugMsg += "Validating fieldType " + (i + 1) + ": " + fieldType;
			debugMsg += "Validating fieldLabel " + (i + 1) + ": " + fieldLabel;
			debugMsg += "Validating fieldOptional " + (i + 1) + ": " + fieldOptional;

			if (fieldType.equalsIgnoreCase("paragraph")) {
				continue;
			}

			if (!fieldOptional && Validator.isNotNull(fieldLabel) && Validator.isNull(fieldValue)) {
				validationErrors.add(fieldLabel);
				continue;
			}

			if (!formPortletConfiguration.isValidationScriptEnabled()) {
				continue;
			}

			String validationScript = GetterUtil
					.getString(preferences.getValue("fieldValidationScript" + (i + 1), BLANK));

			if (Validator.isNotNull(validationScript) && !FormUtil.validate(fieldValue, fieldsMap, validationScript)) {
				validationErrors.add(fieldLabel);
				debugMsg += validationErrors;
				continue;
			}

			_log.debug(debugMsg);
		}

		return validationErrors;
	}

}