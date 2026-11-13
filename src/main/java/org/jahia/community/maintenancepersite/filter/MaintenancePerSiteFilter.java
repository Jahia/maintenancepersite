package org.jahia.community.maintenancepersite.filter;

import javax.jcr.ItemNotFoundException;
import javax.jcr.PathNotFoundException;
import javax.jcr.RepositoryException;
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
import org.jahia.services.render.filter.cache.ClientCachePolicy;
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
 * <p>The maintenance page is rendered in {@link #prepare}, before the requested page, and returned
 * from there, which stops the render chain: the requested page is never rendered. Rendering it after
 * the requested page does not work: Jahia's page render leaves request attributes behind (for example
 * {@code templateSet} and {@code skipWrapper}, which nothing removes), and with them a second page
 * render on the same request skips the page template and produces an empty body, which the HTML
 * cache then stores for the maintenance page.</p>
 *
 * <p>On Jahia 8.2.1+ the client-cache-control bundle owns {@code Cache-Control} on rendered pages: its
 * servlet filter makes the header read-only in strict mode, and its render filter rewrites it at the end
 * of the render chain from {@link RenderContext#getClientCachePolicy()}. Setting the header alone is
 * therefore discarded (the page's fragments can yield {@code public, ..., s-maxage=3600}), so the filter
 * also raises the render context policy to {@link ClientCachePolicy#PRIVATE}, the strongest level, which
 * maps to the {@code private, no-cache, no-store, ...} template and cannot be lowered afterwards.</p>
 *
 * <p>The filter fails open: any misconfiguration (missing or dangling {@code maintenancePage}
 * reference), repository/ACL error while resolving the site, or failure rendering the maintenance
 * page is logged and the requested page is rendered normally, so a bad setting never takes the whole
 * site down with a 500.</p>
 */
@Component(service = RenderFilter.class)
public class MaintenancePerSiteFilter extends AbstractFilter {

    private static final Logger log = LoggerFactory.getLogger(MaintenancePerSiteFilter.class);

    // Package-private (not private) so the unit test can reference these directly instead of
    // duplicating the literals.
    static final String MIXIN_MAINTENANCE = "jmix:maintenancePerSite";
    static final String PROP_MAINTENANCE_PAGE = "maintenancePage";

    /** Advisory delay (seconds) sent in the {@code Retry-After} header of the 503 response. */
    static final int RETRY_AFTER_SECONDS = 3600;

    // no-store alone is sufficient per RFC 7234; no-cache + must-revalidate are belt-and-suspenders
    // for legacy intermediaries that mishandle no-store.
    private static final String NO_STORE_CACHE_CONTROL = "no-store, no-cache, must-revalidate";

    @Activate
    public void activate() {
        setPriority(3);
        setApplyOnEditMode(false);
        setSkipOnAjaxRequest(true);
        setApplyOnConfigurations("page");
        setApplyOnTemplateTypes("html,html-*");
    }

    /**
     * Returns the maintenance page body when the site is in maintenance, which stops the render chain,
     * or {@code null} to let the requested page render normally. {@link AbstractFilter#execute} then
     * passes the returned body through unchanged.
     */
    @Override
    public String prepare(RenderContext renderContext, Resource resource, RenderChain chain) throws Exception {
        if (!Constants.LIVE_WORKSPACE.equals(renderContext.getWorkspace())) {
            return null;
        }

        JCRSiteNode site;
        try {
            site = resource.getNode().getResolveSite();
            if (!site.isNodeType(MIXIN_MAINTENANCE)) {
                return null;
            }
        } catch (RepositoryException e) {
            // Fail open: a repository/ACL error while resolving the site must not turn into a 500.
            log.error("Error resolving site or checking the {} mixin for resource {}; serving normal output.",
                    MIXIN_MAINTENANCE, resource.getNodePath(), e);
            return null;
        }

        JCRNodeWrapper maintenancePage = resolveMaintenancePage(site);
        if (maintenancePage == null) {
            // Misconfigured: mixin present but no usable maintenance page. Fail open.
            return null;
        }

        // The maintenance page itself must stay reachable, otherwise it would 503 itself in a loop.
        // Loop safety: when renderMaintenancePage() below re-enters the render chain (and therefore
        // this filter) for the maintenance page, resource.getNodePath() equals maintenancePage.getPath()
        // because the nested Resource wraps the same JCR node, so this guard terminates the recursion.
        if (resource.getNodePath().equals(maintenancePage.getPath())) {
            return null;
        }

        log.debug("Site {} is in maintenance; serving maintenance page {} with HTTP 503.",
                site.getName(), maintenancePage.getPath());

        String maintenanceHtml;
        try {
            maintenanceHtml = renderMaintenancePage(maintenancePage, renderContext, resource);
        } catch (RenderException e) {
            // Fail open: a broken or unrenderable maintenance page must not turn into a 500.
            log.error("Failed to render maintenance page {} for site {}; serving normal output.",
                    maintenancePage.getPath(), site.getName(), e);
            return null;
        }
        if (maintenanceHtml == null) {
            log.error("Rendering maintenance page {} for site {} produced no output; serving normal output.",
                    maintenancePage.getPath(), site.getName());
            return null;
        }

        // The maintenance body must never be stored by a shared cache. Jahia's client-cache-control
        // render filter turns this policy into the final Cache-Control header (see the class javadoc);
        // done before the isCommitted() check because the body is served on that path too.
        renderContext.computeClientCachePolicy(ClientCachePolicy.PRIVATE);

        // Stamp the 503 only after a successful render, so a render failure leaves the normal 200
        // output untouched. Guarded by isCommitted() in case a nested filter already flushed the response.
        HttpServletResponse response = renderContext.getResponse();
        if (response.isCommitted()) {
            log.warn("Response already committed while serving maintenance page {}; 503 status/headers not applied.",
                    maintenancePage.getPath());
            return maintenanceHtml;
        }
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
        } catch (RepositoryException e) {
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
        // Render it as the main resource, the way Jahia renders a page (Render servlet, TemplateHelper):
        // AggregateCacheFilter resolves cache:include placeholders and strips cache tags only for the main
        // resource, and page templates read renderContext.mainResource (title, navigation).
        Resource previousMainResource = renderContext.getMainResource();
        renderContext.setMainResource(maintenanceResource);
        try {
            return renderResource(maintenanceResource, renderContext);
        } finally {
            renderContext.setMainResource(previousMainResource);
        }
    }

    /**
     * Package-private seam over {@link RenderService} so unit tests can stub the render.
     *
     * <p>Uses {@link RenderService#getInstance()} (the established Jahia access pattern) rather than an
     * OSGi {@code @Reference}: {@code RenderService} is a Spring-managed singleton, not a published OSGi
     * service, so an {@code @Reference} would leave this component permanently unsatisfied and never
     * activated. The singleton is non-null and fully initialised by the time a live page is rendered.</p>
     */
    String renderResource(Resource resource, RenderContext renderContext) throws RenderException {
        return RenderService.getInstance().render(resource, renderContext);
    }

}
