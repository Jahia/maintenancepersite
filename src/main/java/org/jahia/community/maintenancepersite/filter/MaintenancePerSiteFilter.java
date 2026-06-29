package org.jahia.community.maintenancepersite.filter;

import javax.jcr.ItemNotFoundException;
import javax.jcr.PathNotFoundException;
import javax.servlet.http.HttpServletResponse;

import org.jahia.api.Constants;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.decorator.JCRSiteNode;
import org.jahia.services.render.RenderContext;
import org.jahia.services.render.RenderException;
import org.jahia.services.render.RenderService;
import org.jahia.services.render.Resource;
import org.jahia.services.render.filter.AbstractFilter;
import org.jahia.services.render.filter.RenderChain;
import org.jahia.services.render.filter.RenderFilter;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Render filter that puts an individual site into maintenance.
 *
 * <p>When the resolved site carries the {@code jmix:maintenancePerSite} mixin, every live HTML page
 * request — except the configured maintenance page itself — is served the maintenance page body with
 * an HTTP {@code 503 Service Unavailable} status. Serving 503 (rather than redirecting with 302) keeps
 * search engines and uptime monitors from treating the site as healthy while it is down, and the
 * {@code no-store} cache headers ensure the maintenance state is not pinned by an HTML/CDN cache once
 * the mixin is removed.</p>
 *
 * <p>The filter fails open: any misconfiguration (missing or dangling {@code maintenancePage}
 * reference) is logged and normal output is returned, so a bad setting never takes the whole site
 * down with a 500.</p>
 */
@Component(service = RenderFilter.class)
public class MaintenancePerSiteFilter extends AbstractFilter {

    private static final Logger log = LoggerFactory.getLogger(MaintenancePerSiteFilter.class);

    static final String MIXIN_MAINTENANCE = "jmix:maintenancePerSite";
    private static final String PROP_MAINTENANCE_PAGE = "maintenancePage";

    /** Advisory delay (seconds) sent in the {@code Retry-After} header of the 503 response. */
    private static final int RETRY_AFTER_SECONDS = 3600;
    private static final String NO_STORE_CACHE_CONTROL = "no-store, no-cache, must-revalidate";

    @Activate
    public void activate() {
        setPriority(3);
        setApplyOnEditMode(false);
        setSkipOnAjaxRequest(true);
        setApplyOnConfigurations("page");
        setApplyOnTemplateTypes("html,html-*");
    }

    @Override
    public String execute(String previousOut, RenderContext renderContext, Resource resource, RenderChain chain) throws Exception {
        String output = super.execute(previousOut, renderContext, resource, chain);

        if (!Constants.LIVE_WORKSPACE.equals(renderContext.getWorkspace())) {
            return output;
        }

        JCRSiteNode site = resource.getNode().getResolveSite();
        if (!site.isNodeType(MIXIN_MAINTENANCE)) {
            return output;
        }

        JCRNodeWrapper maintenancePage = resolveMaintenancePage(site);
        if (maintenancePage == null) {
            // Misconfigured: mixin present but no usable maintenance page. Fail open.
            return output;
        }

        // The maintenance page itself must stay reachable, otherwise it would 503 itself in a loop.
        if (resource.getNodePath().equals(maintenancePage.getPath())) {
            return output;
        }

        log.debug("Site {} is in maintenance; serving maintenance page {} with HTTP 503.",
                site.getName(), maintenancePage.getPath());

        String maintenanceHtml = renderMaintenancePage(maintenancePage, renderContext, resource);

        HttpServletResponse response = renderContext.getResponse();
        response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        response.setHeader("Retry-After", String.valueOf(RETRY_AFTER_SECONDS));
        response.setHeader("Cache-Control", NO_STORE_CACHE_CONTROL);
        response.setHeader("Pragma", "no-cache");
        return maintenanceHtml;
    }

    /**
     * Resolves the configured maintenance page, failing open (returning {@code null}) on any
     * misconfiguration so the site is never taken down by a bad setting.
     */
    private JCRNodeWrapper resolveMaintenancePage(JCRSiteNode site) {
        try {
            if (!site.hasProperty(PROP_MAINTENANCE_PAGE)) {
                log.warn("Mixin {} is set on site {}, but the {} property is not set.",
                        MIXIN_MAINTENANCE, site.getName(), PROP_MAINTENANCE_PAGE);
                return null;
            }
            JCRNodeWrapper maintenancePage = (JCRNodeWrapper) site.getProperty(PROP_MAINTENANCE_PAGE).getNode();
            if (maintenancePage == null) {
                log.warn("Mixin {} is set on site {}, but no valid maintenance page was found.",
                        MIXIN_MAINTENANCE, site.getName());
            }
            return maintenancePage;
        } catch (ItemNotFoundException | PathNotFoundException e) {
            log.warn("The {} reference on site {} does not resolve to a page; serving normal output.",
                    PROP_MAINTENANCE_PAGE, site.getName(), e);
            return null;
        } catch (javax.jcr.RepositoryException e) {
            log.error("Error resolving the {} property on site {}; serving normal output.",
                    PROP_MAINTENANCE_PAGE, site.getName(), e);
            return null;
        }
    }

    /**
     * Renders the maintenance page body. Package-private seam so unit tests can stub the render
     * without standing up a full Jahia render engine.
     */
    String renderMaintenancePage(JCRNodeWrapper maintenancePage, RenderContext renderContext, Resource currentResource) throws RenderException {
        Resource maintenanceResource = new Resource(maintenancePage, currentResource.getTemplateType(), null, Resource.CONFIGURATION_PAGE);
        return RenderService.getInstance().render(maintenanceResource, renderContext);
    }

}
