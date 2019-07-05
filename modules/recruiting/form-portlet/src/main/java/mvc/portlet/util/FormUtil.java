package mvc.portlet.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.portlet.PortletPreferences;

import com.liferay.counter.kernel.service.CounterLocalServiceUtil;
import com.liferay.expando.kernel.exception.NoSuchTableException;
import com.liferay.expando.kernel.model.ExpandoColumnConstants;
import com.liferay.expando.kernel.model.ExpandoTable;
import com.liferay.expando.kernel.service.ExpandoColumnLocalServiceUtil;
import com.liferay.expando.kernel.service.ExpandoRowLocalServiceUtil;
import com.liferay.expando.kernel.service.ExpandoTableLocalServiceUtil;
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
import com.liferay.portal.kernel.util.StringBundler;
import com.liferay.portal.kernel.util.StringPool;
import com.liferay.portal.kernel.util.StringUtil;
import com.liferay.portal.kernel.util.Validator;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;

import mvc.portlet.configuration.FormPortletConfiguration;
import mvc.portlet.constants.FormPortletKeys;

@Component(
	configurationPid = FormPortletKeys.MVC_PORTLET_CONFIGURATION_PID	
)
public class FormUtil {

	public static ExpandoTable addTable(long companyId, String tableName)
		throws PortalException, SystemException {

		try {
			ExpandoTableLocalServiceUtil.deleteTable(
				companyId, FormUtil.class.getName(), tableName);
		}
		catch (NoSuchTableException nste) {
		}

		return ExpandoTableLocalServiceUtil.addTable(
			companyId, FormUtil.class.getName(), tableName);
	}

	public static ExpandoTable checkTable(
			long companyId, String tableName, PortletPreferences preferences)
		throws Exception {

		ExpandoTable expandoTable = null;

		try {
			DynamicQuery query = DynamicQueryFactoryUtil.forClass(ExpandoTable.class).add(PropertyFactoryUtil.forName("tableName").eq(tableName));
			List<ExpandoTable> li=ExpandoTableLocalServiceUtil.dynamicQuery(query);			
			if (li == null || li.size() == 0) {
				expandoTable = addTable(companyId, tableName);

				int i = 1;
	
				String fieldLabel = preferences.getValue(
					"fieldLabel" + i, StringPool.BLANK);
				String fieldType = preferences.getValue(
					"fieldType" + i, StringPool.BLANK);
	
				while ((i == 1) || Validator.isNotNull(fieldLabel)) {
					if (!StringUtil.equalsIgnoreCase(fieldType, "paragraph")) {
						ExpandoColumnLocalServiceUtil.addColumn(
							expandoTable.getTableId(), fieldLabel,
							ExpandoColumnConstants.STRING);
					}
	
					i++;
	
					fieldLabel = preferences.getValue(
						"fieldLabel" + i, StringPool.BLANK);
					fieldType = preferences.getValue(
						"fieldType" + i, StringPool.BLANK);
				}				
			}
		}
		catch (Exception nste) {
			expandoTable = addTable(companyId, tableName);

			int i = 1;

			String fieldLabel = preferences.getValue(
				"fieldLabel" + i, StringPool.BLANK);
			String fieldType = preferences.getValue(
				"fieldType" + i, StringPool.BLANK);

			while ((i == 1) || Validator.isNotNull(fieldLabel)) {
				if (!StringUtil.equalsIgnoreCase(fieldType, "paragraph")) {
					ExpandoColumnLocalServiceUtil.addColumn(
						expandoTable.getTableId(), fieldLabel,
						ExpandoColumnConstants.STRING);
				}

				i++;

				fieldLabel = preferences.getValue(
					"fieldLabel" + i, StringPool.BLANK);
				fieldType = preferences.getValue(
					"fieldType" + i, StringPool.BLANK);
			}
		}

		return expandoTable;
	}

	public static String getEmailFromAddress(
			PortletPreferences preferences, long companyId)
		throws SystemException {

		return PortalUtil.getEmailFromAddress(
			preferences, companyId, formPortletConfiguration.emailFromAddress());
	}

	public static String getEmailFromName(
			PortletPreferences preferences, long companyId)
		throws SystemException {

		return PortalUtil.getEmailFromName(
			preferences, companyId, formPortletConfiguration.emailFromName());
	}

