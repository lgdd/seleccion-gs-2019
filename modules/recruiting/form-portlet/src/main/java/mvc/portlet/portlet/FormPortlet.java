package mvc.portlet.portlet;

import static com.liferay.petra.string.CharPool.NEW_LINE;
import static com.liferay.petra.string.CharPool.QUOTE;
import static com.liferay.petra.string.StringPool.BLANK;
import static com.liferay.petra.string.StringPool.DOUBLE_QUOTE;
import static com.liferay.portal.kernel.servlet.SessionMessages.KEY_SUFFIX_HIDE_DEFAULT_SUCCESS_MESSAGE;
import static java.util.stream.Collectors.joining;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
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
	private static final String COMMAND_CAPTCHA = "captcha";
	private static final String COMMAND_EXPORT = "export";

	private static final String GENERATED_TABLE_KEY = "databaseTableName";

	private FormPortletConfiguration formPortletConfiguration;

	@Activate
	@Modified
	protected void activate(Map<Object, Object> properties) {
		formPortletConfiguration = ConfigurableUtil.createConfigurable(FormPortletConfiguration.class, properties);
	}

	@Override
	public void serveResource(ResourceRequest resourceRequest, ResourceResponse resourceResponse) {
		String cmd = ParamUtil.getString(resourceRequest, Constants.CMD);
		try {
			switch (cmd) {
			case COMMAND_CAPTCHA:
				serveCaptcha(resourceRequest, resourceResponse);
				break;
			case COMMAND_EXPORT:
				exportData(resourceRequest, resourceResponse);
				break;
			default:
				break;
			}
		} catch (Exception e) {
			_log.error(e.getMessage(), e);
		}
	}

	@Override
	public void processAction(ActionRequest actionRequest, ActionResponse actionResponse)
			throws IOException, PortletException {

		String command = ParamUtil.getString(actionRequest, Constants.CMD);
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

	public void deleteData(ActionRequest actionRequest, ActionResponse actionResponse) throws PortalException {
		ThemeDisplay themeDisplay = (ThemeDisplay) actionRequest.getAttribute(WebKeys.THEME_DISPLAY);
		String portletId = PortalUtil.getPortletId(actionRequest);

		PortletPermissionUtil.check(themeDisplay.getPermissionChecker(), themeDisplay.getPlid(), portletId,
				ActionKeys.CONFIGURATION);

		PortletPreferences preferences = PortletPreferencesFactoryUtil.getPortletSetup(actionRequest);
		String tableName = preferences.getValue(GENERATED_TABLE_KEY, BLANK);

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

		boolean requireCaptcha = getBooleanValue(preferences, "requireCaptcha");

		if (requireCaptcha) {
			try {
				CaptchaUtil.check(actionRequest);
			} catch (CaptchaTextException cte) {
				SessionErrors.add(actionRequest, CaptchaTextException.class.getName());
				return;
			}
		}

		Map<String, String> fields = new HashMap<>();
		FormUtil.extractFields(preferences).forEach((key, val) -> {
			fields.put(key, ParamUtil.getString(actionRequest, key));
		});

		actionRequest.getPortletSession().setAttribute(SAVED_DATA_CACHE + System.currentTimeMillis(), fields);

		try {
			validate(fields, preferences).forEach(error -> SessionErrors.add(actionRequest, "error" + error));
		} catch (Exception ex) {
			SessionErrors.add(actionRequest, "validationScriptError", ex.getMessage());
		}

		if (!SessionErrors.isEmpty(actionRequest)) {
			return;
		}

		boolean emailSuccess = true;
		boolean databaseSuccess = true;
		boolean fileSuccess = true;

		boolean sendAsEmail = getBooleanValue(preferences, "sendAsEmail");

		if (sendAsEmail) {
			emailSuccess = sendEmail(companyId, fields, preferences);
		}

		boolean saveToDatabase = getBooleanValue(preferences, "saveToDatabase");

		if (saveToDatabase) {
			String databaseTableName = getValue(preferences, GENERATED_TABLE_KEY);
			if (Validator.isNull(databaseTableName)) {
				FormUtil.addTable(companyId, databaseTableName);
			}

			databaseSuccess = saveDatabase(companyId, fields, preferences, databaseTableName);
		}

		boolean saveToFile = getBooleanValue(preferences, "saveToFile");

		if (saveToFile) {
			String fileName = getValue(preferences, "fileName");
			if (!formPortletConfiguration.isDataFilePathChangeable()) {
				fileName = FormUtil.getFileName(themeDisplay, portletId);
			}

			fileSuccess = saveFile(fields, fileName);
		}

		String successURL = getValue(preferences, "successURL");

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

	protected void exportData(ResourceRequest resourceRequest, ResourceResponse resourceResponse) throws Exception {
		ThemeDisplay themeDisplay = (ThemeDisplay) resourceRequest.getAttribute(WebKeys.THEME_DISPLAY);

		String portletId = PortalUtil.getPortletId(resourceRequest);

		PortletPermissionUtil.check(themeDisplay.getPermissionChecker(), themeDisplay.getPlid(), portletId,
				ActionKeys.CONFIGURATION);

		PortletPreferences preferences = PortletPreferencesFactoryUtil.getPortletSetup(resourceRequest);
		StringBundler sb = new StringBundler();

		List<String> fieldLabels = new ArrayList<>();
		String lang = themeDisplay.getLanguageId();

		for (int i = 1; true; i++) {
			String fieldLabel = preferences.getValue("fieldLabel" + i, BLANK);
			String localizedfieldLabel = LocalizationUtil.getPreferencesValue(preferences, "fieldLabel" + i, lang);

			if (Validator.isNull(fieldLabel)) {
				break;
			}

			fieldLabels.add(fieldLabel);
			sb.append(getCSVFormattedValue(localizedfieldLabel));
			sb.append(formPortletConfiguration.csvSeparator());
		}

		sb.setIndex(sb.index() - 1);
		sb.append(NEW_LINE);

		String databaseTableName = preferences.getValue(GENERATED_TABLE_KEY, BLANK);

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

		String title = preferences.getValue("title", "no-title");
		String fileName = title + FormUtil.CSV_EXTENSION;
		byte[] bytes = sb.toString().getBytes();
		String contentType = ContentTypes.APPLICATION_TEXT;

		PortletResponseUtil.sendFile(resourceRequest, resourceResponse, fileName, bytes, contentType);
	}

	protected String getCSVFormattedValue(String value) {
		String replacedValue = StringUtil.replace(value, QUOTE, DOUBLE_QUOTE);
		return new StringBuilder().append(QUOTE).append(replacedValue).append(QUOTE).toString();
	}

	protected String getMailBody(Map<String, String> fieldsMap) {
		StringBuilder str = new StringBuilder();
		fieldsMap.forEach((fieldLabel, fieldValue) -> {
			str.append(fieldLabel).append(" : ").append(fieldValue).append(NEW_LINE);
		});
		return str.toString();
	}

	protected boolean saveDatabase(long companyId, Map<String, String> fieldsMap, PortletPreferences preferences,
			String databaseTableName) throws PortalException {

		FormUtil.checkTable(companyId, databaseTableName, preferences);
		String formClassName = FormUtil.class.getName();

		long classPK = 0;
		for (String fieldLabel : fieldsMap.keySet()) {
			classPK++;
			String fieldValue = fieldsMap.get(fieldLabel);

			ExpandoValueLocalServiceUtil.addValue(companyId, formClassName, databaseTableName, fieldLabel, classPK,
					fieldValue);
		}

		return true;
	}

	protected boolean saveFile(Map<String, String> fieldsMap, String fileName) throws IOException {
		String csvContent = fieldsMap.keySet().stream().map(this::getCSVFormattedValue)
				.collect(joining(formPortletConfiguration.csvSeparator()));

		StringBuilder sb = new StringBuilder();
		sb.append(csvContent);
		sb.append(NEW_LINE);
		FileUtil.write(fileName, sb.toString(), false, true);
		return true;
	}

	protected boolean sendEmail(long companyId, Map<String, String> fieldsMap, PortletPreferences preferences)
			throws PortalException {

		String emailAddresses = getValue(preferences, "emailAddress");

		if (Validator.isNull(emailAddresses)) {
			_log.error("The web form email cannot be sent because no email address is configured");
			return false;
		}

		String emailfromAddress = FormUtil.getEmailFromAddress(preferences, companyId);
		String emailFromName = FormUtil.getEmailFromName(preferences, companyId);

		try {
			InternetAddress fromAddress = new InternetAddress(emailfromAddress, emailFromName);
			String subject = getValue(preferences, "subject");
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

		for (int i = 0; i < fieldsMap.size(); i++) {
			int currentIndex = i + 1;
			String fieldType = getValue(preferences, "fieldType" + currentIndex);
			String fieldLabel = getValue(preferences, "fieldLabel" + currentIndex);
			String fieldValue = fieldsMap.get(fieldLabel);

			boolean fieldOptional = getBooleanValue(preferences, "fieldOptional" + currentIndex);

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

			String validationScript = getValue(preferences, "fieldValidationScript" + currentIndex);

			if (Validator.isNotNull(validationScript) && !FormUtil.validate(fieldValue, fieldsMap, validationScript)) {
				validationErrors.add(fieldLabel);
				continue;
			}
		}

		return validationErrors;
	}

	protected String getValue(PortletPreferences preferences, String key) {
		return preferences.getValue(key, BLANK);
	}

	protected boolean getBooleanValue(PortletPreferences preferences, String key) {
		return GetterUtil.getBoolean(getValue(preferences, key));
	}

}