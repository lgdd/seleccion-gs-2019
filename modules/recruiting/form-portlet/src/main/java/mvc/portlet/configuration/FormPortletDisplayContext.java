package mvc.portlet.configuration;

import javax.portlet.PortletRequest;
import javax.servlet.http.HttpServletRequest;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.model.Layout;
import com.liferay.portal.kernel.module.configuration.ConfigurationProvider;
import com.liferay.portal.kernel.theme.PortletDisplay;
import com.liferay.portal.kernel.theme.ThemeDisplay;
import com.liferay.portal.kernel.util.PortalUtil;
import com.liferay.portal.kernel.util.WebKeys;

@Component
public class FormPortletDisplayContext {

	private static final Log log = LogFactoryUtil.getLog(FormPortletDisplayContext.class);

	private FormPortletConfiguration formPortletConfig;
	private ConfigurationProvider configProvider;

	public FormPortletDisplayContext() {
	}

	public FormPortletDisplayContext(final PortletRequest req) {
		this(PortalUtil.getHttpServletRequest(req));
	}

	public FormPortletConfiguration getFormPortletConfiguration() {
		return formPortletConfig;
	}

	@Reference
	protected void setConfigurationProvider(ConfigurationProvider configProvider) {
		this.configProvider = configProvider;
	}

	public FormPortletDisplayContext(final HttpServletRequest req) {
		ThemeDisplay themeDisplay = (ThemeDisplay) req.getAttribute(WebKeys.THEME_DISPLAY);
		PortletDisplay portletDisplay = themeDisplay.getPortletDisplay();
		if (null == portletDisplay.getId()) {
			Layout layout = themeDisplay.getLayout();
			try {
				formPortletConfig = configProvider.getPortletInstanceConfiguration(FormPortletConfiguration.class,
						layout, PortalUtil.getPortletId(req));
			} catch (Exception ce) {
				log.error(ce.getMessage(), ce);
			}
		} else {
			try {
				formPortletConfig = portletDisplay.getPortletInstanceConfiguration(FormPortletConfiguration.class);
			} catch (Exception ce) {
				log.error(ce.getMessage(), ce);
			}
		}
	}

}