	public static String getFileName(
		ThemeDisplay themeDisplay, String portletId) {

		StringBuffer sb = new StringBuffer(8);

		sb.append(formPortletConfiguration.dataRootDir());
		sb.append(StringPool.FORWARD_SLASH);
		sb.append(themeDisplay.getScopeGroupId());
		sb.append("/");
		sb.append(themeDisplay.getPlid());
		sb.append(StringPool.FORWARD_SLASH);
		sb.append(portletId);
		sb.append(".csv");

		return sb.toString();
	}

	public static String getNewDatabaseTableName(String portletId)
		throws SystemException {

		long formId = CounterLocalServiceUtil.increment(
			FormUtil.class.getName());

		return portletId + StringPool.UNDERLINE + formId;
	}

	public static int getTableRowsCount(long companyId, String tableName)
		throws SystemException {

		return ExpandoRowLocalServiceUtil.getRowsCount(
			companyId, FormUtil.class.getName(), tableName);
	}

	public static String[] split(String s) {
		return split(s, StringPool.COMMA);
	}

	public static String[] split(String s, String delimiter) {
		if ((s == null) || (delimiter == null)) {
			return new String[0];
		}

		s = s.trim();

		if (!s.endsWith(delimiter)) {
			s = s.concat(delimiter);
		}

		if (s.equals(delimiter)) {
			return new String[0];
		}

		List<String> nodeValues = new ArrayList<String>();

		if (delimiter.equals("\n") || delimiter.equals("\r")) {
			try {
				BufferedReader br = new BufferedReader(new StringReader(s));

				String line = null;

				while ((line = br.readLine()) != null) {
					nodeValues.add(line);
				}

				br.close();
			}
			catch (IOException ioe) {
				ioe.printStackTrace();
			}
		}
		else {
			int offset = 0;
			int pos = s.indexOf(delimiter, offset);

			while (pos != -1) {
				nodeValues.add(new String(s.substring(offset, pos)));

				offset = pos + delimiter.length();
				pos = s.indexOf(delimiter, offset);
			}
		}

		return nodeValues.toArray(new String[nodeValues.size()]);
	}

	public static boolean validate(
			String currentFieldValue, Map<String, String> fieldsMap,
			String validationScript)
		throws Exception {

		boolean validationResult = false;

		Context cx = Context.enter();

		StringBundler sb = new StringBundler();

		sb.append("currentFieldValue = String('");
		sb.append(HtmlUtil.escapeJS(currentFieldValue));
		sb.append("');\n");

		sb.append("var fieldsMap = {};\n");

		for (String key : fieldsMap.keySet()) {
			sb.append("fieldsMap['");
			sb.append(key);
			sb.append("'] = '");

			String value = StringUtil.replace(
				fieldsMap.get(key), new String[] {"\r\n", "\r", StringPool.NEW_LINE},
				new String[] {"\\n", "\\n", "\\n"});

			sb.append(HtmlUtil.escapeJS(value));
			sb.append("';\n");
		}

		sb.append("function validation(currentFieldValue, fieldsMap) {\n");
		sb.append(validationScript);
		sb.append("}\n");
		sb.append("internalValidationResult = ");
		sb.append("validation(currentFieldValue, fieldsMap);");

		String script = sb.toString();

		try {
			Scriptable scope = cx.initStandardObjects();

			Object jsFieldsMap = Context.toObject(fieldsMap, scope);

			ScriptableObject.putProperty(scope, "jsFieldsMap", jsFieldsMap);

			cx.evaluateString(scope, script, "Validation Script", 1, null);

			Object obj = ScriptableObject.getProperty(
				scope, "internalValidationResult");

			if (obj instanceof Boolean) {
				validationResult = (Boolean)obj;
			}
			else {
				throw new Exception("Exception");
			}
		}
		catch (Exception e) {
			String msg =
				"The following script has execution errors:\n" +
					validationScript + "\n" + e.getMessage();

			System.out.println(msg);

			throw new Exception(msg, e);
		}
		finally {
			Context.exit();
		}

		return validationResult;
	}
	
	@Activate
	@Modified
	protected void activate(Map<Object, Object> properties) {
		formPortletConfiguration = ConfigurableUtil.createConfigurable(FormPortletConfiguration.class, properties);
	}

	private volatile static FormPortletConfiguration formPortletConfiguration;
	
	private static Log _log = LogFactoryUtil.getLog(FormUtil.class);

}