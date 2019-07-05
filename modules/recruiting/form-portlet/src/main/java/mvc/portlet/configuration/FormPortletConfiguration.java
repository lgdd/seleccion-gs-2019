package mvc.portlet.configuration;

import com.liferay.portal.configuration.metatype.annotations.ExtendedObjectClassDefinition;

import aQute.bnd.annotation.metatype.Meta;
import mvc.portlet.constants.FormPortletKeys;

@ExtendedObjectClassDefinition(
	category = "form-portlet-category",
	scope = ExtendedObjectClassDefinition.Scope.PORTLET_INSTANCE
)
@Meta.OCD(
	localization = "content/Language",
	name = "MvcPortlet.portlet.instance.configuration.name",	
	id = FormPortletKeys.MVC_PORTLET_CONFIGURATION_PID
)
public interface FormPortletConfiguration {

	@Meta.AD(required = false)
	public String csvSeparator();

	@Meta.AD(required = false)
	public boolean isDataFilePathChangeable();

	@Meta.AD(required = false)
	public String dataRootDir();

	@Meta.AD(required = false)
	public String emailFromAddress();

	@Meta.AD(required = false)
	public String emailFromName();

	@Meta.AD(required = false)
	public boolean isValidationScriptEnabled();

}