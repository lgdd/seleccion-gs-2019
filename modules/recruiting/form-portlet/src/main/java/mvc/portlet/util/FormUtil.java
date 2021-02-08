package mvc.portlet.util;

import static com.liferay.petra.string.StringPool.BLANK;
import static com.liferay.petra.string.StringPool.FORWARD_SLASH;
import static com.liferay.petra.string.StringPool.NEW_LINE;
import static com.liferay.portal.kernel.util.StringUtil.equalsIgnoreCase;

import java.util.List;
import java.util.Map;

import javax.portlet.PortletPreferences;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;

import com.liferay.counter.kernel.service.CounterLocalServiceUtil;
import com.liferay.expando.kernel.model.ExpandoColumnConstants;
import com.liferay.expando.kernel.model.ExpandoTable;
import com.liferay.expando.kernel.service.ExpandoColumnLocalServiceUtil;
import com.liferay.expando.kernel.service.ExpandoRowLocalServiceUtil;
import com.liferay.expando.kernel.service.ExpandoTableLocalServiceUtil;
import com.liferay.petra.string.StringPool;
import com.liferay.portal.configuration.metatype.bnd.util.ConfigurableUtil;
import com.liferay.portal.kernel.dao.orm.DynamicQuery;
import com.liferay.portal.kernel.dao.orm.DynamicQueryFactoryUtil;
import com.liferay.portal.kernel.dao.orm.PropertyFactoryUtil;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.exception.SystemException;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.theme.ThemeDisplay;
import com.liferay.portal.kernel.util.HtmlUtil;
import com.liferay.portal.kernel.util.PortalUtil;
import com.liferay.portal.kernel.util.StringUtil;
import com.liferay.portal.kernel.util.Validator;

import mvc.portlet.configuration.FormPortletConfiguration;
import mvc.portlet.constants.FormPortletKeys;

@Component(configurationPid = FormPortletKeys.MVC_PORTLET_CONFIGURATION_PID)
public class FormUtil {

	private static final Log _log = LogFactoryUtil.getLog(FormUtil.class);
	private static final String CSV_EXTENSION = ".csv";

	private volatile static FormPortletConfiguration formPortletConfiguration;

	@Activate
	@Modified
	protected void activate(Map<Object, Object> properties) {
		formPortletConfiguration = ConfigurableUtil.createConfigurable(FormPortletConfiguration.class, properties);
	}

	public static ExpandoTable checkTable(long companyId, String tableName, PortletPreferences preferences)
			throws PortalException {

		ExpandoTable expandoTable = null;

		DynamicQuery query = DynamicQueryFactoryUtil.forClass(ExpandoTable.class)
				.add(PropertyFactoryUtil.forName("tableName").eq(tableName));

		List<ExpandoTable> li = ExpandoTableLocalServiceUtil.dynamicQuery(query);
		if (li == null || li.size() == 0) {
			expandoTable = addTable(companyId, tableName);
			addDynamicFields(expandoTable, preferences);
		}

		return expandoTable;
	}

	public static ExpandoTable addTable(long companyId, String tableName) throws PortalException {
		deleteTable(companyId, tableName);
		return ExpandoTableLocalServiceUtil.addTable(companyId, FormUtil.class.getName(), tableName);
	}

	public static void deleteTable(long companyId, String tableName) throws PortalException {
		ExpandoTableLocalServiceUtil.deleteTable(companyId, FormUtil.class.getName(), tableName);
	}

	private static void addDynamicFields(ExpandoTable expandoTable, PortletPreferences preferences)
			throws PortalException {

		int i = 1;
		String fieldLabel;

		do {
			fieldLabel = preferences.getValue("fieldLabel" + i, BLANK);
			String fieldType = preferences.getValue("fieldType" + i, BLANK);

			if (!BLANK.equals(fieldLabel) && !equalsIgnoreCase(fieldType, "paragraph")) {
				ExpandoColumnLocalServiceUtil.addColumn(expandoTable.getTableId(), fieldLabel,
						ExpandoColumnConstants.STRING);
			}

			i++;

		} while (Validator.isNotNull(fieldLabel));
	}

	public static String getEmailFromAddress(PortletPreferences preferences, long companyId) throws SystemException {
		return PortalUtil.getEmailFromAddress(preferences, companyId, formPortletConfiguration.emailFromAddress());
	}

	public static String getEmailFromName(PortletPreferences preferences, long companyId) throws SystemException {
		return PortalUtil.getEmailFromName(preferences, companyId, formPortletConfiguration.emailFromName());
	}

	public static String getFileName(ThemeDisplay themeDisplay, String portletId) {
		StringBuffer sb = new StringBuffer(8);
		sb.append(formPortletConfiguration.dataRootDir());
		sb.append(FORWARD_SLASH);
		sb.append(themeDisplay.getScopeGroupId());
		sb.append(FORWARD_SLASH);
		sb.append(themeDisplay.getPlid());
		sb.append(FORWARD_SLASH);
		sb.append(portletId);
		sb.append(CSV_EXTENSION);
		return sb.toString();
	}

	public static String getNewDatabaseTableName(String portletId) throws SystemException {
		long formId = CounterLocalServiceUtil.increment(FormUtil.class.getName());
		return portletId + StringPool.UNDERLINE + formId;
	}

	public static int getTableRowsCount(long companyId, String tableName) throws SystemException {
		return ExpandoRowLocalServiceUtil.getRowsCount(companyId, FormUtil.class.getName(), tableName);
	}

	public static boolean validate(String currentFieldValue, Map<String, String> fieldsMap, String validationScript)
			throws Exception {

		boolean validationResult = false;

		StringBuilder sb = new StringBuilder();
		sb.append("currentFieldValue = String('");
		sb.append(HtmlUtil.escapeJS(currentFieldValue));
		sb.append("');\n");

		sb.append("var fieldsMap = {};").append(NEW_LINE);

		for (String key : fieldsMap.keySet()) {
			sb.append("fieldsMap['").append(key).append("'] = '");

			String value = StringUtil.replace(fieldsMap.get(key), new String[] { "\r\n", "\r", NEW_LINE },
					new String[] { "\\n", "\\n", "\\n" });

			sb.append(HtmlUtil.escapeJS(value));
			sb.append("';\n");
		}

		sb.append("function validation(currentFieldValue, fieldsMap) {").append(NEW_LINE);
		sb.append(validationScript);
		sb.append("}").append(NEW_LINE);
		sb.append("internalValidationResult = validation(currentFieldValue, fieldsMap);");

		String script = sb.toString();
		Context cx = Context.enter();

		try {

			Scriptable scope = cx.initStandardObjects();
			Object jsFieldsMap = Context.toObject(fieldsMap, scope);
			ScriptableObject.putProperty(scope, "jsFieldsMap", jsFieldsMap);
			cx.evaluateString(scope, script, "Validation Script", 1, null);

			Object obj = ScriptableObject.getProperty(scope, "internalValidationResult");

			if (obj instanceof Boolean) {
				validationResult = (Boolean) obj;
			} else {
				throw new Exception("Boolean result expected");
			}
		} catch (Exception e) {
			_log.error(e.getMessage(), e);
			String msg = "The following script has execution errors:\n" + validationScript + "\n" + e.getMessage();
			throw new Exception(msg, e);
		} finally {
			Context.exit();
		}

		return validationResult;
	}

}