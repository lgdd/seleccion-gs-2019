package mvc.portlet.configuration;

import javax.portlet.ActionRequest;
import javax.portlet.ActionResponse;
import javax.portlet.PortletConfig;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;

import com.liferay.portal.kernel.portlet.ConfigurationAction;
import com.liferay.portal.kernel.portlet.DefaultConfigurationAction;
import com.liferay.portal.kernel.util.ParamUtil;

import mvc.portlet.constants.FormPortletKeys;

/**
 * @author Liferay
 */
@Component(
	configurationPid = FormPortletKeys.MVC_PORTLET_CONFIGURATION_PID, 
	configurationPolicy = ConfigurationPolicy.OPTIONAL, 
	immediate = true, 
	property = "javax.portlet.name=" + FormPortletKeys.MVC_PORTLET_NAME, 
	service = ConfigurationAction.class
)
public class FormPortletConfigurationAction extends DefaultConfigurationAction {

	private static final String EMAIL_VAlIDATION_REGEX = "^[A-Za-z0-9]+@[A-Za-z0-9]+\\.{es|com|net}$";

	@Override
	public void processAction(PortletConfig portletConfig, ActionRequest actionRequest, ActionResponse actionResponse)
			throws Exception {

		String emf = ParamUtil.getString(actionRequest, "emailFromAddress").trim();
		validateEmail(emf);

		String dataRootDir = ParamUtil.getString(actionRequest, "dataRootDir");
		String emailFromName = ParamUtil.getString(actionRequest, "emailFromName");
		String isDataFilePathChangeable = ParamUtil.getString(actionRequest, "isDataFilePathChangeable");
		String isValidationScriptEnabled = ParamUtil.getString(actionRequest, "isValidationScriptEnabled");
		String csvSeparator = ParamUtil.getString(actionRequest, "csvSeparator");

		setPreference(actionRequest, "csvSeparator", csvSeparator);
		setPreference(actionRequest, "dataRootDir", dataRootDir);
		setPreference(actionRequest, "emailFromAddress", emf);
		setPreference(actionRequest, "emailFromName", emailFromName);
		setPreference(actionRequest, "isDataFilePathChangeable", isDataFilePathChangeable);
		setPreference(actionRequest, "isValidationScriptEnabled", isValidationScriptEnabled);
		super.processAction(portletConfig, actionRequest, actionResponse);
	}

	protected void validateEmail(String email) throws Exception {
		if (!email.matches(EMAIL_VAlIDATION_REGEX)) {
			throw new Exception(String.format("invalid email address %s", email));
		}
	}

}